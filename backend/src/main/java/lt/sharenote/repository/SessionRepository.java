package lt.sharenote.repository;

import java.util.Optional;
import java.sql.Timestamp;
import java.time.Instant;
import lt.sharenote.model.RefreshSession;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SessionRepository {
    private final JdbcClient db;

    public SessionRepository(JdbcClient db) {
        this.db = db;
    }

    public long create(String tokenHash, long userId, String device, Instant expiry) {
        return db.sql("INSERT INTO sessions(last_refresh_token_hash, user_id, device, expires_at) VALUES (?, ?, ?, ?) RETURNING session_id")
                .param(tokenHash).param(userId).param(device).param(Timestamp.from(expiry))
                .query(Long.class).single();
    }

    public int updateRefreshToken(long sessionId, String tokenHash) {
        return db.sql("UPDATE sessions SET last_refresh_token_hash = ? WHERE session_id = ?")
                .param(tokenHash).param(sessionId).update();
    }

    // Call within the service transaction to hold the row lock through the operation.
    public Optional<RefreshSession> findActiveForUpdate(String tokenHash, String subject) {
        return db.sql("SELECT session_id, user_id, expires_at FROM sessions WHERE last_refresh_token_hash = ? AND user_id::text = ? AND NOT is_revoked AND expires_at > CURRENT_TIMESTAMP FOR UPDATE")
                .param(tokenHash).param(subject).query((rs, row) -> new RefreshSession(
                        rs.getLong("session_id"), rs.getLong("user_id"), rs.getTimestamp("expires_at").toInstant())).optional();
    }

    public int revoke(String tokenHash) {
        return db.sql("UPDATE sessions SET is_revoked = TRUE WHERE last_refresh_token_hash = ?").param(tokenHash).update();
    }
}
