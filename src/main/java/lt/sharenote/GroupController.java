package lt.sharenote;

import java.net.URI;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.media.*;
import static lt.sharenote.Models.*;

@RestController
@RequestMapping("/api/groups")
@Tag(name = "Groups")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Malformed JSON, incorrect field type, or invalid parameter", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(responseCode = "404", description = "Resource or related resource not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
})
public class GroupController {
    private final ShareNoteService service;
    public GroupController(ShareNoteService service) { this.service = service; }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List groups", operationId = "listGroups")
    @ApiResponse(responseCode = "200", description = "List ordered by ID; may be empty")
    public List<Group> list(@RequestParam(required = false) Long organizationId) { return service.groups(organizationId); }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get group by ID", operationId = "getGroup")
    @ApiResponse(responseCode = "200", description = "Resource found")
    public Group get(@PathVariable long id) { return service.group(id); }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create group", operationId = "createGroup")
    @ApiResponse(responseCode = "201", description = "Resource created; Location contains its URL", headers = @io.swagger.v3.oas.annotations.headers.Header(name = "Location", schema = @Schema(type = "string", format = "uri")))
    @ApiResponse(responseCode = "422", description = "Invalid field values", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Resource relationship conflict", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "415", description = "Content-Type must be application/json", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Group> create(@Valid @RequestBody GroupInput input) {
        var result = service.createGroup(input);
        return ResponseEntity.created(URI.create("/api/groups/" + result.id())).body(result);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replace group", operationId = "updateGroup", description = "All required fields must be provided. Missing resources are not created.")
    @ApiResponse(responseCode = "200", description = "Resource updated")
    @ApiResponse(responseCode = "422", description = "Invalid field values", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Resource relationship conflict", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "415", description = "Content-Type must be application/json", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public Group update(@PathVariable long id, @Valid @RequestBody GroupInput input) {
        return service.updateGroup(id, input);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete group", operationId = "deleteGroup")
    @ApiResponse(responseCode = "204", description = "Resource deleted; response body is empty", content = @Content)
    @ApiResponse(responseCode = "409", description = "Delete dependent resources first", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }
}
