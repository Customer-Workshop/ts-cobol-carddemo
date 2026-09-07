package com.carddemo.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionListRow(
        String selectionViewCode,
        String selectionUpdateCode,
        String transactionId,
        String cardNumber,
        String transactionTypeCode,
        String transactionCategoryCode,
        String description,
        BigDecimal amount,
        LocalDateTime originTimestamp,
        String viewEndpoint) {
}
