package com.carddemo.api;

/**
 * COTRN02A screen state returned by POST /api/transactions (ENTER) and
 * POST /api/transactions/copy-last (PF5): every 3270 field value (trailing
 * pad stripped), the bottom-line message, its colour and the field the
 * cursor lands on. Business rejections come back HTTP 200 — COBOL never
 * set an HTTP status for edit failures, so the JSON message carries the
 * verdict like the 3270 error line did.
 */
public record TransactionAddScreen(
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
        String confirmation,
        String message,
        String messageStyle,
        String cursorField,
        String transactionId) {

    public static TransactionAddScreen blank() {
        return new TransactionAddScreen("", "", "", "", "", "", "", "", "", "",
                "", "", "", "", null, null, "accountId", null);
    }

    /** Screen state preserved on an unrecognised AID (COTRN02C.cbl:153-157). */
    public static TransactionAddScreen preserved(TransactionCreateRequest request,
                                                 String message) {
        return new TransactionAddScreen(trim(request.accountId()), trim(request.cardNumber()),
                trim(request.transactionTypeCode()), trim(request.transactionCategoryCode()),
                trim(request.source()), trim(request.description()),
                trim(request.amount()), trim(request.originDate()), trim(request.processDate()),
                trim(request.merchantId()), trim(request.merchantName()),
                trim(request.merchantCity()), trim(request.merchantZip()),
                trim(request.confirmation()), message, null, null, null);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
