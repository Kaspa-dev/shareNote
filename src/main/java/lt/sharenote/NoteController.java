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
@RequestMapping("/api/notes")
@Tag(name = "Notes")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Malformed JSON, incorrect field type, or invalid parameter", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(responseCode = "404", description = "Resource or related resource not found", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
})
public class NoteController {
    private final ShareNoteService service;
    public NoteController(ShareNoteService service) { this.service = service; }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List notes", operationId = "listNotes")
    @ApiResponse(responseCode = "200", description = "List ordered by ID; may be empty")
    public List<Note> list(@RequestParam(required = false) Long groupId) { return service.notes(groupId); }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get note by ID", operationId = "getNote")
    @ApiResponse(responseCode = "200", description = "Resource found")
    public Note get(@PathVariable long id) { return service.note(id); }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create note", operationId = "createNote")
    @ApiResponse(responseCode = "201", description = "Resource created; Location contains its URL", headers = @io.swagger.v3.oas.annotations.headers.Header(name = "Location", schema = @Schema(type = "string", format = "uri")))
    @ApiResponse(responseCode = "422", description = "Invalid field values", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Resource relationship conflict", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "415", description = "Content-Type must be application/json", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Note> create(@Valid @RequestBody NoteInput input) {
        var result = service.createNote(input);
        return ResponseEntity.created(URI.create("/api/notes/" + result.id())).body(result);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replace note", operationId = "updateNote", description = "All required fields must be provided. Missing resources are not created.")
    @ApiResponse(responseCode = "200", description = "Resource updated")
    @ApiResponse(responseCode = "422", description = "Invalid field values", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Resource relationship conflict", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "415", description = "Content-Type must be application/json", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public Note update(@PathVariable long id, @Valid @RequestBody NoteInput input) {
        return service.updateNote(id, input);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete note", operationId = "deleteNote")
    @ApiResponse(responseCode = "204", description = "Resource deleted; response body is empty", content = @Content)
    @ApiResponse(responseCode = "409", description = "Delete dependent resources first", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.deleteNote(id);
        return ResponseEntity.noContent().build();
    }
}
