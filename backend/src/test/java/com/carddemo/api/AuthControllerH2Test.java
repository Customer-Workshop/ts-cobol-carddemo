package com.carddemo.api;

import com.carddemo.security.SessionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Session semantics of the sign-on slice on the H2 unit profile with the R__seed_test_data users
 * (ADMIN001 'A', USER0001 'U', both sec_usr_pwd_legacy='PASSWORD'): B-0027 session written on
 * success (COSGN00C.cbl:222-229), B-0028 self-contained requests, FR-08 sign-off.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerH2Test {

    @Autowired
    private MockMvc mockMvc;

    private MvcResult signOn(String userId, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/signon").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    @Test
    @DisplayName("FR-05 / B-0027 — success stores SessionContext(userId,userType) in the HTTP session")
    void fr05SessionWritten() throws Exception {
        MvcResult result = signOn("user0001", "password");
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        SessionContext context = SessionContext.from(session).orElseThrow();
        assertEquals("USER0001", context.userId());
        assertEquals(SessionContext.UserType.USER, context.userType());

        mockMvc.perform(get("/api/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is("USER0001")))
                .andExpect(jsonPath("$.userType", is("U")));
    }

    @Test
    @DisplayName("FR-05 / Q-01 — ADMIN001 signs on, session type A, landingTarget /menu")
    void fr05AdminSession() throws Exception {
        MvcResult result = signOn("ADMIN001", "PASSWORD");
        assertEquals("/menu", com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.landingTarget"));
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertTrue(SessionContext.from(session).orElseThrow().isAdmin());
    }

    @Test
    @DisplayName("B-0027 — a protected endpoint is reachable with the sign-on session, 401 JSON without it")
    void b0027SessionAuthenticatesProtectedRequests() throws Exception {
        mockMvc.perform(get("/api/accounts/00000000001"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Authentication required")));

        MockHttpSession session = (MockHttpSession) signOn("USER0001", "PASSWORD").getRequest().getSession(false);
        mockMvc.perform(get("/api/accounts/00000000001").session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FR-08 — sign-off invalidates the session and returns E-14; session then answers 401")
    void fr08SignOffEndsSession() throws Exception {
        MockHttpSession session = (MockHttpSession) signOn("USER0001", "PASSWORD").getRequest().getSession(false);

        mockMvc.perform(post("/api/auth/signoff").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Thank you for using CardDemo application...      ")));
        assertTrue(session.isInvalid());

        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("B-0026 — a second successful sign-on replaces the session identity")
    void b0026SecondSignOnReplacesSession() throws Exception {
        MockHttpSession first = (MockHttpSession) signOn("USER0001", "PASSWORD").getRequest().getSession(false);

        MvcResult second = mockMvc.perform(post("/api/auth/signon").session(first)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"ADMIN001\",\"password\":\"PASSWORD\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession replaced = (MockHttpSession) second.getRequest().getSession(false);
        assertTrue(first.isInvalid());
        assertNotEquals(first.getId(), replaced.getId());
        assertEquals("ADMIN001", SessionContext.from(replaced).orElseThrow().userId());
    }

    @Test
    @DisplayName("FR-06 — a failed sign-on creates no session identity")
    void fr06FailureLeavesNoSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signon").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"USER0001\",\"password\":\"WRONG\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Wrong Password. Try again ...")))
                .andReturn();
        assertTrue(SessionContext.from(result.getRequest().getSession(false)).isEmpty());
    }
}
