package com.carddemo.api;

/**
 * Legacy screen messages of stream S-01 AccountView, copied VERBATIM from the COBOL source
 * (spacing included). Codes E-01..E-14 follow AccountView_functional_requirement.md §5.
 * Dynamic messages (E-09..E-12) are exposed as format helpers reproducing the STRING literals.
 */
public final class CobolMessages {

    private CobolMessages() {
    }

    /** E-01 — COSGN00C.cbl:120 */
    public static final String ENTER_USER_ID = "Please enter User ID ...";

    /** E-02 — COSGN00C.cbl:125 */
    public static final String ENTER_PASSWORD = "Please enter Password ...";

    /** E-03 — CSMSG01Y.cpy:21 (CCDA-MSG-INVALID-KEY, padded to 50) */
    public static final String INVALID_KEY = "Invalid key pressed. Please see below...         ";

    /** E-04 — COACTVWC.cbl:124 (overwrites 'Account number not provided') */
    public static final String NO_INPUT_RECEIVED = "No input received";

    /** E-05 — COACTVWC.cbl:672 (two spaces after 'must' are in the source) */
    public static final String ACCOUNT_FILTER_INVALID = "Account Filter must  be a non-zero 11 digit number";

    /** E-06 — COSGN00C.cbl:242 */
    public static final String WRONG_PASSWORD = "Wrong Password. Try again ...";

    /** E-07 — COSGN00C.cbl:249 */
    public static final String USER_NOT_FOUND = "User not found. Try again ...";

    /** E-08 — COMEN01C.cbl:131 */
    public static final String INVALID_OPTION = "Please enter a valid option number...";

    /** E-13 — COSGN00C.cbl:254 */
    public static final String UNABLE_TO_VERIFY_USER = "Unable to verify the User ...";

    /** E-14 — CSMSG01Y.cpy:19 (CCDA-MSG-THANK-YOU, padded to 50) */
    public static final String THANK_YOU = "Thank you for using CardDemo application...      ";

    /** NEW TEXT (not a legacy literal): menu options 2..11 in the strangler facade (plan Q-12, B-0010). */
    public static final String OPTION_NOT_AVAILABLE = "Option not available in this release";

    /** NEW TEXT (not a legacy literal): generic 500 body, replaces stack traces (CORE drift rule 7). */
    public static final String UNEXPECTED_ERROR = "An unexpected error occurred";

    /** NEW TEXT (not a legacy literal): a sign-on field longer than its 8-byte BMS field (COSGN00C FR A-SGN-1, Q-14). */
    public static final String FIELD_TOO_LONG = "must be at most 8 characters";

    /** NEW TEXT (not a legacy literal): GET /api/auth/session without a signed-on session (B-0027). */
    public static final String AUTHENTICATION_REQUIRED = "Authentication required";

    /** CICS file names as placed in ERROR-FILE — COACTVWC.cbl:750-766, 799-816, 848-865. */
    public static final String FILE_CXACAIX = "CXACAIX";
    public static final String FILE_ACCTDAT = "ACCTDAT";
    public static final String FILE_CUSTDAT = "CUSTDAT";

    /** E-09 — COACTVWC.cbl:747-757 (STRING literals; the id is the 11-digit WS-CARD-RID-ACCT-ID-X). */
    public static String xrefNotFound(String acctId11, String resp, String reas) {
        return returnMsg("Account:" + acctId11 + " not found in Cross ref file.  Resp:" + pad10(resp) + " Reas:" + pad10(reas));
    }

    /** E-10 — COACTVWC.cbl:796-806 */
    public static String accountNotFound(String acctId11, String resp, String reas) {
        return returnMsg("Account:" + acctId11 + " not found in Acct Master file.Resp:" + pad10(resp) + " Reas:" + pad10(reas));
    }

    /** E-11 — COACTVWC.cbl:845-856 (the id is the 9-digit WS-CARD-RID-CUST-ID-X). */
    public static String customerNotFound(String custId9, String resp, String reas) {
        return returnMsg("CustId:" + custId9 + " not found in customer master.Resp: " + pad10(resp) + " REAS:" + pad10(reas));
    }

    /** E-12 — WS-FILE-ERROR-MESSAGE layout, COACTVWC.cbl:86-105. */
    public static String fileError(String opName, String fileName, String resp, String reas) {
        return returnMsg("File Error: " + padRight(opName, 8) + " on " + padRight(fileName, 9)
                + " returned RESP " + pad10(resp) + ",RESP2 " + pad10(reas) + "     ");
    }

    /** WS-RETURN-MSG is PIC X(75): STRING ... DELIMITED BY SIZE and MOVE both truncate (COACTVWC.cbl:117). */
    private static String returnMsg(String text) {
        return text.length() > 75 ? text.substring(0, 75) : text;
    }

    private static String pad10(String value) {
        return padRight(value, 10);
    }

    private static String padRight(String value, int width) {
        String text = value == null ? "" : value;
        if (text.length() >= width) {
            return text.substring(0, width);
        }
        return text + " ".repeat(width - text.length());
    }
}
