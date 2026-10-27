package lt.sharenote.controller;

import lt.sharenote.service.ShareNoteService;
import lt.sharenote.pagination.Pagination;
import java.net.URI;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.media.*;
import static lt.sharenote.model.Models.*;
import static lt.sharenote.model.ResourceRepresentations.*;

@RestController
@RequestMapping("/api/organizations/{organizationId}")
@Tag(name = "Groups")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(responseCode = "403", description = "Insufficient role", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(responseCode = "400", description = "Malformed JSON, incorrect field type, or invalid parameter", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(responseCode = "404", description = "Resource or related resource not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
})
public class NestedGroupController {
    private final ShareNoteService service;
    public NestedGroupController(ShareNoteService service) { this.service = service; }

    @GetMapping(value = "/groups", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List groups", operationId = "listOrganizationGroups", description = "Optional page (zero-based) and size (1–100) enable pagination, defaulting to 0 and 10. Omit both for the full list.")
    @ApiResponse(responseCode = "200", description = "List ordered by ID; may be empty")
    public List<GroupResource> list(@PathVariable long organizationId, @RequestParam(required = false) Integer page,
                            @RequestParam(required = false) Integer size) {
        return service.groups(organizationId, Pagination.optional(page, size)).stream().map(item -> represent(item)).toList();
    }

    @GetMapping(value = "/groups/{groupId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get group by ID", operationId = "getOrganizationGroup")
    @ApiResponse(responseCode = "200", description = "Resource found")
    public GroupResource get(@PathVariable long organizationId, @PathVariable long groupId) { return represent(service.group(organizationId, groupId)); }

    @PostMapping(value = "/groups", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create group", operationId = "createOrganizationGroup")
    @ApiResponse(responseCode = "201", description = "Resource created; Location contains its URL", headers = @io.swagger.v3.oas.annotations.headers.Header(name = "Location", schema = @Schema(type = "string", format = "uri")))
    @ApiResponse(responseCode = "422", description = "Invalid field values", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Resource relationship conflict", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "415", description = "Content-Type must be application/json", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<GroupResource> create(@PathVariable long organizationId, @Valid @RequestBody NestedGroupInput input) {
        var result = service.createGroup(organizationId, input);
        return ResponseEntity.created(URI.create("/api/organizations/" + organizationId + "/groups/" + result.id())).body(represent(result));
    }

    @PutMapping(value = "/groups/{groupId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replace group", operationId = "updateOrganizationGroup", description = "All required fields must be provided. Missing resources are not created.")
    @ApiResponse(responseCode = "200", description = "Resource updated")
    @ApiResponse(responseCode = "422", description = "Invalid field values", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Resource relationship conflict", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "415", description = "Content-Type must be application/json", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public GroupResource update(@PathVariable long organizationId, @PathVariable long groupId, @Valid @RequestBody NestedGroupInput input) {
        return represent(service.updateGroup(organizationId, groupId, input));
    }

    @DeleteMapping("/groups/{groupId}")
    @Operation(summary = "Delete group", operationId = "deleteOrganizationGroup")
    @ApiResponse(responseCode = "204", description = "Resource deleted; response body is empty", content = @Content)
    @ApiResponse(responseCode = "409", description = "Delete dependent resources first", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> delete(@PathVariable long organizationId, @PathVariable long groupId) {
        service.deleteGroup(organizationId, groupId);
        return ResponseEntity.noContent().build();
    }
}
