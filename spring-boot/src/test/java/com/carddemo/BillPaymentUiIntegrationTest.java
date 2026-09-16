package com.carddemo;

import com.carddemo.api.BillPaymentScreen;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.TransactionRepository;
import java.math.BigDecimal;
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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * COBIL00C web surface (S-11): the COBIL0A screen, its AID-key handling,
 * the ?accountId= pre-selection and the menu route must behave like the
 * 3270 map. Test names carry the FR-S11 row each covers; cites live in
 * the program FR doc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:billpayuitest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class BillPaymentUiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;

    @Test
    void billPayScreenRendersTheCobil0aInputMap_frS1101() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(get("/bill-payment").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("bill-payment"))
                .andExpect(content().string(containsString("CB00")))
                .andExpect(content().string(containsString("COBIL00C")))
                .andExpect(content().string(containsString("Bill Payment")))
                .andExpect(content().string(containsString("name=\"acctId\"")))
                .andExpect(content().string(containsString("name=\"currentBalance\"")))
                .andExpect(content().string(containsString("name=\"confirmation\"")))
                .andExpect(content().string(containsString("Enter Acct ID:")))
                .andExpect(content().string(containsString("Your current balance is:")))
                .andExpect(content().string(containsString(
                        "Do you want to pay your balance now. Please confirm:")))
                .andExpect(content().string(containsString("(Y/N)")))
                .andExpect(content().string(containsString(
                        "ENTER=Continue  F3=Back  F4=Clear")))
                .andExpect(model().attribute("message", nullValue()))
                .andReturn();
        BillPaymentScreen screen = screen(result);
        assertThat(screen.cursorField()).isEqualTo("accountId");
        assertThat(screen.accountId()).isEmpty();
    }

    @Test
    void unsignedEntryBouncesToSignon_frS1119() throws Exception {
        // EIBCALEN=0 bounce — Spring Security sends the unauthenticated GET
        // to sign-on.
        mockMvc.perform(get("/bill-payment"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signon"));
        mockMvc.perform(post("/bill-payment").param("aid", "ENTER")
                        .param("acctId", "00000000001"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signon"));
    }

    @Test
    void enterWithBlankAccountIdRedisplaysVerbatimError_frS1101() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/bill-payment").session(session)
                        .param("aid", "ENTER").param("acctId", "")
                        .param("currentBalance", "+0000001234.56"))
                .andExpect(status().isOk())
                .andExpect(view().name("bill-payment"))
                .andExpect(model().attribute("message", "Acct ID can NOT be empty..."))
                .andReturn();
        BillPaymentScreen screen = screen(result);
        assertThat(screen.cursorField()).isEqualTo("accountId");
        // CURBAL is FSET — the previously displayed balance is echoed back.
        assertThat(screen.currentBalance()).isEqualTo("+0000001234.56");
    }

    @Test
    void enterWithInvalidConfirmKeepsTheDisplayedBalance_frS1102() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/bill-payment").session(session)
                        .param("aid", "ENTER")
                        .param("acctId", "00000000001")
                        .param("currentBalance", "+0000000194.00")
                        .param("confirmation", "Q"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("message",
                        "Invalid value. Valid values are (Y/N)..."))
                .andReturn();
        BillPaymentScreen screen = screen(result);
        assertThat(screen.cursorField()).isEqualTo("confirmation");
        assertThat(screen.currentBalance()).isEqualTo("+0000000194.00");
    }

    @Test
    void enterWithDeclineNClearsTheScreen_frS1103() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/bill-payment").session(session)
                        .param("aid", "ENTER")
                        .param("acctId", "00000000001")
                        .param("currentBalance", "+0000000194.00")
                        .param("confirmation", "n"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("message", nullValue()))
                .andReturn();
        BillPaymentScreen screen = screen(result);
        assertThat(screen.accountId()).isEmpty();
        assertThat(screen.currentBalance()).isEmpty();
        assertThat(screen.confirmation()).isEmpty();
        assertThat(screen.cursorField()).isEqualTo("accountId");
    }

    @Test
    void enterWithBlankConfirmShowsBalanceAndPrompt_frS1106_frS1108() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/bill-payment").session(session)
                        .param("aid", "ENTER").param("acctId", "00000000001"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("message",
                        "Confirm to make a bill payment..."))
                .andExpect(content().string(containsString("+0000000194.00")))
                .andReturn();
        BillPaymentScreen screen = screen(result);
        assertThat(screen.cursorField()).isEqualTo("confirmation");
        assertThat(screen.currentBalance()).isEqualTo("+0000000194.00");
    }

    @Test
    void confirmedPaymentZeroesBalanceAndShowsGreenSuccess_frS1112() throws Exception {
        // Test order is not guaranteed; reset so the next id is deterministic.
        transactionRepository.deleteAll();
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/bill-payment").session(session)
                        .param("aid", "ENTER")
                        .param("acctId", "00000000001")
                        .param("currentBalance", "+0000000194.00")
                        .param("confirmation", "Y"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("message",
                        "Payment successful.  Your Transaction ID is 0000000000000001."))
                .andExpect(model().attribute("messageStyle", "info"))
                .andExpect(content().string(containsString(
                        "Payment successful.  Your Transaction ID is 0000000000000001.")))
                .andReturn();
        BillPaymentScreen screen = screen(result);
        assertThat(screen.accountId()).isEmpty();
        assertThat(screen.cursorField()).isEqualTo("accountId");
        assertThat(accountRepository.findById(1L).orElseThrow().getAcctCurrBal())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void pf3ReturnsToTheMenu_frS1116() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(post("/bill-payment").session(session)
                        .param("aid", "PF3").param("acctId", "00000000001"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/menu"));
    }

    @Test
    void pf4ClearsFieldsAndMessage_frS1117() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/bill-payment").session(session)
                        .param("aid", "PF4")
                        .param("acctId", "00000000001")
                        .param("currentBalance", "+0000000194.00")
                        .param("confirmation", "Y"))
                .andExpect(status().isOk())
                .andExpect(view().name("bill-payment"))
                .andExpect(model().attribute("message", nullValue()))
                .andReturn();
        BillPaymentScreen screen = screen(result);
        assertThat(screen.accountId()).isEmpty();
        assertThat(screen.currentBalance()).isEmpty();
        assertThat(screen.confirmation()).isEmpty();
        assertThat(screen.cursorField()).isEqualTo("accountId");
    }

    @Test
    void unmappedFunctionKeyRedisplaysUnchanged_frS1118() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/bill-payment").session(session)
                        .param("aid", "F5")
                        .param("acctId", "00000000001")
                        .param("currentBalance", "+0000000194.00")
                        .param("confirmation", "Y"))
                .andExpect(status().isOk())
                .andExpect(view().name("bill-payment"))
                .andExpect(model().attribute("message",
                        "Invalid key pressed. Please see below..."))
                .andReturn();
        BillPaymentScreen screen = screen(result);
        assertThat(screen.accountId()).isEqualTo("00000000001");
        assertThat(screen.currentBalance()).isEqualTo("+0000000194.00");
        assertThat(screen.confirmation()).isEqualTo("Y");
    }

    @Test
    void preSelectedAccountRunsEnterImmediately_frS1120() throws Exception {
        // CDEMO-CB00-TRN-SELECTED (S11-B5): the id lands in Acct ID and
        // ENTER processing runs at once — balance shown, confirm prompted.
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(get("/bill-payment").session(session)
                        .param("accountId", "00000000001"))
                .andExpect(status().isOk())
                .andExpect(view().name("bill-payment"))
                .andExpect(model().attribute("message",
                        "Confirm to make a bill payment..."))
                .andReturn();
        BillPaymentScreen screen = screen(result);
        assertThat(screen.accountId()).isEqualTo("00000000001");
        assertThat(screen.currentBalance()).isEqualTo("+0000000194.00");
        assertThat(screen.cursorField()).isEqualTo("confirmation");
    }

    @Test
    void menuOptionTenRoutesToTheBillPayScreen() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(post("/menu/select").session(session)
                        .param("aid", "ENTER").param("option", "10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/bill-payment"));
    }

    private BillPaymentScreen screen(MvcResult result) {
        return (BillPaymentScreen) result.getModelAndView().getModel().get("screen");
    }

    private MockHttpSession signon() throws Exception {
        MvcResult result = mockMvc.perform(post("/signon").param("aid", "ENTER")
                        .param("userid", "ADMIN001").param("passwd", "PASSWORD"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
