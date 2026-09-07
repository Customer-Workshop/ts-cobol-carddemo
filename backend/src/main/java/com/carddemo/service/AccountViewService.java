package com.carddemo.service;

import com.carddemo.api.AccountBlock;
import com.carddemo.api.AccountViewResponse;
import com.carddemo.api.CobolApiException;
import com.carddemo.api.CobolMessages;
import com.carddemo.api.CobolPicture;
import com.carddemo.api.CustomerBlock;
import com.carddemo.api.CustomerNotFoundException;
import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.Customer;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

import java.util.Optional;

/**
 * COACTVWC account view (app/cbl/COACTVWC.cbl). One service per program (target state §3).
 *
 * <p>Read-only: 2200-EDIT-MAP-INPUTS / 2210-EDIT-ACCOUNT (:628-681) then 9000-READ-ACCT
 * (:687-720) in paragraph order — xref by account (:723-771), account master (:774-821),
 * customer master (:825-870) — and finally 1200-SETUP-SCREEN-VARS (:459-534) as the response.
 * The pseudo-conversation and the {@code XCTL COMEN01C} of PF3 (:324-352) are the SPA's business
 * (B-0011, B-0028); the {@code SEND MAP} on entry with no read is the component's own initial state,
 * so this service is only ever called with a search value.
 */
@Service
public class AccountViewService {

    /** WS-TRANID / WS-PGMNAME, COACTVWC.cbl:143-145. */
    public static final String TRAN_ID = "CAVW";
    public static final String PROGRAM_NAME = "COACTVWC";

    /** ACCTSID is LENGTH=11, PICIN '99999999999' (app/bms/COACTVW.bms:66-72). */
    private static final int ACCOUNT_ID_DIGITS = 11;
    private static final int CUSTOMER_ID_DIGITS = 9;

    private final CardXrefRepository xrefs;
    private final AccountRepository accounts;
    private final CustomerRepository customers;
    private final ScreenHeaderService headerService;

    public AccountViewService(
            CardXrefRepository xrefs,
            AccountRepository accounts,
            CustomerRepository customers,
            ScreenHeaderService headerService) {
        this.xrefs = xrefs;
        this.accounts = accounts;
        this.customers = customers;
        this.headerService = headerService;
    }

    /**
     * FR-13..FR-21. The raw ACCTSIDI bytes in, the painted map out, or a {@link CobolApiException}
     * carrying the verbatim WS-RETURN-MSG text of the branch that stopped the flow.
     *
     * <p>Not {@code @Transactional}: each read runs in its own transaction so that a datastore that
     * cannot even open one surfaces as this program's own E-12 text rather than escaping the proxy
     * (D-0040).
     */
    public AccountViewResponse view(String rawAccountId) {
        String accountId = editAccount(rawAccountId);
        long accountKey = Long.parseLong(accountId);

        CardXref xref = read(() -> xrefs.findFirstByXrefAcctIdOrderByXrefCardNumberAsc(accountKey),
                CobolMessages.FILE_CXACAIX)
                .orElseThrow(() -> new CobolApiException(HttpStatus.NOT_FOUND, CobolMessages.xrefNotFound(
                        accountId, CobolMessages.RESP_NOTFND, CobolMessages.REAS_NONE)));

        Account account = read(() -> accounts.findById(accountKey), CobolMessages.FILE_ACCTDAT)
                .orElseThrow(() -> new CobolApiException(HttpStatus.NOT_FOUND, CobolMessages.accountNotFound(
                        accountId, CobolMessages.RESP_NOTFND, CobolMessages.REAS_NONE)));

        String customerId = CobolPicture.digits(xref.getXrefCustId(), CUSTOMER_ID_DIGITS);
        Customer customer = read(() -> customers.findById(xref.getXrefCustId()), CobolMessages.FILE_CUSTDAT)
                .orElseThrow(() -> new CustomerNotFoundException(
                        CobolMessages.customerNotFound(
                                customerId, CobolMessages.RESP_NOTFND, CobolMessages.REAS_NONE),
                        accountId,
                        accountBlock(account)));

        return new AccountViewResponse(
                headerService.header(TRAN_ID, PROGRAM_NAME),
                accountId,
                CobolMessages.ACCOUNT_VIEW_PROMPT,
                accountBlock(account),
                customerBlock(customer));
    }

