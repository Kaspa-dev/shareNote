package lt.sharenote;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"ADMIN_USERNAME=lab-admin", "ADMIN_PASSWORD=test-admin-password"})
class ApiIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired TestRestTemplate http;
    @Autowired JwtEncoder encoder;
    @Autowired org.springframework.jdbc.core.simple.JdbcClient db;
    private String accessToken;

    @Test void refreshTokensRotateExpireAndCanBeRevoked() {
        var login = http.postForObject("/api/auth/login", Map.of("username", "lab-admin", "password", "test-admin-password"), JsonNode.class);
        String original = login.path("refreshToken").asText();
        assertThat(original).hasSize(43);
        accessToken = null;
        var rotated = call(HttpMethod.POST, "/api/auth/refresh", Map.of("refreshToken", original), 200).getBody();
        assertThat(rotated.path("refreshToken").asText()).isNotEqualTo(original);
        assertThat(rotated.path("refreshExpiresAt").asLong()).isEqualTo(login.path("refreshExpiresAt").asLong());
        call(HttpMethod.POST, "/api/auth/refresh", Map.of("refreshToken", original), 401);
        accessToken = rotated.path("accessToken").asText();
        call(HttpMethod.GET, "/api/notes", null, 200);
        accessToken = null;
        var body = Map.of("refreshToken", rotated.path("refreshToken").asText());
        call(HttpMethod.POST, "/api/auth/logout", body, 204);
        call(HttpMethod.POST, "/api/auth/logout", body, 204);
        call(HttpMethod.POST, "/api/auth/refresh", body, 401);
        call(HttpMethod.POST, "/api/auth/refresh", Map.of("refreshToken", "unknown"), 401);
        call(HttpMethod.POST, "/api/auth/refresh", Map.of("refreshToken", ""), 422);
        var expired = http.postForObject("/api/auth/login", Map.of("username", "lab-admin", "password", "test-admin-password"), JsonNode.class);
        db.sql("UPDATE refresh_tokens SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second'").update();
        call(HttpMethod.POST, "/api/auth/refresh", Map.of("refreshToken", expired.path("refreshToken").asText()), 401);
    }

    @BeforeEach void signIn() {
        accessToken = http.postForObject("/api/auth/login", Map.of("username", "lab-admin", "password", "test-admin-password"), JsonNode.class)
                .path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
    }

    private ResponseEntity<JsonNode> call(HttpMethod method, String path, Object body, int status) {
        var headers = new HttpHeaders();
        if (accessToken != null) headers.setBearerAuth(accessToken);
        if (body != null) headers.setContentType(MediaType.APPLICATION_JSON);
        var response = http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(response.getStatusCode().value()).as(method + " " + path).isEqualTo(status);
        if (status == 204) assertThat(response.getBody()).isNull();
        else {
            assertThat(response.getHeaders().getContentType()).isNotNull();
            assertThat(response.getHeaders().getContentType().isCompatibleWith(
                    status >= 400 ? MediaType.APPLICATION_PROBLEM_JSON : MediaType.APPLICATION_JSON)).isTrue();
        }
        return response;
    }

    private long create(String resource, Map<String, Object> body) {
        var response = call(HttpMethod.POST, "/api/" + resource, body, 201);
        long id = response.getBody().path("id").asLong();
        assertThat(id).isPositive();
        assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/api/" + resource + "/" + id);
        return id;
    }

    @Test void allFifteenMethodsPersistDataAndProtectRelationships() {
        long org = create("organizations", Map.of("name", "Test organization", "description", "Test"));
        long group = create("groups", Map.of("name", "Test group", "description", "Test", "organizationId", org));
        long note = create("notes", Map.of("title", "Test note", "content", "Content", "category", "REST", "groupId", group));
        Object[][] cases = {
            {"organizations", org, Map.of("name", "Changed organization", "description", "Changed")},
            {"groups", group, Map.of("name", "Changed group", "description", "Changed", "organizationId", org)},
            {"notes", note, Map.of("title", "Changed note", "content", "Changed content", "category", "HTTP", "groupId", group)}
        };
        for (Object[] row : cases) {
            String collection = "/api/" + row[0], path = collection + "/" + row[1];
            var list = call(HttpMethod.GET, collection, null, 200).getBody();
            assertThat(list.isArray()).isTrue();
            assertThat(list.findValuesAsText("id")).contains(row[1].toString());
            call(HttpMethod.GET, path, null, 200);
            var updated = call(HttpMethod.PUT, path, row[2], 200).getBody();
            assertThat(call(HttpMethod.GET, path, null, 200).getBody()).isEqualTo(updated);
            ((Map<?, ?>) row[2]).forEach((key, value) -> assertThat(updated.path(key.toString()).asText()).isEqualTo(value.toString()));
        }
        assertThat(call(HttpMethod.GET, "/api/groups?organizationId=" + org, null, 200).getBody()).hasSize(1);
        assertThat(call(HttpMethod.GET, "/api/notes?groupId=" + group, null, 200).getBody()).hasSize(1);
        call(HttpMethod.DELETE, "/api/organizations/" + org, null, 409);
        call(HttpMethod.DELETE, "/api/groups/" + group, null, 409);
        for (int i = cases.length - 1; i >= 0; i--) {
            String path = "/api/" + cases[i][0] + "/" + cases[i][1];
            call(HttpMethod.DELETE, path, null, 204);
            call(HttpMethod.GET, path, null, 404);
            call(HttpMethod.DELETE, path, null, 404);
        }
    }

    @Test void missingResourcesReturn404ForEveryOperation() {
        for (String resource : new String[]{"organizations", "groups", "notes"}) {
            String path = "/api/" + resource + "/9223372036854775807";
            call(HttpMethod.GET, path, null, 404);
            call(HttpMethod.DELETE, path, null, 404);
            Object input = resource.equals("notes") ? Map.of("title", "Note", "content", "Content", "category", "REST", "groupId", 1)
                    : Map.of("name", "Name", "description", "Description");
            call(HttpMethod.PUT, path, input, 404);
        }
        call(HttpMethod.GET, "/api/groups?organizationId=9223372036854775807", null, 404);
        call(HttpMethod.GET, "/api/notes?groupId=9223372036854775807", null, 404);
        call(HttpMethod.POST, "/api/groups", Map.of("name", "Group", "description", "", "organizationId", Long.MAX_VALUE), 404);
        call(HttpMethod.POST, "/api/notes", Map.of("title", "Note", "content", "Content", "category", "REST", "groupId", Long.MAX_VALUE), 404);
    }

    @Test void malformedAndInvalidPayloadsAreDistinguished() {
        for (String resource : new String[]{"organizations", "groups", "notes"}) {
            call(HttpMethod.POST, "/api/" + resource, "{broken", 400);
            call(HttpMethod.POST, "/api/" + resource, Map.of(), 422);
            call(HttpMethod.PUT, "/api/" + resource + "/1", Map.of(), 422);
            call(HttpMethod.GET, "/api/" + resource + "/not-a-number", null, 400);
        }
        call(HttpMethod.POST, "/api/organizations", Map.of("name", " ", "description", ""), 422);
        call(HttpMethod.POST, "/api/organizations", Map.of("name", "x".repeat(121), "description", ""), 422);
        call(HttpMethod.POST, "/api/organizations", Map.of("name", 12, "description", ""), 400);
        call(HttpMethod.POST, "/api/organizations", Map.of("name", "Name", "description", "", "unexpected", true), 400);
        call(HttpMethod.POST, "/api/notes", Map.of("title", "Title", "content", "Content", "category", "REST", "groupId", -1), 422);
        call(HttpMethod.POST, "/api/notes", Map.of("title", "Title", "content", "Content", "category", "REST", "groupId", 1.5), 400);
        var headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.TEXT_PLAIN);
        assertThat(http.postForEntity("/api/notes", new HttpEntity<>("text", headers), String.class).getStatusCode().value()).isEqualTo(415);
    }

    @Test void standaloneGroupCanBeCreatedAndMovedIntoAnOrganization() {
        long group = create("groups", Map.of("name", "Standalone", "description", ""));
        assertThat(call(HttpMethod.GET, "/api/groups/" + group, null, 200).getBody().path("organizationId").isNull()).isTrue();
        call(HttpMethod.PUT, "/api/groups/" + group, Map.of("name", "Moved", "description", "", "organizationId", 1), 200);
        call(HttpMethod.PUT, "/api/groups/" + group, Map.of("name", "Standalone again", "description", ""), 200);
        assertThat(call(HttpMethod.GET, "/api/groups/" + group, null, 200).getBody().path("organizationId").isNull()).isTrue();
        call(HttpMethod.DELETE, "/api/groups/" + group, null, 204);
    }

    @Test void openApiDocumentsCrudAndAuthentication() {
        var spec = http.getForObject("/v3/api-docs", JsonNode.class);
        assertThat(spec.path("servers").get(0).path("url").asText()).isEqualTo("/");
        assertThat(spec.at("/components/schemas/GroupInput/properties/organizationId/nullable").asBoolean()).isTrue();
        int count = 0;
        var paths = spec.path("paths").elements();
        while (paths.hasNext()) {
            var operations = paths.next().fields();
            while (operations.hasNext()) {
                var entry = operations.next();
                if (!java.util.Set.of("get", "post", "put", "delete").contains(entry.getKey())) continue;
                count++;
                var operation = entry.getValue();
                assertThat(operation.path("operationId").asText()).isNotBlank();
                if (java.util.Set.of("login", "register", "refresh", "logout").contains(operation.path("operationId").asText())) continue;
                assertThat(operation.path("responses").has("404")).isTrue();
                if (entry.getKey().equals("post")) assertThat(operation.path("responses").has("201")).isTrue();
                if (entry.getKey().equals("delete")) assertThat(operation.path("responses").path("204").has("content")).isFalse();
            }
        }
        assertThat(count).isEqualTo(19);
        assertThat(spec.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
        assertThat(spec.at("/paths/~1api~1auth~1login/post/security").isMissingNode()
                || spec.at("/paths/~1api~1auth~1login/post/security").isEmpty()).isTrue();
    }

    @Test void registrationLoginAndRoleAuthorization() throws Exception {
        String adminToken = accessToken;
        accessToken = null;
        for (String resource : new String[]{"organizations", "groups", "notes"})
            call(HttpMethod.GET, "/api/" + resource, null, 401);
        String username = "user-" + java.util.UUID.randomUUID();
        var credentials = Map.of("username", username, "password", "test-user-password");
        var registered = call(HttpMethod.POST, "/api/auth/register", credentials, 201).getBody();
        accessToken = registered.path("accessToken").asText();
        var payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                java.util.Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]));
        assertThat(payload.path("role").asText()).isEqualTo("USER");
        assertThat(payload.path("exp").asLong() - payload.path("iat").asLong()).isEqualTo(3600);
        assertThat(db.sql("SELECT password_hash FROM app_users WHERE username = ?").param(username).query(String.class).single())
                .startsWith("$2a$").isNotEqualTo("test-user-password");
        for (String resource : new String[]{"organizations", "groups", "notes"}) {
            call(HttpMethod.GET, "/api/" + resource, null, 200);
            call(HttpMethod.GET, "/api/" + resource + "/1", null, 200);
            call(HttpMethod.POST, "/api/" + resource, Map.of(), 403);
            call(HttpMethod.PUT, "/api/" + resource + "/1", Map.of(), 403);
            call(HttpMethod.DELETE, "/api/" + resource + "/1", null, 403);
        }
        call(HttpMethod.POST, "/api/auth/register", credentials, 409);
        call(HttpMethod.POST, "/api/auth/register", Map.of("username", "inject-role", "password", "test-user-password", "role", "ADMIN"), 400);
        call(HttpMethod.POST, "/api/auth/register", Map.of("username", "short-password", "password", "short"), 422);
        call(HttpMethod.POST, "/api/auth/register", Map.of("username", "long-password", "password", "ą".repeat(40)), 422);
        call(HttpMethod.POST, "/api/auth/login", Map.of("username", username, "password", "incorrect-password"), 401);
        call(HttpMethod.POST, "/api/auth/login", Map.of("username", "unknown-account", "password", "incorrect-password"), 401);
        accessToken = call(HttpMethod.POST, "/api/auth/login", credentials, 200).getBody().path("accessToken").asText();
        call(HttpMethod.GET, "/api/notes", null, 200);
        String[] parts = accessToken.split("\\.");
        ((com.fasterxml.jackson.databind.node.ObjectNode) payload).put("role", "ADMIN");
        accessToken = parts[0] + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "." + parts[2];
        call(HttpMethod.DELETE, "/api/notes/1", null, 401);
        accessToken = "malformed-token";
        call(HttpMethod.GET, "/api/notes", null, 401);
        var now = java.time.Instant.now();
        for (var claims : new JwtClaimsSet[]{
                JwtClaimsSet.builder().issuer("sharenote").subject("1").issuedAt(now.minusSeconds(7200)).expiresAt(now.minusSeconds(3600)).claim("role", "ADMIN").build(),
                JwtClaimsSet.builder().issuer("wrong-issuer").subject("1").issuedAt(now).expiresAt(now.plusSeconds(3600)).claim("role", "ADMIN").build()}) {
            accessToken = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
            call(HttpMethod.GET, "/api/notes", null, 401);
        }
        accessToken = encoder.encode(JwtEncoderParameters.from(JwtClaimsSet.builder().issuer("sharenote")
                .subject("1").issuedAt(now).expiresAt(now.plusSeconds(3600)).build())).getTokenValue();
        call(HttpMethod.GET, "/api/notes", null, 403);
        accessToken = adminToken;
        call(HttpMethod.GET, "/api/notes", null, 200);
    }
}
