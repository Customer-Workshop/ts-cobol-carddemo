package com.carddemo.service;

import com.carddemo.api.CobolMessages;
import com.carddemo.api.PendingAuthDetailScreen;
import com.carddemo.api.PendingAuthKey;
import com.carddemo.model.PendingAuthDetail;
import com.carddemo.model.PendingAuthSummary;
import com.carddemo.repository.PendingAuthDetailRepository;
import com.carddemo.repository.PendingAuthSummaryRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * COPAUS1C (tran CPVD): the pending-authorization detail. Qualified GNP at
 * the selected key = repository findById; the F8 next-auth hop is the
 * strictly-after keyset fetch; F5 toggles fraud through
 * {@link AuthFraudService} in one transaction — the LINK + SYNCPOINT unit
 * of work (S19-B7).
 */
@Service
public class PendingAuthDetailService {

    private static final String FRAUD_REPORT = "F";
    private static final String FRAUD_REMOVE = "R";

    // WS-DECLINE-REASON-TABLE (COPAUS1C.cbl:53-66) — X(20) 'code'+DESC(16).
    private static final Map<String, String> DECLINE_REASONS = new TreeMap<>(Map.of(
            "0000", "APPROVED", "3100", "INVALID CARD", "4100", "INSUFFICNT FUND",
            "4200", "CARD NOT ACTIVE", "4300", "ACCOUNT CLOSED",
            "4400", "EXCED DAILY LMT", "5100", "CARD FRAUD",
            "5200", "MERCHANT FRAUD", "5300", "LOST CARD", "9000", "UNKNOWN"));

    private final PendingAuthDetailRepository detailRepository;
    private final PendingAuthSummaryRepository summaryRepository;
    private final AuthFraudService fraudService;

    public PendingAuthDetailService(PendingAuthDetailRepository detailRepository,
                                    PendingAuthSummaryRepository summaryRepository,
                                    AuthFraudService fraudService) {
        this.detailRepository = detailRepository;
        this.summaryRepository = summaryRepository;
        this.fraudService = fraudService;
    }

    // PROCESS-ENTER-KEY (:198-224): entry requires a numeric account id and a
    // non-blank selected key; otherwise the map ships empty.
    public PendingAuthDetailScreen view(Long acctId, String authKey) {
        PendingAuthKey key = PendingAuthKey.parse(authKey);
        if (acctId == null || key == null) {
            return PendingAuthDetailScreen.empty(acctId, authKey, null);
        }
        PendingAuthDetail record;
        try {
            record = read(acctId, key);
        } catch (DataAccessException exception) {
            return PendingAuthDetailScreen.empty(acctId, authKey,
                    CobolMessages.pendingAuthReadingDetailsError("EX"));
        }
        if (record == null) {
            return PendingAuthDetailScreen.empty(acctId, authKey, null);
        }
        return screen(record, null);
    }

    // PROCESS-PF8-KEY (:270-296): re-read the current segment then one
    // unqualified GNP — strictly after the current key.
    public PendingAuthDetailScreen next(Long acctId, String authKey) {
        PendingAuthKey key = PendingAuthKey.parse(authKey);
        if (acctId == null || key == null) {
            return PendingAuthDetailScreen.empty(acctId, authKey, null);
        }
        PendingAuthDetail next;
        try {
            List<PendingAuthDetail> following = detailRepository.findByAcctIdAfterKey(
                    acctId, key.date9c(), key.time9c(), PageRequest.ofSize(1));
            next = following.isEmpty() ? null : following.get(0);
        } catch (DataAccessException exception) {
            PendingAuthDetail current = readOrNull(acctId, key);
            if (current == null) {
                return PendingAuthDetailScreen.empty(acctId, authKey,
                        CobolMessages.pendingAuthNextAuthError("EX"));
            }
            return screen(current, CobolMessages.pendingAuthNextAuthError("EX"));
        }
        if (next == null) {
            PendingAuthDetail current = readOrNull(acctId, key);
            if (current == null) {
                return PendingAuthDetailScreen.empty(acctId, authKey,
                        CobolMessages.PENDING_AUTH_LAST_AUTH);
            }
            return screen(current, CobolMessages.PENDING_AUTH_LAST_AUTH);
        }
        return screen(next, null);
    }

