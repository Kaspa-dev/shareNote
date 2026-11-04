package lt.sharenote.repository;

import java.util.Optional;
import java.util.List;
import lt.sharenote.pagination.Pagination;
import static lt.sharenote.model.Models.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class NoteRepository {
    private final JdbcClient db;

    public NoteRepository(JdbcClient db) {
        this.db = db;
    }

    public List<Note> findByGroupId(long groupId, Pagination pagination) {
        return db.sql("SELECT * FROM notes WHERE group_id = ? ORDER BY id LIMIT ? OFFSET ?")
                .param(groupId).param(pagination.size()).param(pagination.offset()).query(Note.class).list();
    }

    public List<Note> findAll() {
        return db.sql("SELECT * FROM notes ORDER BY id").query(Note.class).list();
    }

    public List<Note> findByGroupId(long groupId) {
        return db.sql("SELECT * FROM notes WHERE group_id = ? ORDER BY id").param(groupId).query(Note.class).list();
    }

    public Optional<Note> findById(long id) {
        return db.sql("SELECT * FROM notes WHERE id = ?").param(id).query(Note.class)
                .optional();
    }

    public Note create(NoteInput input, long ownerId) {
        return db.sql("INSERT INTO notes(title, content, category, group_id, owner_id, priority) VALUES (?, ?, ?, ?, ?, ?) RETURNING *")
                .param(input.title()).param(input.content()).param(input.category()).param(input.groupId())
                .param(ownerId)
                .param(input.priority())
                .query(Note.class).single();
    }

    public Optional<Note> update(long id, NoteInput input) {
        return db.sql("UPDATE notes SET title = ?, content = ?, category = ?, group_id = ?, priority = ? WHERE id = ? RETURNING *")
                .param(input.title()).param(input.content()).param(input.category()).param(input.groupId()).param(input.priority()).param(id)
                .query(Note.class).optional();
    }

    public int delete(long id) {
        return db.sql("DELETE FROM notes WHERE id = ?").param(id).update();
    }

    // Call within the service transaction to hold the row lock through the operation.
    public Optional<Note> findByIdForUpdate(long id) {
        return db.sql("SELECT * FROM notes WHERE id = ? FOR UPDATE").param(id).query(Note.class)
                .optional();
    }

    // Call within the service transaction to hold the row lock through the operation.
    public Optional<Note> findByIdAndGroupIdForUpdate(long id, long groupId) {
        return db.sql("SELECT * FROM notes WHERE id = ? AND group_id = ? FOR UPDATE")
                .param(id).param(groupId).query(Note.class).optional();
    }
}
