package lt.sharenote.model;

public record TokenResponse(String accessToken, String tokenType, long expiresIn,
        String refreshToken, long refreshExpiresAt) {}
