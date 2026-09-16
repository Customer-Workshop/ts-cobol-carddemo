package com.carddemo.api;

import java.time.LocalDateTime;

/**
 * The COPAU1A screen as data (COPAUS1C.cbl:300-360): every CIPAUDTY field
 * the detail map renders, the decoded decline-reason and fraud-status
 * displays, the echoed account id + selected key the COMMAREA carries,
 * the derived real timestamp (the AUTHFRDS AUTH_TS derivation), and the
 * ERRMSG line.
 */
public record PendingAuthDetailScreen(
        Long acctId,
        String authKey,
        String cardNum,
        String authDate,
        String authTime,
        String authResp,
        boolean authRespDeclined,
        String authReason,
        String authCode,
        String amount,
        String posEntryMode,
        String messageSource,
        String mccCode,
        String cardExpDate,
        String authType,
        String transactionId,
        String matchStatus,
        String fraudStatus,
        String merchantName,
        String merchantId,
        String merchantCity,
        String merchantState,
        String merchantZip,
        LocalDateTime authTs,
        boolean hasNext,
        String message) {

    public static PendingAuthDetailScreen empty(Long acctId, String authKey, String message) {
        return new PendingAuthDetailScreen(acctId, authKey,
                "", "", "", "", false, "", "", "", "", "", "", "", "", "", "", "",
                "", "", "", "", "", null, false, message);
    }
}
