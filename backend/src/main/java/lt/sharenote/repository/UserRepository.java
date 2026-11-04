package lt.sharenote.repository;

import java.util.Optional;
import lt.sharenote.model.Account;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private final JdbcClient db;

    public UserRepository(JdbcClient db) {
        this.db = db;
    }

    public Optional<Account> createUser(String username, String passwordHash) {
        return db.sql("INSERT INTO app_users(username, password_hash, role) VALUES (?, ?, 'USER') "
                + "ON CONFLICT (username) DO NOTHING RETURNING *")
                .param(username).param(passwordHash)
                .query(Account.class).optional();
    }

    public Optional<Account> findByUsername(String username) {
        return db.sql("SELECT * FROM app_users WHERE username = ?")
                .param(username).query(Account.class).optional();
    }

    public Optional<Account> findById(long id) {
        return db.sql("SELECT * FROM app_users WHERE id = ?").param(id)
                .query(Account.class).optional();
    }

    public int createAdminIfAbsent(String username, String passwordHash) {
        return db.sql("INSERT INTO app_users(username, password_hash, role) VALUES (?, ?, 'ADMIN') ON CONFLICT (username) DO NOTHING")
                .param(username).param(passwordHash).update();
    }
}
