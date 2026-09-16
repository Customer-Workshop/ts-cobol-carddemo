package com.carddemo.api;

import java.util.List;

/**
 * The COPAU0A screen as data: the acct-id input echo, the customer context
 * block, the PAUTSUM0 summary block, five row slots, and the ERRMSG line
 * (COPAU0A / COPAUS0C.cbl:640-700, :750-810).
 */
public record PendingAuthScreenView(
        String acctIdInput,
        String custName,
        String custId,
        String addr1,
        String addr2,
        String acctStatus,
        String phone1,
        String apprCnt,
        String declCnt,
        String creditLimit,
        String cashLimit,
        String apprAmt,
        String creditBal,
        String cashBal,
        String declAmt,
        List<PendingAuthRowView> rows,
        int pageNum,
        String message) {
}
