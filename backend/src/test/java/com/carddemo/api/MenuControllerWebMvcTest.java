package com.carddemo.api;

import com.carddemo.security.SecurityConfig;
import com.carddemo.security.SessionContext;
import com.carddemo.service.MenuService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * COMEN01C controller contract with the service mocked: status plus verbatim payload per branch
 * (FR §3, §5) and the B-0027 401 that replaces the {@code EIBCALEN = 0} refusal (cbl:82-84).
 */
@WebMvcTest(MenuController.class)
@Import(SecurityConfig.class)
class MenuControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MenuService menuService;

    private static MockHttpSession signedOnSession() {
        MockHttpSession session = new MockHttpSession();
        new SessionContext("USER0001", SessionContext.UserType.USER).store(session);
        return session;
    }

    private static MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder) {
        return builder.session(signedOnSession()).with(user("USER0001").roles("USER"));
    }

    @Test
    @DisplayName("FR-09 — GET /api/menu returns the header and the 11 option rows (cbl:208-303)")
    void fr09Menu() throws Exception {
        when(menuService.menu()).thenReturn(new MenuResponse(
                new ScreenHeaderResponse("CM00", "COMEN01C", "AWS Mainframe Modernization", "CardDemo",
                        "09/07/26", "14:05:09", "CARDDEMO", "CICS"),
                List.of(
                        new MenuOption(1, "Account View", "COACTVWC", "/api/accounts/{acctId}", "/accounts/view", true, "U"),
                        new MenuOption(2, "Account Update", "COACTUPC", null, null, false, "U"))));

        mockMvc.perform(authenticated(get("/api/menu")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.tranId", is("CM00")))
                .andExpect(jsonPath("$.header.programName", is("COMEN01C")))
                .andExpect(jsonPath("$.header.currentDate", is("09/07/26")))
                .andExpect(jsonPath("$.header.currentTime", is("14:05:09")))
                .andExpect(jsonPath("$.options[0].number", is(1)))
                .andExpect(jsonPath("$.options[0].name", is("Account View")))
                .andExpect(jsonPath("$.options[0].program", is("COACTVWC")))
                .andExpect(jsonPath("$.options[0].endpoint", is("/api/accounts/{acctId}")))
                .andExpect(jsonPath("$.options[0].route", is("/accounts/view")))
                .andExpect(jsonPath("$.options[0].implemented", is(true)))
                .andExpect(jsonPath("$.options[0].userType", is("U")))
                .andExpect(jsonPath("$.options[1].implemented", is(false)))
                .andExpect(jsonPath("$.options[1].route", is(nullValue())));
    }

    @Test
    @DisplayName("FR-11 — POST /api/menu/select option 1 answers the dispatch payload (cbl:177-187)")
    void fr11SelectOptionOne() throws Exception {
        when(menuService.select("1")).thenReturn(new MenuSelectionResponse(
                "01", "COACTVWC", "/api/accounts/{acctId}", "/accounts/view", true, null));

        mockMvc.perform(authenticated(post("/api/menu/select"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"option\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.option", is("01")))
                .andExpect(jsonPath("$.program", is("COACTVWC")))
                .andExpect(jsonPath("$.route", is("/accounts/view")))
                .andExpect(jsonPath("$.implemented", is(true)))
                .andExpect(jsonPath("$.message", is(nullValue())));
    }

    @Test
    @DisplayName("Q-12 — an excluded option answers 200 with the facade text and no route")
    void q12SelectUnavailableOption() throws Exception {
        when(menuService.select("05")).thenReturn(new MenuSelectionResponse(
                "05", "COCRDUPC", null, null, false, CobolMessages.OPTION_NOT_AVAILABLE));

        mockMvc.perform(authenticated(post("/api/menu/select"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"option\":\"05\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.option", is("05")))
                .andExpect(jsonPath("$.program", is("COCRDUPC")))
                .andExpect(jsonPath("$.implemented", is(false)))
                .andExpect(jsonPath("$.route", is(nullValue())))
                .andExpect(jsonPath("$.message", is("Option not available in this release")));
    }

    @Test
    @DisplayName("FR-10 — E-08 is a 400 body carrying the verbatim text and the normalised echo (cbl:125-134)")
    void fr10InvalidOptionPayload() throws Exception {
        when(menuService.select("")).thenThrow(new InvalidMenuOptionException("00"));

        mockMvc.perform(authenticated(post("/api/menu/select"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"option\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Please enter a valid option number...")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.option", is("00")));
    }

    @Test
    @DisplayName("Q-14 — a 3-character option is rejected as 400 before the service runs (COMEN01.bms:145)")
    void q14OverLengthOption() throws Exception {
        mockMvc.perform(authenticated(post("/api/menu/select"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"option\":\"001\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("option: " + CobolMessages.OPTION_TOO_LONG)));
        verify(menuService, never()).select(anyString());
    }

    @Test
    @DisplayName("B-0027 — GET /api/menu without a session answers 401 JSON (target form of cbl:82-84)")
    void b0027MenuRequiresSession() throws Exception {
        mockMvc.perform(get("/api/menu"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Authentication required")));
        verify(menuService, never()).menu();
    }

    @Test
    @DisplayName("B-0027 — POST /api/menu/select without a session answers 401 JSON")
    void b0027SelectRequiresSession() throws Exception {
        mockMvc.perform(post("/api/menu/select")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"option\":\"1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Authentication required")));
        verify(menuService, never()).select(anyString());
    }

    @Test
    @DisplayName("B-0027 — an authenticated request whose session lost the identity answers 401")
    void b0027MissingSessionContext() throws Exception {
        mockMvc.perform(get("/api/menu").session(new MockHttpSession()).with(user("USER0001").roles("USER")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Authentication required")));
    }
}
