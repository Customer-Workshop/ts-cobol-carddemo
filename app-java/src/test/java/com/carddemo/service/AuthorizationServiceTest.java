package com.carddemo.service;

import com.carddemo.model.Account;
import com.carddemo.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

    @Mock private AccountRepository accountRepository;
    @InjectMocks private AuthorizationService authorizationService;

    private Account makeAccount(BigDecimal bal, BigDecimal limit) {
        return Account.builder()
                .acctId(1L)
                .acctCurrBal(bal)
                .acctCreditLimit(limit)
                .build();
    }

    @Test
    void authorize_approvesWhenWithinLimit() {
        // available = 5000 - 1500 = 3500; txn = 1000 → approve
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(makeAccount(
                        new BigDecimal("1500.00"), new BigDecimal("5000.00"))));

        Map<String, Object> result = authorizationService.authorize(1L,
                new BigDecimal("1000.00"));

        assertEquals(true, result.get("approved"));
        assertEquals("00", result.get("responseCode"));
        assertEquals(new BigDecimal("1000.00"), result.get("approvedAmount"));
    }

    @Test
    void authorize_declinesWhenOverLimit() {
        // available = 5000 - 4800 = 200; txn = 500 → decline
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(makeAccount(
                        new BigDecimal("4800.00"), new BigDecimal("5000.00"))));

        Map<String, Object> result = authorizationService.authorize(1L,
                new BigDecimal("500.00"));

        assertEquals(false, result.get("approved"));
        assertEquals("05", result.get("responseCode"));
        assertEquals(BigDecimal.ZERO, result.get("approvedAmount"));
    }

    @Test
    void authorize_approvesExactLimit() {
        // available = 5000 - 3000 = 2000; txn = 2000 → approve (equal is OK)
        when(accountRepository.findById(1L))
                .thenReturn(Optional.of(makeAccount(
                        new BigDecimal("3000.00"), new BigDecimal("5000.00"))));

        Map<String, Object> result = authorizationService.authorize(1L,
                new BigDecimal("2000.00"));

        assertEquals(true, result.get("approved"));
        assertEquals("00", result.get("responseCode"));
    }

    @Test
    void authorize_throwsWhenAccountNotFound() {
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(AccountService.AccountNotFoundException.class,
                () -> authorizationService.authorize(999L, new BigDecimal("100.00")));
    }
}
