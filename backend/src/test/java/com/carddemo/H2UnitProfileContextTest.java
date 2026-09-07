package com.carddemo;

import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.security.SessionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** H2 unit profile (Docker-free): Flyway V1 + fixture run in PostgreSQL mode; security skeleton answers JSON. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class H2UnitProfileContextTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CardXrefRepository cardXrefRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Flyway migration + fixture load on H2 (MODE=PostgreSQL); ImportRunner is NOT active outside the import profile")
    void contextLoadsWithFixture() {
        assertEquals(3, accountRepository.count());
        assertEquals("4000000000000001",
                cardXrefRepository.findFirstByXrefAcctIdOrderByXrefCardNumberAsc(99999999999L).orElseThrow()
                        .getXrefCardNumber());
        assertFalse(context.containsBean("importRunner"), "default-profile seeding is forbidden (DATA drift rule 3)");
    }

    @Test
    @DisplayName("Security skeleton: unauthenticated API call -> JSON 401 ErrorResponse, no redirect to a login page")
    void unauthenticatedRequestGetsJson401() throws Exception {
        mockMvc.perform(get("/api/accounts/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", is("Authentication required")));
    }

    @Test
    @DisplayName("B-0027 session model: userId X(8) + userType A/U only (COCOM01Y.cpy:25-28)")
    void sessionContextModel() {
        SessionContext admin = new SessionContext("ADMIN001", SessionContext.UserType.fromLegacyCode("A"));
        assertEquals(true, admin.isAdmin());
        assertEquals('U', SessionContext.UserType.USER.legacyCode());
        assertThrows(IllegalArgumentException.class, () -> new SessionContext("TOOLONGUSER", SessionContext.UserType.USER));
        assertThrows(IllegalArgumentException.class, () -> SessionContext.UserType.fromLegacyCode("X"));
    }
}
