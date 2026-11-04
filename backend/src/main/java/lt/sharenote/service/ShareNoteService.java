package lt.sharenote.service;

import lt.sharenote.exception.ApiException;
import lt.sharenote.pagination.Pagination;
import java.util.List;
import org.springframework.http.HttpStatus;
import lt.sharenote.repository.OrganizationRepository;
import lt.sharenote.repository.GroupRepository;
import lt.sharenote.repository.NoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import static lt.sharenote.model.Models.*;

@Service
@Transactional
public class ShareNoteService {
    private final OrganizationRepository organizations;
    private final GroupRepository groups;
    private final NoteRepository notes;

    public ShareNoteService(OrganizationRepository organizations, GroupRepository groups, NoteRepository notes) {
        this.organizations = organizations;
        this.groups = groups;
        this.notes = notes;
    }

    private ApiException missing(String resource, long id) {
        return new ApiException(HttpStatus.NOT_FOUND, resource + " (id=" + id + ") was not found.");
    }

    public List<Organization> organizations() {
        return organizations.findAll();
    }
    public List<Organization> organizations(Pagination pagination) {
        if (pagination == null) return organizations();
        return organizations.findAll(pagination);
    }
    public List<Group> groups(long organizationId, Pagination pagination) {
        if (pagination == null) return groups(organizationId);
        organization(organizationId);
        return groups.findByOrganizationId(organizationId, pagination);
    }
    public List<Note> notes(long organizationId, long groupId, Pagination pagination) {
        if (pagination == null) return notes(organizationId, groupId);
        group(organizationId, groupId);
        return notes.findByGroupId(groupId, pagination);
    }
    public Organization organization(long id) {
        return organizations.findById(id).orElseThrow(() -> missing("Organization", id));
    }
    public Organization createOrganization(OrganizationInput input) {
        return organizations.create(input);
    }
    public Organization updateOrganization(long id, OrganizationInput input) {
        return organizations.update(id, input).orElseThrow(() -> missing("Organization", id));
    }
    public void deleteOrganization(long id) {
        if (organizations.delete(id) == 0)
            throw missing("Organization", id);
    }

    public List<Group> groups(Long organizationId) {
        if (organizationId == null)
            return groups.findAll();
        organization(organizationId);
        return groups.findByOrganizationId(organizationId);
    }
    public Group group(long id) {
        return groups.findById(id).orElseThrow(() -> missing("Group", id));
    }
    public Group createGroup(GroupInput input) {
        organization(input.organizationId());
        return groups.create(input);
    }
    public Group updateGroup(long id, GroupInput input) {
        group(id);
        organization(input.organizationId());
        return groups.update(id, input).orElseThrow(() -> missing("Group", id));
    }
    public void deleteGroup(long id) {
        if (groups.delete(id) == 0) throw missing("Group", id);
    }

    public List<Note> notes(Long groupId) {
        if (groupId == null) return notes.findAll();
        group(groupId);
        return notes.findByGroupId(groupId);
    }
    public Note note(long id) {
        return notes.findById(id).orElseThrow(() -> missing("Note", id));
    }
    public Note createNote(NoteInput input) {
        var actor = noteWriter();
        group(input.groupId());
        return notes.create(input, Long.parseLong(actor.getToken().getSubject()));
    }
    public Note updateNote(long id, NoteInput input) {
        requireNoteOwner(id);
        group(input.groupId());
        return notes.update(id, input).orElseThrow(() -> missing("Note", id));
    }
    public void deleteNote(long id) {
        requireNoteOwner(id);
        if (notes.delete(id) == 0) throw missing("Note", id);
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
        var note = notes.findByIdForUpdate(id).orElseThrow(() -> missing("Note", id));
        boolean admin = actor.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!admin && (note.ownerId() == null || !note.ownerId().toString().equals(actor.getToken().getSubject())))
            throw new ApiException(HttpStatus.FORBIDDEN, "Editors can change only their own notes.");
    }
    // Lock the child during scoped operations so a concurrent move cannot bypass the parent check.
    public Group group(long organizationId, long id) {
        organization(organizationId);
        return groups.findByIdAndOrganizationIdForUpdate(id, organizationId)
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
        return notes.findByIdAndGroupIdForUpdate(id, groupId)
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
