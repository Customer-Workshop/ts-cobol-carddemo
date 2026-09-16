package com.carddemo.api;

/**
 * COTRN02A input map: the fourteen unprotected screen fields as raw
 * PIC X text, exactly as the 3270 would deliver them. COBOL ran its
 * class tests against the blank-padded field, so values keep their
 * typed length here — validation happens in the service
 * (COTRN02C.cbl:253-437), not at bind time.
 */
public record TransactionCreateRequest(
        String accountId,
        String cardNumber,
        String transactionTypeCode,
        String transactionCategoryCode,
        String source,
        String description,
        String amount,
        String originDate,
        String processDate,
        String merchantId,
        String merchantName,
        String merchantCity,
        String merchantZip,
        String confirmation) {
}
