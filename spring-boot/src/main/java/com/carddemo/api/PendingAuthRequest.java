package com.carddemo.api;

import java.util.List;

/**
 * One AID press against the pending-auth list (COPAUS0C): the 3270 key,
 * the ACCTID input, the five SEL fields, and the echoed COMMAREA.
 * {@code pageState == null} is the fresh-entry path (menu entry or any
 * program XCTLing in — COPAUS0C.cbl:187-217).
 */
public record PendingAuthRequest(
        String aid,
        String acctId,
        List<String> selections,
        PendingAuthPageState pageState) {
}
