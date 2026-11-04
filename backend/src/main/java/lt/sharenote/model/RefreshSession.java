package lt.sharenote.model;

import java.time.Instant;

public record RefreshSession(long sessionId, long userId, Instant expiresAt) {}
