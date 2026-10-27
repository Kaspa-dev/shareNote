package lt.sharenote.model;

import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;
import static lt.sharenote.model.Models.*;

/** Read representations keep navigation metadata separate from database and input records. */
public final class ResourceRepresentations {
    private ResourceRepresentations() {}

    @Schema(name = "ResourceLink")
    public record Link(@Schema(format = "uri-reference", example = "/api/organizations/1", requiredMode = Schema.RequiredMode.REQUIRED) String href) {}

    @Schema(name = "Organization")
    public record OrganizationResource(Long id, String name, String description, Map<String, Link> _links) {}
    @Schema(name = "Group")
    public record GroupResource(Long id, String name, String description, Long organizationId, Map<String, Link> _links) {}
    @Schema(name = "Note", description = "A composite resource built from the note and its parent group for note details.")
    public record NoteResource(Long id, String title, String content, String category, Long groupId,
                               @Schema(accessMode = Schema.AccessMode.READ_ONLY, nullable = true, description = "Author user ID assigned from JWT sub; null for historical shared notes.") Long ownerId,
                               @Schema(allowableValues = {"LOW", "MEDIUM", "HIGH"}) String priority,
                               GroupResource group, Map<String, Link> _links) {}

    public static OrganizationResource represent(Organization item) {
        String self = "/api/organizations/" + item.id();
        return new OrganizationResource(item.id(), item.name(), item.description(), Map.of(
                "self", new Link(self), "collection", new Link("/api/organizations"),
                "groups", new Link(self + "/groups")));
    }

    public static GroupResource represent(Group item) {
        String parent = "/api/organizations/" + item.organizationId();
        String collection = parent + "/groups";
        String self = collection + "/" + item.id();
        return new GroupResource(item.id(), item.name(), item.description(), item.organizationId(), Map.of(
                "self", new Link(self), "collection", new Link(collection),
                "organization", new Link(parent), "notes", new Link(self + "/notes")));
    }

    public static NoteResource represent(Note item, Group group) {
        String organization = "/api/organizations/" + group.organizationId();
        String parent = organization + "/groups/" + item.groupId();
        String collection = parent + "/notes";
        return new NoteResource(item.id(), item.title(), item.content(), item.category(), item.groupId(), item.ownerId(), item.priority(), represent(group), Map.of(
                "self", new Link(collection + "/" + item.id()), "collection", new Link(collection),
                "group", new Link(parent), "organization", new Link(organization)));
    }
}
