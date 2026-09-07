package com.carddemo.api;

import com.carddemo.security.SessionContext;
import com.carddemo.support.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sign-on to menu dispatch on real PostgreSQL 16 (Testcontainers, R__seed_test_data users):
 * FR-05 landing target, FR-09 render, FR-11 dispatch of option 1, Q-12 facade, FR-10 E-08 and
 * FR-12 Exit. The menu never rewrites the identity (COMEN01C.cbl:181-182 are commented out).
 */
@AutoConfigureMockMvc
class MenuFlowIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession signOn(String userId) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/auth/signon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"password\":\"PASSWORD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.landingTarget", is("/menu")))
                .andReturn().getRequest().getSession(false);
    }

    @Test
    @DisplayName("FR-05 / FR-09 / FR-11 — sign on, render the menu, select option 1, get /accounts/view")
    void fr11EndToEndDispatch() throws Exception {
        MockHttpSession session = signOn("USER0001");

        mockMvc.perform(get("/api/menu").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.tranId", is("CM00")))
                .andExpect(jsonPath("$.header.programName", is("COMEN01C")))
                .andExpect(jsonPath("$.options.length()", is(11)))
                .andExpect(jsonPath("$.options[0].name", is("Account View")))
                .andExpect(jsonPath("$.options[10].name", is("Pending Authorization View")));

        mockMvc.perform(post("/api/menu/select").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"option\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.program", is("COACTVWC")))
                .andExpect(jsonPath("$.route", is("/accounts/view")))
                .andExpect(jsonPath("$.implemented", is(true)));

        assertEquals("USER0001", SessionContext.from(session).orElseThrow().userId());
    }

    @Test
    @DisplayName("Q-01 — an admin lands on the menu and selects option 1 exactly like a user (D-0024)")
    void q01AdminUsesTheSameMenu() throws Exception {
        MockHttpSession session = signOn("ADMIN001");

        mockMvc.perform(get("/api/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userType", is("A")));
        mockMvc.perform(post("/api/menu/select").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"option\":\"01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route", is("/accounts/view")));
    }

    @Test
    @DisplayName("FR-10 / Q-12 — blank option is 400 E-08 with echo 00; option 5 is the facade")
    void fr10AndQ12OverHttp() throws Exception {
        MockHttpSession session = signOn("USER0001");

        mockMvc.perform(post("/api/menu/select").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"option\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Please enter a valid option number...")))
                .andExpect(jsonPath("$.option", is("00")));

        mockMvc.perform(post("/api/menu/select").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"option\":\"5\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.option", is("05")))
                .andExpect(jsonPath("$.implemented", is(false)))
                .andExpect(jsonPath("$.route", is(nullValue())))
                .andExpect(jsonPath("$.message", is("Option not available in this release")));

        assertEquals("USER0001", SessionContext.from(session).orElseThrow().userId());
    }

    @Test
    @DisplayName("FR-12 — Exit invalidates the session; the menu then answers 401 (cbl:196-203)")
    void fr12ExitEndsTheSession() throws Exception {
        MockHttpSession session = signOn("USER0001");

        mockMvc.perform(post("/api/auth/signoff").session(session))
                .andExpect(status().isOk());
        assertTrue(session.isInvalid());

        mockMvc.perform(get("/api/menu"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Authentication required")));
    }
}
