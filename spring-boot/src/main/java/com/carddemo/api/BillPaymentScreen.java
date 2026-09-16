package com.carddemo.api;

/**
 * COBIL0A screen state returned by POST /api/billing/payments and the
 * /bill-payment web surface: every 3270 field value (trailing pad
 * stripped), the bottom-line message, its colour and the field the cursor
 * lands on. Business rejections come back HTTP 200 — the message line
 * carries the verdict like the 3270 error line did.
 */
public record BillPaymentScreen(
        String accountId,
        String currentBalance,
        String confirmation,
        String message,
        String messageStyle,
        String cursorField,
        String transactionId) {

    /** First display and CLEAR-CURRENT-SCREEN (COBIL00C.cbl:552-566):
        every field blanked, cursor on Acct ID. */
    public static BillPaymentScreen blank() {
        return new BillPaymentScreen("", "", "", null, null, "accountId", null);
    }

    /** Screen state echoed back on an unrecognised AID (COBIL00C.cbl:137-140):
        the map is redisplayed unchanged with the invalid-key message. */
    public static BillPaymentScreen preserved(String accountId, String currentBalance,
                                              String confirmation, String message) {
        return new BillPaymentScreen(trim(accountId), trim(currentBalance), trim(confirmation),
                message, null, null, null);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
