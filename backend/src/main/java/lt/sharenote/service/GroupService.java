package lt.sharenote.service;

import java.util.List;
import lt.sharenote.pagination.Pagination;
import lt.sharenote.repository.GroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static lt.sharenote.exception.ApiException.notFound;
import static lt.sharenote.model.Models.*;

@Service
@Transactional
public class GroupService {
    private final GroupRepository groups;
    private final OrganizationService organizations;

    public GroupService(GroupRepository groups, OrganizationService organizations) {
        this.groups = groups;
        this.organizations = organizations;
    }

    public List<Group> groups(long organizationId, Pagination pagination) {
        if (pagination == null) return groups(organizationId);
        organizations.organization(organizationId);
        return groups.findByOrganizationId(organizationId, pagination);
    }

    public List<Group> groups(Long organizationId) {
        if (organizationId == null)
            return groups.findAll();
        organizations.organization(organizationId);
        return groups.findByOrganizationId(organizationId);
    }

    public Group group(long id) {
        return groups.findById(id).orElseThrow(() -> notFound("Group", id));
    }

    public Group createGroup(GroupInput input) {
        organizations.organization(input.organizationId());
        return groups.create(input);
    }

    public Group updateGroup(long id, GroupInput input) {
        group(id);
        organizations.organization(input.organizationId());
        return groups.update(id, input).orElseThrow(() -> notFound("Group", id));
    }

    public void deleteGroup(long id) {
        if (groups.delete(id) == 0) throw notFound("Group", id);
    }

    // Keep the parent check and child lock in the same transaction.
    public Group group(long organizationId, long id) {
        organizations.organization(organizationId);
        return groups.findByIdAndOrganizationIdForUpdate(id, organizationId)
                .orElseThrow(() -> notFound("Group", id));
    }

    public Group createGroup(long organizationId, NestedGroupInput input) {
        return createGroup(new GroupInput(input.name(), input.description(), organizationId));
    }

    public Group updateGroup(long organizationId, long id, NestedGroupInput input) {
        group(organizationId, id);
        return updateGroup(id, new GroupInput(input.name(), input.description(), organizationId));
    }

    public void deleteGroup(long organizationId, long id) {
        group(organizationId, id);
        deleteGroup(id);
    }
}
