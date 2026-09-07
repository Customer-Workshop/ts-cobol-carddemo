package com.carddemo.data;

import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.Customer;
import com.carddemo.model.SecurityUser;

import java.util.ArrayList;
import java.util.List;

/**
 * Copybook-driven parsers for the four wave-1 datasets. Offsets are 0-based and follow the field
 * dictionary in AccountView_analysis.md §4.2-§4.5 (source copybooks cited per parser).
 */
public final class LegacyExtractParser {

    public static final int ACCOUNT_RECORD_LENGTH = 300;
    public static final int CUSTOMER_RECORD_LENGTH = 500;
    public static final int CARD_XREF_RECORD_LENGTH = 50;
    public static final int USER_RECORD_LENGTH = 80;

    private final DateColumnGate dateGate;
    private final boolean acctdataGroupIdInZipSlot;

    public LegacyExtractParser(DateColumnGate dateGate, boolean acctdataGroupIdInZipSlot) {
        this.dateGate = dateGate;
        this.acctdataGroupIdInZipSlot = acctdataGroupIdInZipSlot;
    }

    /** ACCOUNT-RECORD, app/cpy/CVACT01Y.cpy:5-17. */
    public List<Account> parseAccounts(List<String> records, String sourceName) {
        List<Account> result = new ArrayList<>(records.size());
        for (int i = 0; i < records.size(); i++) {
            String line = records.get(i);
            int recordNumber = i + 1;
            Account value = new Account();
            value.setAcctId(CobolFieldReader.requiredUnsignedLong(line, 0, 11, sourceName, recordNumber));
            value.setAcctActiveStatus(CobolFieldReader.text(line, 11, 1));
            value.setAcctCurrBal(CobolFieldReader.signedDecimal(line, 12, 12, 2));
            value.setAcctCreditLimit(CobolFieldReader.signedDecimal(line, 24, 12, 2));
            value.setAcctCashCreditLimit(CobolFieldReader.signedDecimal(line, 36, 12, 2));
            value.setAcctOpenDate(dateGate.parse("ACCT-OPEN-DATE", recordNumber, CobolFieldReader.rawText(line, 48, 10)));
            value.setAcctExpiraionDate(
                    dateGate.parse("ACCT-EXPIRAION-DATE", recordNumber, CobolFieldReader.rawText(line, 58, 10)));
            value.setAcctReissueDate(
                    dateGate.parse("ACCT-REISSUE-DATE", recordNumber, CobolFieldReader.rawText(line, 68, 10)));
            value.setAcctCurrCycCredit(CobolFieldReader.signedDecimal(line, 78, 12, 2));
            value.setAcctCurrCycDebit(CobolFieldReader.signedDecimal(line, 90, 12, 2));
            String zipSlot = CobolFieldReader.text(line, 102, 10);
            String groupSlot = CobolFieldReader.text(line, 112, 10);
            if (acctdataGroupIdInZipSlot) {
                value.setAcctAddrZip(null);
                value.setAcctGroupId(zipSlot != null ? zipSlot : groupSlot);
            } else {
                value.setAcctAddrZip(zipSlot);
                value.setAcctGroupId(groupSlot);
            }
            result.add(value);
        }
        return result;
    }

    /** CUSTOMER-RECORD, app/cpy/CVCUS01Y.cpy:5-23. */
    public List<Customer> parseCustomers(List<String> records, String sourceName) {
        List<Customer> result = new ArrayList<>(records.size());
        for (int i = 0; i < records.size(); i++) {
            String line = records.get(i);
            int recordNumber = i + 1;
            Customer value = new Customer();
            value.setCustId(CobolFieldReader.requiredUnsignedLong(line, 0, 9, sourceName, recordNumber));
            value.setCustFirstName(CobolFieldReader.text(line, 9, 25));
            value.setCustMiddleName(CobolFieldReader.text(line, 34, 25));
            value.setCustLastName(CobolFieldReader.text(line, 59, 25));
            value.setCustAddrLine1(CobolFieldReader.text(line, 84, 50));
            value.setCustAddrLine2(CobolFieldReader.text(line, 134, 50));
            value.setCustAddrLine3(CobolFieldReader.text(line, 184, 50));
            value.setCustAddrStateCd(CobolFieldReader.text(line, 234, 2));
            value.setCustAddrCountryCd(CobolFieldReader.text(line, 236, 3));
            value.setCustAddrZip(CobolFieldReader.text(line, 239, 10));
            value.setCustPhoneNum1(CobolFieldReader.text(line, 249, 15));
            value.setCustPhoneNum2(CobolFieldReader.text(line, 264, 15));
            value.setCustSsn(CobolFieldReader.optionalUnsignedLong(line, 279, 9));
            value.setCustGovtIssuedId(CobolFieldReader.text(line, 288, 20));
            value.setCustDobYyyyMmDd(
                    dateGate.parse("CUST-DOB-YYYY-MM-DD", recordNumber, CobolFieldReader.rawText(line, 308, 10)));
            value.setCustEftAccountId(CobolFieldReader.text(line, 318, 10));
            value.setCustPriCardHolderInd(CobolFieldReader.text(line, 328, 1));
            Long fico = CobolFieldReader.optionalUnsignedLong(line, 329, 3);
            value.setCustFicoCreditScore(fico == null ? null : fico.intValue());
            result.add(value);
        }
        return result;
    }

    /** CARD-XREF-RECORD, app/cpy/CVACT03Y.cpy:5-8. */
    public List<CardXref> parseCardXrefs(List<String> records, String sourceName) {
        List<CardXref> result = new ArrayList<>(records.size());
        for (int i = 0; i < records.size(); i++) {
            String line = records.get(i);
            int recordNumber = i + 1;
            CardXref value = new CardXref();
            value.setXrefCardNumber(CobolFieldReader.requiredText(line, 0, 16, sourceName, recordNumber));
            value.setXrefCustId(CobolFieldReader.optionalUnsignedLong(line, 16, 9));
            value.setXrefAcctId(CobolFieldReader.optionalUnsignedLong(line, 25, 11));
            result.add(value);
        }
        return result;
    }

    /** SEC-USER-DATA, app/cpy/CSUSR01Y.cpy:18-23 (records already decoded from IBM037). */
    public List<SecurityUser> parseUsers(List<String> records, String sourceName) {
        List<SecurityUser> result = new ArrayList<>(records.size());
        for (int i = 0; i < records.size(); i++) {
            String line = records.get(i);
            int recordNumber = i + 1;
            SecurityUser value = new SecurityUser();
            value.setSecUsrId(CobolFieldReader.requiredText(line, 0, 8, sourceName, recordNumber));
            value.setSecUsrFname(CobolFieldReader.text(line, 8, 20));
            value.setSecUsrLname(CobolFieldReader.text(line, 28, 20));
            value.setSecUsrPwdHash(null);
            value.setSecUsrPwdLegacy(CobolFieldReader.text(line, 48, 8));
            value.setSecUsrType(CobolFieldReader.requiredText(line, 56, 1, sourceName, recordNumber));
            result.add(value);
        }
        return result;
    }
}
