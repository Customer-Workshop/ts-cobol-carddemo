package com.carddemo;

import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.SecurityUser;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.SecurityUserRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * COACTVWC web surface (tran CAVW, map CACTVWA): the screen, its AID keys
 * and the five display outcomes, asserting the verbatim strings the FR doc
 * pins. Test names carry the FR-S02 row each covers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        // application.properties pins spring.datasource.url to a fixed mem:db,
        // so this context needs its own URL to isolate its schema.
        "spring.datasource.url=jdbc:h2:mem:accountviewuitest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AccountViewUiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SecurityUserRepository userRepository;
    @Autowired private CardXrefRepository xrefRepository;
    @Autowired private AccountRepository accountRepository;

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
    void initialRenderShowsPromptAndNoData_frS0201() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(get("/accounts/view").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("account-view"))
                .andExpect(model().attribute("message", nullValue()))
                .andExpect(content().string(containsString("CAVW")))
                .andExpect(content().string(containsString("COACTVWC")))
                .andExpect(content().string(containsString("View Account")))
                .andExpect(content().string(containsString("Account Number :")))
                .andExpect(content().string(containsString(
                        "Enter or update id of account to display")))
                .andExpect(content().string(containsString("F3=Exit")))
                .andReturn();

        var screen = screen(result);
        assertThat(screen.accountEcho()).isEmpty();
        assertThat(screen.accountFieldRed()).isFalse();
        assertThat(screen.account()).isNull();
        assertThat(screen.customer()).isNull();
    }

    @Test
    void blankSubmitShowsStarRedAndNoInput_frS0202() throws Exception {
        MockHttpSession session = signon();
        for (String input : new String[] {"", "   ", "*"}) {
            MvcResult result = mockMvc.perform(post("/accounts/view").session(session)
                            .param("aid", "ENTER").param("acctId", input))
                    .andExpect(status().isOk())
                    .andExpect(view().name("account-view"))
                    .andExpect(model().attribute("message", "No input received"))
                    .andExpect(content().string(containsString("field-red")))
                    .andReturn();
            var screen = screen(result);
            assertThat(screen.accountEcho()).isEqualTo("*");
            assertThat(screen.accountFieldRed()).isTrue();
            assertThat(screen.account()).isNull();
        }
    }

    @Test
    void invalidFilterEchoesRedWithFilterMessage_frS0203() throws Exception {
        MockHttpSession session = signon();
        for (String input : new String[] {"123", "1234567890a", "00000000000"}) {
            MvcResult result = mockMvc.perform(post("/accounts/view").session(session)
                            .param("aid", "ENTER").param("acctId", input))
                    .andExpect(status().isOk())
                    .andExpect(view().name("account-view"))
                    .andExpect(model().attribute("message",
                            "Account Filter must  be a non-zero 11 digit number"))
                    .andExpect(content().string(containsString("field-red")))
                    .andReturn();
            var screen = screen(result);
            assertThat(screen.accountEcho()).isEqualTo(input);
            assertThat(screen.accountFieldRed()).isTrue();
            assertThat(screen.account()).isNull();
        }
    }

    @Test
    void xrefNotFoundRendersVerbatimMessage_frS0204() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/accounts/view").session(session)
                        .param("aid", "ENTER").param("acctId", "00000000002"))
                .andExpect(status().isOk())
                .andExpect(view().name("account-view"))
                .andExpect(model().attribute("message",
                        "Account:00000000002 not found in Cross ref file.  Resp:000000013  Reas:0000"))
                .andReturn();
        var screen = screen(result);
        assertThat(screen.accountFieldRed()).isTrue();
        assertThat(screen.account()).isNull();
        assertThat(screen.customer()).isNull();
    }

    @Test
    void acctNotFoundRendersVerbatimMessage_frS0205() throws Exception {
        // Xref row present, no account master row: cbl:789-807's message.
        xrefRepository.save(xref("9999999999999999", 1L, 77777777777L));
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/accounts/view").session(session)
                        .param("aid", "ENTER").param("acctId", "77777777777"))
                .andExpect(status().isOk())
                .andExpect(view().name("account-view"))
                .andExpect(model().attribute("message",
                        "Account:77777777777 not found in Acct Master file.Resp:000000013  Reas:0000"))
                .andReturn();
        var screen = screen(result);
        assertThat(screen.accountFieldRed()).isTrue();
        assertThat(screen.account()).isNull();
        assertThat(screen.customer()).isNull();
    }

    @Test
    void custNotFoundStillShowsAccountBlock_frS0206() throws Exception {
        Account orphan = new Account();
        orphan.setAcctId(88888888888L);
        orphan.setAcctActiveStatus("Y");
        orphan.setAcctCurrBal(new BigDecimal("42.00"));
        accountRepository.save(orphan);
        xrefRepository.save(xref("8888888888888888", 999L, 88888888888L));
        MockHttpSession session = signon();

        MvcResult result = mockMvc.perform(post("/accounts/view").session(session)
                        .param("aid", "ENTER").param("acctId", "88888888888"))
                .andExpect(status().isOk())
                .andExpect(view().name("account-view"))
                .andExpect(model().attribute("message",
                        "CustId:000000999 not found in customer master.Resp: 000000013  REAS:0000000"))
                .andExpect(content().string(containsString("42.00")))
                .andReturn();
        var screen = screen(result);
        assertThat(screen.accountFieldRed()).isFalse();
        assertThat(screen.account()).isNotNull();
        assertThat(screen.customer()).isNull();
    }

    @Test
    void seededAccountRendersEveryField_frS0207() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/accounts/view").session(session)
                        .param("aid", "ENTER").param("acctId", "00000000001"))
                .andExpect(status().isOk())
                .andExpect(view().name("account-view"))
                .andExpect(model().attribute("message", nullValue()))
                .andExpect(content().string(containsString("Customer Details")))
                .andReturn();

        var screen = screen(result);
        assertThat(screen.accountEcho()).isEqualTo("00000000001");
        assertThat(screen.accountFieldRed()).isFalse();
        var account = screen.account();
        assertThat(account.activeStatus()).isEqualTo("Y");
        assertThat(account.openDate()).isEqualTo("2020-01-01");
        assertThat(account.creditLimit()).isEqualTo("+      2,020.00");
        assertThat(account.expirationDate()).isEqualTo("2025-01-01");
        assertThat(account.cashCreditLimit()).isEqualTo("+      1,020.00");
        assertThat(account.reissueDate()).isEqualTo("2025-01-01");
        assertThat(account.currentBalance()).isEqualTo("+        194.00");
        assertThat(account.currentCycleCredit()).isEqualTo("+           .00");
        assertThat(account.accountGroup()).isEqualTo("02108");
        assertThat(account.currentCycleDebit()).isEqualTo("+           .00");
        var customer = screen.customer();
        assertThat(customer.customerId()).isEqualTo("000000001");
        assertThat(customer.ssn()).isEqualTo("123-45-6789");
        assertThat(customer.dateOfBirth()).isEqualTo("1815-12-10");
        assertThat(customer.ficoScore()).isEqualTo("800");
        assertThat(customer.firstName()).isEqualTo("Ada");
        assertThat(customer.middleName()).isEqualTo("Lovelace");
        assertThat(customer.lastName()).isEqualTo("Byron");
        assertThat(customer.addressLine1()).isEqualTo("1 Main");
        assertThat(customer.stateCode()).isEqualTo("MA");
        assertThat(customer.zip()).isEqualTo("02108");
        assertThat(customer.countryCode()).isEqualTo("USA");
        assertThat(customer.phoneNumber1()).isEqualTo("555");
        assertThat(customer.governmentIssuedId()).isEqualTo("GOV");
        assertThat(customer.eftAccountId()).isEqualTo("EFT");
        assertThat(customer.primaryCardHolderIndicator()).isEqualTo("Y");
    }

    @Test
    void multipleXrefsUseLowestCardNumberCustomer_frS0210() throws Exception {
        // CXACAIX is keyed on card number, so the lowest card wins — the
        // new xref's customer 999 is absent, which pins the choice.
        xrefRepository.save(xref("0000000000000001", 999L, 1L));
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/accounts/view").session(session)
                        .param("aid", "ENTER").param("acctId", "00000000001"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("message",
                        "CustId:000000999 not found in customer master.Resp: 000000013  REAS:0000000"))
                .andReturn();
        var screen = screen(result);
        assertThat(screen.account()).isNotNull();
        assertThat(screen.customer()).isNull();
    }

    @Test
    void pf3ReturnsToCallerOrMenu_frS0211() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(post("/accounts/view").session(session).param("aid", "PF3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/menu"));
        mockMvc.perform(post("/accounts/view").session(session)
                        .param("aid", "PF3").param("returnUrl", "/api/cards"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/api/cards"));
    }

    @Test
    void otherAidSubmitsAsEnterWithNoInvalidKeyText_frS0212() throws Exception {
        MockHttpSession session = signon();
        MvcResult result = mockMvc.perform(post("/accounts/view").session(session)
                        .param("aid", "F7").param("acctId", "00000000001"))
                .andExpect(status().isOk())
                .andExpect(view().name("account-view"))
                .andExpect(content().string(not(containsString("Invalid key"))))
                .andReturn();
        assertThat(screen(result).account()).isNotNull();
    }

    @Test
    void accountInputLengthIsEleven_frS0214() throws Exception {
        MockHttpSession session = signon();
        mockMvc.perform(get("/accounts/view").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("maxlength=\"11\"")));
    }

    @Test
    void unsignedRequestsAreRejected_frS0215() throws Exception {
        mockMvc.perform(get("/accounts/view"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signon"));
        mockMvc.perform(post("/accounts/view").param("acctId", "00000000001"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/signon"));
        mockMvc.perform(get("/api/accounts/00000000001"))
                .andExpect(status().isUnauthorized());
    }

    private com.carddemo.service.AccountViewScreen screen(MvcResult result) {
        return (com.carddemo.service.AccountViewScreen)
                result.getModelAndView().getModel().get("screen");
    }

    private CardXref xref(String cardNumber, long custId, long acctId) {
        CardXref xref = new CardXref();
        xref.setXrefCardNumber(cardNumber);
        xref.setXrefCustId(custId);
        xref.setXrefAcctId(acctId);
        return xref;
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
