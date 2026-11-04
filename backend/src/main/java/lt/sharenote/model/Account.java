package lt.sharenote.model;

public record Account(Long id, String username, String passwordHash, String role) {}
