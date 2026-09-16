package com.carddemo.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CDEMO-CPVS-INFO COMMAREA equivalent (COPAUS0C.cbl WS-PREV-PAGE-KEYS):
 * echoed back on every AID press so the server stays stateless beyond the
 * sign-on session. {@code prevPageKeys} is the 20-deep PAUKEY-PREV-PG
 * stack (entry i = first key of page i+1); {@code lastKey} is
 * PAUKEY-LAST; {@code authKeys} always has exactly five slots, empty
 * slots {@code null} (LOW-VALUES).
 */
public record PendingAuthPageState(
        Long acctId,
        int pageNum,
        List<String> prevPageKeys,
        String lastKey,
        boolean nextPage,
        List<String> authKeys) {

    public static final int ROW_COUNT = 5;
    public static final int MAX_PAGE_KEYS = 20;

    public static PendingAuthPageState fresh(Long acctId) {
        return new PendingAuthPageState(acctId, 0, new ArrayList<>(), null, false,
                new ArrayList<>(Collections.nCopies(ROW_COUNT, null)));
    }
}
