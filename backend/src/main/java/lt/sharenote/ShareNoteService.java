package lt.sharenote;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static lt.sharenote.Models.*;

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
        if (input.organizationId() != null) organization(input.organizationId());
        return db.sql("INSERT INTO groups(name, description, organization_id) VALUES (?, ?, ?) RETURNING *")
                .param(input.name()).param(input.description()).param(input.organizationId())
                .query(Group.class).single();
    }
    public Group updateGroup(long id, GroupInput input) {
        group(id);
        if (input.organizationId() != null) organization(input.organizationId());
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
        group(input.groupId());
        return db.sql("INSERT INTO notes(title, content, category, group_id) VALUES (?, ?, ?, ?) RETURNING *")
                .param(input.title()).param(input.content()).param(input.category()).param(input.groupId())
                .query(Note.class).single();
    }
    public Note updateNote(long id, NoteInput input) {
        note(id);
        group(input.groupId());
        return db.sql("UPDATE notes SET title = ?, content = ?, category = ?, group_id = ? WHERE id = ? RETURNING *")
                .param(input.title()).param(input.content()).param(input.category()).param(input.groupId()).param(id)
                .query(Note.class).optional().orElseThrow(() -> missing("Note", id));
    }
    public void deleteNote(long id) {
        if (db.sql("DELETE FROM notes WHERE id = ?").param(id).update() == 0) throw missing("Note", id);
    }
}
