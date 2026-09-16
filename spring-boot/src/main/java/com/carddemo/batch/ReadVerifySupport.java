package com.carddemo.batch;

import com.carddemo.model.Account;
import com.carddemo.model.Card;
import com.carddemo.model.CardXref;
import com.carddemo.model.Customer;
import com.carddemo.util.DateEditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

import static com.carddemo.data.ZonedDecimalFieldFormatter.date10;
import static com.carddemo.data.ZonedDecimalFieldFormatter.digits;
import static com.carddemo.data.ZonedDecimalFieldFormatter.packed;
import static com.carddemo.data.ZonedDecimalFieldFormatter.text;
import static com.carddemo.data.ZonedDecimalFieldFormatter.zoned;

/**
 * Shared support for the S-17 read/verify jobs (READACCT/CBACT01C,
 * READCARD/CBACT02C, READCUST/CBCUS01C, READXREF/CBACT03C). Rebuilds the
 * fixed-width record images the COBOL DISPLAYs (CVACT01Y/CVACT02Y/CVACT03Y/
 * CVCUS01Y copybook layouts) and assembles the READACCT output records.
 */
final class ReadVerifySupport {

    /** Single log channel standing in for the SYSOUT/SYSPRINT DD. */
    static final Logger SYSOUT = LoggerFactory.getLogger("carddemo.sysout");

    private ReadVerifySupport() {
    }

    static void logStart(String program) {
        SYSOUT.info("START OF EXECUTION OF PROGRAM {}", program);
    }

    static void logEnd(String program) {
        SYSOUT.info("END OF EXECUTION OF PROGRAM {}", program);
    }

    /** 1100-DISPLAY-ACCT-RECORD: labelled per-field lines (CBACT01C.cbl:200-213). */
    static void logLabelledAccount(Account account) {
        SYSOUT.info("ACCT-ID                 :{}", digits(account.getAcctId(), 11));
        SYSOUT.info("ACCT-ACTIVE-STATUS      :{}", text(account.getAcctActiveStatus(), 1));
        SYSOUT.info("ACCT-CURR-BAL           :{}", zoned(account.getAcctCurrBal(), 10, 2));
        SYSOUT.info("ACCT-CREDIT-LIMIT       :{}", zoned(account.getAcctCreditLimit(), 10, 2));
        SYSOUT.info("ACCT-CASH-CREDIT-LIMIT  :{}", zoned(account.getAcctCashCreditLimit(), 10, 2));
        SYSOUT.info("ACCT-OPEN-DATE          :{}", date10(account.getAcctOpenDate()));
        SYSOUT.info("ACCT-EXPIRAION-DATE     :{}", date10(account.getAcctExpirationDate()));
        SYSOUT.info("ACCT-REISSUE-DATE       :{}", date10(account.getAcctReissueDate()));
        SYSOUT.info("ACCT-CURR-CYC-CREDIT    :{}", zoned(account.getAcctCurrCycCredit(), 10, 2));
        SYSOUT.info("ACCT-CURR-CYC-DEBIT     :{}", zoned(account.getAcctCurrCycDebit(), 10, 2));
        SYSOUT.info("ACCT-GROUP-ID           :{}", text(account.getAcctGroupId(), 10));
        SYSOUT.info("-".repeat(49));
    }

    /** ACCOUNT-RECORD image (CVACT01Y, 300 bytes) for the raw DISPLAY. */
    static String accountImage(Account account) {
        return digits(account.getAcctId(), 11)
                + text(account.getAcctActiveStatus(), 1)
                + zoned(account.getAcctCurrBal(), 10, 2)
                + zoned(account.getAcctCreditLimit(), 10, 2)
                + zoned(account.getAcctCashCreditLimit(), 10, 2)
                + date10(account.getAcctOpenDate())
                + date10(account.getAcctExpirationDate())
                + date10(account.getAcctReissueDate())
                + zoned(account.getAcctCurrCycCredit(), 10, 2)
                + zoned(account.getAcctCurrCycDebit(), 10, 2)
                + text(account.getAcctAddrZip(), 10)
                + text(account.getAcctGroupId(), 10)
                + " ".repeat(178);
    }

    /** CARD-RECORD image (CVACT02Y, 150 bytes). */
    static String cardImage(Card card) {
        return text(card.getCardNumber(), 16)
                + digits(card.getCardAcctId(), 11)
                + digits(card.getCardCvvCode(), 3)
                + text(card.getCardEmbossedName(), 50)
                + date10(card.getCardExpirationDate())
                + text(card.getCardActiveStatus(), 1)
                + " ".repeat(59);
    }

