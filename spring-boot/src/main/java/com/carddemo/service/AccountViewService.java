package com.carddemo.service;

import com.carddemo.api.AccountViewResponse;
import com.carddemo.api.CobolApiException;
import com.carddemo.api.CobolMessages;
import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.Customer;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * COACTVWC (transaction CAVW) — view an account plus its customer.
 *
 * One resolution path (edit + the three keyed reads, cbl:622-715) feeds the
 * REST surface ({@link #view}) and the Thymeleaf screen surface
 * ({@link #viewScreen}, {@link #initialScreen}). Source quirks reproduced:
 * the X(11) account field truncates input before the edits, blank/`*` is a
 * distinct no-input path (cbl:628-633, :653-661), and each file's non-NOTFND
 * RESP renders the fixed WS-FILE-ERROR-MESSAGE template (S02-B2).
 *
 * Not reproduced: cbl:704 exits on DID-NOT-FIND-ACCT-IN-ACCTDAT, whose only
 * SET is commented out (:792), so a real ACCTDAT NOTFND would still run the
 * customer read. The FR contract pins the observable outcome — "no data" on
 * account-not-found (FR-S02-05) — so the resolution stops there.
 */
@Service
public class AccountViewService {
    private static final String FILE_XREF = "CXACAIX";
    private static final String FILE_ACCOUNT = "ACCTDAT";
    private static final String FILE_CUSTOMER = "CUSTDAT";

    private final CardXrefRepository cardXrefRepository;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    public AccountViewService(CardXrefRepository cardXrefRepository, AccountRepository accountRepository,
                              CustomerRepository customerRepository) {
        this.cardXrefRepository = cardXrefRepository;
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
    }

    /** First display (cbl:353-360): empty field, fixed prompt, no data. */
    public AccountViewScreen initialScreen() {
        return new AccountViewScreen("", false, null, null, null);
    }

    /** Re-entry: edit + reads, returned as display-ready screen state. */
    public AccountViewScreen viewScreen(String rawAccountId) {
        Resolution r = resolve(rawAccountId);
        return switch (r.kind()) {
            case BLANK -> new AccountViewScreen("*", true,
                    CobolMessages.NO_INPUT_RECEIVED, null, null);
            case INVALID_FILTER -> new AccountViewScreen(r.echo(), true,
                    CobolMessages.ACCOUNT_FILTER_INVALID, null, null);
            case XREF_NOT_FOUND -> new AccountViewScreen(r.echo(), true,
                    CobolMessages.xrefNotFound(r.echo()), null, null);
            case ACCT_NOT_FOUND -> new AccountViewScreen(r.echo(), true,
                    CobolMessages.accountNotFound(r.echo()), null, null);
            case STORE_ERROR -> new AccountViewScreen(r.echo(), r.account() == null,
                    CobolMessages.fileError(r.errorFile()), toAccountBlock(r.account()), null);
            case CUST_NOT_FOUND -> new AccountViewScreen(r.echo(), false,
                    CobolMessages.customerNotFound("%09d".formatted(r.custId())),
                    toAccountBlock(r.account()), null);
            case OK -> new AccountViewScreen(r.echo(), false, null,
                    toAccountBlock(r.account()), toCustomerBlock(r.customer()));
        };
    }

    /** REST surface: same resolution, failures as HTTP-mapped exceptions. */
    public AccountViewResponse view(String rawAccountId) {
        Resolution r = resolve(rawAccountId);
        return switch (r.kind()) {
            case BLANK -> throw new CobolApiException(HttpStatus.BAD_REQUEST,
                    CobolMessages.NO_INPUT_RECEIVED);
            case INVALID_FILTER -> throw new CobolApiException(HttpStatus.BAD_REQUEST,
                    CobolMessages.ACCOUNT_FILTER_INVALID);
            case XREF_NOT_FOUND -> throw new CobolApiException(HttpStatus.NOT_FOUND,
                    CobolMessages.xrefNotFound(r.echo()));
            case ACCT_NOT_FOUND -> throw new CobolApiException(HttpStatus.NOT_FOUND,
                    CobolMessages.accountNotFound(r.echo()));
            case CUST_NOT_FOUND -> throw new CobolApiException(HttpStatus.NOT_FOUND,
                    CobolMessages.customerNotFound("%09d".formatted(r.custId())));
            case STORE_ERROR -> throw r.cause();
            case OK -> map(r.account(), r.customer());
        };
    }

    private enum Kind {
        BLANK, INVALID_FILTER, XREF_NOT_FOUND, ACCT_NOT_FOUND,
        CUST_NOT_FOUND, STORE_ERROR, OK
    }

    private record Resolution(Kind kind, String echo, Long custId, String errorFile,
                              RuntimeException cause, Account account, Customer customer) {
        static Resolution blank() {
            return new Resolution(Kind.BLANK, "*", null, null, null, null, null);
        }
        static Resolution invalid(String echo) {
            return new Resolution(Kind.INVALID_FILTER, echo, null, null, null, null, null);
        }
        static Resolution notFound(Kind kind, String echo) {
            return new Resolution(kind, echo, null, null, null, null, null);
        }
        static Resolution storeError(String file, String echo, RuntimeException cause, Account account) {
            return new Resolution(Kind.STORE_ERROR, echo, null, file, cause, account, null);
        }
        static Resolution custNotFound(String echo, Long custId, Account account) {
            return new Resolution(Kind.CUST_NOT_FOUND, echo, custId, null, null, account, null);
        }
        static Resolution ok(String echo, Account account, Customer customer) {
            return new Resolution(Kind.OK, echo, null, null, null, account, customer);
        }
    }

    // cbl:628-680 — the input arrives as an X(11) field: anything past 11
    // chars truncates before the edits, `*` + spaces and all-spaces are the
    // blank path, and the NUMERIC test demands exactly 11 digits, non-zero.
    private Resolution resolve(String rawAccountId) {
        String field = rawAccountId == null ? ""
                : rawAccountId.substring(0, Math.min(rawAccountId.length(), 11));
        if (field.isBlank() || (field.startsWith("*") && field.substring(1).isBlank())) {
            return Resolution.blank();
        }
        String echo = field.replaceAll("\\s+$", "");
        if (!field.matches("\\d{11}") || field.equals("00000000000")) {
            return Resolution.invalid(echo);
        }
        long accountId = Long.parseLong(field);

        List<CardXref> xrefs;
        try {
            xrefs = cardXrefRepository.findByXrefAcctId(accountId);
        } catch (RuntimeException e) {
            return Resolution.storeError(FILE_XREF, field, e, null);
        }
        if (xrefs.isEmpty()) {
            return Resolution.notFound(Kind.XREF_NOT_FOUND, field);
        }

        Account account;
        try {
            account = accountRepository.findById(accountId).orElse(null);
        } catch (RuntimeException e) {
            return Resolution.storeError(FILE_ACCOUNT, field, e, null);
        }
        if (account == null) {
            return Resolution.notFound(Kind.ACCT_NOT_FOUND, field);
        }

        Long custId = xrefs.getFirst().getXrefCustId();
        Customer customer;
        try {
            customer = customerRepository.findById(custId).orElse(null);
        } catch (RuntimeException e) {
            return Resolution.storeError(FILE_CUSTOMER, field, e, account);
        }
        if (customer == null) {
            return Resolution.custNotFound(field, custId, account);
        }
        return Resolution.ok(field, account, customer);
    }

    // cbl:471-491 — account block, all fields display-edited to the map.
    private AccountViewScreen.AccountBlock toAccountBlock(Account account) {
        if (account == null) {
            return null;
        }
        return new AccountViewScreen.AccountBlock(
                CobolFormat.truncate(account.getAcctActiveStatus(), 1),
                date(account.getAcctOpenDate()),
                CobolFormat.editSignedAmount(account.getAcctCreditLimit()),
                date(account.getAcctExpirationDate()),
                CobolFormat.editSignedAmount(account.getAcctCashCreditLimit()),
                date(account.getAcctReissueDate()),
                CobolFormat.editSignedAmount(account.getAcctCurrBal()),
                CobolFormat.editSignedAmount(account.getAcctCurrCycCredit()),
                CobolFormat.truncate(account.getAcctGroupId(), 10),
                CobolFormat.editSignedAmount(account.getAcctCurrCycDebit()));
    }

    // cbl:493-523 — customer block, moved only when the customer read hit.
    private AccountViewScreen.CustomerBlock toCustomerBlock(Customer customer) {
        if (customer == null) {
            return null;
        }
        return new AccountViewScreen.CustomerBlock(
                customer.getCustId() == null ? "" : "%09d".formatted(customer.getCustId()),
                CobolFormat.ssn(customer.getCustSsn()),
                date(customer.getCustDob()),
                customer.getCustFicoCreditScore() == null ? ""
                        : "%03d".formatted(customer.getCustFicoCreditScore()),
                CobolFormat.truncate(customer.getCustFirstName(), 25),
                CobolFormat.truncate(customer.getCustMiddleName(), 25),
                CobolFormat.truncate(customer.getCustLastName(), 25),
                CobolFormat.truncate(customer.getCustAddrLine1(), 50),
                CobolFormat.truncate(customer.getCustAddrLine2(), 50),
                CobolFormat.truncate(customer.getCustAddrLine3(), 50),
                CobolFormat.truncate(customer.getCustAddrStateCode(), 2),
                CobolFormat.truncate(customer.getCustAddrZip(), 5),
                CobolFormat.truncate(customer.getCustAddrCountryCode(), 3),
                CobolFormat.truncate(customer.getCustPhoneNum1(), 13),
                CobolFormat.truncate(customer.getCustPhoneNum2(), 13),
                CobolFormat.truncate(customer.getCustGovernmentIssuedId(), 20),
                CobolFormat.truncate(customer.getCustEftAccountId(), 10),
                CobolFormat.truncate(customer.getCustPrimaryCardHolderIndicator(), 1));
    }

    private String date(java.time.LocalDate value) {
        return value == null ? "" : value.toString();
    }

    private AccountViewResponse map(Account account, Customer customer) {
        return new AccountViewResponse(account.getAcctId(), account.getAcctActiveStatus(),
                account.getAcctCurrBal(), account.getAcctCreditLimit(), account.getAcctCashCreditLimit(),
                account.getAcctOpenDate(), account.getAcctExpirationDate(), account.getAcctReissueDate(),
                account.getAcctCurrCycCredit(), account.getAcctCurrCycDebit(), account.getAcctGroupId(),
                customer.getCustId(), formatSsn(customer.getCustSsn()), customer.getCustDob(),
                customer.getCustFicoCreditScore(), customer.getCustFirstName(), customer.getCustMiddleName(),
                customer.getCustLastName(), customer.getCustAddrLine1(), customer.getCustAddrLine2(),
                customer.getCustAddrLine3(), customer.getCustAddrStateCode(), customer.getCustAddrZip(),
                customer.getCustAddrCountryCode(), customer.getCustPhoneNum1(), customer.getCustPhoneNum2(),
                customer.getCustGovernmentIssuedId(), customer.getCustEftAccountId(),
                customer.getCustPrimaryCardHolderIndicator());
    }

    private String formatSsn(Long ssn) {
        if (ssn == null) {
            return null;
        }
        String value = "%09d".formatted(ssn);
        return value.substring(0, 3) + "-" + value.substring(3, 5) + "-" + value.substring(5);
    }
}
