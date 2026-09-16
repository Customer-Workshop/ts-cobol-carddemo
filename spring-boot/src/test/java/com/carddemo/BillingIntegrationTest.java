package com.carddemo;

import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.Transaction;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.TransactionRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * COBIL00C over the real seeded stores (S-11): the locked
 * read-modify-write, the duplicate-key outcome, and the D1 atomic
 * rollback — a failed write leaves no transaction row and the balance
 * untouched. Test names carry the FR row each covers. (Plan calls for
 * Testcontainers Postgres; this stack runs integration tests on the
 * per-class H2 datasource, same as every sibling stream.)
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:billingtest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class BillingIntegrationTest {

    private static final String PAYMENTS = "/api/billing/payments";

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private CardXrefRepository cardXrefRepository;
    @Autowired private TransactionRepository transactionRepository;

    @BeforeEach
    void resetSeededState() {
        // Tests share the class datasource; restore the seeded 194.00
        // balance and the account's card xref.
        Account account = accountRepository.findById(1L).orElseThrow();
        account.setAcctCurrBal(new BigDecimal("194.00"));
        accountRepository.saveAndFlush(account);
        if (cardXrefRepository.findByXrefAcctId(1L).isEmpty()) {
            CardXref xref = new CardXref();
            xref.setXrefCardNumber("1111222233334444");
            xref.setXrefCustId(1L);
            xref.setXrefAcctId(1L);
            cardXrefRepository.saveAndFlush(xref);
        }
    }

    @Test
    void confirmedPaymentWritesAndZeroesBalance_frS1111_frS1112_frS1115() throws Exception {
        transactionRepository.deleteAll();
        MockHttpSession session = signon();
        mockMvc.perform(post(PAYMENTS).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"00000000001\",\"confirmation\":\"Y\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "Payment successful.  Your Transaction ID is 0000000000000001."))
                .andExpect(jsonPath("$.messageStyle").value("info"))
                .andExpect(jsonPath("$.transactionId").value("0000000000000001"));
        Account account = accountRepository.findById(1L).orElseThrow();
        assertThat(account.getAcctCurrBal()).isEqualByComparingTo("0.00");
        Transaction stored = transactionRepository.findById("0000000000000001").orElseThrow();
        assertThat(stored.getTranTypeCode()).isEqualTo("02");
        assertThat(stored.getTranCategoryCode()).isEqualTo(2);
        assertThat(stored.getTranSource()).isEqualTo("POS TERM");
        assertThat(stored.getTranDescription()).isEqualTo("BILL PAYMENT - ONLINE");
        assertThat(stored.getTranAmount()).isEqualByComparingTo("194.00");
        assertThat(stored.getTranCardNumber()).isEqualTo("1111222233334444");
        assertThat(stored.getTranMerchantId()).isEqualTo(999999999L);
        assertThat(stored.getTranOriginTimestamp()).isNotNull();
        assertThat(stored.getTranOriginTimestamp().getNano()).isZero();
        assertThat(stored.getTranProcessTimestamp())
                .isEqualTo(stored.getTranOriginTimestamp());
        assertThat(stored.getTranMerchantName()).isEqualTo("BILL PAYMENT");
        assertThat(stored.getTranMerchantCity()).isEqualTo("N/A");
        assertThat(stored.getTranMerchantZip()).isEqualTo("N/A");
    }

    @Test
    void unkeyedAccountIdReadsAsNotFound_frS1104() throws Exception {
        MockHttpSession session = signon();
        // R3: the as-typed key compare — "1" does not match "00000000001".
        mockMvc.perform(post(PAYMENTS).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"1\",\"confirmation\":\"Y\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account ID NOT found..."));
    }

    @Test
    void nothingToPayWhenBalanceNotPositive_frS1107() throws Exception {
        Account account = accountRepository.findById(1L).orElseThrow();
        account.setAcctCurrBal(BigDecimal.ZERO);
        accountRepository.saveAndFlush(account);
        MockHttpSession session = signon();
        mockMvc.perform(post(PAYMENTS).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"00000000001\",\"confirmation\":\"Y\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("You have nothing to pay..."))
                .andExpect(jsonPath("$.currentBalance").value("+0000000000.00"));
    }

    @Test
    void blankConfirmPromptsAndWritesNothing_frS1108() throws Exception {
        long before = transactionRepository.count();
        MockHttpSession session = signon();
        mockMvc.perform(post(PAYMENTS).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"00000000001\",\"confirmation\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Confirm to make a bill payment..."))
                .andExpect(jsonPath("$.currentBalance").value("+0000000194.00"))
                .andExpect(jsonPath("$.cursorField").value("confirmation"));
        assertThat(transactionRepository.count()).isEqualTo(before);
        assertThat(accountRepository.findById(1L).orElseThrow().getAcctCurrBal())
                .isEqualByComparingTo("194.00");
    }

    @Test
    void missingXrefReadsAsAccountNotFound_frS1109() throws Exception {
        // CXACAIX NOTFND: account 1 exists but its xref row is gone — the
        // legacy program reports the same Account ID NOT found text.
        cardXrefRepository.deleteAll();
        long before = transactionRepository.count();
        MockHttpSession session = signon();
        mockMvc.perform(post(PAYMENTS).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"00000000001\",\"confirmation\":\"Y\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account ID NOT found..."));
        assertThat(transactionRepository.count()).isEqualTo(before);
        assertThat(accountRepository.findById(1L).orElseThrow().getAcctCurrBal())
                .isEqualByComparingTo("194.00");
    }

    @Test
    void duplicateTranIdRollsBackAtomically_frS1113_d1() throws Exception {
        transactionRepository.deleteAll();
        // Force a real PK collision at write time: varchar key order puts
        // the short key "9" above "0000000000000010", so max+1 allocates
        // "...010", which already exists (legacy DUPREC path).
        Transaction shortKey = new Transaction();
        shortKey.setTranId("9");
        shortKey.setTranAmount(new BigDecimal("1.00"));
        transactionRepository.saveAndFlush(shortKey);
        Transaction blocker = new Transaction();
        blocker.setTranId("0000000000000010");
        blocker.setTranAmount(new BigDecimal("1.00"));
        transactionRepository.saveAndFlush(blocker);
        long count = transactionRepository.count();
        MockHttpSession session = signon();
        mockMvc.perform(post(PAYMENTS).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"00000000001\",\"confirmation\":\"Y\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tran ID already exist..."));
        // D1 evidence: the colliding insert rolled back together with the
        // balance rewrite — nothing persisted.
        assertThat(transactionRepository.count()).isEqualTo(count);
        assertThat(accountRepository.findById(1L).orElseThrow().getAcctCurrBal())
                .isEqualByComparingTo("194.00");
    }

    @Test
    void unauthenticatedApiCallGets401_frS1119() throws Exception {
        mockMvc.perform(post(PAYMENTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"00000000001\",\"confirmation\":\"Y\"}"))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpSession signon() throws Exception {
        MvcResult result = mockMvc.perform(post("/signon").param("aid", "ENTER")
                        .param("userid", "ADMIN001").param("passwd", "PASSWORD"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
