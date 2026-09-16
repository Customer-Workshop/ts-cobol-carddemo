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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * COUSR01C screen parity (FR-S12-17..23): COBOL-order validation, the green
 * added/duplicate/other messages, PF3/PF4 handling, and PF12 being an
 * invalid key even though the footer shows F12=Exit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:useradduitest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserAddUiIntegrationTest {

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
        mockMvc.perform(get("/admin/users/add").session(regular))
                .andExpect(status().isForbidden());
    }

    @Test
    void entryRendersAddMap_frS1217() throws Exception {
        mockMvc.perform(get("/admin/users/add").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("user-add"))
                .andExpect(content().string(containsString("CU01")))
                .andExpect(content().string(containsString("COUSR01C")))
                .andExpect(content().string(containsString("Add User")))
                .andExpect(content().string(containsString("(8 Char)")))
                .andExpect(content().string(containsString("(A=Admin, U=User)")))
                .andExpect(content().string(containsString(
                        "ENTER=Add User  F3=Back  F4=Clear  F12=Exit")));
    }

    @Test
    void enterChecksFieldsInCobolOrder_frS1217() throws Exception {
        MockHttpSession session = adminSession();
        mockMvc.perform(post("/admin/users/add").session(session)
                        .param("aid", "ENTER")
                        .param("userId", "NEWID1").param("lname", "LAST")
                        .param("passwd", "PASS").param("usrtype", "U"))
                .andExpect(model().attribute("message",
                        "First Name can NOT be empty..."));
        mockMvc.perform(post("/admin/users/add").session(session)
                        .param("aid", "ENTER")
                        .param("userId", "NEWID1").param("fname", "FIRST")
                        .param("passwd", "PASS").param("usrtype", "U"))
                .andExpect(model().attribute("message",
                        "Last Name can NOT be empty..."));
        mockMvc.perform(post("/admin/users/add").session(session)
                        .param("aid", "ENTER")
                        .param("fname", "FIRST").param("lname", "LAST")
                        .param("passwd", "PASS").param("usrtype", "U"))
                .andExpect(model().attribute("message",
                        "User ID can NOT be empty..."));
        mockMvc.perform(post("/admin/users/add").session(session)
                        .param("aid", "ENTER")
                        .param("userId", "NEWID1").param("fname", "FIRST")
                        .param("lname", "LAST").param("usrtype", "U"))
                .andExpect(model().attribute("message",
                        "Password can NOT be empty..."));
        mockMvc.perform(post("/admin/users/add").session(session)
                        .param("aid", "ENTER")
                        .param("userId", "NEWID1").param("fname", "FIRST")
                        .param("lname", "LAST").param("passwd", "PASS"))
                .andExpect(model().attribute("message",
                        "User Type can NOT be empty..."));
    }

    @Test
    void enterWritesClearsAndConfirms_frS1218() throws Exception {
        MockHttpSession session = adminSession();
        mockMvc.perform(post("/admin/users/add").session(session)
                        .param("aid", "ENTER")
                        .param("userId", "NEWUSER1").param("fname", "NEW")
                        .param("lname", "USER").param("passwd", "NEWPASS")
                        .param("usrtype", "U"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("message",
                        "User NEWUSER1 has been added ..."))
                .andExpect(model().attribute("messageStyle", "info"));
        // Store-as-typed: the record lands verbatim.
        SecurityUser stored = userRepository.findById("NEWUSER1").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(stored.getPassword())
                .isEqualTo("NEWPASS");
    }

    @Test
    void enterDuplicateKeepsFieldsAndReports_frS1219() throws Exception {
        MockHttpSession session = adminSession();
        mockMvc.perform(post("/admin/users/add").session(session)
                        .param("aid", "ENTER")
                        .param("userId", "ADMIN001").param("fname", "X")
                        .param("lname", "Y").param("passwd", "P")
                        .param("usrtype", "A"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("message", "User ID already exist..."))
                .andExpect(content().string(containsString("value=\"ADMIN001\"")));
    }

    @Test
    void pf3ReturnsToAdminMenu_frS1221() throws Exception {
        mockMvc.perform(post("/admin/users/add").session(adminSession())
                        .param("aid", "PF3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/menu"));
    }

    @Test
    void pf4ClearsFieldsAndMessage_frS1222() throws Exception {
        mockMvc.perform(post("/admin/users/add").session(adminSession())
                        .param("aid", "PF4")
                        .param("userId", "KEEP").param("fname", "ME"))
                .andExpect(status().isOk())
                .andExpect(view().name("user-add"))
                .andExpect(model().attribute("message", nullValue()))
                .andExpect(content().string(containsString("value=\"\"")));
    }

    @Test
    void pf12IsInvalidDespiteTheFooter_frS1223() throws Exception {
        mockMvc.perform(post("/admin/users/add").session(adminSession())
                        .param("aid", "PF12").param("fname", "KEEP"))
                .andExpect(status().isOk())
                .andExpect(view().name("user-add"))
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
