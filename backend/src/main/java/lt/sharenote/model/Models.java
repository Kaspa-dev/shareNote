package lt.sharenote.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public final class Models {
    private Models() {}

    public record Organization(Long id, String name, String description) {}
    public record Group(Long id, String name, String description, Long organizationId) {}
    public record Note(Long id, String title, String content, String category, Long groupId, Long ownerId, String priority) {}

    @Schema(name = "OrganizationInput")
    public record OrganizationInput(
            @NotBlank @Size(min = 1, max = 120) @Schema(example = "KTU Faculty of Informatics") String name,
            @NotNull @Size(max = 2000) @Schema(example = "A collaboration space for students") String description) {}

    @Schema(name = "GroupInput")
    public record GroupInput(
            @NotBlank @Size(min = 1, max = 120) @Schema(example = "Web Technologies") String name,
            @NotNull @Size(max = 2000) @Schema(example = "Course notes and laboratory assignments") String description,
            @NotNull @Positive @Schema(description = "Required organization ID", example = "1", minimum = "1") Long organizationId) {}

    @Schema(name = "NoteInput")
    public record NoteInput(
            @NotBlank @Size(min = 1, max = 200) @Schema(example = "HTTP methods") String title,
            @NotBlank @Size(min = 1, max = 20000) @Schema(example = "GET reads, POST creates, PUT replaces, DELETE removes.") String content,
            @NotBlank @Size(min = 1, max = 100) @Schema(example = "REST") String category,
            @NotNull @Positive @Schema(example = "1", minimum = "1") Long groupId,
            @Pattern(regexp = "LOW|MEDIUM|HIGH") @Schema(allowableValues = {"LOW", "MEDIUM", "HIGH"}, defaultValue = "MEDIUM") String priority) {
        public NoteInput { if (priority == null) priority = "MEDIUM"; }
    }
    @Schema(name = "NestedGroupInput", description = "The organization is supplied by the URL.")
    public record NestedGroupInput(
            @NotBlank @Size(min = 1, max = 120) String name,
            @NotNull @Size(max = 2000) String description) {}

    @Schema(name = "NestedNoteInput", description = "The group is supplied by the URL.")
    public record NestedNoteInput(
            @NotBlank @Size(min = 1, max = 200) String title,
            @NotBlank @Size(min = 1, max = 20000) String content,
            @NotBlank @Size(min = 1, max = 100) String category,
            @Pattern(regexp = "LOW|MEDIUM|HIGH") @Schema(allowableValues = {"LOW", "MEDIUM", "HIGH"}, defaultValue = "MEDIUM") String priority) {
        public NestedNoteInput { if (priority == null) priority = "MEDIUM"; }
    }
}
