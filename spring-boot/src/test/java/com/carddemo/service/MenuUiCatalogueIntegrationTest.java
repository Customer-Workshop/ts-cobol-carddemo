package com.carddemo.service;

import com.carddemo.api.MenuOption;
import com.carddemo.model.SecurityUser;
import com.carddemo.repository.SecurityUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Menu-surface tests that need a catalogue fixture: the shipped COMEN02Y has
 * no 'A'-flagged rows and no placeholder rows, so FR-S01-12 (admin-only gate)
 * and FR-S01-15 (coming-soon placeholder) are reachable only via an injected
 * catalogue — same approach the FR docs prescribe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        // application.properties pins spring.datasource.url to a fixed mem:db,
        // so generate-unique-name alone cannot isolate this context: it would
        // share the pooled context's schema, which another context's close
        // (create-drop) can wipe mid-suite.
        "spring.datasource.url=jdbc:h2:mem:menucataloguetest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.main.allow-bean-definition-overriding=true"
})
class MenuUiCatalogueIntegrationTest {

    @TestConfiguration
    static class CatalogueFixture {
        @Bean
        MenuService menuService(SecurityUserRepository userRepository) {
            List<MenuOption> main = List.of(
                    new MenuOption(1, "Shared Option", "COTRN00C",
                            "/api/transactions", "U", true, true),
                    new MenuOption(2, "Admin Option", "COADMXXC",
                            "/api/admin/task", "A", true, true),
                    new MenuOption(3, "Future Feature", "DUMMY001",
                            "/api/future", "U", true, false),
                    new MenuOption(4, "Templated Route", "CORPT00C",
                            "/api/accounts/{acctId}", "U", true, true));
            return new MenuService(userRepository, main, List.of());
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private SecurityUserRepository userRepository;

    @BeforeEach
    void addRegularUser() {
        SecurityUser user = new SecurityUser();
        user.setUserId("USER0001");
        user.setFirstName("REGULAR");
        user.setLastName("USER");
        user.setPassword("PASSWORD");
        user.setUserType("U");
        userRepository.save(user);
    }

    @Test
    void regularUserSelectingAdminOptionShowsAdminOnlyMessage_frS0112() throws Exception {
        MockHttpSession session = signon("USER0001");
        mockMvc.perform(post("/menu/select").session(session)
                        .param("aid", "ENTER").param("option", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("menu"))
                .andExpect(model().attribute("message",
                        "No access - Admin Only option... "));
    }

    @Test
    void placeholderOptionShowsComingSoonMessageInGreen_frS0115() throws Exception {
        MockHttpSession session = signon("USER0001");
        mockMvc.perform(post("/menu/select").session(session)
                        .param("aid", "ENTER").param("option", "3"))
                .andExpect(status().isOk())
                .andExpect(view().name("menu"))
                .andExpect(model().attribute("message",
                        "This option Futureis coming soon ..."))
                .andExpect(model().attribute("messageStyle", "info"));
    }

    @Test
    void implementedOptionWithBrowsableRouteRedirects_frS0113() throws Exception {
        MockHttpSession session = signon("USER0001");
        mockMvc.perform(post("/menu/select").session(session)
                        .param("aid", "ENTER").param("option", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transactions/list"));
    }

    @Test
    void implementedOptionWithoutUiRouteShowsNotInstalled() throws Exception {
        MockHttpSession session = signon("USER0001");
        mockMvc.perform(post("/menu/select").session(session)
                        .param("aid", "ENTER").param("option", "4"))
                .andExpect(status().isOk())
                .andExpect(view().name("menu"))
                .andExpect(model().attribute("message",
                        "This option Templated Route is not installed..."));
    }

    private MockHttpSession signon(String userId) throws Exception {
        MvcResult result = mockMvc.perform(post("/signon").param("aid", "ENTER")
                        .param("userid", userId).param("passwd", "PASSWORD"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/menu"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
