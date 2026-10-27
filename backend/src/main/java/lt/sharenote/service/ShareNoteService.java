package lt.sharenote.service;

import lt.sharenote.exception.ApiException;
import lt.sharenote.pagination.Pagination;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import static lt.sharenote.model.Models.*;

@Service
@Transactional
public class ShareNoteService {
    private final JdbcClient db;
    public ShareNoteService(JdbcClient db) { this.db = db; }

    private ApiException missing(String resource, long id) {
        return new ApiException(HttpStatus.NOT_FOUND, resource + " (id=" + id + ") was not found.");
    }

    public List<Organization> organizations() {
        return db.sql("SELECT * FROM organizations ORDER BY id").query(Organization.class).list();
    }
    public List<Organization> organizations(Pagination pagination) {
        if (pagination == null) return organizations();
        return db.sql("SELECT * FROM organizations ORDER BY id LIMIT ? OFFSET ?")
                .param(pagination.size()).param(pagination.offset()).query(Organization.class).list();
    }
    public List<Group> groups(long organizationId, Pagination pagination) {
        if (pagination == null) return groups(organizationId);
        organization(organizationId);
        return db.sql("SELECT * FROM groups WHERE organization_id = ? ORDER BY id LIMIT ? OFFSET ?")
                .param(organizationId).param(pagination.size()).param(pagination.offset()).query(Group.class).list();
    }
    public List<Note> notes(long organizationId, long groupId, Pagination pagination) {
        if (pagination == null) return notes(organizationId, groupId);
        group(organizationId, groupId);
        return db.sql("SELECT * FROM notes WHERE group_id = ? ORDER BY id LIMIT ? OFFSET ?")
                .param(groupId).param(pagination.size()).param(pagination.offset()).query(Note.class).list();
    }
    public Organization organization(long id) {
        return db.sql("SELECT * FROM organizations WHERE id = ?").param(id)
                .query(Organization.class).optional().orElseThrow(() -> missing("Organization", id));
    }
    public Organization createOrganization(OrganizationInput input) {
        return db.sql("INSERT INTO organizations(name, description) VALUES (?, ?) RETURNING *")
                .param(input.name()).param(input.description()).query(Organization.class).single();
    }
    public Organization updateOrganization(long id, OrganizationInput input) {
        return db.sql("UPDATE organizations SET name = ?, description = ? WHERE id = ? RETURNING *")
                .param(input.name()).param(input.description()).param(id).query(Organization.class)
                .optional().orElseThrow(() -> missing("Organization", id));
    }
    public void deleteOrganization(long id) {
        if (db.sql("DELETE FROM organizations WHERE id = ?").param(id).update() == 0)
            throw missing("Organization", id);
    }

    public List<Group> groups(Long organizationId) {
        if (organizationId == null)
            return db.sql("SELECT * FROM groups ORDER BY id").query(Group.class).list();
        organization(organizationId);
        return db.sql("SELECT * FROM groups WHERE organization_id = ? ORDER BY id")
                .param(organizationId).query(Group.class).list();
    }
    public Group group(long id) {
        return db.sql("SELECT * FROM groups WHERE id = ?").param(id).query(Group.class)
                .optional().orElseThrow(() -> missing("Group", id));
    }
    public Group createGroup(GroupInput input) {
        organization(input.organizationId());
        return db.sql("INSERT INTO groups(name, description, organization_id) VALUES (?, ?, ?) RETURNING *")
                .param(input.name()).param(input.description()).param(input.organizationId())
                .query(Group.class).single();
    }
    public Group updateGroup(long id, GroupInput input) {
        group(id);
        organization(input.organizationId());
        return db.sql("UPDATE groups SET name = ?, description = ?, organization_id = ? WHERE id = ? RETURNING *")
                .param(input.name()).param(input.description()).param(input.organizationId())
                .param(id).query(Group.class).optional().orElseThrow(() -> missing("Group", id));
    }
    public void deleteGroup(long id) {
        if (db.sql("DELETE FROM groups WHERE id = ?").param(id).update() == 0) throw missing("Group", id);
    }

