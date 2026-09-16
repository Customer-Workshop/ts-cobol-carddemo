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
 * COUSR02C screen parity (FR-S12-24..36): entry prefetch via the userId
 * param, ENTER fetch with the stored password echo, PF5 save, PF3
 * save-and-exit to the `from` caller, PF12 cancel, PF4 clear, invalid AID.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:userupdateuitest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserUpdateUiIntegrationTest {

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
    void regularUserIsForbidden_frS12b5() throws Exception {
        MockHttpSession regular = signon("USER0001", "PASSWORD", "/menu");
        mockMvc.perform(get("/admin/users/update").session(regular))
                .andExpect(status().isForbidden());
    }

    @Test
    void bareEntryRendersUpdateMap() throws Exception {
        mockMvc.perform(get("/admin/users/update").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("user-update"))
                .andExpect(content().string(containsString("CU02")))
                .andExpect(content().string(containsString("COUSR02C")))
                .andExpect(content().string(containsString("Update User")))
                .andExpect(content().string(containsString("Enter User ID:")))
                .andExpect(content().string(containsString("type=\"password\"")))
                .andExpect(content().string(containsString(
                        "ENTER=Fetch  F3=Save&amp;Exit  F4=Clear  F5=Save"
                                + "  F12=Cancel")));
    }

    @Test
    void entryWithUserIdPrefetches_frS1224_frS1226() throws Exception {
        // S12-B4 + S12-B2: a populated userId param fetches immediately and
        // the stored password echoes into the type=password field.
        MockHttpSession session = adminSession();
        mockMvc.perform(get("/admin/users/update").session(session)
                        .param("userId", "USER0001").param("from", "list"))
                .andExpect(status().isOk())
                .andExpect(view().name("user-update"))
                .andExpect(model().attribute("message",
                        "Press PF5 key to save your updates ..."))
                .andExpect(model().attribute("messageStyle", "info"))
                .andExpect(content().string(containsString("value=\"REGULAR\"")))
                .andExpect(content().string(containsString("value=\"PASSWORD\"")))
                .andExpect(content().string(containsString("value=\"U\"")))
                .andExpect(content().string(containsString("value=\"list\"")));
    }

    @Test
    void enterWithBlankIdReports_frS1225() throws Exception {
        mockMvc.perform(post("/admin/users/update").session(adminSession())
                        .param("aid", "ENTER").param("usrIdIn", "   "))
                .andExpect(status().isOk())
                .andExpect(model().attribute("message",
                        "User ID can NOT be empty..."));
    }

    @Test
    void enterWithUnknownIdReportsNotFound_frS1227() throws Exception {
        mockMvc.perform(post("/admin/users/update").session(adminSession())
                        .param("aid", "ENTER").param("usrIdIn", "GHOST001"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("message", "User ID NOT found..."));
    }

    @Test
    void pf5ValidatesThenSavesAndStays_frS1229_frS1231_frS1234() throws Exception {
        MockHttpSession session = adminSession();
        mockMvc.perform(post("/admin/users/update").session(session)
                        .param("aid", "PF5")
                        .param("usrIdIn", "USER0001").param("fname", "")
                        .param("lname", "USER").param("passwd", "PASSWORD")
                        .param("usrtype", "U"))
                .andExpect(model().attribute("message",
                        "First Name can NOT be empty..."));

        mockMvc.perform(post("/admin/users/update").session(session)
                        .param("aid", "PF5")
                        .param("usrIdIn", "USER0001").param("fname", "RENAMED")
                        .param("lname", "USER").param("passwd", "PASSWORD")
                        .param("usrtype", "U"))
                .andExpect(status().isOk())
                .andExpect(view().name("user-update"))
                .andExpect(model().attribute("message",
                        "User USER0001 has been updated ..."))
                .andExpect(model().attribute("messageStyle", "info"));
        org.assertj.core.api.Assertions
                .assertThat(userRepository.findById("USER0001").orElseThrow()
                        .getFirstName())
                .isEqualTo("RENAMED");
    }

    @Test
    void pf5WithoutChangesPromptsModify_frS1230() throws Exception {
        mockMvc.perform(post("/admin/users/update").session(adminSession())
                        .param("aid", "PF5")
                        .param("usrIdIn", "USER0001").param("fname", "REGULAR")
                        .param("lname", "USER").param("passwd", "PASSWORD")
                        .param("usrtype", "U"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("message",
                        "Please modify to update ..."));
    }

    @Test
    void pf3SavesThenReturnsToCaller_frS1233() throws Exception {
        MockHttpSession session = adminSession();
        // from=list returns to COUSR00C; the save side-effect still happens.
        mockMvc.perform(post("/admin/users/update").session(session)
                        .param("aid", "PF3").param("from", "list")
                        .param("usrIdIn", "USER0001").param("fname", "VIAF3")
                        .param("lname", "USER").param("passwd", "PASSWORD")
                        .param("usrtype", "U"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));
        org.assertj.core.api.Assertions
                .assertThat(userRepository.findById("USER0001").orElseThrow()
                        .getFirstName())
                .isEqualTo("VIAF3");

        // No caller lands back on the admin menu.
        mockMvc.perform(post("/admin/users/update").session(session)
                        .param("aid", "PF3")
                        .param("usrIdIn", "USER0001").param("fname", "VIAF3")
                        .param("lname", "USER").param("passwd", "PASSWORD")
                        .param("usrtype", "U"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/menu"));
    }

    @Test
    void pf12CancelsToMenuWithoutSaving_frS1235() throws Exception {
        MockHttpSession session = adminSession();
        mockMvc.perform(post("/admin/users/update").session(session)
                        .param("aid", "PF12")
                        .param("usrIdIn", "USER0001").param("fname", "IGNORED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/menu"));
        org.assertj.core.api.Assertions
                .assertThat(userRepository.findById("USER0001").orElseThrow()
                        .getFirstName())
                .isEqualTo("REGULAR");
    }

    @Test
    void pf4ClearsAndOtherAidIsInvalid_frS1236() throws Exception {
        MockHttpSession session = adminSession();
        mockMvc.perform(post("/admin/users/update").session(session)
                        .param("aid", "PF4").param("usrIdIn", "KEEP"))
                .andExpect(status().isOk())
                .andExpect(view().name("user-update"))
                .andExpect(content().string(not(containsString("KEEP"))));

        mockMvc.perform(post("/admin/users/update").session(session)
                        .param("aid", "PF6").param("usrIdIn", "KEEP"))
                .andExpect(status().isOk())
                .andExpect(view().name("user-update"))
                .andExpect(model().attribute("message",
                        "Invalid key pressed. Please see below..."))
                .andExpect(content().string(containsString("value=\"KEEP\"")));
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
