package com.carddemo.service;

import com.carddemo.model.Account;
import com.carddemo.model.Card;
import com.carddemo.model.CardXref;
import com.carddemo.model.Customer;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private CardXrefRepository cardXrefRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private CardRepository cardRepository;

    @InjectMocks private AccountService accountService;

    private Account testAccount;

    @BeforeEach
    void setUp() {
        testAccount = Account.builder()
                .acctId(1L)
                .acctActiveStatus("Y")
                .acctCurrBal(new BigDecimal("1500.00"))
                .acctCreditLimit(new BigDecimal("5000.00"))
                .acctCashCreditLimit(new BigDecimal("2000.00"))
                .acctOpenDate("2018-03-15")
                .acctExpirationDate("2028-03-15")
                .acctReissueDate("2023-03-15")
                .acctCurrCycCredit(new BigDecimal("200.00"))
                .acctCurrCycDebit(new BigDecimal("50.00"))
                .acctAddrZip("60601")
                .acctGroupId("GROUP01")
                .version(0L)
                .build();
    }

    @Test
    void getAccountDetails_returnsAccountWithCustomerAndCards() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

        CardXref xref = CardXref.builder()
                .xrefCardNum("4111111111111111").xrefCustId(1L).xrefAcctId(1L).build();
        when(cardXrefRepository.findByXrefAcctId(1L)).thenReturn(List.of(xref));

        Customer customer = Customer.builder().custId(1L).custFirstName("JOHN").build();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        Card card = Card.builder().cardNum("4111111111111111").cardAcctId(1L).build();
        when(cardRepository.findByCardAcctId(1L)).thenReturn(List.of(card));

        Map<String, Object> result = accountService.getAccountDetails(1L);

        assertNotNull(result.get("account"));
        assertEquals(testAccount, result.get("account"));
        assertNotNull(result.get("customer"));
        assertNotNull(result.get("cards"));
    }

    @Test
    void getAccountDetails_throwsWhenNotFound() {
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(AccountService.AccountNotFoundException.class,
                () -> accountService.getAccountDetails(999L));
    }

    @Test
    void updateAccount_validatesAndSaves() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any())).thenReturn(testAccount);

        Account updates = new Account();
        updates.setAcctCreditLimit(new BigDecimal("6000.00"));
        updates.setAcctActiveStatus("Y");

        Account result = accountService.updateAccount(1L, updates);
        assertNotNull(result);
        verify(accountRepository).save(testAccount);
    }

    @Test
    void updateAccount_rejectsInvalidActiveStatus() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

        Account updates = new Account();
        updates.setAcctActiveStatus("X");

        assertThrows(IllegalArgumentException.class,
                () -> accountService.updateAccount(1L, updates));
    }

    @Test
    void updateAccount_rejectsNegativeCreditLimit() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

        Account updates = new Account();
        updates.setAcctCreditLimit(new BigDecimal("-100.00"));

        assertThrows(IllegalArgumentException.class,
                () -> accountService.updateAccount(1L, updates));
    }

    @Test
    void updateAccount_rejectsInvalidDateFormat() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

        Account updates = new Account();
        updates.setAcctOpenDate("03/15/2018");

        assertThrows(IllegalArgumentException.class,
                () -> accountService.updateAccount(1L, updates));
    }

    @Test
    void updateAccount_acceptsValidDate() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any())).thenReturn(testAccount);

        Account updates = new Account();
        updates.setAcctOpenDate("2024-01-15");

        Account result = accountService.updateAccount(1L, updates);
        assertNotNull(result);
        assertEquals("2024-01-15", testAccount.getAcctOpenDate());
    }
}
