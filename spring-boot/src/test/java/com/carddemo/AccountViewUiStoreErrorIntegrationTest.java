package com.carddemo;

import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.SecurityUser;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import com.carddemo.repository.SecurityUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * FR-S02-13 / S02-B2: a store failure on each keyed read renders the fixed
 * WS-FILE-ERROR-MESSAGE layout (COACTVWC.cbl:121-127) — a customer-side
 * failure still displays the account block. Kept in its own context so the
 * repository doubles cannot leak into the real-data tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.datasource.generate-unique-name=true",
        "spring.datasource.url=jdbc:h2:mem:accountviewstoretest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AccountViewUiStoreErrorIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SecurityUserRepository userRepository;
    @MockitoBean private CardXrefRepository xrefRepository;
    @MockitoBean private AccountRepository accountRepository;
    @MockitoBean private CustomerRepository customerRepository;

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
    void xrefStoreErrorRendersFileErrorLayout_frS0213() throws Exception {
        Mockito.when(xrefRepository.findByXrefAcctId(ArgumentMatchers.anyLong()))
                .thenThrow(new DataAccessResourceFailureException("store unavailable"));
        MockHttpSession session = signon();

        MvcResult result = mockMvc.perform(post("/accounts/view").session(session)
                        .param("aid", "ENTER").param("acctId", "00000000001"))
                .andExpect(status().isOk())
                .andExpect(view().name("account-view"))
                .andExpect(model().attribute("message",
                        "File Error: READ     on CXACAIX   returned RESP 000000017 ,RESP2 000000120 "))
                .andReturn();
        var screen = screen(result);
        assertThat(screen.accountFieldRed()).isTrue();
        assertThat(screen.account()).isNull();
    }

    @Test
    void acctStoreErrorRendersFileErrorLayout_frS0213() throws Exception {
        Mockito.when(xrefRepository.findByXrefAcctId(1L))
                .thenReturn(List.of(xref("1111222233334444", 1L, 1L)));
        Mockito.when(accountRepository.findById(1L))
                .thenThrow(new DataAccessResourceFailureException("store unavailable"));
        MockHttpSession session = signon();

        MvcResult result = mockMvc.perform(post("/accounts/view").session(session)
                        .param("aid", "ENTER").param("acctId", "00000000001"))
                .andExpect(status().isOk())
                .andExpect(view().name("account-view"))
                .andExpect(model().attribute("message",
                        "File Error: READ     on ACCTDAT   returned RESP 000000017 ,RESP2 000000120 "))
                .andReturn();
        var screen = screen(result);
        assertThat(screen.accountFieldRed()).isTrue();
        assertThat(screen.account()).isNull();
    }

    @Test
    void custStoreErrorKeepsAccountBlock_frS0213() throws Exception {
        Mockito.when(xrefRepository.findByXrefAcctId(1L))
                .thenReturn(List.of(xref("1111222233334444", 1L, 1L)));
        Account account = new Account();
        account.setAcctId(1L);
        account.setAcctActiveStatus("Y");
        account.setAcctCurrBal(new BigDecimal("194.00"));
        Mockito.when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        Mockito.when(customerRepository.findById(1L))
                .thenThrow(new DataAccessResourceFailureException("store unavailable"));
        MockHttpSession session = signon();

        MvcResult result = mockMvc.perform(post("/accounts/view").session(session)
                        .param("aid", "ENTER").param("acctId", "00000000001"))
                .andExpect(status().isOk())
                .andExpect(view().name("account-view"))
                .andExpect(model().attribute("message",
                        "File Error: READ     on CUSTDAT   returned RESP 000000017 ,RESP2 000000120 "))
                .andReturn();
        var screen = screen(result);
        assertThat(screen.accountFieldRed()).isFalse();
        assertThat(screen.account()).isNotNull();
        assertThat(screen.customer()).isNull();
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
