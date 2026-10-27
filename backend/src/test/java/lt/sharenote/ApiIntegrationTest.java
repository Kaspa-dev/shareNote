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

    @Test void notePriorityPersistsDefaultsAndRejectsInvalidValues() {
        long org = create("organizations", Map.of("name", "Priority", "description", "Test"));
        String groups = "organizations/" + org + "/groups";
        long group = create(groups, Map.of("name", "Priority", "description", "Test"));
        String notes = "/api/" + groups + "/" + group + "/notes";
        var input = new java.util.HashMap<String, Object>(Map.of("title", "Priority", "content", "Test", "category", "Test"));
        var created = call(HttpMethod.POST, notes, input, 201);
        assertThat(created.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        assertThat(created.getBody().path("priority").asText()).isEqualTo("MEDIUM");
        String path = notes + "/" + created.getBody().path("id").asLong();
        for (String priority : new String[] {"HIGH", "LOW", "MEDIUM"}) {
            input.put("priority", priority);
            call(HttpMethod.PUT, path, input, 200);
            assertThat(call(HttpMethod.GET, path, null, 200).getBody().path("priority").asText()).isEqualTo(priority);
            assertThat(call(HttpMethod.GET, notes, null, 200).getBody().get(0).path("priority").asText()).isEqualTo(priority);
        }
        input.put("priority", "HIGH");
        assertThat(call(HttpMethod.POST, notes, input, 201).getBody().path("priority").asText()).isEqualTo("HIGH");
        for (String invalid : new String[] {"URGENT", "high", ""}) {
            input.put("priority", invalid);
            call(HttpMethod.POST, notes, input, 422);
            call(HttpMethod.PUT, path, input, 422);
        }
        assertThat(call(HttpMethod.GET, path, null, 200).getBody().path("priority").asText()).isEqualTo("MEDIUM");
    }

    @Test void noteResourceCombinesPersistedNoteAndGroup() {
        long org = create("organizations", Map.of("name", "Composite resource", "description", "Test"));
        String groups = "organizations/" + org + "/groups";
        long group = create(groups, Map.of("name", "Original course", "description", "Course notes"));
        String groupPath = "/api/" + groups + "/" + group;
        String notesPath = groupPath + "/notes";
        var input = Map.of("title", "Lecture", "content", "Persisted content", "category", "REST");
        var created = call(HttpMethod.POST, notesPath, input, 201).getBody();
        String notePath = notesPath + "/" + created.path("id").asLong();
        assertThat(created.path("group")).isEqualTo(call(HttpMethod.GET, groupPath, null, 200).getBody());

        var updatedGroup = call(HttpMethod.PUT, groupPath,
                Map.of("name", "Renamed course", "description", "Updated description"), 200).getBody();
        var detail = call(HttpMethod.GET, notePath, null, 200).getBody();
        assertThat(detail.path("title").asText()).isEqualTo("Lecture");
        assertThat(detail.path("content").asText()).isEqualTo("Persisted content");
        assertThat(detail.path("groupId").asLong()).isEqualTo(group);
        assertThat(detail.path("group")).isEqualTo(updatedGroup);
        assertThat(call(HttpMethod.GET, notesPath + "?page=0&size=1", null, 200).getBody().get(0))
                .isEqualTo(detail);
        assertThat(call(HttpMethod.PUT, notePath, input, 200).getBody().path("group")).isEqualTo(updatedGroup);
    }

    @Test void hypermediaLinksCanBeFollowedThroughTheHierarchy() {
        long org = create("organizations", Map.of("name", "Hypermedia", "description", "Test"));
        String organizationPath = "/api/organizations/" + org;
        var organization = call(HttpMethod.GET, organizationPath, null, 200).getBody();
        String groupsPath = organization.path("_links").path("groups").path("href").asText();
        var groupResponse = call(HttpMethod.POST, groupsPath, Map.of("name", "Links", "description", "Test"), 201);
        var group = groupResponse.getBody();
        assertThat(group.path("_links").path("self").path("href").asText())
                .isEqualTo(groupResponse.getHeaders().getLocation().toString());
        String notesPath = group.path("_links").path("notes").path("href").asText();
        var note = call(HttpMethod.POST, notesPath, Map.of("title", "Links", "content", "Test", "category", "REST"), 201).getBody();
        for (var resource : new JsonNode[] {organization, group, note}) {
            resource.path("_links").elements().forEachRemaining(link ->
                    call(HttpMethod.GET, link.path("href").asText(), null, 200));
            String self = resource.path("_links").path("self").path("href").asText();
            assertThat(call(HttpMethod.GET, self, null, 200).getBody()).isEqualTo(resource);
        }
        accessToken = null;
        call(HttpMethod.GET, note.path("_links").path("self").path("href").asText(), null, 401);
    }

    @Test void collectionPagesAreOrderedBoundedAndKeepParentChecks() {
        long org = create("organizations", Map.of("name", "Paging", "description", "Test"));
        String groups = "organizations/" + org + "/groups";
        long group = create(groups, Map.of("name", "Paging", "description", "Test"));
        create(groups, Map.of("name", "Second", "description", "Test"));
        String notes = groups + "/" + group + "/notes";
        create(notes, Map.of("title", "First", "content", "Test", "category", "Test"));
        create(notes, Map.of("title", "Second", "content", "Test", "category", "Test"));
        for (String path : new String[] {"organizations", groups, notes}) {
            var all = call(HttpMethod.GET, "/api/" + path, null, 200).getBody();
            var first = call(HttpMethod.GET, "/api/" + path + "?page=0&size=1", null, 200).getBody();
            var second = call(HttpMethod.GET, "/api/" + path + "?page=1&size=1", null, 200).getBody();
            assertThat(first.size()).isEqualTo(1);
            assertThat(second.size()).isEqualTo(1);
            assertThat(first.get(0)).isEqualTo(all.get(0));
            assertThat(second.get(0)).isEqualTo(all.get(1));
            assertThat(call(HttpMethod.GET, "/api/" + path + "?page=2147483647&size=100", null, 200).getBody().size()).isZero();
            for (String invalid : new String[] {"page=-1", "size=0", "size=101", "page=abc"})
                call(HttpMethod.GET, "/api/" + path + "?" + invalid, null, 400);
        }
        call(HttpMethod.GET, "/api/organizations/999999/groups?page=0", null, 404);
        call(HttpMethod.GET, "/api/organizations/999999/groups/" + group + "/notes?page=0", null, 404);
    }

    @Test void refreshTokensRotateExpireAndCanBeRevoked() throws Exception {
        var login = http.postForObject("/api/auth/login", Map.of("username", "lab-admin", "password", "test-admin-password"), JsonNode.class);
        String original = login.path("refreshToken").asText();
        var refreshJwt = com.nimbusds.jwt.SignedJWT.parse(original).getJWTClaimsSet();
        assertThat(refreshJwt.getStringClaim("token_use")).isEqualTo("refresh");
        assertThat(refreshJwt.getJWTID()).isNotBlank();
        long sessionId = Long.parseLong(refreshJwt.getStringClaim("sid"));
        var initiated = db.sql("SELECT initiated_at FROM sessions WHERE session_id = ?").param(sessionId).query(java.time.OffsetDateTime.class).single();
        assertThat(db.sql("SELECT is_revoked FROM sessions WHERE session_id = ?").param(sessionId).query(Boolean.class).single()).isFalse();
        assertThat(refreshJwt.getExpirationTime().getTime()).isEqualTo(login.path("refreshExpiresAt").asLong());
        assertThat(login.path("expiresIn").asLong()).isEqualTo(180);
        assertThat(login.path("refreshExpiresAt").asLong() - System.currentTimeMillis())
                .isBetween(2L * 24 * 60 * 60 * 1000 - 10000, 2L * 24 * 60 * 60 * 1000);
        accessToken = null;
        var rotated = call(HttpMethod.POST, "/api/auth/refresh", Map.of("refreshToken", original), 200).getBody();
        assertThat(rotated.path("refreshToken").asText()).isNotEqualTo(original);
        assertThat(com.nimbusds.jwt.SignedJWT.parse(rotated.path("refreshToken").asText()).getJWTClaimsSet().getStringClaim("sid"))
                .isEqualTo(Long.toString(sessionId));
        assertThat(db.sql("SELECT initiated_at FROM sessions WHERE session_id = ?").param(sessionId).query(java.time.OffsetDateTime.class).single()).isEqualTo(initiated);
        assertThat(rotated.path("expiresIn").asLong()).isEqualTo(180);
        assertThat(rotated.path("refreshExpiresAt").asLong()).isEqualTo(login.path("refreshExpiresAt").asLong());
        call(HttpMethod.POST, "/api/auth/refresh", Map.of("refreshToken", original), 401);
        accessToken = rotated.path("accessToken").asText();
        call(HttpMethod.GET, "/api/organizations/1/groups/1/notes", null, 200);
        accessToken = null;
        var body = Map.of("refreshToken", rotated.path("refreshToken").asText());
        call(HttpMethod.POST, "/api/auth/logout", body, 204);
        assertThat(db.sql("SELECT is_revoked FROM sessions WHERE session_id = ?").param(sessionId).query(Boolean.class).single()).isTrue();
        call(HttpMethod.POST, "/api/auth/logout", body, 204);
        call(HttpMethod.POST, "/api/auth/refresh", body, 401);
        call(HttpMethod.POST, "/api/auth/refresh", Map.of("refreshToken", "unknown"), 401);
        call(HttpMethod.POST, "/api/auth/refresh", Map.of("refreshToken", ""), 422);
        var expired = http.postForObject("/api/auth/login", Map.of("username", "lab-admin", "password", "test-admin-password"), JsonNode.class);
        db.sql("UPDATE sessions SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second'").update();
        call(HttpMethod.POST, "/api/auth/refresh", Map.of("refreshToken", expired.path("refreshToken").asText()), 401);
    }

    @Test void refreshJwtValidationRejectsWrongPurposeClaimsAndSignatures() throws Exception {
        var login = http.postForObject("/api/auth/login", Map.of("username", "lab-admin", "password", "test-admin-password"), JsonNode.class);
        String refresh = login.path("refreshToken").asText();
        var parsed = com.nimbusds.jwt.SignedJWT.parse(refresh).getJWTClaimsSet();
        String subject = parsed.getSubject();
        accessToken = refresh;
        call(HttpMethod.GET, "/api/organizations", null, 401);
        accessToken = null;
        call(HttpMethod.POST, "/api/auth/refresh", Map.of("refreshToken", login.path("accessToken").asText()), 401);
        var now = java.time.Instant.now();
        for (String variant : java.util.List.of("access", "missing-purpose", "missing-expiry", "expired", "wrong-issuer", "missing-id", "tampered", "wrong-key")) {
            var builder = JwtClaimsSet.builder().issuer(variant.equals("wrong-issuer") ? "other" : "sharenote")
                    .subject(subject).issuedAt(now.minusSeconds(120)).claim("role", "ADMIN");
            if (!variant.equals("missing-purpose")) builder.claim("token_use", variant.equals("access") ? "access" : "refresh");
            if (!variant.equals("missing-id")) builder.id(java.util.UUID.randomUUID().toString());
            if (!variant.equals("missing-expiry")) builder.expiresAt(variant.equals("expired") ? now.minusSeconds(90) : now.plusSeconds(3600));
            String candidate = encoder.encode(JwtEncoderParameters.from(builder.build())).getTokenValue();
            if (variant.equals("tampered")) {
                String[] parts = candidate.split("\\.");
                candidate = parts[0] + "." + parts[1] + "." + (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
            }
            if (variant.equals("wrong-key")) {
                var key = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).generate();
                var otherEncoder = new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(key)));
                candidate = otherEncoder.encode(JwtEncoderParameters.from(builder.build())).getTokenValue();
            }
            // Even an active database record cannot make an invalid JWT acceptable.
            String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(candidate.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            db.sql("INSERT INTO sessions(last_refresh_token_hash, user_id, expires_at) VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '1 day')")
                    .param(hash).param(Long.parseLong(subject)).update();
            call(HttpMethod.POST, "/api/auth/refresh", Map.of("refreshToken", candidate), 401);
            if (!variant.equals("access")) {
                accessToken = candidate;
                call(HttpMethod.GET, "/api/organizations", null, 401);
                accessToken = null;
            }
        }
        // Rejected requests did not consume the legitimate refresh token.
        call(HttpMethod.POST, "/api/auth/refresh", Map.of("refreshToken", refresh), 200);
    }

    @Test void sessionsKeepDeviceAndIndependentLoginState() throws Exception {
        var headers = new HttpHeaders();
        headers.set("User-Agent", "ShareNote test browser");
        var credentials = Map.of("username", "lab-admin", "password", "test-admin-password");
        var first = http.postForObject("/api/auth/login", new HttpEntity<>(credentials, headers), JsonNode.class);
        var second = http.postForObject("/api/auth/login", credentials, JsonNode.class);
        String firstToken = first.path("refreshToken").asText();
        String secondToken = second.path("refreshToken").asText();
        String firstId = com.nimbusds.jwt.SignedJWT.parse(firstToken).getJWTClaimsSet().getStringClaim("sid");
        String secondId = com.nimbusds.jwt.SignedJWT.parse(secondToken).getJWTClaimsSet().getStringClaim("sid");
        assertThat(firstId).isNotEqualTo(secondId);
        assertThat(db.sql("SELECT device FROM sessions WHERE session_id = ?").param(Long.parseLong(firstId)).query(String.class).single())
                .isEqualTo("ShareNote test browser");
        accessToken = null;
        call(HttpMethod.POST, "/api/auth/logout", Map.of("refreshToken", firstToken), 204);
        call(HttpMethod.POST, "/api/auth/refresh", Map.of("refreshToken", firstToken), 401);
        call(HttpMethod.POST, "/api/auth/refresh", Map.of("refreshToken", secondToken), 200);
    }

    @BeforeEach void signIn() {
        accessToken = http.postForObject("/api/auth/login", Map.of("username", "lab-admin", "password", "test-admin-password"), JsonNode.class)
                .path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
    }

    @Test void editorsCanWriteOnlyTheirOwnNotes() {
        String adminToken = accessToken;
        var input = Map.of("title", "Owned note", "content", "Content", "category", "Test");
        String collection = "/api/organizations/1/groups/1/notes";
        String editorOne = editorLogin();
        String editorTwo = editorLogin();
        accessToken = editorOne;
        var own = call(HttpMethod.POST, collection, input, 201).getBody();
        String ownPath = collection + "/" + own.path("id").asLong();
        long ownerId = own.path("ownerId").asLong();
        assertThat(ownerId).isPositive();
        assertThat(db.sql("SELECT role FROM app_users WHERE id = ?").param(ownerId).query(String.class).single())
                .isEqualTo("EDITOR");
        call(HttpMethod.PUT, ownPath, input, 200);
        call(HttpMethod.GET, "/api/organizations", null, 200);
        call(HttpMethod.GET, "/api/organizations/1/groups", null, 200);
        for (String path : new String[]{"/api/organizations", "/api/organizations/1/groups"}) {
            call(HttpMethod.POST, path, Map.of("name", "Forbidden", "description", "Test"), 403);
            call(HttpMethod.PUT, path + "/1", Map.of("name", "Forbidden", "description", "Test"), 403);
            call(HttpMethod.DELETE, path + "/1", null, 403);
        }
        // Historical shared notes cannot be claimed by an editor.
        call(HttpMethod.PUT, collection + "/1", input, 403);
        call(HttpMethod.DELETE, collection + "/1", null, 403);
        var spoofed = new java.util.HashMap<String, Object>(input);
        spoofed.put("ownerId", ownerId + 1);
        call(HttpMethod.POST, collection, spoofed, 400);
        call(HttpMethod.PUT, ownPath, spoofed, 400);

        accessToken = editorTwo;
        call(HttpMethod.GET, ownPath, null, 200);
        call(HttpMethod.PUT, ownPath, input, 403);
        call(HttpMethod.DELETE, ownPath, null, 403);
        assertThat(call(HttpMethod.GET, ownPath, null, 200).getBody().path("ownerId").asLong()).isEqualTo(ownerId);
        var other = call(HttpMethod.POST, collection, input, 201).getBody();
        assertThat(other.path("ownerId").asLong()).isNotEqualTo(ownerId);
        String otherPath = collection + "/" + other.path("id").asLong();
        call(HttpMethod.DELETE, otherPath, null, 204);

        accessToken = adminToken;
        assertThat(call(HttpMethod.PUT, ownPath, input, 200).getBody().path("ownerId").asLong()).isEqualTo(ownerId);
        call(HttpMethod.DELETE, ownPath, null, 204);
    }

    private String editorLogin() {
        var credentials = Map.of("username", "editor-" + java.util.UUID.randomUUID(), "password", "test-editor-password");
        accessToken = null;
        call(HttpMethod.POST, "/api/auth/register", credentials, 201);
        db.sql("UPDATE app_users SET role = 'EDITOR' WHERE username = ?").param(credentials.get("username")).update();
        var login = call(HttpMethod.POST, "/api/auth/login", credentials, 200).getBody();
        var renewed = call(HttpMethod.POST, "/api/auth/refresh", Map.of("refreshToken", login.path("refreshToken").asText()), 200).getBody();
        return renewed.path("accessToken").asText();
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

    private String itemPath(String collection, long id) {
        return collection + "/" + id;
    }

    private long create(String resource, Map<String, Object> body) {
        var response = call(HttpMethod.POST, "/api/" + resource, body, 201);
        long id = response.getBody().path("id").asLong();
        assertThat(id).isPositive();
        assertThat(response.getHeaders().getLocation().toString()).isEqualTo(itemPath("/api/" + resource, id));
        return id;
    }

    @Test void allFifteenMethodsPersistDataAndProtectRelationships() {
        long org = create("organizations", Map.of("name", "Test organization", "description", "Test"));
        String groups = "organizations/" + org + "/groups";
        long group = create(groups, Map.of("name", "Test group", "description", "Test"));
        String notes = groups + "/" + group + "/notes";
        long note = create(notes, Map.of("title", "Test note", "content", "Content", "category", "REST"));
        Object[][] cases = {
            {"organizations", org, Map.of("name", "Changed organization", "description", "Changed")},
            {groups, group, Map.of("name", "Changed group", "description", "Changed")},
            {notes, note, Map.of("title", "Changed note", "content", "Changed content", "category", "HTTP")}
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
        assertThat(call(HttpMethod.GET, "/api/" + groups, null, 200).getBody()).hasSize(1);
        assertThat(call(HttpMethod.GET, "/api/" + notes, null, 200).getBody()).hasSize(1);
        call(HttpMethod.DELETE, "/api/organizations/" + org, null, 409);
        call(HttpMethod.DELETE, "/api/" + groups + "/" + group, null, 409);
        for (int i = cases.length - 1; i >= 0; i--) {
            String path = "/api/" + cases[i][0] + "/" + cases[i][1];
            call(HttpMethod.DELETE, path, null, 204);
            call(HttpMethod.GET, path, null, 404);
            call(HttpMethod.DELETE, path, null, 404);
        }
    }

    @Test void nestedCrudScopesChildrenAndUsesParentFromUrl() {
        long org = create("organizations", Map.of("name", "Nested organization", "description", ""));
        long otherOrg = create("organizations", Map.of("name", "Other organization", "description", ""));
        String groups = "organizations/" + org + "/groups";
        var groupInput = Map.<String, Object>of("name", "Nested group", "description", "");
        long group = create(groups, groupInput);
        long otherGroup = create("organizations/" + otherOrg + "/groups", Map.of("name", "Other group", "description", ""));
        String notes = "organizations/" + org + "/groups/" + group + "/notes";
        var noteInput = Map.<String, Object>of("title", "Nested note", "content", "Content", "category", "REST");
        long note = create(notes, noteInput);
        long otherNote = create("organizations/" + otherOrg + "/groups/" + otherGroup + "/notes", noteInput);
        String wrongOrganization = "/api/organizations/" + otherOrg + "/groups/" + group + "/notes";
        call(HttpMethod.GET, wrongOrganization, null, 404);
        call(HttpMethod.POST, wrongOrganization, noteInput, 404);
        for (HttpMethod method : new HttpMethod[]{HttpMethod.GET, HttpMethod.PUT, HttpMethod.DELETE}) {
            call(method, wrongOrganization + "/" + note, method == HttpMethod.PUT ? noteInput : null, 404);
        }
        assertThat(call(HttpMethod.GET, "/api/" + groups, null, 200).getBody().findValuesAsText("id"))
                .containsExactly(Long.toString(group));
        assertThat(call(HttpMethod.GET, "/api/" + notes, null, 200).getBody())
                .extracting(item -> item.path("id").asLong()).containsExactly(note);
        assertThat(call(HttpMethod.GET, "/api/organizations/" + otherOrg + "/groups", null, 200).getBody().findValuesAsText("id"))
                .containsExactly(Long.toString(otherGroup));
        for (HttpMethod method : new HttpMethod[]{HttpMethod.GET, HttpMethod.PUT, HttpMethod.DELETE}) {
            call(method, "/api/organizations/" + otherOrg + "/groups/" + group,
                    method == HttpMethod.PUT ? groupInput : null, 404);
            call(method, "/api/organizations/" + otherOrg + "/groups/" + otherGroup + "/notes/" + note,
                    method == HttpMethod.PUT ? noteInput : null, 404);
            call(method, itemPath("/api/" + groups, Long.MAX_VALUE),
                    method == HttpMethod.PUT ? groupInput : null, 404);
            call(method, "/api/" + notes + "/" + Long.MAX_VALUE,
                    method == HttpMethod.PUT ? noteInput : null, 404);
        }
        for (String collection : new String[]{groups, notes}) {
            var input = collection.equals(groups) ? groupInput : noteInput;
            long id = collection.equals(groups) ? group : note;
            String path = itemPath("/api/" + collection, id);
            var updated = call(HttpMethod.PUT, path, input, 200).getBody();
            assertThat(call(HttpMethod.GET, path, null, 200).getBody()).isEqualTo(updated);
            assertThat(updated.path(collection.equals(groups) ? "organizationId" : "groupId").asLong())
                    .isEqualTo(collection.equals(groups) ? org : group);
            call(HttpMethod.POST, "/api/" + collection, Map.of(), 422);
            call(HttpMethod.PUT, path, Map.of(), 422);
            var conflicting = new java.util.HashMap<>(input);
            conflicting.put(collection.equals(groups) ? "organizationId" : "groupId", 999);
            call(HttpMethod.POST, "/api/" + collection, conflicting, 400);
            call(HttpMethod.PUT, path, conflicting, 400);
        }
        call(HttpMethod.DELETE, itemPath("/api/" + groups, group), null, 409);
        for (String collection : new String[]{"organizations/" + Long.MAX_VALUE + "/groups", "organizations/" + org + "/groups/" + Long.MAX_VALUE + "/notes"}) {
            var input = collection.endsWith("groups") ? groupInput : noteInput;
            call(HttpMethod.GET, "/api/" + collection, null, 404);
            call(HttpMethod.POST, "/api/" + collection, input, 404);
            call(HttpMethod.GET, itemPath("/api/" + collection, 1), null, 404);
        }
        call(HttpMethod.GET, "/api/organizations/1/groups/not-a-number/notes", null, 400);
        call(HttpMethod.GET, "/api/organizations/not-a-number/groups", null, 400);
        call(HttpMethod.DELETE, "/api/" + notes + "/" + note, null, 204);
        call(HttpMethod.DELETE, itemPath("/api/" + groups, group), null, 204);
        call(HttpMethod.DELETE, "/api/organizations/" + otherOrg + "/groups/" + otherGroup + "/notes/" + otherNote, null, 204);
        call(HttpMethod.DELETE, "/api/organizations/" + otherOrg + "/groups/" + otherGroup, null, 204);
        call(HttpMethod.DELETE, "/api/organizations/" + org, null, 204);
        call(HttpMethod.DELETE, "/api/organizations/" + otherOrg, null, 204);
    }

    @Test void nestedRoutesRequireAuthenticationAndAdminForWrites() {
        String adminToken = accessToken;
        accessToken = null;
        for (String path : new String[]{"/api/organizations/1/groups", "/api/organizations/1/groups/1/notes"}) {
            call(HttpMethod.GET, path, null, 401);
            call(HttpMethod.POST, path, Map.of(), 401);
            call(HttpMethod.GET, itemPath(path, 1), null, 401);
            call(HttpMethod.PUT, itemPath(path, 1), Map.of(), 401);
            call(HttpMethod.DELETE, itemPath(path, 1), null, 401);
        }
        var now = java.time.Instant.now();
        accessToken = encoder.encode(JwtEncoderParameters.from(JwtClaimsSet.builder().claim("token_use", "access").issuer("sharenote")
                .subject("1").issuedAt(now).expiresAt(now.plusSeconds(3600)).claim("role", "USER").build())).getTokenValue();
        for (String path : new String[]{"/api/organizations/1/groups", "/api/organizations/1/groups/1/notes"}) {
            call(HttpMethod.GET, path, null, 200);
            call(HttpMethod.GET, itemPath(path, 1), null, 200);
            call(HttpMethod.POST, path, Map.of(), 403);
            call(HttpMethod.PUT, itemPath(path, 1), Map.of(), 403);
            call(HttpMethod.DELETE, itemPath(path, 1), null, 403);
        }
        accessToken = adminToken;
    }

    @Test void missingResourcesReturn404ForEveryOperation() {
        for (String resource : new String[]{"organizations", "organizations/1/groups", "organizations/1/groups/1/notes"}) {
            String path = "/api/" + resource + "/9223372036854775807";
            call(HttpMethod.GET, path, null, 404);
            call(HttpMethod.DELETE, path, null, 404);
            Object input = resource.endsWith("/notes") ? Map.of("title", "Note", "content", "Content", "category", "REST")
                    : resource.endsWith("/groups") ? Map.of("name", "Name", "description", "Description")
                    : Map.of("name", "Name", "description", "Description");
            call(HttpMethod.PUT, path, input, 404);
        }
        call(HttpMethod.GET, "/api/organizations/9223372036854775807/groups", null, 404);
        call(HttpMethod.GET, "/api/organizations/1/groups/9223372036854775807/notes", null, 404);
        call(HttpMethod.POST, "/api/organizations/9223372036854775807/groups", Map.of("name", "Group", "description", ""), 404);
        call(HttpMethod.POST, "/api/organizations/1/groups/9223372036854775807/notes", Map.of("title", "Note", "content", "Content", "category", "REST"), 404);
    }

    @Test void malformedAndInvalidPayloadsAreDistinguished() {
        for (String resource : new String[]{"organizations", "organizations/1/groups", "organizations/1/groups/1/notes"}) {
            call(HttpMethod.POST, "/api/" + resource, "{broken", 400);
            call(HttpMethod.POST, "/api/" + resource, Map.of(), 422);
            call(HttpMethod.PUT, "/api/" + resource + "/1", Map.of(), 422);
            call(HttpMethod.GET, "/api/" + resource + "/not-a-number", null, 400);
        }
        call(HttpMethod.POST, "/api/organizations", Map.of("name", " ", "description", ""), 422);
        call(HttpMethod.POST, "/api/organizations", Map.of("name", "x".repeat(121), "description", ""), 422);
        call(HttpMethod.POST, "/api/organizations", Map.of("name", 12, "description", ""), 400);
        call(HttpMethod.POST, "/api/organizations", Map.of("name", "Name", "description", "", "unexpected", true), 400);
        call(HttpMethod.POST, "/api/organizations/1/groups/1/notes", Map.of("title", "", "content", "Content", "category", "REST"), 422);
        call(HttpMethod.POST, "/api/organizations/1/groups/1/notes", Map.of("title", "Title", "content", "Content", "category", "REST", "groupId", 1.5), 400);
        var headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.TEXT_PLAIN);
        assertThat(http.postForEntity("/api/organizations/1/groups/1/notes", new HttpEntity<>("text", headers), String.class).getStatusCode().value()).isEqualTo(415);
    }

    @Test void nonHierarchicalEndpointsDoNotExist() {
        for (String path : new String[]{"/api/groups", "/api/groups/1", "/api/notes", "/api/notes/1", "/api/groups/1/notes", "/api/groups/1/notes/1"}) {
            for (HttpMethod method : new HttpMethod[]{HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE}) {
                call(method, path, method == HttpMethod.POST || method == HttpMethod.PUT ? Map.of() : null, 404);
            }
        }
    }

    @Test void migrationsProvideAtLeastFiveRecordsPerCollection() {
        assertThat(db.sql("SELECT count(*) FROM organizations").query(Long.class).single()).isGreaterThanOrEqualTo(5L);
        assertThat(db.sql("SELECT count(*) FROM groups").query(Long.class).single()).isGreaterThanOrEqualTo(5L);
        assertThat(db.sql("SELECT count(*) FROM notes").query(Long.class).single()).isGreaterThanOrEqualTo(5L);
    }

    @Test void migrationPreservesStandaloneGroupsAndDatabaseRequiresMembership() {
        assertThat(db.sql("SELECT count(*) FROM groups WHERE organization_id IS NULL").query(Long.class).single()).isZero();
        assertThat(db.sql("SELECT o.name FROM groups g JOIN organizations o ON o.id = g.organization_id WHERE g.id = 4")
                .query(String.class).single()).isEqualTo("Imported groups");
        assertThat(db.sql("SELECT count(*) FROM notes WHERE group_id = 4").query(Long.class).single()).isPositive();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                db.sql("INSERT INTO groups(name, description) VALUES ('Invalid', '')").update())
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                db.sql("UPDATE groups SET organization_id = NULL WHERE id = 4").update())
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        for (String sql : new String[]{
                "INSERT INTO groups(name, description, organization_id) VALUES ('Invalid', '', 9223372036854775807)",
                "UPDATE groups SET organization_id = 9223372036854775807 WHERE id = 4",
                "INSERT INTO notes(title, content, category) VALUES ('Invalid', 'Content', 'REST')",
                "INSERT INTO notes(title, content, category, group_id) VALUES ('Invalid', 'Content', 'REST', 9223372036854775807)",
                "UPDATE notes SET group_id = NULL WHERE id = 1",
                "UPDATE notes SET group_id = 9223372036854775807 WHERE id = 1",
                "DELETE FROM organizations WHERE id = 1",
                "DELETE FROM groups WHERE id = 1"}) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> db.sql(sql).update())
                    .as(sql).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
        assertThat(db.sql("SELECT count(*) FROM notes n LEFT JOIN groups g ON g.id = n.group_id WHERE g.id IS NULL")
                .query(Long.class).single()).isZero();
    }

    @Test void openApiDocumentsCrudAndAuthentication() {
        var spec = http.getForObject("/v3/api-docs", JsonNode.class);
        assertThat(spec.path("servers").get(0).path("url").asText()).isEqualTo("/");
        assertThat(spec.path("paths").has("/api/groups")).isFalse();
        assertThat(spec.path("paths").has("/api/notes")).isFalse();
        assertThat(spec.path("paths").size()).isEqualTo(10);
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
        for (String resource : new String[]{"organizations", "organizations/1/groups", "organizations/1/groups/1/notes"})
            call(HttpMethod.GET, "/api/" + resource, null, 401);
        String username = "user-" + java.util.UUID.randomUUID();
        var credentials = Map.of("username", username, "password", "test-user-password");
        var registered = call(HttpMethod.POST, "/api/auth/register", credentials, 201).getBody();
        accessToken = registered.path("accessToken").asText();
        var payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                java.util.Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]));
        assertThat(payload.path("role").asText()).isEqualTo("USER");
        assertThat(payload.path("exp").asLong() - payload.path("iat").asLong()).isEqualTo(180);
        assertThat(db.sql("SELECT password_hash FROM app_users WHERE username = ?").param(username).query(String.class).single())
                .startsWith("$2a$").isNotEqualTo("test-user-password");
        for (String resource : new String[]{"organizations", "organizations/1/groups", "organizations/1/groups/1/notes"}) {
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
        call(HttpMethod.GET, "/api/organizations/1/groups/1/notes", null, 200);
        String[] parts = accessToken.split("\\.");
        ((com.fasterxml.jackson.databind.node.ObjectNode) payload).put("role", "ADMIN");
        accessToken = parts[0] + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "." + parts[2];
        call(HttpMethod.DELETE, "/api/organizations/1/groups/1/notes/1", null, 401);
        accessToken = "malformed-token";
        call(HttpMethod.GET, "/api/organizations/1/groups/1/notes", null, 401);
        var now = java.time.Instant.now();
        for (var claims : new JwtClaimsSet[]{
                JwtClaimsSet.builder().claim("token_use", "access").issuer("sharenote").subject("1").issuedAt(now.minusSeconds(7200)).expiresAt(now.minusSeconds(3600)).claim("role", "ADMIN").build(),
                JwtClaimsSet.builder().claim("token_use", "access").issuer("wrong-issuer").subject("1").issuedAt(now).expiresAt(now.plusSeconds(3600)).claim("role", "ADMIN").build()}) {
            accessToken = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
            call(HttpMethod.GET, "/api/organizations/1/groups/1/notes", null, 401);
        }
        accessToken = encoder.encode(JwtEncoderParameters.from(JwtClaimsSet.builder().claim("token_use", "access").issuer("sharenote")
                .subject("1").issuedAt(now).expiresAt(now.plusSeconds(3600)).build())).getTokenValue();
        call(HttpMethod.GET, "/api/organizations/1/groups/1/notes", null, 403);
        accessToken = adminToken;
        call(HttpMethod.GET, "/api/organizations/1/groups/1/notes", null, 200);
    }
}
