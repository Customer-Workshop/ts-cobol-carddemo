package com.carddemo.api;

import com.carddemo.security.SecurityConfig;
import com.carddemo.security.SessionContext;
import com.carddemo.service.AuthService;
import com.carddemo.service.ScreenHeaderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * COSGN00C controller contract: status + verbatim ErrorResponse per branch (FR §5) with the
 * service mocked. Session behaviour on the real service is in AuthControllerH2Test.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private ScreenHeaderService headerService;

    private static String json(String userId, String password) {
        return "{\"userId\":\"" + userId + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    @DisplayName("FR-05 — 200 {userId,userType,landingTarget} for a regular user (COSGN00C.cbl:221-233)")
    void fr05Success() throws Exception {
        SessionContext context = new SessionContext("USER0001", SessionContext.UserType.USER);
        when(authService.signOn("USER0001", "PASSWORD")).thenReturn(context);
        when(authService.landingTarget(context)).thenReturn("/menu");

        mockMvc.perform(post("/api/auth/signon").contentType(MediaType.APPLICATION_JSON)
                        .content(json("USER0001", "PASSWORD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is("USER0001")))
                .andExpect(jsonPath("$.userType", is("U")))
                .andExpect(jsonPath("$.landingTarget", is("/menu")));
    }

    @Test
    @DisplayName("FR-05 / Q-01 — admin ('A') also receives landingTarget /menu (COSGN00C.cbl:234-239, D-0024)")
    void fr05AdminRouting() throws Exception {
        SessionContext context = new SessionContext("ADMIN001", SessionContext.UserType.ADMIN);
        when(authService.signOn("ADMIN001", "PASSWORD")).thenReturn(context);
        when(authService.landingTarget(context)).thenReturn("/menu");

        mockMvc.perform(post("/api/auth/signon").contentType(MediaType.APPLICATION_JSON)
                        .content(json("ADMIN001", "PASSWORD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userType", is("A")))
                .andExpect(jsonPath("$.landingTarget", is("/menu")));
    }

    @Test
    @DisplayName("FR-02 — E-01 as 400 ErrorResponse (COSGN00C.cbl:120)")
    void fr02BlankUserIdPayload() throws Exception {
        when(authService.signOn("", "PASSWORD"))
                .thenThrow(new CobolApiException(HttpStatus.BAD_REQUEST, CobolMessages.ENTER_USER_ID));
        mockMvc.perform(post("/api/auth/signon").contentType(MediaType.APPLICATION_JSON)
                        .content(json("", "PASSWORD")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Please enter User ID ...")))
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("FR-03 — E-02 as 400 ErrorResponse (COSGN00C.cbl:125)")
    void fr03BlankPasswordPayload() throws Exception {
        when(authService.signOn("USER0001", ""))
                .thenThrow(new CobolApiException(HttpStatus.BAD_REQUEST, CobolMessages.ENTER_PASSWORD));
        mockMvc.perform(post("/api/auth/signon").contentType(MediaType.APPLICATION_JSON)
                        .content(json("USER0001", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Please enter Password ...")));
    }

    @Test
    @DisplayName("FR-06 — E-07 as 401 ErrorResponse (COSGN00C.cbl:249)")
    void fr06NotFoundPayload() throws Exception {
        when(authService.signOn("NOBODY", "PASSWORD"))
                .thenThrow(new CobolApiException(HttpStatus.UNAUTHORIZED, CobolMessages.USER_NOT_FOUND));
        mockMvc.perform(post("/api/auth/signon").contentType(MediaType.APPLICATION_JSON)
                        .content(json("NOBODY", "PASSWORD")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("User not found. Try again ...")));
    }

    @Test
    @DisplayName("FR-06 — E-06 as 401 ErrorResponse (COSGN00C.cbl:242)")
    void fr06WrongPasswordPayload() throws Exception {
        when(authService.signOn("USER0001", "BAD"))
                .thenThrow(new CobolApiException(HttpStatus.UNAUTHORIZED, CobolMessages.WRONG_PASSWORD));
        mockMvc.perform(post("/api/auth/signon").contentType(MediaType.APPLICATION_JSON)
                        .content(json("USER0001", "BAD")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Wrong Password. Try again ...")));
    }

    @Test
    @DisplayName("FR-07 — E-13 as 500 ErrorResponse (COSGN00C.cbl:254)")
    void fr07TechnicalErrorPayload() throws Exception {
        when(authService.signOn("USER0001", "PASSWORD"))
                .thenThrow(new CobolApiException(HttpStatus.INTERNAL_SERVER_ERROR, CobolMessages.UNABLE_TO_VERIFY_USER));
        mockMvc.perform(post("/api/auth/signon").contentType(MediaType.APPLICATION_JSON)
                        .content(json("USER0001", "PASSWORD")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message", is("Unable to verify the User ...")))
                .andExpect(jsonPath("$.status", is(500)));
    }

    @Test
    @DisplayName("A-SGN-1 / Q-14 — 9-character User ID rejected as 400 before the service runs (BMS LENGTH=8)")
    void aSgn1OverLengthUserId() throws Exception {
        mockMvc.perform(post("/api/auth/signon").contentType(MediaType.APPLICATION_JSON)
                        .content(json("USER00001", "PASSWORD")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("userId: " + CobolMessages.FIELD_TOO_LONG)));
        verify(authService, never()).signOn(anyString(), anyString());
    }

    @Test
    @DisplayName("FR-08 — POST /api/auth/signoff answers 200 with the thank-you text even without a session")
    void fr08SignOffWithoutSession() throws Exception {
        when(authService.signOffMessage()).thenReturn(CobolMessages.THANK_YOU);
        mockMvc.perform(post("/api/auth/signoff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Thank you for using CardDemo application...      ")));
    }

    @Test
    @DisplayName("B-0027 — GET /api/auth/session without a session -> 401 JSON")
    void b0027SessionMissing() throws Exception {
        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Authentication required")));
    }

    @Test
    @DisplayName("FR-01 — GET /api/auth/header is permitted without authentication (/api/auth/** permitAll)")
    void fr01HeaderPermitted() throws Exception {
        when(headerService.header("CC00", "COSGN00C")).thenReturn(new ScreenHeaderResponse(
                "CC00", "COSGN00C", "AWS Mainframe Modernization", "CardDemo", "09/07/26", "14:05:09", "CARDDEMO", "CICS"));
        mockMvc.perform(get("/api/auth/header"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tranId", is("CC00")))
                .andExpect(jsonPath("$.programName", is("COSGN00C")))
                .andExpect(jsonPath("$.currentDate", is("09/07/26")))
                .andExpect(jsonPath("$.applId", is("CARDDEMO")));
        verify(headerService).header(any(), any());
    }
}
