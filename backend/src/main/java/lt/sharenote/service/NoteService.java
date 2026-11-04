package lt.sharenote.service;

import java.util.List;
import lt.sharenote.pagination.Pagination;
import lt.sharenote.repository.NoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lt.sharenote.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import static lt.sharenote.exception.ApiException.notFound;
import static lt.sharenote.model.Models.*;

@Service
@Transactional
public class NoteService {
    private final NoteRepository notes;
    private final GroupService groups;

    public NoteService(NoteRepository notes, GroupService groups) {
        this.notes = notes;
        this.groups = groups;
    }

    public List<Note> notes(long organizationId, long groupId, Pagination pagination) {
        if (pagination == null) return notes(organizationId, groupId);
        groups.group(organizationId, groupId);
        return notes.findByGroupId(groupId, pagination);
    }

    public List<Note> notes(Long groupId) {
        if (groupId == null) return notes.findAll();
        groups.group(groupId);
        return notes.findByGroupId(groupId);
    }

    public Note note(long id) {
        return notes.findById(id).orElseThrow(() -> notFound("Note", id));
    }

    public Note createNote(NoteInput input) {
        var actor = noteWriter();
        groups.group(input.groupId());
        return notes.create(input, Long.parseLong(actor.getToken().getSubject()));
    }

    public Note updateNote(long id, NoteInput input) {
        requireNoteOwner(id);
        groups.group(input.groupId());
        return notes.update(id, input).orElseThrow(() -> notFound("Note", id));
    }

    public void deleteNote(long id) {
        requireNoteOwner(id);
        if (notes.delete(id) == 0) throw notFound("Note", id);
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
        var note = notes.findByIdForUpdate(id).orElseThrow(() -> notFound("Note", id));
        boolean admin = actor.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!admin && (note.ownerId() == null || !note.ownerId().toString().equals(actor.getToken().getSubject())))
            throw new ApiException(HttpStatus.FORBIDDEN, "Editors can change only their own notes.");
    }

    public List<Note> notes(long organizationId, long groupId) {
        groups.group(organizationId, groupId);
        return notes(groupId);
    }

    public Note note(long organizationId, long groupId, long id) {
        groups.group(organizationId, groupId);
        return notes.findByIdAndGroupIdForUpdate(id, groupId)
                .orElseThrow(() -> notFound("Note", id));
    }

    public Note createNote(long organizationId, long groupId, NestedNoteInput input) {
        groups.group(organizationId, groupId);
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
