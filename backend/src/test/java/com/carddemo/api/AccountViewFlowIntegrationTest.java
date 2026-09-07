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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CAVW end to end on real PostgreSQL 16 (Testcontainers): sign on, menu, option 1, then the whole
 * CXACAIX -> ACCTDAT -> CUSTDAT chain against the wave-1 fixture. Every expected string is decoded by
 * hand from app/data/ASCII (custdata.txt:1, acctdata.txt:2, cardxref.txt:3), never produced by the
 * Java under test. The complete chain runs over account 99999999999, the fixture's only account with
 * both an xref and a customer row (the wave-1 Q-04 pair); its account columns are the synthetic
 * wave-1 values, which is what makes the negative-balance edit visible here.
 */
@AutoConfigureMockMvc
class AccountViewFlowIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession signOnAndSelectAccountView() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/auth/signon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"USER0001\",\"password\":\"PASSWORD\"}"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);

        mockMvc.perform(post("/api/menu/select").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"option\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route", is("/accounts/view")));
        return session;
    }

    @Test
    @DisplayName("FR-11 / FR-16..FR-19 — menu option 1 then account 99999999999 paints the whole map from the fixture rows")
    void fr16FixtureAccountEndToEnd() throws Exception {
        MockHttpSession session = signOnAndSelectAccountView();

        mockMvc.perform(get("/api/accounts/99999999999").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.tranId", is("CAVW")))
                .andExpect(jsonPath("$.header.programName", is("COACTVWC")))
                .andExpect(jsonPath("$.accountNumber", is("99999999999")))
                .andExpect(jsonPath("$.infoMessage", is("Enter or update id of account to display")))
                // wave-1 fixture account row: N / -12.34 / 1000.00 / 500.00 / 2020-01-01 / 2030-01-01
                .andExpect(jsonPath("$.account.activeStatus", is("N")))
                .andExpect(jsonPath("$.account.openDate", is("2020-01-01")))
                .andExpect(jsonPath("$.account.creditLimit", is("+      1,000.00")))
                .andExpect(jsonPath("$.account.expirationDate", is("2030-01-01")))
                .andExpect(jsonPath("$.account.cashCreditLimit", is("+        500.00")))
                .andExpect(jsonPath("$.account.reissueDate", is("2030-01-01")))
                .andExpect(jsonPath("$.account.currentBalance", is("-         12.34")))
                .andExpect(jsonPath("$.account.currentCycleCredit", is("+           .00")))
                .andExpect(jsonPath("$.account.currentCycleDebit", is("+           .00")))
                // DV-06 / D-0038: the stored group id is displayed, not the legacy screen's blank
                .andExpect(jsonPath("$.account.groupId", is("ZZZZZZZZZZ")))
                // custdata.txt:1, reached through the lowest card 4000000000000001
                .andExpect(jsonPath("$.customer.customerId", is("000000001")))
                .andExpect(jsonPath("$.customer.firstName", is("Immanuel")))
                .andExpect(jsonPath("$.customer.middleName", is("Madeline")))
                .andExpect(jsonPath("$.customer.lastName", is("Kessler")))
                .andExpect(jsonPath("$.customer.addressLine1", is("618 Deshaun Route")))
                .andExpect(jsonPath("$.customer.addressLine2", is("Apt. 802")))
                .andExpect(jsonPath("$.customer.city", is("Altenwerthshire")))
                .andExpect(jsonPath("$.customer.stateCode", is("NC")))
                .andExpect(jsonPath("$.customer.countryCode", is("USA")))
                // custdata.txt:1 CUST-SSN 020973888 -> STRING (1:3)'-'(4:2)'-'(6:4)
                .andExpect(jsonPath("$.customer.ssn", is("020-97-3888")))
                .andExpect(jsonPath("$.customer.dateOfBirth", is("1961-06-08")))
                .andExpect(jsonPath("$.customer.ficoScore", is(274)))
                .andExpect(jsonPath("$.customer.governmentIssuedId", is("00000000000049368437")))
                .andExpect(jsonPath("$.customer.eftAccountId", is("0053581756")))
                .andExpect(jsonPath("$.customer.primaryCardHolder", is("Y")))
                // DV-05: the full stored ZIP and both full phone numbers
                .andExpect(jsonPath("$.customer.zipCode", is("12546")))
                .andExpect(jsonPath("$.customer.phone1", is("(908)119-8310")))
                .andExpect(jsonPath("$.customer.phone2", is("(373)693-8684")));

        // DV-02: the view never rewrites the signed-on identity
        assertEquals("USER0001", SessionContext.from(session).orElseThrow().userId());
    }

    @Test
    @DisplayName("FR-14 / FR-15 — the input edits reject blank and malformed filters before any read")
    void fr14AndFr15InputEditsOverHttp() throws Exception {
        MockHttpSession session = signOnAndSelectAccountView();

        mockMvc.perform(get("/api/accounts/").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("No input received")));
        mockMvc.perform(get("/api/accounts/27").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Account Filter must  be a non-zero 11 digit number")));
        mockMvc.perform(get("/api/accounts/0000000002A").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Account Filter must  be a non-zero 11 digit number")));
        mockMvc.perform(get("/api/accounts/00000000000").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Account Filter must  be a non-zero 11 digit number")));
    }

    @Test
    @DisplayName("FR-17 / E-09 — account 00000000001 has no CXACAIX row in the fixture")
    void fr17XrefNotFoundOverHttp() throws Exception {
        MockHttpSession session = signOnAndSelectAccountView();

        mockMvc.perform(get("/api/accounts/00000000001").session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message",
                        is("Account:00000000001 not found in Cross ref file.  Resp:0000000013 Reas:0000")));
    }

    @Test
    @DisplayName("DV-01 / E-10 — xref 0500024453765740 points at account 50, which ACCTDAT does not hold")
    void dv01AccountNotFoundOverHttp() throws Exception {
        MockHttpSession session = signOnAndSelectAccountView();

        mockMvc.perform(get("/api/accounts/00000000050").session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message",
                        is("Account:00000000050 not found in Acct Master file.Resp:0000000013 Reas:0000")))
                .andExpect(jsonPath("$.account").doesNotExist());
    }

    @Test
    @DisplayName("FR-20 / E-11 / DV-06 — account 2 exists but customer 2 does not: the account block still paints")
    void fr20CustomerNotFoundOverHttp() throws Exception {
        MockHttpSession session = signOnAndSelectAccountView();

        mockMvc.perform(get("/api/accounts/00000000002").session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message",
                        is("CustId:000000002 not found in customer master.Resp: 0000000013 REAS:0000000")))
                .andExpect(jsonPath("$.accountNumber", is("00000000002")))
                // acctdata.txt:2 "Y 00000001580{ 00000061300{ 00000054480{ 2013-06-19 2024-08-11 2024-08-11"
                .andExpect(jsonPath("$.account.currentBalance", is("+        158.00")))
                .andExpect(jsonPath("$.account.creditLimit", is("+      6,130.00")))
                .andExpect(jsonPath("$.account.cashCreditLimit", is("+      5,448.00")))
                .andExpect(jsonPath("$.account.openDate", is("2013-06-19")))
                // DV-06 on a real extract row: acctdata.txt:2 stores group 'A000000000'
                .andExpect(jsonPath("$.account.groupId", is("A000000000")));
    }

    @Test
    @DisplayName("B-0027 / FR-12 — after sign-off the view answers 401, and it never answered without a session")
    void b0027SessionRequiredOverHttp() throws Exception {
        MockHttpSession session = signOnAndSelectAccountView();
        mockMvc.perform(post("/api/auth/signoff").session(session)).andExpect(status().isOk());

        mockMvc.perform(get("/api/accounts/99999999999"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Authentication required")));
    }
}
