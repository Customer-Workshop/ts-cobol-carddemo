package com.carddemo.api;

public final class CobolMessages {
    public static final String USER_ID_REQUIRED = "Please enter User ID ...";
    public static final String PASSWORD_REQUIRED = "Please enter Password ...";
    public static final String WRONG_PASSWORD = "Wrong Password. Try again ...";
    public static final String USER_NOT_FOUND = "User not found. Try again ...";
    public static final String USER_VERIFY_FAILED = "Unable to verify the User ...";
    public static final String THANK_YOU = "Thank you for using CardDemo application...";
    public static final String INVALID_KEY_PRESSED = "Invalid key pressed. Please see below...";
    public static final String USER_EXISTS = "User ID already exist...";
    public static final String USER_TYPE_INVALID = "User Type must be A or U...";
    public static final String FIRST_NAME_REQUIRED = "First Name can NOT be empty...";
    public static final String LAST_NAME_REQUIRED = "Last Name can NOT be empty...";
    public static final String USER_ID_REQUIRED_EDIT = "User ID can NOT be empty...";
    public static final String PASSWORD_REQUIRED_EDIT = "Password can NOT be empty...";
    public static final String USER_TYPE_REQUIRED = "User Type can NOT be empty...";
    public static final String USER_ID_NOT_FOUND = "User ID NOT found...";
    public static final String USER_ID_TOO_LONG = "User ID must not exceed 8 characters...";
    public static final String PASSWORD_TOO_LONG = "Password must not exceed 8 characters...";
    public static final String FIRST_NAME_TOO_LONG = "First Name must not exceed 20 characters...";
    public static final String LAST_NAME_TOO_LONG = "Last Name must not exceed 20 characters...";
    public static final String USER_ADD_FAILED = "Unable to Add User...";
    public static final String USER_UPDATE_FAILED = "Unable to Update User...";
    public static final String USER_DELETE_CONFIRM =
        "Press PF5 key to delete this user ...";
    public static final String REPORT_TYPE_REQUIRED = "Select a report type to print report...";
    public static final String REPORT_START_INVALID = "Start Date - Not a valid date...";
    public static final String REPORT_END_INVALID = "End Date - Not a valid date...";
    public static final String REPORT_RANGE_INVALID = "End Date must not be before Start Date...";
    public static final String BILL_NOTHING_TO_PAY = "You have nothing to pay...";
    public static final String BILL_CONFIRM = "Confirm to make a bill payment...";
    public static final String ACCOUNT_NOT_FOUND = "Account ID NOT found...";
    public static final String INVALID_OPTION = "Please enter a valid option number...";
    public static final String INVALID_KEY = "Invalid key pressed...";
    public static final String TRANSACTION_BOTTOM =
        "You have reached the bottom of the page...";
    public static final String TRANSACTION_TOP =
        "You have reached the top of the page...";
    public static final String CARD_NO_MORE_RECORDS = "NO MORE RECORDS TO SHOW";
    public static final String CARD_NO_PREVIOUS_PAGES = "NO PREVIOUS PAGES TO DISPLAY";
    public static final String USER_BOTTOM = "NO MORE PAGES TO DISPLAY";
    public static final String USER_TOP = "NO PREVIOUS PAGES TO DISPLAY";
    public static final String ADMIN_ONLY = "No access - Admin Only option... ";
    public static final String ACCOUNT_FILTER_INVALID =
        "Account Filter must  be a non-zero 11 digit number";
    // COACTVWC verbatim (X(75) receiver): NO-SEARCH-CRITERIA-RECEIVED
    // (cbl:98-99) and the fixed prompt (cbl:107-108, :528-530).
    public static final String NO_INPUT_RECEIVED = "No input received";
    public static final String ACCOUNT_VIEW_PROMPT =
        "Enter or update id of account to display";
    public static final String CARD_FILTER_INVALID =
        "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER";
    public static final String CARD_ACCOUNT_FILTER_INVALID =
        "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER";
    public static final String CARD_ACCOUNT_NOT_FOUND =
        "Did not find this account in cards database";
    public static final String CARD_COMBINATION_NOT_FOUND =
        "Did not find cards for this search condition";
    public static final String CARD_NAME_REQUIRED = "Card name not provided";
    public static final String CARD_NAME_ALPHA = "Card name can only contain alphabets and spaces";
    public static final String CARD_STATUS_INVALID = "Card Active Status must be Y or N";
    public static final String CARD_EXPIRY_MONTH_INVALID =
        "Card expiry month must be between 1 and 12";
    public static final String CARD_EXPIRY_YEAR_INVALID = "Invalid card expiry year";
    public static final String NO_CHANGES_DETECTED =
        "No change detected with respect to values fetched.";
    public static final String RECORD_CHANGED =
        "Record changed by some one else. Please review";
    public static final String SNAPSHOT_REQUIRED =
        "Original values must be supplied for update.";
    public static final String TRANSACTION_ID_INVALID = "Tran ID must be Numeric...";
    public static final String TRANSACTION_NOT_FOUND = "Transaction ID NOT found...";
    public static final String TRANSACTION_ID_REQUIRED = "Tran ID can NOT be empty...";
    public static final String TRANSACTION_VIEW_LOOKUP_FAILED =
        "Unable to lookup Transaction...";
    public static final String TRANSACTION_ACCOUNT_OR_CARD_REQUIRED =
        "Account or Card Number must be entered...";
    public static final String TRANSACTION_ACCOUNT_NOT_FOUND =
        "Account ID NOT found...";
    public static final String TRANSACTION_CARD_INVALID = "Card Number must be Numeric...";
    public static final String TRANSACTION_CARD_NOT_FOUND = "Card Number NOT found...";
    public static final String TRANSACTION_TYPE_REQUIRED = "Type CD can NOT be empty...";
    public static final String TRANSACTION_CATEGORY_REQUIRED = "Category CD can NOT be empty...";
    public static final String TRANSACTION_SOURCE_REQUIRED = "Source can NOT be empty...";
    public static final String TRANSACTION_DESCRIPTION_REQUIRED = "Description can NOT be empty...";
    public static final String TRANSACTION_AMOUNT_REQUIRED = "Amount can NOT be empty...";
    public static final String TRANSACTION_ORIG_DATE_REQUIRED = "Orig Date can NOT be empty...";
    public static final String TRANSACTION_PROC_DATE_REQUIRED = "Proc Date can NOT be empty...";
    public static final String TRANSACTION_MERCHANT_ID_REQUIRED = "Merchant ID can NOT be empty...";
    public static final String TRANSACTION_MERCHANT_NAME_REQUIRED = "Merchant Name can NOT be empty...";
    public static final String TRANSACTION_MERCHANT_CITY_REQUIRED = "Merchant City can NOT be empty...";
    public static final String TRANSACTION_MERCHANT_ZIP_REQUIRED = "Merchant Zip can NOT be empty...";
    public static final String TRANSACTION_CONFIRM = "Confirm to add this transaction...";
    public static final String TRANSACTION_TYPE_INVALID = "Type CD must be Numeric...";
    public static final String TRANSACTION_CATEGORY_INVALID = "Category CD NOT found...";
    public static final String TRANSACTION_CATEGORY_NUMERIC = "Category CD must be Numeric...";
    public static final String TRANSACTION_ORIG_DATE_INVALID =
        "Orig Date should be in format YYYY-MM-DD";
    public static final String TRANSACTION_PROC_DATE_INVALID =
        "Proc Date should be in format YYYY-MM-DD";
    public static final String TRANSACTION_ACCOUNT_NUMERIC =
        "Account ID must be Numeric...";
    public static final String TRANSACTION_ACCOUNT_LOOKUP_FAILED =
        "Unable to lookup Acct in XREF AIX file...";
    public static final String TRANSACTION_CARD_LOOKUP_FAILED =
        "Unable to lookup Card # in XREF file...";
    public static final String TRANSACTION_AMOUNT_FORMAT =
        "Amount should be in format -99999999.99";
    public static final String TRANSACTION_ORIG_DATE_NOT_VALID =
        "Orig Date - Not a valid date...";
    public static final String TRANSACTION_PROC_DATE_NOT_VALID =
        "Proc Date - Not a valid date...";
    public static final String TRANSACTION_MERCHANT_ID_NUMERIC =
        "Merchant ID must be Numeric...";
    public static final String TRANSACTION_CONFIRM_INVALID =
        "Invalid value. Valid values are (Y/N)...";
    public static final String TRANSACTION_ADD_LOOKUP_FAILED =
        "Unable to lookup Transaction...";
    public static final String TRANSACTION_DUPLICATE = "Tran ID already exist...";
    public static final String TRANSACTION_ADD_FAILED = "Unable to Add Transaction...";
    public static final String UPDATE_FAILED = "Update of record failed";
    public static final String ACCOUNT_NUMBER_INVALID =
        "Account number must be a non zero 11 digit number";
    public static final String ACCOUNT_STATUS_INVALID = "Account Active Status must be Y or N";
    public static final String PRIMARY_CARD_HOLDER_INVALID =
        "Primary Card Holder must be Y or N.";
    public static final String SSN_INVALID = "SSN must be a 9 digit number";
    public static final String FICO_INVALID = "FICO Score: should be between 300 and 850";
    public static final String FIELD_REQUIRED_SUFFIX = " must be supplied.";
    public static final String FIELD_NOT_VALID_SUFFIX = " is not valid";
    public static final String FIELD_ALPHA_SUFFIX = " can have alphabets only.";
    public static final String FIELD_ALPHANUM_SUFFIX =
        " can have numbers or alphabets only.";
    public static final String TRANSACTION_ID_NOT_NUMERIC = "Tran ID must be Numeric ...";
    public static final String TRANSACTION_AT_TOP = "You are at the top of the page...";
    public static final String TRANSACTION_ALREADY_TOP =
        "You are already at the top of the page...";
    public static final String TRANSACTION_ALREADY_BOTTOM =
        "You are already at the bottom of the page...";
    public static final String TRANSACTION_LOOKUP_FAILED =
        "Unable to lookup transaction...";
    public static final String TRANSACTION_SELECTION_INVALID =
        "Invalid selection. Valid value is S";

