package com.carddemo.api;

public final class CobolMessages {
    public static final String USER_ID_REQUIRED = "Please enter User ID ...";
    public static final String PASSWORD_REQUIRED = "Please enter Password ...";
    public static final String WRONG_PASSWORD = "Wrong Password. Try again ...";
    public static final String USER_NOT_FOUND = "User not found. Try again ...";
    public static final String USER_VERIFY_FAILED = "Unable to verify the User ...";
    public static final String INVALID_OPTION = "Please enter a valid option number...";
    public static final String ADMIN_ONLY = "No access - Admin Only option... ";
    public static final String ACCOUNT_FILTER_INVALID =
            "Account Filter must  be a non-zero 11 digit number";

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
}
