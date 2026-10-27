package lt.sharenote;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import lt.sharenote.controller.*;
import lt.sharenote.service.ShareNoteService;
import static lt.sharenote.model.Models.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HypermediaTest {
    private MockMvc mvc;
    private final String organization = "/api/organizations/7";
    private final String group = organization + "/groups/13";
    private final String note = group + "/notes/29";

    @BeforeEach void setup() {
        var service = mock(ShareNoteService.class);
        var o = new Organization(7L, "University", "Shared notes");
        var g = new Group(13L, "Course", "Notes", 7L);
        var n = new Note(29L, "HTTP", "Methods", "REST", 13L, 5L, "MEDIUM");
        when(service.organizations(null)).thenReturn(List.of(o));
        when(service.groups(7, null)).thenReturn(List.of(g));
        when(service.notes(7, 13, null)).thenReturn(List.of(n));
        when(service.organization(7)).thenReturn(o);
        when(service.group(7, 13)).thenReturn(g);
        when(service.note(7, 13, 29)).thenReturn(n);
        when(service.createOrganization(any())).thenReturn(o);
        when(service.createGroup(eq(7L), any())).thenReturn(g);
        when(service.createNote(eq(7L), eq(13L), any())).thenReturn(n);
        when(service.updateOrganization(eq(7L), any())).thenReturn(o);
        when(service.updateGroup(eq(7L), eq(13L), any())).thenReturn(g);
        when(service.updateNote(eq(7L), eq(13L), eq(29L), any())).thenReturn(n);
        mvc = MockMvcBuilders.standaloneSetup(new OrganizationController(service),
                new NestedGroupController(service), new NestedNoteController(service)).build();
    }

    @Test void listsAndDetailsExposeFullHierarchy() throws Exception {
        for (String self : List.of(organization, group, note)) {
            String collection = self.substring(0, self.lastIndexOf('/'));
            mvc.perform(get(collection)).andExpect(status().isOk())
                    .andExpect(jsonPath("$[0]._links.self.href").value(self))
                    .andExpect(jsonPath("$[0]._links.collection.href").value(collection));
            mvc.perform(get(self)).andExpect(status().isOk())
                    .andExpect(jsonPath("$._links.self.href").value(self))
                    .andExpect(jsonPath("$._links.collection.href").value(collection));
        }
        mvc.perform(get(organization)).andExpect(jsonPath("$._links.groups.href").value(organization + "/groups"));
        mvc.perform(get(group)).andExpect(jsonPath("$._links.organization.href").value(organization))
                .andExpect(jsonPath("$._links.notes.href").value(group + "/notes"));
        mvc.perform(get(note)).andExpect(jsonPath("$._links.group.href").value(group))
                .andExpect(jsonPath("$._links.organization.href").value(organization))
                .andExpect(jsonPath("$.groupId").value(13));
    }

    @Test void writesReturnLinksAndKeepLocationAndEmptyDeletes() throws Exception {
        for (String self : List.of(organization, group, note)) {
            String collection = self.substring(0, self.lastIndexOf('/'));
            String body = self.equals(note)
                    ? "{\"title\":\"HTTP\",\"content\":\"Methods\",\"category\":\"REST\"}"
                    : "{\"name\":\"University\",\"description\":\"Notes\"}";
            mvc.perform(post(collection).contentType("application/json").content(body))
                    .andExpect(status().isCreated()).andExpect(header().string("Location", self))
                    .andExpect(jsonPath("$._links.self.href").value(self));
            mvc.perform(put(self).contentType("application/json").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$._links.self.href").value(self));
            mvc.perform(delete(self)).andExpect(status().isNoContent()).andExpect(content().string(""));
        }
    }
}
