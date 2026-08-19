package com.carddemo.service;

import com.carddemo.api.*;
import com.carddemo.model.Account;
import com.carddemo.model.Card;
import com.carddemo.model.Transaction;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardRepository;
import com.carddemo.repository.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;

@Service
public class BillingService {
    private final AccountRepository accountRepository;
    private final CardRepository cardRepository;
    private final TransactionRepository transactionRepository;

    public BillingService(AccountRepository accountRepository,
                          CardRepository cardRepository,
                          TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.cardRepository = cardRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public BillPaymentResponse pay(BillPaymentRequest request) {
        if (request == null || request.accountId() == null
                || !request.accountId().matches("\\d{1,11}")) {
            throw bad(CobolMessages.ACCOUNT_NUMBER_INVALID);
        }
        long accountId = Long.parseLong(request.accountId());
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> notFound(CobolMessages.ACCOUNT_NOT_FOUND));
        if (account.getAcctCurrBal() == null || account.getAcctCurrBal().signum() <= 0) {
            throw bad(CobolMessages.BILL_NOTHING_TO_PAY);
        }
        if (!"Y".equalsIgnoreCase(request.confirmation())) {
            throw bad(CobolMessages.BILL_CONFIRM);
        }
        Card card = cardRepository.findByCardAcctId(accountId).stream().findFirst()
                .orElseThrow(() -> notFound(CobolMessages.TRANSACTION_CARD_NOT_FOUND));
        BigDecimal amount = account.getAcctCurrBal();
        Transaction transaction = new Transaction();
        transaction.setTranId(nextId());
        transaction.setTranTypeCode("02");
        transaction.setTranCategoryCode(2);
        transaction.setTranSource("POS TERM");
        transaction.setTranDescription("BILL PAYMENT - ONLINE");
        transaction.setTranAmount(amount);
        transaction.setTranCardNumber(card.getCardNumber());
        transaction.setTranMerchantId(999999999L);
        transaction.setTranMerchantName("BILL PAYMENT");
        transaction.setTranMerchantCity("N/A");
        transaction.setTranMerchantZip("N/A");
        transaction.setTranOriginTimestamp(LocalDateTime.now());
        transaction.setTranProcessTimestamp(transaction.getTranOriginTimestamp());
        transactionRepository.save(transaction);
        account.setAcctCurrBal(account.getAcctCurrBal().subtract(amount));
        accountRepository.save(account);
        return new BillPaymentResponse(request.accountId(), amount, account.getAcctCurrBal(),
                transaction.getTranId());
    }

    private String nextId() {
        return transactionRepository.findAll().stream().map(Transaction::getTranId)
                .max(Comparator.naturalOrder())
                .map(id -> "%016d".formatted(Long.parseLong(id) + 1))
                .orElse("0000000000000001");
    }

    private CobolApiException bad(String message) {
        return new CobolApiException(HttpStatus.BAD_REQUEST, message);
    }

    private CobolApiException notFound(String message) {
        return new CobolApiException(HttpStatus.NOT_FOUND, message);
    }
}
