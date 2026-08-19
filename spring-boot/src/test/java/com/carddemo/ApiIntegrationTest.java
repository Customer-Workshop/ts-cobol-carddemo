package com.carddemo;

import com.carddemo.model.SecurityUser;
import com.carddemo.repository.SecurityUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ApiIntegrationTest {
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
    void signonAndSessionSupportAdminAndRegularUsers() throws Exception {
        MockHttpSession adminSession = signon("admin001", "password", "/api/admin/menu");
        mockMvc.perform(get("/api/auth/session").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("ADMIN001"))
                .andExpect(jsonPath("$.userType").value("A"));

        MockHttpSession userSession = signon("user0001", "password", "/api/menu");
        mockMvc.perform(get("/api/menu").session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options.length()").value(11));
        mockMvc.perform(get("/api/admin/menu").session(userSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void signonPreservesCobolValidationOrderAndMessages() throws Exception {
        mockMvc.perform(post("/api/auth/signon").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please enter User ID ..."));
        mockMvc.perform(post("/api/auth/signon").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"ADMIN001\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please enter Password ..."));
        mockMvc.perform(post("/api/auth/signon").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"ADMIN001\",\"password\":\"WRONG\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Wrong Password. Try again ..."));
    }

    @Test
    void menuSelectionAndAccountViewUseCobolTargetsAndValues() throws Exception {
        MockHttpSession session = signon("ADMIN001", "PASSWORD", "/api/admin/menu");
        mockMvc.perform(post("/api/menu/select").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"option\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.program").value("COACTVWC"))
                .andExpect(jsonPath("$.implemented").value(true));
        mockMvc.perform(post("/api/menu/select").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"option\":\"11\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.implemented").value(false))
                .andExpect(jsonPath("$.message").value(containsString("not installed")));

        mockMvc.perform(get("/api/accounts/00000000001").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentBalance").value(194.00))
                .andExpect(jsonPath("$.ssn").value("123-45-6789"));
        mockMvc.perform(get("/api/accounts/1").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1));
        mockMvc.perform(get("/api/accounts/00000000002").session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Cross ref file")));
    }

    private MockHttpSession signon(String userId, String password, String landing) throws Exception {
        var result = mockMvc.perform(post("/api/auth/signon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.landingTarget").value(landing))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
