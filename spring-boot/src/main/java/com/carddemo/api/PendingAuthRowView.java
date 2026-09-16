package com.carddemo.api;

/**
 * One rendered row of the COPAU0A list: the SEL input plus the eight
 * display columns (COPAUS0C.cbl:520-610). {@code key} is the encoded
 * PAUT9CTS the SEL scan resolves through (CDEMO-CPVS-AUTH-KEYS).
 */
public record PendingAuthRowView(
        String select,
        String transactionId,
        String date,
        String time,
        String type,
        String approveDecline,
        String matchStatus,
        String amount,
        String key) {
}
