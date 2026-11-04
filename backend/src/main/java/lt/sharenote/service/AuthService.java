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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import lt.sharenote.model.Account;
import lt.sharenote.repository.UserRepository;
import lt.sharenote.repository.SessionRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class AuthService implements ApplicationRunner {
    private static final long ACCESS_TOKEN_SECONDS = 3 * 60;
    private static final long REFRESH_TOKEN_SECONDS = 2 * 24 * 60 * 60;
    private final UserRepository users;
    private final SessionRepository sessions;
    private final PasswordEncoder passwords;
    private final JwtEncoder tokens;
    private final JwtDecoder refreshTokens;
    private final String adminUsername;
    private final String adminPassword;
    private final String dummyHash;

    public AuthService(UserRepository users, SessionRepository sessions, PasswordEncoder passwords, JwtEncoder tokens,
            @Qualifier("refreshJwtDecoder") JwtDecoder refreshTokens,
            @Value("${ADMIN_USERNAME:admin}") String adminUsername,
            @Value("${ADMIN_PASSWORD:}") String adminPassword) {
        this.users = users;
        this.sessions = sessions;
        this.passwords = passwords;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.dummyHash = passwords.encode("unused-login-timing-password");
    }

    private String normalize(String username) { return username.toLowerCase(Locale.ROOT); }

    @Transactional
    public TokenResponse register(Credentials input, String device) {
        var account = users.createUser(normalize(input.username()), passwords.encode(input.password()))
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Username is already registered."));
        return startSession(account, device);
    }

    @Transactional
    public TokenResponse login(Credentials input, String device) {
        var account = users.findByUsername(normalize(input.username()));
        boolean matches = passwords.matches(input.password(), account.map(Account::passwordHash).orElse(dummyHash));
        if (account.isEmpty() || !matches)
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
        return startSession(account.get(), device);
    }

    private TokenResponse startSession(Account account, String device) {
        Instant expiry = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusSeconds(REFRESH_TOKEN_SECONDS);
        String label = device == null || device.isBlank() ? "Unknown" : device.substring(0, Math.min(device.length(), 512));
        long sessionId = sessions.create(hash(UUID.randomUUID().toString()), account.id(), label, expiry);
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
        sessions.updateRefreshToken(sessionId, hash(refresh));
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
        var consumed = sessions.findActiveForUpdate(hash(refreshToken), verified.getSubject())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token."));
        var account = users.findById(consumed.userId()).orElseThrow(() -> new IllegalStateException("Session account was not found."));
        return token(account, consumed.expiresAt(), consumed.sessionId());
    }

    public void logout(String refreshToken) {
        sessions.revoke(hash(refreshToken));
    }

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
        users.createAdminIfAbsent(normalize(adminUsername), passwords.encode(adminPassword));
        var account = users.findByUsername(normalize(adminUsername)).orElseThrow(() -> new IllegalStateException("Administrator account was not found."));
        if (!account.role().equals("ADMIN"))
            throw new IllegalStateException("Administrator username already belongs to a regular user; choose another ADMIN_USERNAME.");
    }
}
