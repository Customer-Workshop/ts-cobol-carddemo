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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The REST surface behind the COUSR0nC screens — same endpoints as the
 * baseline contract, offset paging, and the verbatim USRSEC messages
 * (values stored as typed per S12-B2).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:useradminapitest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserAdminApiIntegrationTest {

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
        for (int i = 1; i <= 12; i++) {
            SecurityUser row = new SecurityUser();
            row.setUserId("A%07d".formatted(i));
            row.setFirstName("FIRST" + i);
            row.setLastName("LAST" + i);
            row.setPassword("PASS" + i);
            row.setUserType("U");
            userRepository.save(row);
        }
    }

    @Test
    void adminEndpointsRequireAuthAndAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());

        MockHttpSession regular = signon("USER0001", "PASSWORD", "/api/menu");
        mockMvc.perform(get("/api/admin/users").session(regular))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/users/USER0001").session(regular))
                .andExpect(status().isForbidden());
    }

    @Test
    void listPagesAndFiltersWithStoredValues() throws Exception {
        MockHttpSession admin = signon("ADMIN001", "PASSWORD", "/api/admin/menu");
        mockMvc.perform(get("/api/admin/users").session(admin).param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(4))
                .andExpect(jsonPath("$.hasNextPage").value(false))
                .andExpect(jsonPath("$.hasPreviousPage").value(true));
        // Store-as-typed filter: 'a0000002' does not match 'A0000002'.
        mockMvc.perform(get("/api/admin/users").session(admin)
                        .param("userId", "A0000005"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[0].userId").value("A0000005"));
        mockMvc.perform(get("/api/admin/users").session(admin)
                        .param("userId", "a0000002"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("You have reached the bottom of the page..."));
    }

    @Test
    void addChecksFieldsInCobolOrderAndWidths() throws Exception {
        MockHttpSession admin = signon("ADMIN001", "PASSWORD", "/api/admin/menu");
        mockMvc.perform(post("/api/admin/users").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"userId":"N1","firstName":"", "lastName":"L",
                                 "password":"P","userType":"U"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("First Name can NOT be empty..."));
        mockMvc.perform(post("/api/admin/users").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"userId":"TOOLONGID","firstName":"F","lastName":"L",
                                 "password":"P","userType":"U"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("User ID must not exceed 8 characters..."));
    }

    @Test
    void addPersistsAsTypedAndRejectsDuplicates() throws Exception {
        MockHttpSession admin = signon("ADMIN001", "PASSWORD", "/api/admin/menu");
        mockMvc.perform(post("/api/admin/users").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"userId":"newid01","firstName":"New","lastName":"User",
                                 "password":"lowpass","userType":"U"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("newid01"));
        mockMvc.perform(post("/api/admin/users").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"userId":"newid01","firstName":"Dup","lastName":"User",
                                 "password":"P","userType":"U"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("User ID already exist..."));
    }

    @Test
    void updateReportsNotFoundModifyAndSuccess() throws Exception {
        MockHttpSession admin = signon("ADMIN001", "PASSWORD", "/api/admin/menu");
        mockMvc.perform(put("/api/admin/users/GHOST001").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"userId":"GHOST001","firstName":"F","lastName":"L",
                                 "password":"P","userType":"U"}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User ID NOT found..."));
        // Byte-compare: no change → 'Please modify to update ...'
        mockMvc.perform(put("/api/admin/users/USER0001").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"userId":"USER0001","firstName":"REGULAR","lastName":"USER",
                                 "password":"PASSWORD","userType":"U"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please modify to update ..."));
        mockMvc.perform(put("/api/admin/users/USER0001").session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"userId":"USER0001","firstName":"Renamed","lastName":"USER",
                                 "password":"PASSWORD","userType":"U"}"""))
                .andExpect(status().isOk());
    }

    @Test
    void deleteRemovesRowAndReportsNotFound() throws Exception {
        MockHttpSession admin = signon("ADMIN001", "PASSWORD", "/api/admin/menu");
        mockMvc.perform(delete("/api/admin/users/A0000003").session(admin))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/users/A0000003").session(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User ID NOT found..."));
    }

    private MockHttpSession signon(String userId, String password, String landing)
            throws Exception {
        var result = mockMvc.perform(post("/api/auth/signon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"password\":\""
                                + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.landingTarget").value(landing))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