    // COCRDLIC verbatim messages (S-04; COCRDLIC.cbl:112-126, :153-171).
    // CARD_FILE_ERROR_READ is the 75-byte WS-FILE-ERROR-MESSAGE for
    // READPREV ENDFILE — RESP 20, RESP2 90 per CICS docs (S04-B4).
    public static final String CARD_SELECT_ONE =
        "PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE";
    public static final String CARD_INVALID_ACTION = "INVALID ACTION CODE";
    public static final String CARD_NO_RECORDS_FOUND =
        "NO RECORDS FOUND FOR THIS SEARCH CONDITION.";
    public static final String CARD_NO_MORE_PAGES = "NO MORE PAGES TO DISPLAY";
    public static final String CARD_INFO_ACTIONS =
        "TYPE S FOR DETAIL, U TO UPDATE ANY RECORD";
    public static final String CARD_FILE_ERROR_READ =
        "File Error: READ     on CARDDAT   returned RESP 000000020 ,RESP2 000000090 ";

    // COTRTLIC verbatim messages (S-21; COTRTLIC.cbl:236-262, :1115-1117,
    // :1253-1265, :1534-1548, :1867-1938).
    public static final String TRTYPE_FILTER_INVALID =
        "TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER";
    public static final String TRTYPE_SELECT_ONLY_ONE = "Please select only 1 action";
    public static final String TRTYPE_ACTION_INVALID = "Action code selected is invalid";
    public static final String TRTYPE_NO_RECORDS_FILTERS =
        "No Records found for these filter conditions";
    public static final String TRTYPE_NO_PREVIOUS_PAGES = "No previous pages to display";
    public static final String TRTYPE_NO_MORE_PAGES = "No more pages to display";
    public static final String TRTYPE_NO_MORE_RECORDS =
        "No more pages for these search conditions";
    public static final String TRTYPE_NO_RECORDS_FOUND =
        "No records found for this search condition.";
    public static final String TRTYPE_RECORD_GONE =
        "Record not found. Deleted by others ? ";
    public static final String TRTYPE_DEADLOCK = "Deadlock. Someone else updating ?";
    public static final String TRTYPE_CHILD_RECORDS =
        "Please delete associated child records first:";
    public static final String TRTYPE_INFO_ACTIONS =
        "Type U to update, D to delete any record";
    public static final String TRTYPE_CONFIRM_DELETE =
        "Delete HIGHLIGHTED row ? Press F10 to confirm";
    public static final String TRTYPE_CONFIRM_UPDATE =
        "Update HIGHLIGHTED row. Press F10 to save";
    public static final String TRTYPE_DELETE_DONE =
        "HIGHLIGHTED row deleted.Hit Enter to continue";
    public static final String TRTYPE_UPDATE_DONE = "HIGHLIGHTED row was updated";
    public static final String TRTYPE_NO_CHANGES =
        "No change detected with respect to database values.";

