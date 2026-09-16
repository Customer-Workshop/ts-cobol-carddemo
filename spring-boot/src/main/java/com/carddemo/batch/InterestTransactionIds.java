package com.carddemo.batch;

import com.carddemo.service.TransactionIdGenerator;

/**
 * TRAN-ID derivation for interest transactions (CBACT04C.cbl:473-480):
 * TRAN-ID X(16) = PARM-DATE X(10) + WS-TRANID-SUFFIX 9(06), a per-run counter
 * starting at 1. Rerunning with the same parmDate produces the same ids —
 * faithful to legacy, where the suffix restarts each run (documented in the
 * runbook; the transactions row is then merged on insert).
 *
 * When parmDate is absent the builder degrades to {@link TransactionIdGenerator}
 * (S15-B4 note in the plan). The cbact04Job validator rejects a missing parm
 * before the job runs, so the fallback only fires for direct service use
 * outside the job flow.
 */
public class InterestTransactionIds {
    private final String parmDate;
    private final TransactionIdGenerator generator;
    private long suffix;
    private String generated;

    public InterestTransactionIds(String parmDate, TransactionIdGenerator generator) {
        this.parmDate = parmDate;
        this.generator = generator;
    }

    public String next() {
        if (parmDate != null && !parmDate.isBlank()) {
            suffix++;
            return parmDate + "%06d".formatted(suffix);
        }
        generated = generated == null ? generator.nextId() : generator.nextIdAfter(generated);
        return generated;
    }
}
