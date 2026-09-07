package com.carddemo.api;

public final class CobolMessages {
    public static final String USER_ID_REQUIRED = "Please enter User ID ...";
    public static final String PASSWORD_REQUIRED = "Please enter Password ...";
    public static final String WRONG_PASSWORD = "Wrong Password. Try again ...";
    public static final String USER_NOT_FOUND = "User not found. Try again ...";
    public static final String USER_VERIFY_FAILED = "Unable to verify the User ...";
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

    private CobolMessages() {
    }

    public static String optionNotInstalled(String optionName) {
        return "This option " + optionName + " is not installed...";
    }

    public static String optionComingSoon(String optionName) {
        return "This option " + optionName + "is coming soon ...";
    }

    public static String xrefNotFound(String accountId) {
        return "Account:" + accountId + " not found in Cross ref file. Resp:13 Reas:0";
    }

    public static String accountNotFound(String accountId) {
        return "Account:" + accountId + " not found in Acct Master file.Resp:13 Reas:0";
    }

    public static String customerNotFound(String customerId) {
        return "CustId:" + customerId + " not found in customer master.Resp:13 REAS:0";
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
}
