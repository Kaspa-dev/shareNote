package lt.sharenote;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
@SecurityRequirements
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Malformed input or unknown field", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(responseCode = "422", description = "Invalid username or password format", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
})
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }

    public record RefreshRequest(@NotBlank @Size(max = 128) String refreshToken) {}

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token and issue a new access token", operationId = "refresh")
    @ApiResponse(responseCode = "200", description = "New token pair")
    @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    public ResponseEntity<AuthService.TokenResponse> refresh(@Valid @RequestBody RefreshRequest input) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.refresh(input.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke a refresh token", operationId = "logout")
    @ApiResponse(responseCode = "204", description = "Refresh token revoked or already invalid", content = @Content)
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest input) {
        service.logout(input.refreshToken());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    public record Credentials(
            @NotNull @Pattern(regexp = "[A-Za-z0-9_.-]{3,80}") String username,
            @NotBlank @Size(min = 12, max = 72) String password) {
        @JsonIgnore
        @AssertTrue(message = "Password must not exceed 72 UTF-8 bytes")
        public boolean isPasswordWithinByteLimit() {
            return password == null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
        }
    }

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Register a USER and receive a JWT", operationId = "register")
    @ApiResponse(responseCode = "201", description = "Registered; JWT contains the USER role")
    @ApiResponse(responseCode = "409", description = "Username already registered", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<AuthService.TokenResponse> register(@Valid @RequestBody Credentials input) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(service.register(input));
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Sign in and receive a JWT containing the role", operationId = "login")
    @ApiResponse(responseCode = "200", description = "Signed in")
    @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<AuthService.TokenResponse> login(@Valid @RequestBody Credentials input) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.login(input));
    }
}
