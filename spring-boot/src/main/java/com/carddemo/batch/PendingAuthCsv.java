package com.carddemo.batch;

import com.carddemo.model.PendingAuthDetail;
import com.carddemo.model.PendingAuthSummary;

import java.math.BigDecimal;

/**
 * Documented CSV layouts standing in for PAUDBLOD's input records and
 * PAUDBUNL/DBUNLDGS's output records (S20-B8): the 100-byte PAUTSUM0 root
 * record (INFIL1-REC/OPFIL1-REC) becomes one comma-separated line in segment
 * field order, and the 206-byte key-prefixed PAUTDTL1 record (INFIL2-REC /
 * OPFIL2-REC = ROOT-SEG-KEY + CHILD-SEG-REC) becomes
 * {@code acctId|authDate9c,authTime9c,...body fields}. Empty columns are
 * nulls. There is no quoting — a literal comma inside a text field produces
 * a malformed record on reload, the same boundary the fixed-width originals
 * had (DBUNLDGS's GSAM writers share this layout; one implementation covers
 * both unload FRs).
 */
public final class PendingAuthCsv {

    private PendingAuthCsv() {
    }

    /** OPFIL1-REC equivalent: the 12 PAUTSUM0 fields in CIPAUSMY order. */
    public static String summaryLine(PendingAuthSummary s) {
        return join(s.getAcctId(), s.getCustId(), s.getAuthStatus(), s.getAccountStatus(),
                s.getCreditLimit(), s.getCashLimit(), s.getCreditBalance(), s.getCashBalance(),
                s.getApprovedAuthCnt(), s.getDeclinedAuthCnt(),
                s.getApprovedAuthAmt(), s.getDeclinedAuthAmt());
    }

    /** INFIL1-REC parse; throws on anything but the 12-column layout. */
    public static PendingAuthSummary parseSummary(String line) {
        String[] f = line.split(",", -1);
        if (f.length != 12 || blank(f[0])) {
            throw new IllegalArgumentException("malformed root record: " + line);
        }
        PendingAuthSummary s = new PendingAuthSummary();
        s.setAcctId(Long.valueOf(f[0].trim()));
        s.setCustId(longField(f[1]));
        s.setAuthStatus(str(f[2]));
        s.setAccountStatus(str(f[3]));
        s.setCreditLimit(decimal(f[4]));
        s.setCashLimit(decimal(f[5]));
        s.setCreditBalance(decimal(f[6]));
        s.setCashBalance(decimal(f[7]));
        s.setApprovedAuthCnt(intField(f[8]));
        s.setDeclinedAuthCnt(intField(f[9]));
        s.setApprovedAuthAmt(decimal(f[10]));
        s.setDeclinedAuthAmt(decimal(f[11]));
        return s;
    }

    /**
     * OPFIL2-REC equivalent: {@code rootKey|child} — the acct id as the plain
     * ROOT-SEG-KEY followed by the 27 detail fields (key complements first,
     * then CIPAUDTY body order).
     */
    public static String detailLine(PendingAuthDetail d) {
        PendingAuthDetail.Id id = d.getId();
        return id.getAcctId() + "|" + join(id.getAuthDate9c(), id.getAuthTime9c(),
                d.getAuthOrigDate(), d.getAuthOrigTime(), d.getCardNum(), d.getAuthType(),
                d.getCardExpiryDate(), d.getMessageType(), d.getMessageSource(),
                d.getAuthIdCode(), d.getAuthRespCode(), d.getAuthRespReason(),
                d.getProcessingCode(), d.getTransactionAmt(), d.getApprovedAmt(),
                d.getMerchantCategoryCode(), d.getAcqrCountryCode(), d.getPosEntryMode(),
                d.getMerchantId(), d.getMerchantName(), d.getMerchantCity(),
                d.getMerchantState(), d.getMerchantZip(), d.getTransactionId(),
                d.getMatchStatus(), d.getAuthFraud(), d.getFraudRptDate());
    }

    /**
     * INFIL2-REC parse. Returns null when the root key is not numeric — the
     * {@code IF ROOT-SEG-KEY IS NUMERIC} guard skips such records silently.
     * Throws when the child record is not the 27-column layout.
     */
    public static PendingAuthDetail parseDetail(String line) {
        int pipe = line.indexOf('|');
        String key = pipe < 0 ? line : line.substring(0, pipe);
        if (!key.trim().matches("\\d+")) {
            return null;
        }
        String[] f = (pipe < 0 ? "" : line.substring(pipe + 1)).split(",", -1);
        if (f.length != 27 || blank(f[0]) || blank(f[1])) {
            throw new IllegalArgumentException("malformed child record: " + line);
        }
        PendingAuthDetail d = new PendingAuthDetail();
        d.setId(new PendingAuthDetail.Id(Long.valueOf(key.trim()),
                Integer.valueOf(f[0].trim()), Integer.valueOf(f[1].trim())));
        d.setAuthOrigDate(str(f[2]));
        d.setAuthOrigTime(str(f[3]));
        d.setCardNum(str(f[4]));
        d.setAuthType(str(f[5]));
        d.setCardExpiryDate(str(f[6]));
        d.setMessageType(str(f[7]));
        d.setMessageSource(str(f[8]));
        d.setAuthIdCode(str(f[9]));
        d.setAuthRespCode(str(f[10]));
        d.setAuthRespReason(str(f[11]));
        d.setProcessingCode(intField(f[12]));
        d.setTransactionAmt(decimal(f[13]));
        d.setApprovedAmt(decimal(f[14]));
        d.setMerchantCategoryCode(str(f[15]));
        d.setAcqrCountryCode(str(f[16]));
        d.setPosEntryMode(intField(f[17]));
        d.setMerchantId(str(f[18]));
        d.setMerchantName(str(f[19]));
        d.setMerchantCity(str(f[20]));
        d.setMerchantState(str(f[21]));
        d.setMerchantZip(str(f[22]));
        d.setTransactionId(str(f[23]));
        d.setMatchStatus(str(f[24]));
        d.setAuthFraud(str(f[25]));
        d.setFraudRptDate(str(f[26]));
        return d;
    }

    private static String join(Object... fields) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            if (fields[i] != null) {
                out.append(fields[i]);
            }
        }
        return out.toString();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String str(String value) {
        return value.isEmpty() ? null : value;
    }

    private static Long longField(String value) {
        return blank(value) ? null : Long.valueOf(value.trim());
    }

    private static Integer intField(String value) {
        return blank(value) ? null : Integer.valueOf(value.trim());
    }

    private static BigDecimal decimal(String value) {
        return blank(value) ? null : new BigDecimal(value.trim());
    }
}
