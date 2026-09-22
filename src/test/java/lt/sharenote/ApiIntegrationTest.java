package lt.sharenote;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired TestRestTemplate http;

    private ResponseEntity<JsonNode> call(HttpMethod method, String path, Object body, int status) {
        var headers = new HttpHeaders();
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

    @Test void openApiDocumentsExactlyFifteenOperations() {
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
                assertThat(operation.path("responses").has("404")).isTrue();
                if (entry.getKey().equals("post")) assertThat(operation.path("responses").has("201")).isTrue();
                if (entry.getKey().equals("delete")) assertThat(operation.path("responses").path("204").has("content")).isFalse();
            }
        }
        assertThat(count).isEqualTo(15);
    }
}
