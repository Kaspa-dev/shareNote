package lt.sharenote.repository;

import java.util.Optional;
import java.util.List;
import lt.sharenote.pagination.Pagination;
import static lt.sharenote.model.Models.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OrganizationRepository {
    private final JdbcClient db;

    public OrganizationRepository(JdbcClient db) {
        this.db = db;
    }

    public List<Organization> findAll() {
        return db.sql("SELECT * FROM organizations ORDER BY id").query(Organization.class).list();
    }

    public List<Organization> findAll(Pagination pagination) {
        return db.sql("SELECT * FROM organizations ORDER BY id LIMIT ? OFFSET ?")
                .param(pagination.size()).param(pagination.offset()).query(Organization.class).list();
    }

    public Optional<Organization> findById(long id) {
        return db.sql("SELECT * FROM organizations WHERE id = ?").param(id)
                .query(Organization.class).optional();
    }

    public Organization create(OrganizationInput input) {
        return db.sql("INSERT INTO organizations(name, description) VALUES (?, ?) RETURNING *")
                .param(input.name()).param(input.description()).query(Organization.class).single();
    }

    public Optional<Organization> update(long id, OrganizationInput input) {
        return db.sql("UPDATE organizations SET name = ?, description = ? WHERE id = ? RETURNING *")
                .param(input.name()).param(input.description()).param(id).query(Organization.class)
                .optional();
    }

    public int delete(long id) {
        return db.sql("DELETE FROM organizations WHERE id = ?").param(id).update();
    }
}
