package com.carddemo.batch;

import com.carddemo.model.PendingAuthDetail;
import com.carddemo.model.PendingAuthSummary;
import com.carddemo.repository.PendingAuthDetailRepository;
import com.carddemo.repository.PendingAuthSummaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecutionException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * CBPAUP0C workhorse (app/app-authorization-ims-db2-mq/cbl/CBPAUP0C.cbl, S-20
 * wave 2): the GN/GNP walk of PAUTSUM0/PAUTDTL1 with expiry evaluation and
 * DLET calls. One {@link #purgeBatch} call covers a checkpoint interval —
 * {@code WS-AUTH-SMRY-PROC-CNT > P-CHKP-FREQ} roots per batch so the commit at
 * batch end lands where the EXEC DLI CHKP did (S20-B7; a failure rolls back to
 * the previous CHKP position and fails the job, the 9999-ABEND RC16).
 * <p>
 * Preserved behaviours (do not "fix"):
 * <ul>
 *   <li>The summary-delete guard tests PA-APPROVED-AUTH-CNT twice
 *       ({@code <= 0 AND <= 0}, cbl:156) — the declined count is never consulted,
 *       so a summary whose in-memory approved count reaches zero is deleted even
 *       with declined details outstanding.</li>
 *   <li>Counter decrements happen only in the segment buffer (:288-293) and are
 *       never REPL'd — a surviving summary keeps its stored counters.</li>
 *   <li>Expiry is the raw integer compare {@code CURRENT-YYDDD - (99999 - date9c)
 *       >= expiryDays} (:280-284): YYDDD is year-mod-100 plus day-of-year, so the
 *       subtraction does not roll over a year boundary — same quirk as the
 *       source.</li>
 * </ul>
 */
@Service
public class PendingAuthPurgeService {

    private static final Logger log = LoggerFactory.getLogger(PendingAuthPurgeService.class);

    private final PendingAuthSummaryRepository summaries;
    private final PendingAuthDetailRepository details;

    public PendingAuthPurgeService(PendingAuthSummaryRepository summaries,
                                   PendingAuthDetailRepository details) {
        this.summaries = summaries;
        this.details = details;
    }

    /** Unqualified GN: every PAUTSUM0 root key in ascending order. */
    public List<Long> summaryIds() {
        return summaries.findAll(Sort.by("acctId")).stream()
                .map(PendingAuthSummary::getAcctId).toList();
    }

    /**
     * One checkpoint interval: every root in {@code acctIds} gets the detail
     * sweep (3000/4000), DLETs (5000), and the summary-delete check (6000).
     * Counters are tracked in locals only — matching the source, which never
     * writes decremented counters back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BatchOutcome purgeBatch(List<Long> acctIds, int expiryDays, int currentYyddd,
                                   boolean debug) throws JobExecutionException {
        long dtlRead = 0;
        long dtlDeleted = 0;
        long summaryDeleted = 0;
        Long lastAcctId = null;
        for (Long acctId : acctIds) {
            PendingAuthSummary summary = summaries.findById(acctId).orElse(null);
            if (summary == null) {
                continue;
            }
            lastAcctId = acctId;
            int approvedCnt = nz(summary.getApprovedAuthCnt());
            BigDecimal approvedAmt = nz(summary.getApprovedAuthAmt());
            int declinedCnt = nz(summary.getDeclinedAuthCnt());
            BigDecimal declinedAmt = nz(summary.getDeclinedAuthAmt());
            List<PendingAuthDetail> children = details
                    .findByIdAcctIdOrderByIdAuthDate9cAscIdAuthTime9cAsc(acctId, Pageable.unpaged());
            for (PendingAuthDetail detail : children) {
                dtlRead++;
                if (debug) {
                    log.info("DEBUG: AUTH DTL READ : {}", dtlRead);
                }
                int authDate = 99999 - detail.getId().getAuthDate9c();
                int dayDiff = currentYyddd - authDate;
                if (dayDiff >= expiryDays) {
                    if ("00".equals(detail.getAuthRespCode())) {
                        approvedCnt -= 1;
                        approvedAmt = approvedAmt.subtract(nz(detail.getApprovedAmt()));
                    } else {
                        declinedCnt -= 1;
                        declinedAmt = declinedAmt.subtract(nz(detail.getTransactionAmt()));
                    }
                    if (debug) {
                        log.info("DEBUG: AUTH DTL DLET : {}", acctId);
                    }
                    try {
                        details.delete(detail);
                    } catch (RuntimeException failure) {
                        throw new JobExecutionException(
                                "AUTH DETAIL DELETE FAILED acct " + acctId, failure);
                    }
                    dtlDeleted++;
                }
            }
            // cbl:156 — the doubled approved-count guard is verbatim.
            if (approvedCnt <= 0 && approvedCnt <= 0) {
                if (debug) {
                    log.info("DEBUG: AUTH SMRY DLET : {}", acctId);
                }
                try {
                    summaries.delete(summary);
                } catch (RuntimeException failure) {
                    throw new JobExecutionException(
                            "AUTH SUMMARY DELETE FAILED acct " + acctId, failure);
                }
                summaryDeleted++;
            }
        }
        return new BatchOutcome(dtlRead, dtlDeleted, summaryDeleted, lastAcctId);
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record BatchOutcome(long dtlRead, long dtlDeleted, long summaryDeleted,
                               Long lastAcctId) {
    }
}
