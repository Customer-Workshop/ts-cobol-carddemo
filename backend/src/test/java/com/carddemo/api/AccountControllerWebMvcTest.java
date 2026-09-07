package com.carddemo.api;

import com.carddemo.security.SecurityConfig;
import com.carddemo.security.SessionContext;
import com.carddemo.service.AccountViewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * COACTVWC controller contract with the service mocked: status plus verbatim payload per branch
 * (FR §3, §5, §7) and the B-0027 401 that replaces the {@code EIBCALEN = 0} refusal (cbl:266-270).
 */
@WebMvcTest(AccountController.class)
@Import(SecurityConfig.class)
class AccountControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountViewService accountViewService;

    private static MockHttpSession signedOnSession() {
        MockHttpSession session = new MockHttpSession();
        new SessionContext("USER0001", SessionContext.UserType.USER).store(session);
        return session;
    }

    private static MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder) {
        return builder.session(signedOnSession()).with(user("USER0001").roles("USER"));
    }

    private static AccountBlock account27Block() {
        return new AccountBlock("Y", "2012-09-30", "+      5,572.00", "2025-07-13", "+      2,075.00",
                "2025-07-13", "+        284.00", "+           .00", "A000000000", "+           .00");
    }

    private static CustomerBlock customer27Block() {
        return new CustomerBlock("000000027", "980-16-1210", "1986-11-08", 78, "Ward", "Henri", "Jones",
                "210 Amaya Turnpike", "Suite 180", "Port Dwight", "GU", "07923-8822", "USA",
                "(935)027-1145", "(103)537-5007", "00000000000881558757", "0050024139", "Y");
    }

    @Test
    @DisplayName("FR-16 / FR-18 / FR-19 — GET /api/accounts/{acctId} returns the painted map CACTVWA")
    void fr16Success() throws Exception {
        when(accountViewService.view("00000000027")).thenReturn(new AccountViewResponse(
                new ScreenHeaderResponse("CAVW", "COACTVWC", "AWS Mainframe Modernization", "CardDemo",
                        "09/07/26", "14:05:09", "CARDDEMO", "CICS"),
                "00000000027", CobolMessages.ACCOUNT_VIEW_PROMPT, account27Block(), customer27Block()));

        mockMvc.perform(authenticated(get("/api/accounts/00000000027")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.tranId", is("CAVW")))
                .andExpect(jsonPath("$.header.programName", is("COACTVWC")))
                .andExpect(jsonPath("$.accountNumber", is("00000000027")))
                .andExpect(jsonPath("$.infoMessage", is("Enter or update id of account to display")))
                .andExpect(jsonPath("$.account.activeStatus", is("Y")))
                .andExpect(jsonPath("$.account.creditLimit", is("+      5,572.00")))
                .andExpect(jsonPath("$.account.currentBalance", is("+        284.00")))
                .andExpect(jsonPath("$.account.groupId", is("A000000000")))
                .andExpect(jsonPath("$.customer.customerId", is("000000027")))
                .andExpect(jsonPath("$.customer.ssn", is("980-16-1210")))
                .andExpect(jsonPath("$.customer.zipCode", is("07923-8822")))
                .andExpect(jsonPath("$.customer.phone1", is("(935)027-1145")))
                .andExpect(jsonPath("$.customer.ficoScore", is(78)));
    }

    @Test
    @DisplayName("FR-14 / E-04 — the raw filter reaches the service, which answers 400 'No input received' (Q-14)")
    void fr14ValidationPayload() throws Exception {
        when(accountViewService.view("abc"))
                .thenThrow(new CobolApiException(HttpStatus.BAD_REQUEST, CobolMessages.NO_INPUT_RECEIVED));

        mockMvc.perform(authenticated(get("/api/accounts/abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("No input received")))
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("FR-14 / E-04 — an empty path segment is the blank ACCTSID field, not a 404 route")
    void fr14BlankPathSegment() throws Exception {
        when(accountViewService.view(isNull()))
                .thenThrow(new CobolApiException(HttpStatus.BAD_REQUEST, CobolMessages.NO_INPUT_RECEIVED));

        mockMvc.perform(authenticated(get("/api/accounts/")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("No input received")));

        mockMvc.perform(authenticated(get("/api/accounts")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("No input received")));
    }

    @Test
    @DisplayName("FR-15 / E-05 — 400 with the verbatim two-space literal")
    void fr15InvalidFilterPayload() throws Exception {
        when(accountViewService.view("0000000000A")).thenThrow(new CobolApiException(
                HttpStatus.BAD_REQUEST, CobolMessages.ACCOUNT_FILTER_INVALID));

        mockMvc.perform(authenticated(get("/api/accounts/0000000000A")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Account Filter must  be a non-zero 11 digit number")));
    }

    @Test
    @DisplayName("FR-17 / E-09 — 404 with the CXACAIX text and no payload")
    void fr17XrefNotFoundPayload() throws Exception {
        when(accountViewService.view("00000000001")).thenThrow(new CobolApiException(HttpStatus.NOT_FOUND,
                CobolMessages.xrefNotFound("00000000001", CobolMessages.RESP_NOTFND, CobolMessages.REAS_NONE)));

        mockMvc.perform(authenticated(get("/api/accounts/00000000001")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message",
                        is("Account:00000000001 not found in Cross ref file.  Resp:0000000013 Reas:0000")))
                .andExpect(jsonPath("$.account").doesNotExist())
                .andExpect(jsonPath("$.customer").doesNotExist());
    }

    @Test
    @DisplayName("DV-01 / E-10 — 404 with the ACCTDAT text only: no account and no customer block")
    void dv01AccountNotFoundPayload() throws Exception {
        when(accountViewService.view("00000000050")).thenThrow(new CobolApiException(HttpStatus.NOT_FOUND,
                CobolMessages.accountNotFound("00000000050", CobolMessages.RESP_NOTFND, CobolMessages.REAS_NONE)));

        mockMvc.perform(authenticated(get("/api/accounts/00000000050")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message",
                        is("Account:00000000050 not found in Acct Master file.Resp:0000000013 Reas:0000")))
                .andExpect(jsonPath("$.account").doesNotExist());
    }

    @Test
    @DisplayName("FR-20 / E-11 — 404 keeps the account block visible under the message (A-ACV-1)")
    void fr20CustomerNotFoundKeepsAccountBlock() throws Exception {
        when(accountViewService.view("00000000027")).thenThrow(new CustomerNotFoundException(
                CobolMessages.customerNotFound("000000027", CobolMessages.RESP_NOTFND, CobolMessages.REAS_NONE),
                "00000000027", account27Block()));

        mockMvc.perform(authenticated(get("/api/accounts/00000000027")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message",
                        is("CustId:000000027 not found in customer master.Resp: 0000000013 REAS:0000000")))
                .andExpect(jsonPath("$.accountNumber", is("00000000027")))
                .andExpect(jsonPath("$.account.currentBalance", is("+        284.00")))
                .andExpect(jsonPath("$.account.groupId", is("A000000000")))
                .andExpect(jsonPath("$.customer").doesNotExist());
    }

    @Test
    @DisplayName("E-12 / D-0040 — a datastore failure surfaces this program's File Error text, not a stack trace")
    void e12FileErrorPayload() throws Exception {
        when(accountViewService.view("00000000027")).thenThrow(new CobolApiException(
                HttpStatus.INTERNAL_SERVER_ERROR, CobolMessages.fileError(CobolMessages.OP_READ,
                        CobolMessages.FILE_ACCTDAT, CobolMessages.RESP_OTHER, CobolMessages.REAS_NONE)));

        mockMvc.perform(authenticated(get("/api/accounts/00000000027")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message",
                        is("File Error: READ     on ACCTDAT   returned RESP 0000000016,RESP2 0000000000")));
    }

    @Test
    @DisplayName("B-0027 — without the sign-on session the endpoint answers JSON 401 and never reads (cbl:266-270)")
    void b0027UnauthenticatedIs401() throws Exception {
        mockMvc.perform(get("/api/accounts/00000000027"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Authentication required")));

        verify(accountViewService, never()).view(any());
    }

    @Test
    @DisplayName("FR-21 — the account view is read-only: POST is not allowed")
    void fr21NoWriteEndpoint() throws Exception {
        mockMvc.perform(authenticated(post("/api/accounts/00000000027")))
                .andExpect(status().isMethodNotAllowed());
    }
}