    // COTRTUPC verbatim messages (S-21; COTRTUPC.cbl:140-195, :1640-1661).
    public static final String TTUP_SEARCH_KEYS =
        "Enter transaction type to be maintained";
    public static final String TTUP_CREATE_PROMPT = "Press F05 to add. F12 to cancel";
    public static final String TTUP_CHANGES_PROMPT =
        "Update transaction type details shown.";
    public static final String TTUP_DELETE_CONFIRM =
        "Delete this record ? Press F4 to confirm";
    public static final String TTUP_DELETE_DONE = "Delete successful.";
    public static final String TTUP_NEW_DATA = "Enter new transaction type details.";
    public static final String TTUP_CONFIRM_SAVE = "Changes validated.Press F5 to save";
    public static final String TTUP_COMMIT_DONE = "Changes committed to database";
    public static final String TTUP_FAILURE = "Changes unsuccessful";
    public static final String TTUP_RECORD_NOT_FOUND =
        "No record found for this key in database";
    public static final String TTUP_NO_INPUT = "No input received";
    public static final String TTUP_INVALID_KEY = "Invalid key pressed";
    public static final String TTUP_EXIT = "PF03 pressed.Exiting";
    public static final String TTUP_LOCK_FAILED = "Could not lock record for update";
    public static final String TTUP_UPDATE_CANCELLED = "Update was cancelled";
    public static final String TTUP_DELETE_CANCELLED = "Delete was cancelled";
    public static final String TTUP_CODE_REQUIRED = "Tran Type code must be supplied.";
    public static final String TTUP_CODE_NUMERIC = "Tran Type code must be numeric.";
    public static final String TTUP_CODE_NOT_ZERO = "Tran Type code must not be zero.";
    public static final String TTUP_DESC_REQUIRED = "Transaction Desc must be supplied.";
    public static final String TTUP_DESC_ALPHANUM =
        "Transaction Desc can have numbers or alphabets only.";
    public static final String TTUP_UPDATE_FAILED_PREFIX =
        "Error updating: TRANSACTION_TYPE Table. SQLCODE:";
    public static final String TTUP_INSERT_FAILED_PREFIX =
        "Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:";
    public static final String TTUP_DELETE_FAILED_PREFIX =
        "Delete failed with message:SQLCODE :";

