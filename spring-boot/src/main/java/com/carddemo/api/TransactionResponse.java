package com.carddemo.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        String transactionId,
        String cardNumber,
        String transactionTypeCode,
        String transactionCategoryCode,
        String source,
        String description,
        BigDecimal amount,
        Long merchantId,
        String merchantName,
        String merchantCity,
        String merchantZip,
        LocalDateTime originTimestamp,
        LocalDateTime processTimestamp) {
}
