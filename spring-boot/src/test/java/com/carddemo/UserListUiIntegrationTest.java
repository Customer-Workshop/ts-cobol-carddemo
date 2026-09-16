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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * COUSR00C screen parity (FR-S12-01..16): the list renders the first page
 * in key order, selection hands off to update/delete with userId + from,
 * paging round-trips the hidden COMMAREA state, PF3 exits and other AIDs
 * report the invalid-key message.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:userlistuitest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserListUiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SecurityUserRepository userRepository;

    @BeforeEach
    void seedUsers() {
        SecurityUser regular = new SecurityUser();
        regular.setUserId("USER0001");
        regular.setFirstName("REGULAR");
        regular.setLastName("USER");
        regular.setPassword("PASSWORD");
        regular.setUserType("U");
        userRepository.save(regular);
        for (int i = 1; i <= 12; i++) {
            SecurityUser user = new SecurityUser();
            user.setUserId("U%07d".formatted(i));
            user.setFirstName("FIRST%03d".formatted(i));
            user.setLastName("LAST%03d".formatted(i));
            user.setPassword("PASS%04d".formatted(i));
            user.setUserType(i % 2 == 0 ? "U" : "A");
            userRepository.save(user);
        }
    }

    @Test
    void unsignedBouncesToSignonAndRegularUserIsForbidden_frS12b5() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signon"));
        MockHttpSession regular = signon("USER0001", "PASSWORD", "/menu");
        mockMvc.perform(get("/admin/users").session(regular))
                .andExpect(status().isForbidden());
    }

    @Test
    void firstEntryRendersMapAndFirstPage_frS1201() throws Exception {
        MockHttpSession session = adminSession();
        mockMvc.perform(get("/admin/users").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("user-list"))
                .andExpect(content().string(containsString("CU00")))
                .andExpect(content().string(containsString("COUSR00C")))
                .andExpect(content().string(containsString("List Users")))
                .andExpect(content().string(
                        containsString("Page: <span>00000001</span>")))
                .andExpect(content().string(containsString("Search User ID:")))
                .andExpect(content().string(containsString("Sel")))
                .andExpect(content().string(containsString("Type 'U' to Update "
                        + "or 'D' to Delete a User from the list")))
                .andExpect(content().string(containsString(
                        "ENTER=Continue  F3=Back  F7=Backward  F8=Forward")))
                // ADMIN001 (seeded) sorts first; ten rows fill the page.
                .andExpect(content().string(containsString(">ADMIN001<")))
                .andExpect(content().string(containsString(">U0000009<")))
                .andExpect(content().string(not(containsString(">U0000010<"))))
                .andExpect(content().string(
                        containsString("name=\"firstId\" value=\"ADMIN001\"")))
                .andExpect(content().string(
                        containsString("name=\"lastId\" value=\"U0000009\"")))
                .andExpect(content().string(
                        containsString("name=\"nextPage\" value=\"Y\"")));
    }

    @Test
    void enterWithUpdateSelectionOpensUpdateWithUserIdAndFrom_frS1203() throws Exception {
        MockHttpSession session = adminSession();
        mockMvc.perform(post("/admin/users").session(session)
                        .param("aid", "ENTER")
                        .param("sel", "u")
                        .param("usrId", "ADMIN001"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/users/update?userId=ADMIN001&from=list"));
    }

    @Test
    void enterWithDeleteSelectionOpensDeleteWithUserIdAndFrom_frS1204() throws Exception {
        MockHttpSession session = adminSession();
        mockMvc.perform(post("/admin/users").session(session)
                        .param("aid", "ENTER")
                        .param("sel", "", "", "D")
                        .param("usrId", "", "", "U0000003"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/users/delete?userId=U0000003&from=list"));
    }

    @Test
    void enterWithInvalidSelectionRefreshesAndReports_frS1205() throws Exception {
        MockHttpSession session = adminSession();
        mockMvc.perform(post("/admin/users").session(session)
                        .param("aid", "ENTER")
                        .param("sel", "X")
                        .param("usrId", "ADMIN001"))
                .andExpect(status().isOk())
                .andExpect(view().name("user-list"))
                .andExpect(model().attribute("message",
                        "Invalid selection. Valid values are U and D"))
                .andExpect(content().string(containsString(">ADMIN001<")));
    }

    @Test
    void enterHonoursOnlyTheFirstNonBlankSelection_frS1206() throws Exception {
        MockHttpSession session = adminSession();
        mockMvc.perform(post("/admin/users").session(session)
                        .param("aid", "ENTER")
                        .param("sel", "", "D", "", "", "U")
                        .param("usrId", "", "U0000002", "", "", "U0000005"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/users/delete?userId=U0000002&from=list"));
    }

    @Test
    void enterWithSearchKeyRestartsAtThatKey_frS1202() throws Exception {
        MockHttpSession session = adminSession();
        mockMvc.perform(post("/admin/users").session(session)
                        .param("aid", "ENTER")
                        .param("usrIdIn", "U0000011"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">U0000011<")))
                .andExpect(content().string(not(containsString(">U0000010<"))))
                .andExpect(content().string(
                        containsString("Page: <span>00000001</span>")));
    }

    @Test
    void pf8AndPf7PageBothDirections_frS1207_frS1210() throws Exception {
        MockHttpSession session = adminSession();
        mockMvc.perform(post("/admin/users").session(session)
                        .param("aid", "PF8")
                        .param("firstId", "ADMIN001").param("lastId", "U0000009")
                        .param("pageNum", "1").param("nextPage", "Y"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("Page: <span>00000002</span>")))
                .andExpect(content().string(containsString(">U0000010<")))
                .andExpect(content().string(not(containsString(">U0000009<"))))
                .andExpect(model().attribute("message",
                        "You have reached the bottom of the page..."));

        mockMvc.perform(post("/admin/users").session(session)
                        .param("aid", "PF7")
                        .param("firstId", "U0000010").param("lastId", "USER0001")
                        .param("pageNum", "2").param("nextPage", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("Page: <span>00000001</span>")))
                .andExpect(content().string(containsString(">ADMIN001<")));
    }

    @Test
    void pf3ReturnsToAdminMenu_frS1215() throws Exception {
        MockHttpSession session = adminSession();
        mockMvc.perform(post("/admin/users").session(session).param("aid", "PF3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/menu"));
    }

    @Test
    void unmappedAidShowsInvalidKey_frS1216() throws Exception {
        MockHttpSession session = adminSession();
        mockMvc.perform(post("/admin/users").session(session).param("aid", "PF5"))
                .andExpect(status().isOk())
                .andExpect(view().name("user-list"))
                .andExpect(model().attribute("message",
                        "Invalid key pressed. Please see below..."));
    }

    private MockHttpSession adminSession() throws Exception {
        return signon("ADMIN001", "PASSWORD", "/admin/menu");
    }

    private MockHttpSession signon(String userId, String password, String landing)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/signon").param("aid", "ENTER")
                        .param("userid", userId).param("passwd", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(landing))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