    public List<Note> notes(Long groupId) {
        if (groupId == null) return db.sql("SELECT * FROM notes ORDER BY id").query(Note.class).list();
        group(groupId);
        return db.sql("SELECT * FROM notes WHERE group_id = ? ORDER BY id").param(groupId).query(Note.class).list();
    }
    public Note note(long id) {
        return db.sql("SELECT * FROM notes WHERE id = ?").param(id).query(Note.class)
                .optional().orElseThrow(() -> missing("Note", id));
    }
    public Note createNote(NoteInput input) {
        var actor = noteWriter();
        group(input.groupId());
        return db.sql("INSERT INTO notes(title, content, category, group_id, owner_id, priority) VALUES (?, ?, ?, ?, ?, ?) RETURNING *")
                .param(input.title()).param(input.content()).param(input.category()).param(input.groupId())
                .param(Long.parseLong(actor.getToken().getSubject()))
                .param(input.priority())
                .query(Note.class).single();
    }
    public Note updateNote(long id, NoteInput input) {
        requireNoteOwner(id);
        group(input.groupId());
        return db.sql("UPDATE notes SET title = ?, content = ?, category = ?, group_id = ?, priority = ? WHERE id = ? RETURNING *")
                .param(input.title()).param(input.content()).param(input.category()).param(input.groupId()).param(input.priority()).param(id)
                .query(Note.class).optional().orElseThrow(() -> missing("Note", id));
    }
    public void deleteNote(long id) {
        requireNoteOwner(id);
        if (db.sql("DELETE FROM notes WHERE id = ?").param(id).update() == 0) throw missing("Note", id);
    }

    private JwtAuthenticationToken noteWriter() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) ||
                jwt.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_EDITOR")))
            throw new ApiException(HttpStatus.FORBIDDEN, "Only EDITOR and ADMIN can write notes.");
        return jwt;
    }

    private void requireNoteOwner(long id) {
        var actor = noteWriter();
        var note = db.sql("SELECT * FROM notes WHERE id = ? FOR UPDATE").param(id).query(Note.class)
                .optional().orElseThrow(() -> missing("Note", id));
        boolean admin = actor.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!admin && (note.ownerId() == null || !note.ownerId().toString().equals(actor.getToken().getSubject())))
            throw new ApiException(HttpStatus.FORBIDDEN, "Editors can change only their own notes.");
    }
    // Lock the child during scoped operations so a concurrent move cannot bypass the parent check.
    public Group group(long organizationId, long id) {
        organization(organizationId);
        return db.sql("SELECT * FROM groups WHERE id = ? AND organization_id = ? FOR UPDATE")
                .param(id).param(organizationId).query(Group.class).optional()
                .orElseThrow(() -> missing("Group", id));
    }
    public Group createGroup(long organizationId, NestedGroupInput input) {
        return createGroup(new GroupInput(input.name(), input.description(), organizationId));
    }
    public Group updateGroup(long organizationId, long id, NestedGroupInput input) {
        group(organizationId, id);
        return updateGroup(id, new GroupInput(input.name(), input.description(), organizationId));
    }
    public void deleteGroup(long organizationId, long id) {
        group(organizationId, id);
        deleteGroup(id);
    }

    public List<Note> notes(long organizationId, long groupId) {
        group(organizationId, groupId);
        return notes(groupId);
    }
    public Note note(long organizationId, long groupId, long id) {
        group(organizationId, groupId);
        return db.sql("SELECT * FROM notes WHERE id = ? AND group_id = ? FOR UPDATE")
                .param(id).param(groupId).query(Note.class).optional()
                .orElseThrow(() -> missing("Note", id));
    }
    public Note createNote(long organizationId, long groupId, NestedNoteInput input) {
        group(organizationId, groupId);
        return createNote(new NoteInput(input.title(), input.content(), input.category(), groupId, input.priority()));
    }
    public Note updateNote(long organizationId, long groupId, long id, NestedNoteInput input) {
        note(organizationId, groupId, id);
        return updateNote(id, new NoteInput(input.title(), input.content(), input.category(), groupId, input.priority()));
    }
    public void deleteNote(long organizationId, long groupId, long id) {
        note(organizationId, groupId, id);
        deleteNote(id);
    }
}
