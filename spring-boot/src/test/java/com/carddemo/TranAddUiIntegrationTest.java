package com.carddemo;

import com.carddemo.api.TransactionAddScreen;
import com.carddemo.model.Transaction;
import com.carddemo.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * COTRN02C web surface (S-09): the add-transaction screen, its AID-key
 * handling, the COMMAREA-style cardNumber entry and the seeded writes must
 * behave like COTRN02A. Test names carry the FR-S09 row each covers; cites
 * live in the program FR doc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:tranadduitest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TranAddUiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TransactionRepository transactionRepository;

    @Test
    void addScreenRendersTheCotrn2aInputMap_frS0901() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(get("/transactions/add").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-add"))
                .andExpect(content().string(containsString("CT02")))
                .andExpect(content().string(containsString("COTRN02C")))
                .andExpect(content().string(containsString("Add Transaction")))
                .andExpect(content().string(containsString("name=\"accountId\"")))
                .andExpect(content().string(containsString("name=\"cardNumber\"")))
                .andExpect(content().string(containsString("name=\"transactionTypeCode\"")))
                .andExpect(content().string(containsString("name=\"transactionCategoryCode\"")))
                .andExpect(content().string(containsString("name=\"source\"")))
                .andExpect(content().string(containsString("name=\"description\"")))
                .andExpect(content().string(containsString("name=\"amount\"")))
                .andExpect(content().string(containsString("name=\"originDate\"")))
                .andExpect(content().string(containsString("name=\"processDate\"")))
                .andExpect(content().string(containsString("name=\"merchantId\"")))
                .andExpect(content().string(containsString("name=\"merchantName\"")))
                .andExpect(content().string(containsString("name=\"merchantCity\"")))
                .andExpect(content().string(containsString("name=\"merchantZip\"")))
                .andExpect(content().string(containsString("name=\"confirmation\"")))
                .andExpect(content().string(containsString("Enter Acct #:")))
                .andExpect(content().string(containsString("Confirmation :")))
                .andExpect(content().string(containsString("(-99999999.99)")))
                .andExpect(content().string(containsString("(YYYY-MM-DD)")))
                .andExpect(content().string(containsString(
                        "ENTER=Continue  F3=Back  F4=Clear  F5=Copy Last-Tran.")))
                .andExpect(model().attribute("message",
                        org.hamcrest.Matchers.nullValue()))
                .andReturn();
        // First entry: every field blank, cursor on Acct #.
        TransactionAddScreen screen = screen(result);
        assertThat(screen.cursorField()).isEqualTo("accountId");
        assertThat(screen.accountId()).isEmpty();
        assertThat(screen.confirmation()).isEmpty();
    }

    @Test
    void unsignedEntryBouncesToSignon_frS0901() throws Exception {
        // S09-B1: the EIBCALEN=0 bounce — Spring Security sends the
        // unauthenticated GET to sign-on.
        mockMvc.perform(get("/transactions/add"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signon"));
    }

    @Test
    void enterWithValidFieldsWritesAndShowsGreenMessage_frS0921() throws Exception {
        // Test order is not guaranteed; reset so the next id is deterministic.
        transactionRepository.deleteAll();
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/transactions/add").session(session)
                        .param("aid", "ENTER")
                        .param("accountId", "00000000001")
                        .param("transactionTypeCode", "01")
                        .param("transactionCategoryCode", "0001")
                        .param("source", "POS TERM")
                        .param("description", "Web purchase")
                        .param("amount", "+00000042.50")
                        .param("originDate", "2024-01-15")
                        .param("processDate", "2024-01-16")
                        .param("merchantId", "000000001")
                        .param("merchantName", "Merchant")
                        .param("merchantCity", "Boston")
                        .param("merchantZip", "02108")
                        .param("confirmation", "Y"))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-add"))
                .andExpect(model().attribute("message",
                        "Transaction added successfully.  Your Tran ID is 0000000000000001."))
                .andExpect(model().attribute("messageStyle", "info"))
                .andExpect(content().string(containsString(
                        "Transaction added successfully.  Your Tran ID is 0000000000000001.")))
                .andReturn();
        // All fields cleared, cursor back on Acct #.
        TransactionAddScreen screen = screen(result);
        assertThat(screen.accountId()).isEmpty();
        assertThat(screen.description()).isEmpty();
        assertThat(screen.confirmation()).isEmpty();
        assertThat(screen.cursorField()).isEqualTo("accountId");
        assertThat(screen.transactionId()).isEqualTo("0000000000000001");
    }

    @Test
    void enterWithBlankFieldRedisplaysVerbatimError_frS0911() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/transactions/add").session(session)
                        .param("aid", "ENTER")
                        .param("accountId", "00000000001")
                        .param("transactionTypeCode", "01")
                        .param("transactionCategoryCode", "0001")
                        .param("description", "Web purchase"))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-add"))
                .andExpect(model().attribute("message", "Source can NOT be empty..."))
                .andExpect(content().string(containsString("value=\"Web purchase\"")))
                .andReturn();
        TransactionAddScreen screen = screen(result);
        assertThat(screen.cursorField()).isEqualTo("source");
        assertThat(screen.source()).isEmpty();
        assertThat(screen.description()).isEqualTo("Web purchase");
    }

    @Test
    void pf3ReturnsToTheMenu_frS0925() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(post("/transactions/add").session(session)
                        .param("aid", "PF3").param("accountId", "00000000001"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/menu"));
    }

    @Test
    void pf4ClearsFieldsAndMessage_frS0926() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/transactions/add").session(session)
                        .param("aid", "PF4")
                        .param("accountId", "00000000001")
                        .param("source", "POS TERM"))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-add"))
                .andExpect(model().attribute("message",
                        org.hamcrest.Matchers.nullValue()))
                .andReturn();
        TransactionAddScreen screen = screen(result);
        assertThat(screen.accountId()).isEmpty();
        assertThat(screen.source()).isEmpty();
        assertThat(screen.cursorField()).isEqualTo("accountId");
    }

    @Test
    void pf5CopiesTheLastTransaction_frS0927() throws Exception {
        // The seeded row's timestamps are truncated, so pin a deterministic
        // highest-id transaction to copy from.
        Transaction last = new Transaction();
        last.setTranId("0000000000000099");
        last.setTranTypeCode("02");
        last.setTranCategoryCode(5);
        last.setTranSource("WEB      ");
        last.setTranDescription("Copied purchase");
        last.setTranAmount(new BigDecimal("7.50"));
        last.setTranMerchantId(123456789L);
        last.setTranMerchantName("Copied merchant");
        last.setTranMerchantCity("Copied city");
        last.setTranMerchantZip("90210");
        last.setTranCardNumber("1111222233334444");
        last.setTranOriginTimestamp(LocalDateTime.of(2023, 3, 4, 5, 6, 7));
        last.setTranProcessTimestamp(LocalDateTime.of(2023, 3, 5, 6, 7, 8));
        transactionRepository.saveAndFlush(last);

        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/transactions/add").session(session)
                        .param("aid", "PF5").param("accountId", "00000000001"))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-add"))
                .andExpect(model().attribute("message", "Confirm to add this transaction..."))
                .andReturn();
        TransactionAddScreen screen = screen(result);
        assertThat(screen.transactionTypeCode()).isEqualTo("02");
        assertThat(screen.transactionCategoryCode()).isEqualTo("0005");
        assertThat(screen.source()).isEqualTo("WEB");
        assertThat(screen.description()).isEqualTo("Copied purchase");
        assertThat(screen.amount()).isEqualTo("+00000007.50");
        assertThat(screen.originDate()).isEqualTo("2023-03-04");
        assertThat(screen.processDate()).isEqualTo("2023-03-05");
        assertThat(screen.merchantId()).isEqualTo("123456789");
        assertThat(screen.merchantName()).isEqualTo("Copied merchant");
        assertThat(screen.merchantCity()).isEqualTo("Copied city");
        assertThat(screen.merchantZip()).isEqualTo("90210");
        // Key fields keep the resolved values; confirmation stays typed.
        assertThat(screen.cardNumber()).isEqualTo("1111222233334444");
        assertThat(screen.accountId()).isEqualTo("00000000001");
        assertThat(screen.confirmation()).isEmpty();
    }

    @Test
    void unmappedFunctionKeyPreservesState_frS0929() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/transactions/add").session(session)
                        .param("aid", "F7")
                        .param("accountId", "00000000001")
                        .param("source", "POS TERM"))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-add"))
                .andExpect(model().attribute("message",
                        "Invalid key pressed. Please see below..."))
                .andReturn();
        assertThat(screen(result).source()).isEqualTo("POS TERM");
    }

    @Test
    void preSelectedCardRunsEnterImmediately_frS0930() throws Exception {
        // CDEMO-CT02-TRN-SELECTED: the card lands in Card # and ENTER
        // processing runs at once (COTRN02C.cbl:124-129).
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(get("/transactions/add").session(session)
                        .param("cardNumber", "1111222233334444"))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-add"))
                .andExpect(model().attribute("message", "Type CD can NOT be empty..."))
                .andReturn();
        TransactionAddScreen screen = screen(result);
        assertThat(screen.cardNumber()).isEqualTo("1111222233334444");
        assertThat(screen.accountId()).isEqualTo("00000000001");
        assertThat(screen.cursorField()).isEqualTo("transactionTypeCode");
    }

    @Test
    void menuOptionEightRoutesToTheAddScreen_frS0901() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(post("/menu/select").session(session)
                        .param("aid", "ENTER").param("option", "8"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transactions/add"));
    }

    private TransactionAddScreen screen(MvcResult result) {
        return (TransactionAddScreen) result.getModelAndView().getModel().get("screen");
    }

    private MockHttpSession signon() throws Exception {
        MvcResult result = mockMvc.perform(post("/signon").param("aid", "ENTER")
                        .param("userid", "ADMIN001").param("passwd", "PASSWORD"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
