package com.carddemo;

import com.carddemo.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * COTRN02C API seam (S-09): POST /api/transactions (ENTER) and
 * POST /api/transactions/copy-last (PF5) against the seeded store. The
 * seeded transaction file holds one row (tran-id 0000000000000001) and the
 * xref maps card 1111222233334444 to account 00000000001. Test names carry
 * the FR-S09 row each covers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:tranaddtest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TransactionAddIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TransactionRepository transactionRepository;

    private static final String VALID = """
            {"accountId":"00000000001","transactionTypeCode":"01",
             "transactionCategoryCode":"0001","source":"POS TERM",
             "description":"API purchase","amount":"+00000099.99",
             "originDate":"2024-01-15","processDate":"2024-01-16",
             "merchantId":"000000001","merchantName":"Merchant",
             "merchantCity":"Boston","merchantZip":"02108","confirmation":"Y"}
            """;

    @Test
    void enterWritesTheRowAndReturnsTheScreenState_frS0920_21() throws Exception {
        // Test order is not guaranteed; reset so the next id is deterministic.
        transactionRepository.deleteAll();
        MockHttpSession session = signon();
        mockMvc.perform(post("/api/transactions").session(session)
                        .contentType("application/json").content(VALID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("Transaction added successfully.  "
                                + "Your Tran ID is 0000000000000001."))
                .andExpect(jsonPath("$.messageStyle").value("info"))
                .andExpect(jsonPath("$.transactionId").value("0000000000000001"))
                .andExpect(jsonPath("$.accountId").value(""))
                .andExpect(jsonPath("$.cursorField").value("accountId"));

        var row = transactionRepository.findById("0000000000000001").orElseThrow();
        assertThat(row.getTranCardNumber()).isEqualTo("1111222233334444");
        assertThat(row.getTranTypeCode()).isEqualTo("01");
        assertThat(row.getTranCategoryCode()).isEqualTo(1);
        assertThat(row.getTranAmount()).isEqualByComparingTo("99.99");
        assertThat(row.getTranOriginTimestamp()).hasToString("2024-01-15T00:00");
        assertThat(row.getTranMerchantName()).isEqualTo("Merchant");
        assertThat(row.getTranMerchantCity()).isEqualTo("Boston");
    }

    @Test
    void businessRejectionStaysOnTheScreenWithTheCobolMessage_frS0903() throws Exception {
        MockHttpSession session = signon();
        // HTTP 200 with the redisplay state: the 3270 had no status codes.
        mockMvc.perform(post("/api/transactions").session(session)
                        .contentType("application/json")
                        .content("{\"accountId\":\"123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account ID must be Numeric..."))
                .andExpect(jsonPath("$.cursorField").value("accountId"));
    }

    @Test
    void accountResolutionEchoesTheXrefCard_frS0904() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(post("/api/transactions").session(session)
                        .contentType("application/json")
                        .content(VALID.replace("\"confirmation\":\"Y\"", "\"confirmation\":\"N\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardNumber").value("1111222233334444"))
                .andExpect(jsonPath("$.message").value("Confirm to add this transaction..."));
        assertThat(transactionRepository.findById("0000000000000002")).isEmpty();
    }

    @Test
    void copyLastReturnsTheHighestIdFields_frS0927() throws Exception {
        // The seeded row's timestamps are truncated; pin a deterministic
        // highest-id transaction to copy from.
        var last = new com.carddemo.model.Transaction();
        last.setTranId("0000000000000099");
        last.setTranTypeCode("02");
        last.setTranCategoryCode(5);
        last.setTranSource("WEB");
        last.setTranDescription("Copied purchase");
        last.setTranAmount(new java.math.BigDecimal("7.50"));
        last.setTranMerchantId(123456789L);
        last.setTranMerchantName("Copied merchant");
        last.setTranMerchantCity("Copied city");
        last.setTranMerchantZip("90210");
        last.setTranCardNumber("1111222233334444");
        last.setTranOriginTimestamp(java.time.LocalDateTime.of(2023, 3, 4, 5, 6, 7));
        last.setTranProcessTimestamp(java.time.LocalDateTime.of(2023, 3, 5, 6, 7, 8));
        transactionRepository.saveAndFlush(last);

        MockHttpSession session = signon();
        mockMvc.perform(post("/api/transactions/copy-last").session(session)
                        .contentType("application/json")
                        .content("{\"accountId\":\"00000000001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionTypeCode").value("02"))
                .andExpect(jsonPath("$.transactionCategoryCode").value("0005"))
                .andExpect(jsonPath("$.originDate").value("2023-03-04"))
                .andExpect(jsonPath("$.processDate").value("2023-03-05"))
                .andExpect(jsonPath("$.amount").value("+00000007.50"))
                .andExpect(jsonPath("$.merchantId").value("123456789"))
                .andExpect(jsonPath("$.message").value("Confirm to add this transaction..."));
    }

    @Test
    void overWidthFieldIsRejectedAtTheEdge_frS0913() throws Exception {
        MockHttpSession session = signon();
        // 13-byte amount cannot come off the 3270 (S09-B6/D-3): a plain 400,
        // not a screen redisplay.
        mockMvc.perform(post("/api/transactions").session(session)
                        .contentType("application/json")
                        .content(VALID.replace("+00000099.99", "+1234567890.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("amount")));
    }

    private MockHttpSession signon() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signon")
                        .contentType("application/json")
                        .content("{\"userId\":\"ADMIN001\",\"password\":\"PASSWORD\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
