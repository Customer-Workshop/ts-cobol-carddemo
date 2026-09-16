package com.carddemo.service;

import com.carddemo.api.BillPaymentScreen;
import com.carddemo.api.CobolMessages;
import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.Transaction;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.TransactionRepository;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * COBIL00C PROCESS-ENTER-KEY (app/cbl/COBIL00C.cbl:154-244): the input
 * edits run before any file access, then the account read under the record
 * lock, the balance edits, and — for a confirmed payment — the whole
 * xref/browse/write/rewrite chain. Deviation D1: every post-confirm step
 * is blocking at first failure and the transaction insert + balance
 * rewrite commit atomically; the source falls through on failures.
 */
@Service
public class BillingService {

    // ACCT-ID is the as-typed 9(11) key: "123" does not match
    // "00000000123" (COBIL00C.cbl:170, S11 R3). A typed value that cannot
    // equal the %011d rendering of a bigint acct_id reads as NOTFND.
    private static final String ACCOUNT_KEY_MASK = "\\d{11}";

    // TRAN-AMT S9(09)V99 (COBIL00C.cbl:223): the balance move truncates the
    // high-order digits when |balance| >= 1e9; the residual stays on the
    // account (S11 R4).
    private static final BigDecimal TRAN_AMT_MODULUS = new BigDecimal("1000000000");

    private final AccountRepository accountRepository;
    private final CardXrefRepository cardXrefRepository;
    private final EntityManager entityManager;
    private final TransactionIdGenerator idGenerator;
    private final Clock clock;
    private final TransactionOperations transactions;

