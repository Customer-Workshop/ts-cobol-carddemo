package com.carddemo;

import com.carddemo.model.SecurityUser;
import com.carddemo.model.Transaction;
import com.carddemo.repository.SecurityUserRepository;
import com.carddemo.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
 * COTRN01C web surface (tran CT01, map COTRN1A): the screen, its AID keys
 * and the verbatim-key keyed read, asserting the strings the FR doc pins.
 * Test names carry the FR-S08 row each covers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        // application.properties pins spring.datasource.url to a fixed mem:db,
        // so this context needs its own URL to isolate its schema.
        "spring.datasource.url=jdbc:h2:mem:tranviewuitest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TransactionViewUiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SecurityUserRepository userRepository;
    @Autowired private TransactionRepository transactionRepository;

    @BeforeEach
    void addRegularUser() {
        SecurityUser user = new SecurityUser();
        user.setUserId("USER0001");
        user.setFirstName("REGULAR");
        user.setLastName("USER");
        user.setPassword("PASSWORD");
        user.setUserType("U");
        userRepository.save(user);
    }

    @Test
    void unsignedBouncesToSignon_frS0801() throws Exception {
        mockMvc.perform(get("/transactions/view"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signon"));
        mockMvc.perform(post("/transactions/view").param("aid", "ENTER"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signon"));
        mockMvc.perform(get("/api/transactions/0000000000683580"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void firstEntryBlankScreen_frS0802() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(get("/transactions/view").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-view"))
                .andExpect(model().attribute("message", nullValue()))
                .andExpect(content().string(containsString("View Transaction")))
                .andExpect(content().string(containsString("CT01")))
                .andExpect(content().string(containsString("COTRN01C")))
                .andExpect(content().string(containsString("Enter Tran ID:")))
                .andExpect(content().string(containsString("autofocus")))
                .andReturn();
        var screen = screen(result);
        assertThat(screen.tranIdIn()).isEmpty();
        assertThat(screen.details()).isNull();
    }

    @Test
    void preselectedFetchesImmediately_frS0803() throws Exception {
        transactionRepository.save(goldenRow());
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(get("/transactions/view")
                        .session(session).param("tranId", "0000000000683580"))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-view"))
                .andExpect(model().attribute("message", nullValue()))
                .andReturn();
        var screen = screen(result);
        assertThat(screen.tranIdIn()).isEqualTo("0000000000683580");
        assertThat(screen.details()).isNotNull();
        assertThat(screen.details().amount()).isEqualTo("+00000504.77");
    }

    @Test
    void blankEnterKeepsDetails_frS0804() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/transactions/view").session(session)
                        .param("aid", "ENTER").param("trnIdIn", "")
                        .param("trnid", "0000000000683580")
                        .param("cardnum", "4859452612877065")
                        .param("trnamt", "+00000504.77")
                        .param("mname", "Abshire-Lowe"))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-view"))
                .andExpect(model().attribute("message", "Tran ID can NOT be empty..."))
                .andReturn();
        var screen = screen(result);
        // The shown record stays on the blank-id rejection (:147-152).
        assertThat(screen.details()).isNotNull();
        assertThat(screen.details().tranId()).isEqualTo("0000000000683580");
        assertThat(screen.details().cardNumber()).isEqualTo("4859452612877065");
        assertThat(screen.details().amount()).isEqualTo("+00000504.77");
    }

    @Test
    void failedLookupClearsDetails_frS0805() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/transactions/view").session(session)
                        .param("aid", "ENTER").param("trnIdIn", "9999999999999999")
                        .param("trnid", "0000000000683580")
                        .param("cardnum", "4859452612877065")
                        .param("mname", "Abshire-Lowe"))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-view"))
                .andExpect(model().attribute("message", "Transaction ID NOT found..."))
                .andReturn();
        var screen = screen(result);
        // The detail fields clear before the keyed read (:158-173).
        assertThat(screen.details()).isNull();
        assertThat(screen.tranIdIn()).isEqualTo("9999999999999999");
    }

    @Test
    void notFoundRetainsTypedId_frS0806() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/transactions/view").session(session)
                        .param("aid", "ENTER").param("trnIdIn", "NOPE000000000001"))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-view"))
                .andExpect(model().attribute("message", "Transaction ID NOT found..."))
                .andReturn();
        var screen = screen(result);
        assertThat(screen.tranIdIn()).isEqualTo("NOPE000000000001");
        assertThat(screen.details()).isNull();
    }

    @Test
    void seededDebitRendersEveryField_frS0808() throws Exception {
        transactionRepository.save(goldenRow());
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/transactions/view").session(session)
                        .param("aid", "ENTER").param("trnIdIn", "0000000000683580"))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-view"))
                .andExpect(model().attribute("message", nullValue()))
                .andExpect(content().string(containsString("Transaction ID:")))
                .andExpect(content().string(containsString("Card Number:")))
                .andExpect(content().string(containsString("Type CD:")))
                .andExpect(content().string(containsString("Category CD:")))
                .andExpect(content().string(containsString("Source:")))
                .andExpect(content().string(containsString("Description:")))
                .andExpect(content().string(containsString("Amount:")))
                .andExpect(content().string(containsString("Orig Date:")))
                .andExpect(content().string(containsString("Proc Date:")))
                .andExpect(content().string(containsString("Merchant ID:")))
                .andExpect(content().string(containsString("Merchant Name:")))
                .andExpect(content().string(containsString("Merchant City:")))
                .andExpect(content().string(containsString("Merchant Zip:")))
                .andReturn();
        var details = screen(result).details();
        assertThat(details.tranId()).isEqualTo("0000000000683580");
        assertThat(details.cardNumber()).isEqualTo("4859452612877065");
        assertThat(details.typeCode()).isEqualTo("01");
        assertThat(details.categoryCode()).isEqualTo("0001");
        assertThat(details.source()).isEqualTo("POS TERM");
        assertThat(details.description()).isEqualTo("Purchase at Abshire-Lowe");
        assertThat(details.amount()).isEqualTo("+00000504.77");
        assertThat(details.originDate()).isEqualTo("2022-06-10");
        assertThat(details.processDate()).isEqualTo("");
        assertThat(details.merchantId()).isEqualTo("800000000");
        assertThat(details.merchantName()).isEqualTo("Abshire-Lowe");
        assertThat(details.merchantCity()).isEqualTo("North Enoshaven");
        assertThat(details.merchantZip()).isEqualTo("72112");
    }

    @Test
    void seededCreditShowsSignedAmount_frS0809() throws Exception {
        Transaction credit = goldenRow();
        credit.setTranId("0000000000000100");
        credit.setTranAmount(new BigDecimal("-919.00"));
        transactionRepository.save(credit);
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/transactions/view").session(session)
                        .param("aid", "ENTER").param("trnIdIn", "0000000000000100"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("-00000919.00")))
                .andReturn();
        assertThat(screen(result).details().amount()).isEqualTo("-00000919.00");
    }

    @Test
    void caseAndLeadingSpaceAreVerbatim_frS0812() throws Exception {
        transactionRepository.save(goldenRow());
        MockHttpSession session = signon();
        // Case is significant: only the numeric key is stored.
        mockMvc.perform(post("/transactions/view").session(session)
                        .param("aid", "ENTER").param("trnIdIn", "nope000000000001"))
                .andExpect(model().attribute("message", "Transaction ID NOT found..."));
        // A leading blank makes it a different 16-char key entirely.
        mockMvc.perform(post("/transactions/view").session(session)
                        .param("aid", "ENTER").param("trnIdIn", " 000000000068358"))
                .andExpect(model().attribute("message", "Transaction ID NOT found..."));
        mockMvc.perform(post("/transactions/view").session(session)
                        .param("aid", "ENTER").param("trnIdIn", "0000000000683580"))
                .andExpect(model().attribute("message", nullValue()));
    }

    @Test
    void tranIdInputLengthIsSixteen_frS0812() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(get("/transactions/view").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("maxlength=\"16\"")));
    }

    @Test
    void pf3ReturnsToCallerOrMenu_frS0813() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(post("/transactions/view").session(session).param("aid", "PF3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/menu"));
        mockMvc.perform(post("/transactions/view").session(session)
                        .param("aid", "PF3").param("returnUrl", "/transactions/list"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transactions/list"));
        // Internal paths only: an external returnUrl falls back to the menu.
        mockMvc.perform(post("/transactions/view").session(session)
                        .param("aid", "PF3").param("returnUrl", "https://evil.example"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/menu"));
    }

    @Test
    void pf4ClearsTheMap_frS0814() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/transactions/view").session(session)
                        .param("aid", "PF4").param("trnIdIn", "0000000000683580")
                        .param("trnid", "0000000000683580"))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-view"))
                .andExpect(model().attribute("message", nullValue()))
                .andReturn();
        var screen = screen(result);
        assertThat(screen.tranIdIn()).isEmpty();
        assertThat(screen.details()).isNull();
    }

    @Test
    void pf5BrowsesTheListRoute_frS0815() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(post("/transactions/view").session(session)
                        .param("aid", "PF5").param("trnIdIn", "0000000000683580"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transactions/list"));
    }

    @Test
    void menuOptionSevenRoutesToView() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(post("/menu/select").session(session)
                        .param("aid", "ENTER").param("option", "7"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/transactions/view"));
    }

    @Test
    void invalidAidKeepsDetails_frS0816() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/transactions/view").session(session)
                        .param("aid", "F7").param("trnIdIn", "0000000000683580")
                        .param("trnid", "0000000000683580")
                        .param("cardnum", "4859452612877065"))
                .andExpect(status().isOk())
                .andExpect(view().name("transaction-view"))
                .andExpect(model().attribute("message",
                        "Invalid key pressed. Please see below..."))
                .andReturn();
        var screen = screen(result);
        assertThat(screen.tranIdIn()).isEqualTo("0000000000683580");
        assertThat(screen.details()).isNotNull();
        assertThat(screen.details().tranId()).isEqualTo("0000000000683580");
    }

    @Test
    void headerAndFooterAreVerbatim_frS0817() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(get("/transactions/view").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tran: ")))
                .andExpect(content().string(containsString("CT01")))
                .andExpect(content().string(containsString("Prog: ")))
                .andExpect(content().string(containsString("COTRN01C")))
                .andExpect(content().string(containsString("View Transaction")))
                .andExpect(content().string(containsString(
                        "ENTER=Fetch  F3=Back  F4=Clear  F5=Browse Tran.")));
    }

    private com.carddemo.service.TransactionViewScreen screen(MvcResult result) {
        return (com.carddemo.service.TransactionViewScreen)
                result.getModelAndView().getModel().get("screen");
    }

    private Transaction goldenRow() {
        Transaction row = new Transaction();
        row.setTranId("0000000000683580");
        row.setTranCardNumber("4859452612877065");
        row.setTranTypeCode("01");
        row.setTranCategoryCode(1);
        row.setTranSource("POS TERM");
        row.setTranDescription("Purchase at Abshire-Lowe");
        row.setTranAmount(new BigDecimal("504.77"));
        row.setTranOriginTimestamp(LocalDateTime.of(2022, 6, 10, 0, 0));
        row.setTranMerchantId(800000000L);
        row.setTranMerchantName("Abshire-Lowe");
        row.setTranMerchantCity("North Enoshaven");
        row.setTranMerchantZip("72112");
        return row;
    }

    private MockHttpSession signon() throws Exception {
        MvcResult result = mockMvc.perform(post("/signon").param("aid", "ENTER")
                        .param("userid", "USER0001").param("passwd", "PASSWORD"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/menu"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
