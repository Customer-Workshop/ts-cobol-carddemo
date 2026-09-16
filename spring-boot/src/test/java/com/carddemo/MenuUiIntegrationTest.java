package com.carddemo;

import com.carddemo.model.SecurityUser;
import com.carddemo.repository.SecurityUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * COMEN01C + COADM01C web surfaces (wave 3): the menu screens, AID-key
 * handling and option dispatch must behave like COMEN1A/COADM1A. Test names
 * carry the FR-S01 row each covers; cites live in the program FR docs.
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
        "spring.datasource.url=jdbc:h2:mem:menuuitest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MenuUiIntegrationTest {

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
    void mainMenuListsAllElevenCatalogueOptionsVerbatim_frS0110() throws Exception {
        MockHttpSession session = signon("USER0001", "PASSWORD", "/menu");
        mockMvc.perform(get("/menu").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("menu"))
                .andExpect(content().string(containsString("CM00")))
                .andExpect(content().string(containsString("COMEN01C")))
                .andExpect(content().string(containsString("Main Menu")))
                .andExpect(content().string(containsString("01. Account View")))
                .andExpect(content().string(containsString("02. Account Update")))
                .andExpect(content().string(containsString("03. Credit Card List")))
                .andExpect(content().string(containsString("04. Credit Card View")))
                .andExpect(content().string(containsString("05. Credit Card Update")))
                .andExpect(content().string(containsString("06. Transaction List")))
                .andExpect(content().string(containsString("07. Transaction View")))
                .andExpect(content().string(containsString("08. Transaction Add")))
                .andExpect(content().string(containsString("09. Transaction Reports")))
                .andExpect(content().string(containsString("10. Bill Payment")))
                .andExpect(content().string(containsString("11. Pending Authorization View")))
                .andExpect(content().string(containsString("Please select an option :")))
                .andExpect(content().string(containsString("name=\"option\"")))
                .andExpect(content().string(containsString("ENTER=Continue  F3=Exit")));
    }

    @Test
    void invalidOptionRedisplaysMenuWithValidOptionMessage_frS0111() throws Exception {
        MockHttpSession session = signon("USER0001", "PASSWORD", "/menu");
        for (String option : new String[] {"0", "12", "AB"}) {
            mockMvc.perform(post("/menu/select").session(session)
                            .param("aid", "ENTER").param("option", option))
                    .andExpect(status().isOk())
                    .andExpect(view().name("menu"))
                    .andExpect(model().attribute("message",
                            "Please enter a valid option number..."));
        }
    }

    @Test
    void validOptionRedirectsToBrowsableRouteOrShowsNotInstalled_frS0113() throws Exception {
        MockHttpSession session = signon("USER0001", "PASSWORD", "/menu");
        mockMvc.perform(post("/menu/select").session(session)
                        .param("aid", "ENTER").param("option", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cards/list"));
        mockMvc.perform(post("/menu/select").session(session)
                        .param("aid", "ENTER").param("option", "6"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transactions/list"));
        mockMvc.perform(post("/menu/select").session(session)
                        .param("aid", "ENTER").param("option", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/accounts/view"));
        // Implemented target without a browsable route yet: the menu
        // redisplays the not-installed idiom instead of a dead link.
        mockMvc.perform(post("/menu/select").session(session)
                        .param("aid", "ENTER").param("option", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("menu"))
                .andExpect(model().attribute("message",
                        "This option Account Update is not installed..."));
    }

    @Test
    void pendingAuthorizationRoutesToScreen_frS0114() throws Exception {
        MockHttpSession session = signon("USER0001", "PASSWORD", "/menu");
        // S-19 flipped the catalogue flag: option 11 now resolves its UI
        // route instead of the not-installed idiom.
        mockMvc.perform(post("/menu/select").session(session)
                        .param("aid", "ENTER").param("option", "11"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ui/pending-auth"));
    }

    @Test
    void pf3SignsOffAndReturnsToSignon_frS0116() throws Exception {
        MockHttpSession session = signon("USER0001", "PASSWORD", "/menu");
        mockMvc.perform(post("/menu/select").session(session).param("aid", "PF3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signon"));
        assertThat(session.isInvalid()).isTrue();

        MockHttpSession adminSession = signon("ADMIN001", "PASSWORD", "/admin/menu");
        mockMvc.perform(post("/admin/menu/select").session(adminSession).param("aid", "PF3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signon"));
        assertThat(adminSession.isInvalid()).isTrue();
    }

    @Test
    void adminMenuListsAllSixCatalogueOptionsVerbatim_frS0117() throws Exception {
        MockHttpSession session = signon("ADMIN001", "PASSWORD", "/admin/menu");
        mockMvc.perform(get("/admin/menu").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-menu"))
                .andExpect(content().string(containsString("CA00")))
                .andExpect(content().string(containsString("COADM01C")))
                .andExpect(content().string(containsString("Admin Menu")))
                .andExpect(content().string(containsString("01. User List (Security)")))
                .andExpect(content().string(containsString("02. User Add (Security)")))
                .andExpect(content().string(containsString("03. User Update (Security)")))
                .andExpect(content().string(containsString("04. User Delete (Security)")))
                .andExpect(content().string(containsString("05. Transaction Type List/Update (Db2)")))
                .andExpect(content().string(containsString("06. Transaction Type Maintenance (Db2)")))
                .andExpect(content().string(containsString("Please select an option :")))
                .andExpect(content().string(containsString("ENTER=Continue  F3=Exit")));
    }

    @Test
    void invalidAdminOptionRedisplaysMenuWithValidOptionMessage_frS0118() throws Exception {
        MockHttpSession session = signon("ADMIN001", "PASSWORD", "/admin/menu");
        for (String option : new String[] {"0", "7", "AB"}) {
            mockMvc.perform(post("/admin/menu/select").session(session)
                            .param("aid", "ENTER").param("option", option))
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin-menu"))
                    .andExpect(model().attribute("message",
                            "Please enter a valid option number..."));
        }
    }

    @Test
    void validAdminOptionRedirectsOrShowsNotInstalled_frS0119() throws Exception {
        MockHttpSession session = signon("ADMIN001", "PASSWORD", "/admin/menu");
        mockMvc.perform(post("/admin/menu/select").session(session)
                        .param("aid", "ENTER").param("option", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));
        mockMvc.perform(post("/admin/menu/select").session(session)
                        .param("aid", "ENTER").param("option", "5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ui/tran-types"));
        mockMvc.perform(post("/admin/menu/select").session(session)
                        .param("aid", "ENTER").param("option", "6"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ui/tran-types/maint"));
    }

    @Test
    void unmappedAidShowsInvalidKeyMessageOnBothMenus_frS0120() throws Exception {
        MockHttpSession session = signon("USER0001", "PASSWORD", "/menu");
        mockMvc.perform(post("/menu/select").session(session)
                        .param("aid", "F7").param("option", "3"))
                .andExpect(status().isOk())
                .andExpect(view().name("menu"))
                .andExpect(model().attribute("message",
                        "Invalid key pressed. Please see below..."));

        MockHttpSession adminSession = signon("ADMIN001", "PASSWORD", "/admin/menu");
        mockMvc.perform(post("/admin/menu/select").session(adminSession)
                        .param("aid", "F5").param("option", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-menu"))
                .andExpect(model().attribute("message",
                        "Invalid key pressed. Please see below..."));
    }

    @Test
    void unsignedMenuSelectBouncesToSignon() throws Exception {
        mockMvc.perform(post("/menu/select").param("aid", "ENTER").param("option", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signon"));
        mockMvc.perform(post("/admin/menu/select").param("aid", "ENTER").param("option", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signon"));
    }

    @Test
    void regularUserCannotSelectOnAdminMenu() throws Exception {
        MockHttpSession session = signon("USER0001", "PASSWORD", "/menu");
        mockMvc.perform(post("/admin/menu/select").session(session)
                        .param("aid", "ENTER").param("option", "1"))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession signon(String userId, String password, String landing) throws Exception {
        MvcResult result = mockMvc.perform(post("/signon").param("aid", "ENTER")
                        .param("userid", userId).param("passwd", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(landing))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
