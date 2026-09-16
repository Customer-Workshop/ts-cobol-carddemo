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
 * COUSR03C screen parity (FR-S12-37..40): entry prefetch via the userId
 * param, ENTER fetch with the neutral delete prompt, PF5 delete, PF3
 * exit-without-delete, PF12 to the admin menu, PF4 clear, invalid AID.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:userdeleteuitest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserDeleteUiIntegrationTest {

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
        mockMvc.perform(get("/admin/users/delete").session(regular))
                .andExpect(status().isForbidden());
    }

    @Test
    void bareEntryRendersDeleteMap() throws Exception {
        mockMvc.perform(get("/admin/users/delete").session(adminSession()))
                .andExpect(status().isOk())
                .andExpect(view().name("user-delete"))
                .andExpect(content().string(containsString("CU03")))
                .andExpect(content().string(containsString("COUSR03C")))
                .andExpect(content().string(containsString("Delete User")))
                .andExpect(content().string(containsString("Enter User ID:")))
                .andExpect(content().string(not(containsString("type=\"password\""))))
                .andExpect(content().string(containsString(
                        "ENTER=Fetch  F3=Back  F4=Clear  F5=Delete")));
    }

    @Test
    void entryWithUserIdPrefetches_frS1237_frS1238() throws Exception {
        mockMvc.perform(get("/admin/users/delete").session(adminSession())
                        .param("userId", "USER0001").param("from", "list"))
                .andExpect(status().isOk())
                .andExpect(view().name("user-delete"))
                .andExpect(model().attribute("message",
                        "Press PF5 key to delete this user ..."))
                .andExpect(model().attribute("messageStyle", "info"))
                .andExpect(content().string(containsString("REGULAR")))
                .andExpect(content().string(containsString("value=\"list\"")));
    }

    @Test
    void enterWithBlankAndUnknownIdsReport_frS1238() throws Exception {
        MockHttpSession session = adminSession();
        mockMvc.perform(post("/admin/users/delete").session(session)
                        .param("aid", "ENTER").param("usrIdIn", "  "))
                .andExpect(model().attribute("message",
                        "User ID can NOT be empty..."));
        mockMvc.perform(post("/admin/users/delete").session(session)
                        .param("aid", "ENTER").param("usrIdIn", "GHOST001"))
                .andExpect(model().attribute("message", "User ID NOT found..."));
    }

    @Test
    void pf5DeletesClearsAndConfirms_frS1239() throws Exception {
        mockMvc.perform(post("/admin/users/delete").session(adminSession())
                        .param("aid", "PF5").param("usrIdIn", "USER0001"))
                .andExpect(status().isOk())
                .andExpect(view().name("user-delete"))
                .andExpect(model().attribute("message",
                        "User USER0001 has been deleted ..."))
                .andExpect(model().attribute("messageStyle", "info"));
        org.assertj.core.api.Assertions
                .assertThat(userRepository.findById("USER0001")).isEmpty();
    }

    @Test
    void pf5OnMissingRowReportsNotFound_frS1239() throws Exception {
        mockMvc.perform(post("/admin/users/delete").session(adminSession())
                        .param("aid", "PF5").param("usrIdIn", "GHOST001"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("message", "User ID NOT found..."));
    }

    @Test
    void pf3ReturnsToCallerWithoutDeleting_frS1240() throws Exception {
        MockHttpSession session = adminSession();
        mockMvc.perform(post("/admin/users/delete").session(session)
                        .param("aid", "PF3").param("from", "list")
                        .param("usrIdIn", "USER0001"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));
        org.assertj.core.api.Assertions
                .assertThat(userRepository.findById("USER0001")).isPresent();

        mockMvc.perform(post("/admin/users/delete").session(session)
                        .param("aid", "PF3").param("usrIdIn", "USER0001"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/menu"));
    }

    @Test
    void pf12Pf4AndOtherAids_frS1240() throws Exception {
        MockHttpSession session = adminSession();
        mockMvc.perform(post("/admin/users/delete").session(session)
                        .param("aid", "PF12").param("usrIdIn", "USER0001"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/menu"));

        mockMvc.perform(post("/admin/users/delete").session(session)
                        .param("aid", "PF4").param("usrIdIn", "KEEP"))
                .andExpect(status().isOk())
                .andExpect(view().name("user-delete"))
                .andExpect(content().string(not(containsString("KEEP"))));

        mockMvc.perform(post("/admin/users/delete").session(session)
                        .param("aid", "PF6").param("usrIdIn", "KEEP"))
                .andExpect(status().isOk())
                .andExpect(view().name("user-delete"))
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
