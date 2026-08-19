package com.carddemo.api;

import java.math.BigDecimal;

public record TransactionCreateRequest(
        String accountId,
        String cardNumber,
        String transactionTypeCode,
        String transactionCategoryCode,
        String source,
        String description,
        BigDecimal amount,
        String originDate,
        String processDate,
        Long merchantId,
        String merchantName,
        String merchantCity,
        String merchantZip,
        String confirmation) {
}