    /** CUSTOMER-RECORD image (CVCUS01Y, 500 bytes). */
    static String customerImage(Customer customer) {
        return digits(customer.getCustId(), 9)
                + text(customer.getCustFirstName(), 25)
                + text(customer.getCustMiddleName(), 25)
                + text(customer.getCustLastName(), 25)
                + text(customer.getCustAddrLine1(), 50)
                + text(customer.getCustAddrLine2(), 50)
                + text(customer.getCustAddrLine3(), 50)
                + text(customer.getCustAddrStateCode(), 2)
                + text(customer.getCustAddrCountryCode(), 3)
                + text(customer.getCustAddrZip(), 10)
                + text(customer.getCustPhoneNum1(), 15)
                + text(customer.getCustPhoneNum2(), 15)
                + digits(customer.getCustSsn(), 9)
                + text(customer.getCustGovernmentIssuedId(), 20)
                + date10(customer.getCustDob())
                + text(customer.getCustEftAccountId(), 10)
                + text(customer.getCustPrimaryCardHolderIndicator(), 1)
                + digits(customer.getCustFicoCreditScore(), 3)
                + " ".repeat(168);
    }

    /** CARD-XREF-RECORD image (CVACT03Y, 50 bytes). */
    static String xrefImage(CardXref xref) {
        return text(xref.getXrefCardNumber(), 16)
                + digits(xref.getXrefCustId(), 9)
                + digits(xref.getXrefAcctId(), 11)
                + " ".repeat(14);
    }

    /**
     * OUT-ACCT-REC builder for PSCOMP (CBACT01C.cbl:215-239).
     *
     * Quirk preserved (CBACT01C.cbl:236-238): OUT-ACCT-CURR-CYC-DEBIT is only
     * assigned when ACCT-CURR-CYC-DEBIT is zero — there is no unconditional
     * MOVE of the input value. For non-zero input the field keeps the bytes
     * the previous record left (or the program-start record area, binary
     * zeros — the FD area's initial content is unspecified: MEDIUM
     * confidence, FACT-pending). A null debit is treated as zero.
     */
    static final class PscompRecordBuilder {
        private String currCycDebit = "\0".repeat(7);

        String build(Account account, DateEditService dates) {
            String reissue = date10(account.getAcctReissueDate());
            DateEditService.DateEditResult edited = dates.edit(
                    new DateEditService.DateEditRequest('2', reissue, '2'));
            // MOVE CODATECN-0UT-DATE TO OUT-ACCT-REISSUE-DATE: first 10 bytes.
            String outReissue = edited.outDate().substring(0, 10);

            if (account.getAcctCurrCycDebit() == null
                    || account.getAcctCurrCycDebit().signum() == 0) {
                currCycDebit = packed(new BigDecimal("2525.00"), 10, 2);
            }
            return digits(account.getAcctId(), 11)
                    + text(account.getAcctActiveStatus(), 1)
                    + zoned(account.getAcctCurrBal(), 10, 2)
                    + zoned(account.getAcctCreditLimit(), 10, 2)
                    + zoned(account.getAcctCashCreditLimit(), 10, 2)
                    + date10(account.getAcctOpenDate())
                    + date10(account.getAcctExpirationDate())
                    + outReissue
                    + zoned(account.getAcctCurrCycCredit(), 10, 2)
                    + currCycDebit
                    + text(account.getAcctGroupId(), 10);
            // FACT-pending (S17-B7): the layout is exactly LRECL=107 with a
            // 7-byte COMP-3 field; BatchFileSupport.pad at the writer keeps
            // the record at JCL LRECL if a field ever drifts short.
        }
    }

    /**
     * ARR-ARRAY-REC for ARRYPS (CBACT01C.cbl:253-260). Only occurs 1-3 are
     * populated; occurs 4-5 keep the INITIALIZE zeros and ARR-FILLER stays
     * spaces. Layout is exactly LRECL=110 (7-byte COMP-3 occurs).
     */
    static String arrypsRecord(Account account) {
        StringBuilder record = new StringBuilder(digits(account.getAcctId(), 11));
        record.append(zoned(account.getAcctCurrBal(), 10, 2));
        record.append(packed(new BigDecimal("1005.00"), 10, 2));
        record.append(zoned(account.getAcctCurrBal(), 10, 2));
        record.append(packed(new BigDecimal("1525.00"), 10, 2));
        record.append(zoned(new BigDecimal("-1025.00"), 10, 2));
        record.append(packed(new BigDecimal("-2500.00"), 10, 2));
        record.append(zoned(BigDecimal.ZERO, 10, 2));
        record.append(packed(BigDecimal.ZERO, 10, 2));
        record.append(zoned(BigDecimal.ZERO, 10, 2));
        record.append(packed(BigDecimal.ZERO, 10, 2));
        return record.append("    ").toString();
    }

    /** VB1 record (12 bytes): VBRC-REC1 = acct id + status. */
    static String vb1Record(Account account) {
        return digits(account.getAcctId(), 11) + text(account.getAcctActiveStatus(), 1);
    }

    /**
     * VB2 record (39 bytes): id + curr-bal + credit-limit + reissue year.
     * The year is the first 4 chars of the source YYYY-MM-DD field, not the
     * COBDATFT output (CBACT01C.cbl:282).
     */
    static String vb2Record(Account account) {
        String reissueYear = date10(account.getAcctReissueDate()).substring(0, 4);
        return digits(account.getAcctId(), 11)
                + zoned(account.getAcctCurrBal(), 10, 2)
                + zoned(account.getAcctCreditLimit(), 10, 2)
                + reissueYear;
    }
}