    // COCRDUPC verbatim messages (S-06; COCRDUPC.cbl:146-212). The
    // WS-FILE-ERROR-MESSAGE layout (:133-152) renders READ on CARDDAT with
    // the fixed IOERR codes — RESP/RESP2 have no target equivalent
    // (FR-S06-12 assumption: 000000017 / 000000000).
    public static final String CARD_ACCOUNT_NOT_PROVIDED =
        "Account number not provided";
    public static final String CARD_NUMBER_NOT_PROVIDED =
        "Card number not provided";
    public static final String CARD_COULD_NOT_LOCK =
        "Could not lock record for update";
    public static final String CARD_UPDATE_FILE_ERROR_READ =
        "File Error: READ     on CARDDAT   returned RESP 000000017 ,RESP2 000000000 ";
    public static final String CARD_UPDATE_PROMPT_KEYS =
        "Please enter Account and Card Number";
    public static final String CARD_UPDATE_DETAILS_SHOWN =
        "Details of selected card shown above";
    public static final String CARD_UPDATE_PROMPT_CHANGES =
        "Update card details presented above.";
    public static final String CARD_UPDATE_PROMPT_CONFIRM =
        "Changes validated.Press F5 to save";
    public static final String CARD_UPDATE_COMMITTED =
        "Changes committed to database";
    public static final String CARD_UPDATE_UNSUCCESSFUL =
        "Changes unsuccessful. Please try again";

