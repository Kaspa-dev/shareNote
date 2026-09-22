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
@RequestMapping("/api/organizations")
@Tag(name = "Organizacijos")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Netaisyklingas JSON, lauko tipas arba parametras", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(responseCode = "404", description = "Resursas arba susijęs resursas nerastas", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
})
public class OrganizationController {
    private final ShareNoteService service;
    public OrganizationController(ShareNoteService service) { this.service = service; }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Organizacijos: gauti sąrašą", operationId = "listOrganizations")
    @ApiResponse(responseCode = "200", description = "Surikiuotas pagal ID sąrašas; gali būti tuščias")
    public List<Organization> list() { return service.organizations(); }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Organizacijos: gauti pagal ID", operationId = "getOrganization")
    @ApiResponse(responseCode = "200", description = "Rastas resursas")
    public Organization get(@PathVariable long id) { return service.organization(id); }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Organizacijos: sukurti", operationId = "createOrganization")
    @ApiResponse(responseCode = "201", description = "Sukurtas resursas; Location nurodo jo adresą", headers = @io.swagger.v3.oas.annotations.headers.Header(name = "Location", schema = @Schema(type = "string", format = "uri")))
    @ApiResponse(responseCode = "422", description = "Netinkamos laukų reikšmės", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Duomenų ryšių konfliktas", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "415", description = "Reikalingas application/json", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Organization> create(@Valid @RequestBody OrganizationInput input) {
        var result = service.createOrganization(input);
        return ResponseEntity.created(URI.create("/api/organizations/" + result.id())).body(result);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Organizacijos: pakeisti", operationId = "updateOrganization", description = "Visi privalomi laukai turi būti pateikti. Neegzistuojantis resursas nekuriamas.")
    @ApiResponse(responseCode = "200", description = "Atnaujintas resursas")
    @ApiResponse(responseCode = "422", description = "Netinkamos laukų reikšmės", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Duomenų ryšių konfliktas", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "415", description = "Reikalingas application/json", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public Organization update(@PathVariable long id, @Valid @RequestBody OrganizationInput input) {
        return service.updateOrganization(id, input);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Organizacijos: ištrinti", operationId = "deleteOrganization")
    @ApiResponse(responseCode = "204", description = "Ištrinta, atsakymo kūnas tuščias", content = @Content)
    @ApiResponse(responseCode = "409", description = "Pirmiausia reikia ištrinti priklausomus resursus", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.deleteOrganization(id);
        return ResponseEntity.noContent().build();
    }
}
