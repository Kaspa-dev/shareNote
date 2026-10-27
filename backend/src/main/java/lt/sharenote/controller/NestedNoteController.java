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
@RequestMapping("/api/organizations/{organizationId}/groups/{groupId}/notes")
@Tag(name = "Notes")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(responseCode = "403", description = "Insufficient role or not the note owner", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(responseCode = "400", description = "Malformed JSON, incorrect field type, or invalid parameter", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(responseCode = "404", description = "Resource or related resource not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
})
public class NestedNoteController {
    private final ShareNoteService service;
    public NestedNoteController(ShareNoteService service) { this.service = service; }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List notes", operationId = "listGroupNotes", description = "Optional page (zero-based) and size (1–100) enable pagination, defaulting to 0 and 10. Omit both for the full list.")
    @ApiResponse(responseCode = "200", description = "List ordered by ID; may be empty")
    public List<NoteResource> list(@PathVariable long organizationId, @PathVariable long groupId,
                           @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        var pagination = Pagination.optional(page, size);
        var group = service.group(organizationId, groupId);
        return service.notes(organizationId, groupId, pagination).stream().map(item -> represent(item, group)).toList();
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get note by ID", operationId = "getGroupNote")
    @ApiResponse(responseCode = "200", description = "Resource found")
    public NoteResource get(@PathVariable long organizationId, @PathVariable long groupId, @PathVariable long id) {
        return represent(service.note(organizationId, groupId, id), service.group(organizationId, groupId));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create note", operationId = "createGroupNote", description = "EDITOR or ADMIN. The server assigns ownerId from the authenticated JWT subject.")
    @ApiResponse(responseCode = "201", description = "Resource created; Location contains its URL", headers = @io.swagger.v3.oas.annotations.headers.Header(name = "Location", schema = @Schema(type = "string", format = "uri")))
    @ApiResponse(responseCode = "422", description = "Invalid field values", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Resource relationship conflict", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "415", description = "Content-Type must be application/json", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<NoteResource> create(@PathVariable long organizationId, @PathVariable long groupId, @Valid @RequestBody NestedNoteInput input) {
        var result = service.createNote(organizationId, groupId, input);
        return ResponseEntity.created(URI.create("/api/organizations/" + organizationId + "/groups/" + groupId + "/notes/" + result.id())).body(represent(result, service.group(organizationId, groupId)));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replace note", operationId = "updateGroupNote", description = "ADMIN or the owning EDITOR. All required fields must be provided. Missing resources are not created. Ownership cannot be changed.")
    @ApiResponse(responseCode = "200", description = "Resource updated")
    @ApiResponse(responseCode = "422", description = "Invalid field values", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Resource relationship conflict", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "415", description = "Content-Type must be application/json", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public NoteResource update(@PathVariable long organizationId, @PathVariable long groupId, @PathVariable long id, @Valid @RequestBody NestedNoteInput input) {
        return represent(service.updateNote(organizationId, groupId, id, input), service.group(organizationId, groupId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete note", operationId = "deleteGroupNote", description = "ADMIN or the owning EDITOR.")
    @ApiResponse(responseCode = "204", description = "Resource deleted; response body is empty", content = @Content)
    @ApiResponse(responseCode = "409", description = "Delete dependent resources first", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> delete(@PathVariable long organizationId, @PathVariable long groupId, @PathVariable long id) {
        service.deleteNote(organizationId, groupId, id);
        return ResponseEntity.noContent().build();
    }
}
