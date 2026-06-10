package com.carddemo.service;

import com.carddemo.model.Account;
import com.carddemo.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Replaces COPAUA0C.cbl authorization decision logic (lines 664-696).
 * <pre>
 *   available = ACCT-CREDIT-LIMIT - ACCT-CURR-BAL
 *   if transactionAmount > available → decline ('05')
 *   else → approve ('00')
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final AccountRepository accountRepository;

    public Map<String, Object> authorize(Long acctId, BigDecimal transactionAmount) {
        Account account = accountRepository.findById(acctId)
                .orElseThrow(() -> new AccountService.AccountNotFoundException(acctId));

        BigDecimal available = account.getAcctCreditLimit()
                .subtract(account.getAcctCurrBal());

        boolean approved = transactionAmount.compareTo(available) <= 0;

        return Map.of(
                "acctId", acctId,
                "transactionAmount", transactionAmount,
                "availableAmount", available,
                "approved", approved,
                "responseCode", approved ? "00" : "05",
                "approvedAmount", approved ? transactionAmount : BigDecimal.ZERO,
                "reason", approved ? "APPROVED" : "INSUFFICIENT_FUNDS"
        );
    }
}