    // COUSR00C–COUSR03C verbatim messages (S-12; the STRING-delimited forms
    // use SEC-USR-ID DELIMITED BY SPACE, so the id stops at its first blank).
    public static final String USER_INVALID_SELECTION =
        "Invalid selection. Valid values are U and D";
    public static final String USER_ALREADY_TOP =
        "You are already at the top of the page...";
    public static final String USER_ALREADY_BOTTOM =
        "You are already at the bottom of the page...";
    public static final String USER_AT_TOP = "You are at the top of the page...";
    public static final String USER_REACHED_BOTTOM =
        "You have reached the bottom of the page...";
    public static final String USER_REACHED_TOP =
        "You have reached the top of the page...";
    public static final String USER_LOOKUP_FAILED = "Unable to lookup User...";
    public static final String USER_SAVE_PROMPT =
        "Press PF5 key to save your updates ...";
    public static final String USER_MODIFY_TO_UPDATE = "Please modify to update ...";

    // COCRDSLC verbatim (S-05): WS-INFO-MSG 88-levels (cbl:127-133) and the
    // per-field prompts of 2210/2220-EDIT (cbl:656-660, :695-700).
    public static final String CARD_VIEW_PROMPT =
        "Please enter Account and Card Number";
    public static final String CARD_VIEW_FOUND =
        "   Displaying requested details";
    public static final String CARD_ACCOUNT_REQUIRED = "Account number not provided";
    public static final String CARD_NUMBER_REQUIRED = "Card number not provided";


    // COBIL00C verbatim messages (S-11; COBIL00C.cbl:161-540).
    public static final String BILL_ACCOUNT_EMPTY = "Acct ID can NOT be empty...";
    public static final String BILL_ACCOUNT_LOOKUP_FAILED =
        "Unable to lookup Account...";
    public static final String BILL_XREF_LOOKUP_FAILED =
        "Unable to lookup XREF AIX file...";
    public static final String BILL_TRANSACTION_ADD_FAILED =
        "Unable to Add Bill pay Transaction...";
    public static final String BILL_ACCOUNT_UPDATE_FAILED =
        "Unable to Update Account...";

    private CobolMessages() {
    }

    public static String optionNotInstalled(String optionName) {
        return "This option " + optionName + " is not installed...";
    }

    public static String optionComingSoon(String optionName) {
        // COMEN01C.cbl:172-176 emits the option name DELIMITED BY SPACE, so
        // only its first word reaches the message ("Transaction View" ->
        // "Transactionis coming soon ...").
        String firstWord = optionName == null ? "" : optionName.split(" ", 2)[0];
        return "This option " + firstWord + "is coming soon ...";
    }

    // NOTFND texts are the exact X(75) STRING-truncated forms shared by
    // COACTVWC (:747-757, :796-806, :846-856) and COACTUPC (:3674-3684,
    // :3723-3733, :3773-3783): ERROR-RESP/RESP2 are X(10) = 9 digits + space,
    // and the 75-char receiver cuts RESP2 to 4 digits (Reas:) or 7 (REAS:).
    public static String xrefNotFound(String accountId) {
        return "Account:" + accountId + " not found in Cross ref file.  Resp:000000013  Reas:0000";
    }

    public static String accountNotFound(String accountId) {
        return "Account:" + accountId + " not found in Acct Master file.Resp:000000013  Reas:0000";
    }

    public static String customerNotFound(String customerId) {
        return "CustId:" + customerId + " not found in customer master.Resp: 000000013  REAS:0000000";
    }

    // WS-FILE-ERROR-MESSAGE (cbl:86-105) as laid into the X(75) receiver:
    // 'File Error: ' + op X(8) + ' on ' + file X(9) + ' returned RESP ' +
    // resp X(10) + ',RESP2 ' + resp2 X(10). RESP/RESP2 have no target
    // equivalent; per S02-B2 they render the fixed IOERR codes.
    public static String fileError(String file) {
        String padded = file.length() >= 9 ? file.substring(0, 9)
                : file + " ".repeat(9 - file.length());
        return "File Error: " + "READ    " + " on " + padded
                + " returned RESP " + "000000017 " + ",RESP2 " + "000000120 ";
    }

    public static String fieldAlpha(String field) {
        return field + FIELD_ALPHA_SUFFIX;
    }

    public static String fieldNumeric(String field) {
        return field + " must be all numeric.";
    }

    public static String phoneInvalid(String field, int part) {
        return field + ": Area code must be A 3 digit number.";
    }

    public static String reportConfirm(String report) {
        return "Please confirm to print the " + report + " report...";
    }

    public static String unknownBatchJob(String jobName) {
        return "Unknown batch job: " + jobName;
    }