    /**
     * 2210-EDIT-ACCOUNT (COACTVWC.cbl:640-681) in source order: '*' and spaces are "not supplied"
     * (:640-645) and the cross-field edit of :628-633 overwrites the message with E-04; anything not
     * eleven numeric digits or all zeroes is E-05 (:666-677, Q-02). Returns the 11-digit key.
     */
    private static String editAccount(String rawAccountId) {
        String value = rawAccountId == null ? "" : rawAccountId.trim();
        if (value.isEmpty() || "*".equals(value)) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST, CobolMessages.NO_INPUT_RECEIVED);
        }
        if (!value.matches("\\d{" + ACCOUNT_ID_DIGITS + "}") || value.chars().allMatch(digit -> digit == '0')) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST, CobolMessages.ACCOUNT_FILTER_INVALID);
        }
        return value;
    }

    /**
     * The {@code WHEN OTHER} arm every read shares (COACTVWC.cbl:759-770, :808-819, :858-868):
     * a failure that is not NOTFND becomes E-12 naming the operation and the file (D-0040).
     */
    private <T> Optional<T> read(java.util.function.Supplier<Optional<T>> operation, String fileName) {
        try {
            return operation.get();
        } catch (DataAccessException | TransactionException ex) {
            throw new CobolApiException(HttpStatus.INTERNAL_SERVER_ERROR, CobolMessages.fileError(
                    CobolMessages.OP_READ, fileName, CobolMessages.RESP_OTHER, CobolMessages.REAS_NONE));
        }
    }

    /** COACTVWC.cbl:471-490; ACCT-GROUP-ID is shown as stored (DV-06 / D-0038). */
    private static AccountBlock accountBlock(Account account) {
        return new AccountBlock(
                CobolPicture.text(account.getAcctActiveStatus()),
                CobolPicture.date(account.getAcctOpenDate()),
                CobolPicture.money(account.getAcctCreditLimit()),
                CobolPicture.date(account.getAcctExpiraionDate()),
                CobolPicture.money(account.getAcctCashCreditLimit()),
                CobolPicture.date(account.getAcctReissueDate()),
                CobolPicture.money(account.getAcctCurrBal()),
                CobolPicture.money(account.getAcctCurrCycCredit()),
                CobolPicture.text(account.getAcctGroupId()),
                CobolPicture.money(account.getAcctCurrCycDebit()));
    }

    /** COACTVWC.cbl:493-523; ZIP and both phone numbers are complete values (DV-05). */
    private static CustomerBlock customerBlock(Customer customer) {
        return new CustomerBlock(
                CobolPicture.digits(customer.getCustId(), CUSTOMER_ID_DIGITS),
                CobolPicture.ssn(customer.getCustSsn()),
                CobolPicture.date(customer.getCustDobYyyyMmDd()),
                customer.getCustFicoCreditScore(),
                CobolPicture.text(customer.getCustFirstName()),
                CobolPicture.text(customer.getCustMiddleName()),
                CobolPicture.text(customer.getCustLastName()),
                CobolPicture.text(customer.getCustAddrLine1()),
                CobolPicture.text(customer.getCustAddrLine2()),
                CobolPicture.text(customer.getCustAddrLine3()),
                CobolPicture.text(customer.getCustAddrStateCd()),
                CobolPicture.text(customer.getCustAddrZip()),
                CobolPicture.text(customer.getCustAddrCountryCd()),
                CobolPicture.text(customer.getCustPhoneNum1()),
                CobolPicture.text(customer.getCustPhoneNum2()),
                CobolPicture.text(customer.getCustGovtIssuedId()),
                CobolPicture.text(customer.getCustEftAccountId()),
                CobolPicture.text(customer.getCustPriCardHolderInd()));
    }
}
