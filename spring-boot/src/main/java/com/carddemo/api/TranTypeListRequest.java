package com.carddemo.api;

import java.util.List;

/**
 * One AID press against CTLI: the 3270 input map — AID, the two filters, the
 * per-row select flags, and the per-row (possibly edited) descriptions —
 * plus the echoed COMMAREA pageState.
 */
public record TranTypeListRequest(
        String aid,
        String trType,
        String trDesc,
        List<String> selects,
        List<String> rowDescs,
        TranTypeListPageState pageState) {
}