    // COTRN02C.cbl:728-733 — two spaces before "Your".
    public static String transactionAdded(String tranId) {
        return "Transaction added successfully.  Your Tran ID is " + tranId + ".";
    }

    // COPAUS0C/COPAUS1C/COPAUS2C verbatim messages (S-19).
    public static final String PENDING_AUTH_ACCT_REQUIRED = "Please enter Acct Id...";
    public static final String PENDING_AUTH_ACCT_NUMERIC = "Acct Id must be Numeric ...";
    public static final String PENDING_AUTH_LAST_AUTH =
        "Already at the last Authorization...";
    public static final String PENDING_AUTH_FRAUD_MARKED = "AUTH MARKED FRAUD...";
    public static final String PENDING_AUTH_FRAUD_REMOVED = "AUTH FRAUD REMOVED...";
    public static final String PENDING_AUTH_ADD_SUCCESS = "ADD SUCCESS";
    public static final String PENDING_AUTH_UPDT_SUCCESS = "UPDT SUCCESS";

    // COPAUS0C STRING messages (:833-960): RESP/RESP2 render as 9(09).
    // NOTFND uses fixed RESP 13/REAS 0; store errors use the S02-B2 IOERR
    // convention (RESP 17, RESP2 120) since a relational read has no RESP.
    public static String pendingAuthXrefNotFound(String acctId) {
        return "Account:" + acctId + " not found in XREF file. Resp:000000013"
                + " Reas:000000000";
    }

    public static String pendingAuthAcctNotFound(String acctId) {
        return "Account:" + acctId + " not found in ACCT file. Resp:000000013"
                + " Reas:000000000";
    }

    public static String pendingAuthCustNotFound(String custId) {
        return "Customer:" + custId + " not found in CUST file. Resp:000000013"
                + " Reas:000000000";
    }

    public static String pendingAuthXrefError(String acctId) {
        return "Account:" + acctId + " System error while reading XREF file."
                + " Resp:000000017 Reas:000000120";
    }

    public static String pendingAuthAcctError(String acctId) {
        return "Account:" + acctId + " System error while reading ACCT file."
                + " Resp:000000017 Reas:000000120";
    }

    public static String pendingAuthCustError(String custId) {
        return "Customer:" + custId + " System error while reading CUST file."
                + " Resp:000000017 Reas:000000120";
    }

    // IMS DIBSTAT has no relational equivalent; a failed store read reports
    // code 'EX' in place of the two-character status (COPAUS0C.cbl:476-514,
    // COPAUS1C.cbl:455-485).
    public static String pendingAuthSummaryError(String code) {
        return " System error while reading AUTH Summary: Code:" + code;
    }

    public static String pendingAuthDetailsError(String code) {
        return " System error while reading AUTH Details: Code:" + code;
    }

    public static String pendingAuthFraudTagError(String code) {
        return " System error while FRAUD Tagging, ROLLBACK||" + code;
    }

    // COPAUS2C.cbl:206-213, :234-241 — SQLCODE/SQLSTATE text for the journal.
    public static String pendingAuthDb2Error(String code, String state) {
        return " SYSTEM ERROR DB2: CODE:" + code + ", STATE: " + state;
    }

    // COUSR01C.cbl:255-258, COUSR02C.cbl:366-371, COUSR03C.cbl:319-324 —
    // 'User ' + SEC-USR-ID DELIMITED BY SPACE + ' has been <verb> ...'.
    public static String userAdded(String userId) {
        return "User " + delimitedBySpace(userId) + " has been added ...";
    }

    public static String userUpdated(String userId) {
        return "User " + delimitedBySpace(userId) + " has been updated ...";
    }

    public static String userDeleted(String userId) {
        return "User " + delimitedBySpace(userId) + " has been deleted ...";
    }

    private static String delimitedBySpace(String value) {
        if (value == null) {
            return "";
        }
        int space = value.indexOf(' ');
        return space < 0 ? value : value.substring(0, space);
    }

    // COBIL00C.cbl:526-530 — 'Payment successful. ' + ' Your Transaction ID
    // is ' + TRAN-ID + '.', so two spaces before "Your".
    public static String billPaymentSuccess(String tranId) {
        return "Payment successful.  Your Transaction ID is " + tranId + ".";
    }
}
