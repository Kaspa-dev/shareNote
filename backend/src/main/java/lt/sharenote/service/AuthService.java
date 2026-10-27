package lt.sharenote.service;

import lt.sharenote.exception.ApiException;
import lt.sharenote.model.Credentials;
import lt.sharenote.model.TokenResponse;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.HexFormat;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private static final long ACCESS_TOKEN_SECONDS = 3 * 60;
    private static final long REFRESH_TOKEN_SECONDS = 2 * 24 * 60 * 60;
    private final JdbcClient db;
    private final PasswordEncoder passwords;
    private final JwtEncoder tokens;
    private final JwtDecoder refreshTokens;
    private final String adminUsername;
    private final String adminPassword;
    private final String dummyHash;

    public AuthService(JdbcClient db, PasswordEncoder passwords, JwtEncoder tokens,
            @Qualifier("refreshJwtDecoder") JwtDecoder refreshTokens,
            @Value("${ADMIN_USERNAME:admin}") String adminUsername,
            @Value("${ADMIN_PASSWORD:}") String adminPassword) {
        this.db = db;
        this.passwords = passwords;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.dummyHash = passwords.encode("unused-login-timing-password");
    }

    public record Account(Long id, String username, String passwordHash, String role) {}

    private String normalize(String username) { return username.toLowerCase(Locale.ROOT); }

    @Transactional
    public TokenResponse register(Credentials input, String device) {
        var account = db.sql("INSERT INTO app_users(username, password_hash, role) VALUES (?, ?, 'USER') "
                + "ON CONFLICT (username) DO NOTHING RETURNING *")
                .param(normalize(input.username())).param(passwords.encode(input.password()))
                .query(Account.class).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Username is already registered."));
        return startSession(account, device);
    }

    @Transactional
    public TokenResponse login(Credentials input, String device) {
        var account = db.sql("SELECT * FROM app_users WHERE username = ?")
                .param(normalize(input.username())).query(Account.class).optional();
        boolean matches = passwords.matches(input.password(), account.map(Account::passwordHash).orElse(dummyHash));
        if (account.isEmpty() || !matches)
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
        return startSession(account.get(), device);
    }

    private TokenResponse startSession(Account account, String device) {
        Instant expiry = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusSeconds(REFRESH_TOKEN_SECONDS);
        String label = device == null || device.isBlank() ? "Unknown" : device.substring(0, Math.min(device.length(), 512));
        long sessionId = db.sql("INSERT INTO sessions(last_refresh_token_hash, user_id, device, expires_at) VALUES (?, ?, ?, ?) RETURNING session_id")
                .param(hash(UUID.randomUUID().toString())).param(account.id()).param(label).param(Timestamp.from(expiry))
                .query(Long.class).single();
        return token(account, expiry, sessionId);
    }

    private TokenResponse token(Account account, Instant refreshExpiry, long sessionId) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("sharenote").subject(account.id().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(ACCESS_TOKEN_SECONDS))
                .claim("token_use", "access").claim("username", account.username()).claim("role", account.role()).build();
        var refreshClaims = JwtClaimsSet.builder().issuer("sharenote").subject(account.id().toString())
                .issuedAt(now).expiresAt(refreshExpiry).id(UUID.randomUUID().toString())
                .claim("token_use", "refresh").claim("sid", Long.toString(sessionId)).build();
        String refresh = tokens.encode(JwtEncoderParameters.from(refreshClaims)).getTokenValue();
        db.sql("UPDATE sessions SET last_refresh_token_hash = ? WHERE session_id = ?")
                .param(hash(refresh)).param(sessionId).update();
        return new TokenResponse(tokens.encode(JwtEncoderParameters.from(claims)).getTokenValue(),
                "Bearer", ACCESS_TOKEN_SECONDS, refresh, refreshExpiry.toEpochMilli());
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        Jwt verified;
        try {
            verified = refreshTokens.decode(refreshToken);
            if (!verified.getExpiresAt().isAfter(Instant.now())) throw new JwtException("Expired refresh token");
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token.");
        }
        // Lock the session so concurrent refreshes cannot consume the same token twice.
        var consumed = db.sql("SELECT session_id, user_id, expires_at FROM sessions WHERE last_refresh_token_hash = ? AND user_id::text = ? AND NOT is_revoked AND expires_at > CURRENT_TIMESTAMP FOR UPDATE")
                .param(hash(refreshToken)).param(verified.getSubject()).query((rs, row) -> new RefreshSession(
                        rs.getLong("session_id"), rs.getLong("user_id"), rs.getTimestamp("expires_at").toInstant())).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token."));
        var account = db.sql("SELECT * FROM app_users WHERE id = ?").param(consumed.userId())
                .query(Account.class).single();
        return token(account, consumed.expiresAt(), consumed.sessionId());
    }

    public void logout(String refreshToken) {
        db.sql("UPDATE sessions SET is_revoked = TRUE WHERE last_refresh_token_hash = ?").param(hash(refreshToken)).update();
    }

    private record RefreshSession(long sessionId, long userId, Instant expiresAt) {}

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