    public BillingService(AccountRepository accountRepository,
                          CardXrefRepository cardXrefRepository,
                          EntityManager entityManager,
                          TransactionIdGenerator idGenerator,
                          Clock clock,
                          PlatformTransactionManager transactionManager) {
        this.accountRepository = accountRepository;
        this.cardXrefRepository = cardXrefRepository;
        this.entityManager = entityManager;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * ENTER on the bill-payment map. {@code priorBalance} is the CURBAL
     * value the form carried back — it is echoed on every rejection that
     * happens before the account read, because CURBAL is FSET and RECEIVE
     * keeps it on screen (COBIL00C.cbl:44-56, :173-191).
     */
    public BillPaymentScreen enter(String accountId, String confirmation,
                                   String priorBalance) {
        String acct = accountId == null ? "" : accountId.trim();
        String confirm = confirmation == null ? "" : confirmation.trim();
        String curbal = priorBalance == null ? "" : priorBalance;

        if (acct.isEmpty()) {
            return reject(acct, curbal, confirm,
                    CobolMessages.BILL_ACCOUNT_EMPTY, "accountId");
        }
        if ("N".equalsIgnoreCase(confirm)) {
            // CONF-PAY-NO: CLEAR-CURRENT-SCREEN, no message, no file access.
            return BillPaymentScreen.blank();
        }
        boolean confirmed = "Y".equalsIgnoreCase(confirm);
        if (!confirmed && !confirm.isEmpty()) {
            return reject(acct, curbal, confirm,
                    CobolMessages.TRANSACTION_CONFIRM_INVALID, "confirmation");
        }
        return transactions.execute(status -> processEnter(
                status, acct, curbal, confirm, confirmed));
    }

    private BillPaymentScreen processEnter(
            org.springframework.transaction.TransactionStatus status, String acct,
            String curbal, String confirm, boolean confirmed) {
        Account account = null;
        if (acct.matches(ACCOUNT_KEY_MASK)) {
            try {
                account = accountRepository.findForUpdate(Long.parseLong(acct)).orElse(null);
            } catch (DataAccessException exception) {
                return reject(acct, curbal, confirm,
                        CobolMessages.BILL_ACCOUNT_LOOKUP_FAILED, "accountId");
            }
        }
        if (account == null) {
            return reject(acct, curbal, confirm,
                    CobolMessages.ACCOUNT_NOT_FOUND, "accountId");
        }
        BigDecimal balance = account.getAcctCurrBal();
        String shown = formatBalance(balance);
        if (balance == null || balance.signum() <= 0) {
            return reject(acct, shown, confirm,
                    CobolMessages.BILL_NOTHING_TO_PAY, "accountId");
        }
        if (!confirmed) {
            return new BillPaymentScreen(acct, shown, confirm,
                    CobolMessages.BILL_CONFIRM, null, "confirmation", null);
        }

        CardXref xref;
        try {
            xref = cardXrefRepository.findByXrefAcctId(account.getAcctId())
                    .stream().findFirst().orElse(null);
        } catch (DataAccessException exception) {
            return reject(acct, shown, confirm,
                    CobolMessages.BILL_XREF_LOOKUP_FAILED, "accountId");
        }
        if (xref == null) {
            return reject(acct, shown, confirm,
                    CobolMessages.ACCOUNT_NOT_FOUND, "accountId");
        }
        String tranId;
        try {
            tranId = idGenerator.nextId();
        } catch (DataAccessException exception) {
            return reject(acct, shown, confirm,
                    CobolMessages.TRANSACTION_ADD_LOOKUP_FAILED, "accountId");
        }
        BigDecimal amount = balance.remainder(TRAN_AMT_MODULUS);
        Transaction transaction = billPayment(tranId, xref, amount);
        // WRITE TRANSACT (S11-B1): a true INSERT — save() would merge() over
        // the existing key and the DUPREC path would never fire. The PK
        // constraint violation is the duplicate outcome; anything else is
        // the generic add failure.
        try {
            entityManager.persist(transaction);
            entityManager.flush();
        } catch (EntityExistsException | ConstraintViolationException exception) {
            status.setRollbackOnly();
            return reject(acct, shown, confirm,
                    CobolMessages.TRANSACTION_DUPLICATE, "accountId");
        } catch (PersistenceException | DataAccessException exception) {
            status.setRollbackOnly();
            return reject(acct, shown, confirm,
                    CobolMessages.BILL_TRANSACTION_ADD_FAILED, "accountId");
        }
        account.setAcctCurrBal(balance.subtract(amount));
        try {
            accountRepository.saveAndFlush(account);
        } catch (DataAccessException exception) {
            status.setRollbackOnly();
            return reject(acct, shown, confirm,
                    CobolMessages.BILL_ACCOUNT_UPDATE_FAILED, "accountId");
        }
        // INITIALIZE-ALL-FIELDS then the green success line (COBIL00C.cbl:
        // 522-531); cursor lands back on Acct ID.
        return new BillPaymentScreen("", "", "",
                CobolMessages.billPaymentSuccess(tranId), "info", "accountId", tranId);
    }

    private Transaction billPayment(String tranId, CardXref xref, BigDecimal amount) {
        Transaction transaction = new Transaction();
        transaction.setTranId(tranId);
        transaction.setTranTypeCode("02");
        transaction.setTranCategoryCode(2);
        transaction.setTranSource("POS TERM");
        transaction.setTranDescription("BILL PAYMENT - ONLINE");
        transaction.setTranAmount(amount);
        transaction.setTranCardNumber(xref.getXrefCardNumber());
        transaction.setTranMerchantId(999999999L);
        transaction.setTranMerchantName("BILL PAYMENT");
        transaction.setTranMerchantCity("N/A");
        transaction.setTranMerchantZip("N/A");
        // CICS ASKTIME/FORMATTIME: wall clock at second precision; the
        // MS6 slot is never populated so microseconds read 000000
        // (COBIL00C.cbl:249-267, S11-B6).
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        transaction.setTranOriginTimestamp(now);
        transaction.setTranProcessTimestamp(now);
        return transaction;
    }

    // CURBAL PIC +9999999999.99 (COBIL00C.cbl:56): explicit sign, ten
    // zero-filled integer digits, two decimals — 14 chars.
    static String formatBalance(BigDecimal value) {
        BigDecimal scaled = (value == null ? BigDecimal.ZERO : value)
                .setScale(2, RoundingMode.DOWN).abs();
        String digits = scaled.unscaledValue().toString();
        if (digits.length() > 12) {
            digits = digits.substring(digits.length() - 12);
        }
        digits = "0".repeat(12 - digits.length()) + digits;
        return ((value != null && value.signum() < 0) ? "-" : "+")
                + digits.substring(0, 10) + "." + digits.substring(10);
    }

    private BillPaymentScreen reject(String acct, String curbal, String confirm,
                                     String message, String cursorField) {
        return new BillPaymentScreen(acct, curbal, confirm, message, null, cursorField, null);
    }
}
