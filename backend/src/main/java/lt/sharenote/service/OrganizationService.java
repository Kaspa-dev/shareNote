package lt.sharenote.service;

import java.util.List;
import lt.sharenote.pagination.Pagination;
import lt.sharenote.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static lt.sharenote.exception.ApiException.notFound;
import static lt.sharenote.model.Models.*;

@Service
@Transactional
public class OrganizationService {
    private final OrganizationRepository organizations;

    public OrganizationService(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    public List<Organization> organizations() {
        return organizations.findAll();
    }

    public List<Organization> organizations(Pagination pagination) {
        if (pagination == null) return organizations();
        return organizations.findAll(pagination);
    }

    public Organization organization(long id) {
        return organizations.findById(id).orElseThrow(() -> notFound("Organization", id));
    }

    public Organization createOrganization(OrganizationInput input) {
        return organizations.create(input);
    }

    public Organization updateOrganization(long id, OrganizationInput input) {
        return organizations.update(id, input).orElseThrow(() -> notFound("Organization", id));
    }

    public void deleteOrganization(long id) {
        if (organizations.delete(id) == 0)
            throw notFound("Organization", id);
    }
}