    // MARK-AUTH-FRAUD (:226-267): re-read, flip the 88-level intent, journal
    // through COPAUS2C, then REPL the flag + fraud report date inside one
    // unit of work. A failed journal leaves the flag untouched (ROLLBACK).
    @Transactional
    public PendingAuthDetailScreen markFraud(Long acctId, String authKey) {
        PendingAuthKey key = PendingAuthKey.parse(authKey);
        if (acctId == null || key == null) {
            return PendingAuthDetailScreen.empty(acctId, authKey, null);
        }
        PendingAuthDetail record;
        try {
            record = read(acctId, key);
        } catch (DataAccessException exception) {
            return PendingAuthDetailScreen.empty(acctId, authKey,
                    CobolMessages.pendingAuthReadingDetailsError("EX"));
        }
        if (record == null) {
            return PendingAuthDetailScreen.empty(acctId, authKey, null);
        }
        String action = FRAUD_REPORT.equals(record.getAuthFraud())
                ? FRAUD_REMOVE : FRAUD_REPORT;

        PendingAuthSummary summary;
        try {
            summary = summaryRepository.findById(acctId).orElse(null);
        } catch (DataAccessException exception) {
            return screen(record, CobolMessages.pendingAuthReadingSummaryError("EX"));
        }
        Long custId = summary == null ? null : summary.getCustId();

        AuthFraudService.FraudResult result = fraudService.journal(acctId, custId, record,
                action.charAt(0));
        if (!result.success()) {
            return screen(record, result.message());
        }
        // REPL (UPDATE-AUTH-DETAILS :518-560): COPAUS2C stamps the segment's
        // PA-FRAUD-RPT-DATE with the MMDDYY current date (:95-101). A failed
        // REPL rolls the unit of work back and reports the FRAUD-tagging
        // text (:540-556).
        record.setAuthFraud(action);
        record.setFraudRptDate(LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yy")));
        try {
            detailRepository.save(record);
        } catch (DataAccessException exception) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return screen(record, CobolMessages.pendingAuthFraudTagError("EX"));
        }
        String message = FRAUD_REMOVE.equals(action)
                ? CobolMessages.PENDING_AUTH_FRAUD_REMOVED
                : CobolMessages.PENDING_AUTH_FRAUD_MARKED;
        return screen(record, message);
    }

    /** Whether a next row exists — used by REST consumers as a hasNext hint. */
    public boolean hasNext(Long acctId, String authKey) {
        PendingAuthKey key = PendingAuthKey.parse(authKey);
        if (acctId == null || key == null) {
            return false;
        }
        try {
            return !detailRepository.findByAcctIdAfterKey(acctId, key.date9c(),
                    key.time9c(), PageRequest.ofSize(1)).isEmpty();
        } catch (DataAccessException exception) {
            return false;
        }
    }

    // Qualified GNP at the selected key: a store failure is a caller-visible
    // 'reading Auth Details' error, not a missing segment.
    private PendingAuthDetail read(Long acctId, PendingAuthKey key) {
        return detailRepository.findById(
                new PendingAuthDetail.Id(acctId, key.date9c(), key.time9c())).orElse(null);
    }

    private PendingAuthDetail readOrNull(Long acctId, PendingAuthKey key) {
        try {
            return read(acctId, key);
        } catch (DataAccessException exception) {
            return null;
        }
    }

    // POPULATE-AUTH-DETAILS (:299-360): field-by-field CIPAUDTY render.
    private PendingAuthDetailScreen screen(PendingAuthDetail record, String message) {
        Long acctId = record.getId().getAcctId();
        String authKey = PendingAuthKey.of(record).encoded();
        boolean declined = !"00".equals(record.getAuthRespCode());
        String fraud = record.getAuthFraud();
        String fraudDisplay = FRAUD_REPORT.equals(fraud) || FRAUD_REMOVE.equals(fraud)
                ? fraud + "-" + nullToEmpty(record.getFraudRptDate())
                : "-";
        return new PendingAuthDetailScreen(acctId, authKey,
                nullToEmpty(record.getCardNum()),
                PendingAuthService.editDate(record.getAuthOrigDate()),
                PendingAuthService.editTime(record.getAuthOrigTime()),
                declined ? "D" : "A",
                declined,
                declineReason(record.getAuthRespReason()),
                record.getProcessingCode() == null ? "" : "%06d".formatted(record.getProcessingCode()),
                CobolFormat.editSuppressedAmount(record.getApprovedAmt()),
                record.getPosEntryMode() == null ? "" : "%02d".formatted(record.getPosEntryMode()),
                nullToEmpty(record.getMessageSource()),
                nullToEmpty(record.getMerchantCategoryCode()),
                cardExpiry(record.getCardExpiryDate()),
                nullToEmpty(record.getAuthType()),
                nullToEmpty(record.getTransactionId()),
                nullToEmpty(record.getMatchStatus()),
                fraudDisplay,
                nullToEmpty(record.getMerchantName()),
                nullToEmpty(record.getMerchantId()),
                nullToEmpty(record.getMerchantCity()),
                nullToEmpty(record.getMerchantState()),
                nullToEmpty(record.getMerchantZip()),
                PendingAuthKey.realTimestamp(record),
                hasNext(acctId, authKey),
                message);
    }

    // SEARCH ALL over WS-DECLINE-REASON-TAB (:317-330): 'code-desc(16)' on a
    // hit, '9999-ERROR' at end.
    private static String declineReason(String reasonCode) {
        if (reasonCode == null) {
            return "9999-ERROR";
        }
        String desc = DECLINE_REASONS.get(reasonCode.trim());
        return desc == null ? "9999-ERROR" : reasonCode.trim() + "-" + desc;
    }

    // MMYY -> MM/YY (:333-335).
    private static String cardExpiry(String mmyy) {
        if (mmyy == null || mmyy.length() < 4) {
            return "";
        }
        return mmyy.substring(0, 2) + "/" + mmyy.substring(2, 4);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
