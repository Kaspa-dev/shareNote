package lt.sharenote;

import java.time.Instant;
import java.util.Locale;
import java.util.Base64;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class AuthService implements ApplicationRunner {
    private final JdbcClient db;
    private final PasswordEncoder passwords;
    private final JwtEncoder tokens;
    private final String adminUsername;
    private final String adminPassword;
    private final String dummyHash;

    public AuthService(JdbcClient db, PasswordEncoder passwords, JwtEncoder tokens,
            @Value("${ADMIN_USERNAME:admin}") String adminUsername,
            @Value("${ADMIN_PASSWORD:}") String adminPassword) {
        this.db = db;
        this.passwords = passwords;
        this.tokens = tokens;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.dummyHash = passwords.encode("unused-login-timing-password");
    }

    public record Account(Long id, String username, String passwordHash, String role) {}
    public record TokenResponse(String accessToken, String tokenType, long expiresIn,
            String refreshToken, long refreshExpiresAt) {}
    private static final SecureRandom RANDOM = new SecureRandom();

    private String normalize(String username) { return username.toLowerCase(Locale.ROOT); }

    @Transactional
    public TokenResponse register(AuthController.Credentials input) {
        var account = db.sql("INSERT INTO app_users(username, password_hash, role) VALUES (?, ?, 'USER') "
                + "ON CONFLICT (username) DO NOTHING RETURNING *")
                .param(normalize(input.username())).param(passwords.encode(input.password()))
                .query(Account.class).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Username is already registered."));
        return token(account);
    }

    @Transactional
    public TokenResponse login(AuthController.Credentials input) {
        var account = db.sql("SELECT * FROM app_users WHERE username = ?")
                .param(normalize(input.username())).query(Account.class).optional();
        boolean matches = passwords.matches(input.password(), account.map(Account::passwordHash).orElse(dummyHash));
        if (account.isEmpty() || !matches)
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
        return token(account.get());
    }

    private TokenResponse token(Account account) {
        return token(account, Instant.now().plusSeconds(7 * 24 * 3600));
    }

    private TokenResponse token(Account account, Instant refreshExpiry) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("sharenote").subject(account.id().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(3600))
                .claim("username", account.username()).claim("role", account.role()).build();
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        db.sql("DELETE FROM refresh_tokens WHERE expires_at <= CURRENT_TIMESTAMP").update();
        db.sql("INSERT INTO refresh_tokens(token_hash, user_id, expires_at) VALUES (?, ?, ?)")
                .param(hash(refresh)).param(account.id()).param(Timestamp.from(refreshExpiry)).update();
        return new TokenResponse(tokens.encode(JwtEncoderParameters.from(claims)).getTokenValue(),
                "Bearer", 3600, refresh, refreshExpiry.toEpochMilli());
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        // DELETE RETURNING atomically consumes the token, including under concurrent requests.
        var consumed = db.sql("DELETE FROM refresh_tokens WHERE token_hash = ? AND expires_at > CURRENT_TIMESTAMP RETURNING user_id, expires_at")
                .param(hash(refreshToken)).query((rs, row) -> new RefreshSession(
                        rs.getLong("user_id"), rs.getTimestamp("expires_at").toInstant())).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token."));
        var account = db.sql("SELECT * FROM app_users WHERE id = ?").param(consumed.userId())
                .query(Account.class).single();
        return token(account, consumed.expiresAt());
    }

    public void logout(String refreshToken) {
        db.sql("DELETE FROM refresh_tokens WHERE token_hash = ?").param(hash(refreshToken)).update();
    }

    private record RefreshSession(long userId, Instant expiresAt) {}

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override public void run(ApplicationArguments args) {
        if (adminPassword.isEmpty()) return;
        if (!adminUsername.matches("[A-Za-z0-9_.-]{3,80}") || adminPassword.length() < 12
                || adminPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new IllegalStateException("Administrator username must be 3-80 valid characters; password must be at least 12 characters and at most 72 UTF-8 bytes.");
        // Never promote an existing self-registered account or reset its password.
        db.sql("INSERT INTO app_users(username, password_hash, role) VALUES (?, ?, 'ADMIN') ON CONFLICT (username) DO NOTHING")
                .param(normalize(adminUsername)).param(passwords.encode(adminPassword)).update();
        var account = db.sql("SELECT * FROM app_users WHERE username = ?")
                .param(normalize(adminUsername)).query(Account.class).single();
        if (!account.role().equals("ADMIN"))
            throw new IllegalStateException("Administrator username already belongs to a regular user; choose another ADMIN_USERNAME.");
    }
}
