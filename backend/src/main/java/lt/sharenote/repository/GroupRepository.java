package lt.sharenote.repository;

import java.util.Optional;
import java.util.List;
import lt.sharenote.pagination.Pagination;
import static lt.sharenote.model.Models.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class GroupRepository {
    private final JdbcClient db;

    public GroupRepository(JdbcClient db) {
        this.db = db;
    }

    public List<Group> findByOrganizationId(long organizationId, Pagination pagination) {
        return db.sql("SELECT * FROM groups WHERE organization_id = ? ORDER BY id LIMIT ? OFFSET ?")
                .param(organizationId).param(pagination.size()).param(pagination.offset()).query(Group.class).list();
    }

    public List<Group> findAll() {
        return db.sql("SELECT * FROM groups ORDER BY id").query(Group.class).list();
    }

    public List<Group> findByOrganizationId(long organizationId) {
        return db.sql("SELECT * FROM groups WHERE organization_id = ? ORDER BY id")
                .param(organizationId).query(Group.class).list();
    }

    public Optional<Group> findById(long id) {
        return db.sql("SELECT * FROM groups WHERE id = ?").param(id).query(Group.class)
                .optional();
    }

    public Group create(GroupInput input) {
        return db.sql("INSERT INTO groups(name, description, organization_id) VALUES (?, ?, ?) RETURNING *")
                .param(input.name()).param(input.description()).param(input.organizationId())
                .query(Group.class).single();
    }

    public Optional<Group> update(long id, GroupInput input) {
        return db.sql("UPDATE groups SET name = ?, description = ?, organization_id = ? WHERE id = ? RETURNING *")
                .param(input.name()).param(input.description()).param(input.organizationId())
                .param(id).query(Group.class).optional();
    }

    public int delete(long id) {
        return db.sql("DELETE FROM groups WHERE id = ?").param(id).update();
    }

    // Call within the service transaction to hold the row lock through the operation.
    public Optional<Group> findByIdAndOrganizationIdForUpdate(long id, long organizationId) {
        return db.sql("SELECT * FROM groups WHERE id = ? AND organization_id = ? FOR UPDATE")
                .param(id).param(organizationId).query(Group.class).optional();
    }
}
