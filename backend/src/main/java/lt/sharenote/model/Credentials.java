package lt.sharenote.model;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

public record Credentials(
        @NotNull @Pattern(regexp = "[A-Za-z0-9_.-]{3,80}") String username,
        @NotBlank @Size(min = 12, max = 72) String password) {
    @JsonIgnore
    @AssertTrue(message = "Password must not exceed 72 UTF-8 bytes")
    public boolean isPasswordWithinByteLimit() {
        return password == null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
    }
}
