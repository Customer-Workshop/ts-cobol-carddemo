package com.carddemo.service;

import com.carddemo.api.CobolApiException;
import com.carddemo.api.TransactionAddScreen;
import com.carddemo.api.TransactionCreateRequest;
import com.carddemo.model.CardXref;
import com.carddemo.model.Transaction;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * COTRN02C processing (S-09): the FR-S09-02..28 edit/confirm/write matrix,
 * derived from COTRN02C.cbl and its FR doc. Xref and transaction stores are
 * mocked at the S09-B2/B3 boundary seams; the CSUTLDTC port runs for real
 * (leaf-first). Test names carry the FR row each covers.
 */
class TransactionAddServiceTest {

    private static final String ACCT = "00000000001";
    private static final String CARD = "1111222233334444";

    private TransactionRepository transactions;
    private CardXrefRepository xrefs;
    private TransactionService service;

    @BeforeEach
    void setUp() {
        transactions = mock(TransactionRepository.class);
        xrefs = mock(CardXrefRepository.class);
        service = new TransactionService(transactions, xrefs,
                new DateValidationService(), new TransactionIdGenerator(transactions));
        stubAccountXref();
    }

    private void stubAccountXref() {
        CardXref xref = new CardXref();
        xref.setXrefCardNumber(CARD);
        xref.setXrefAcctId(1L);
        when(xrefs.findByXrefAcctId(1L)).thenReturn(List.of(xref));
        when(xrefs.findById(CARD)).thenReturn(Optional.of(xref));
    }

    /** All fourteen fields valid; confirmation is the caller's choice. */
    private TransactionCreateRequest valid(String confirmation) {
        return new TransactionCreateRequest(ACCT, null, "01", "0001", "POS TERM",
                "Test purchase", "+00000100.50", "2024-01-15", "2024-01-16",
                "000000001", "Merchant", "Boston", "02108", confirmation);
    }

    @Test
    void bothKeysBlankYieldsKeyRequired_frS0902() {
        TransactionAddScreen screen = service.enter(new TransactionCreateRequest(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null));

        assertThat(screen.message()).isEqualTo("Account or Card Number must be entered...");
        assertThat(screen.cursorField()).isEqualTo("accountId");
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test
    void shortAccountYieldsAccountNumeric_frS0903() {
        // "123" pads to "123        " and the padded field fails NUMERIC.
        TransactionAddScreen screen = service.enter(new TransactionCreateRequest(
                "123", null, null, null, null, null, null, null, null,
                null, null, null, null, null));

        assertThat(screen.message()).isEqualTo("Account ID must be Numeric...");
        assertThat(screen.cursorField()).isEqualTo("accountId");
    }

    @Test
    void accountResolutionFillsCard_frS0904() {
        TransactionAddScreen screen = service.enter(valid(null));

        assertThat(screen.cardNumber()).isEqualTo(CARD);
        // Resolution succeeded; the edits ran and hit the confirm prompt.
        assertThat(screen.message()).isEqualTo("Confirm to add this transaction...");
    }

    @Test
    void accountPathWinsOverTypedCard_frS0904() {
        // COTRN02C.cbl:196 — the account branch runs whenever ACTIDINI is
        // non-blank, so a typed card is replaced by the xref card.
        TransactionAddScreen screen = service.enter(new TransactionCreateRequest(
                ACCT, "9999999999999999", "01", "0001", "POS TERM", "Test purchase",
                "+00000100.50", "2024-01-15", "2024-01-16", "000000001",
                "Merchant", "Boston", "02108", null));

        assertThat(screen.cardNumber()).isEqualTo(CARD);
    }

    @Test
    void unknownAccountYieldsNotFound_frS0905() {
        when(xrefs.findByXrefAcctId(99999999999L)).thenReturn(List.of());

        TransactionAddScreen screen = service.enter(new TransactionCreateRequest(
                "99999999999", null, null, null, null, null, null, null, null,
                null, null, null, null, null));

        assertThat(screen.message()).isEqualTo("Account ID NOT found...");
        assertThat(screen.cursorField()).isEqualTo("accountId");
    }

    @Test
    void accountLookupErrorYieldsUnableToLookup_frS0906() {
        when(xrefs.findByXrefAcctId(1L))
                .thenThrow(new DataAccessException("store down") {
                });

        TransactionAddScreen screen = service.enter(new TransactionCreateRequest(
                ACCT, null, null, null, null, null, null, null, null,
                null, null, null, null, null));

        assertThat(screen.message()).isEqualTo("Unable to lookup Acct in XREF AIX file...");
        assertThat(screen.cursorField()).isEqualTo("accountId");
    }

    @Test
    void nonNumericCardYieldsCardNumeric_frS0907() {
        TransactionAddScreen screen = service.enter(new TransactionCreateRequest(
                null, "12AB", null, null, null, null, null, null, null,
                null, null, null, null, null));

        assertThat(screen.message()).isEqualTo("Card Number must be Numeric...");
        assertThat(screen.cursorField()).isEqualTo("cardNumber");
    }

    @Test
    void cardResolutionFillsAccount_frS0908() {
        TransactionAddScreen screen = service.enter(new TransactionCreateRequest(
                null, CARD, "01", "0001", "POS TERM", "Test purchase",
                "+00000100.50", "2024-01-15", "2024-01-16", "000000001",
                "Merchant", "Boston", "02108", null));

        assertThat(screen.accountId()).isEqualTo(ACCT);
        assertThat(screen.message()).isEqualTo("Confirm to add this transaction...");
    }

    @Test
    void unknownCardYieldsNotFound_frS0909() {
        when(xrefs.findById("9999999999999999")).thenReturn(Optional.empty());

        TransactionAddScreen screen = service.enter(new TransactionCreateRequest(
                null, "9999999999999999", null, null, null, null, null, null, null,
                null, null, null, null, null));

        assertThat(screen.message()).isEqualTo("Card Number NOT found...");
        assertThat(screen.cursorField()).isEqualTo("cardNumber");
    }

    @Test
    void cardLookupErrorYieldsUnableToLookup_frS0910() {
        when(xrefs.findById(CARD)).thenThrow(new DataAccessException("store down") {
        });

        TransactionAddScreen screen = service.enter(new TransactionCreateRequest(
                null, CARD, null, null, null, null, null, null, null,
                null, null, null, null, null));

        assertThat(screen.message()).isEqualTo("Unable to lookup Card # in XREF file...");
        assertThat(screen.cursorField()).isEqualTo("cardNumber");
    }

    @Test
    void blankDataFieldsFailInScreenOrder_frS0911() {
        // COTRN02C.cbl:251-320 — the eleven mandatory edits in order; filling
        // fields one by one must surface each message in turn.
        String[][] fields = {
                {"transactionTypeCode", "01", "Type CD can NOT be empty..."},
                {"transactionCategoryCode", "0001", "Category CD can NOT be empty..."},
                {"source", "POS TERM", "Source can NOT be empty..."},
                {"description", "Test purchase", "Description can NOT be empty..."},
                {"amount", "+00000100.50", "Amount can NOT be empty..."},
                {"originDate", "2024-01-15", "Orig Date can NOT be empty..."},
                {"processDate", "2024-01-16", "Proc Date can NOT be empty..."},
                {"merchantId", "000000001", "Merchant ID can NOT be empty..."},
                {"merchantName", "Merchant", "Merchant Name can NOT be empty..."},
                {"merchantCity", "Boston", "Merchant City can NOT be empty..."},
                {"merchantZip", "02108", "Merchant Zip can NOT be empty..."}
        };
        String[] values = new String[11];
        for (int step = 0; step < fields.length; step++) {
            TransactionCreateRequest request = new TransactionCreateRequest(
                    ACCT, null, values[0], values[1], values[2], values[3], values[4],
                    values[5], values[6], values[7], values[8], values[9], values[10], null);
            TransactionAddScreen screen = service.enter(request);
            assertThat(screen.message()).isEqualTo(fields[step][2]);
            assertThat(screen.cursorField()).isEqualTo(fields[step][0]);
            values[step] = fields[step][1];
        }
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test
    void nonDigitTypeYieldsTypeNumeric_frS0912() {
        for (String type : new String[] {"AB", "1"}) {
            TransactionAddScreen screen = service.enter(new TransactionCreateRequest(
                    ACCT, null, type, "0001", "POS TERM", "Test purchase",
                    "+00000100.50", "2024-01-15", "2024-01-16", "000000001",
                    "Merchant", "Boston", "02108", null));
            assertThat(screen.message()).isEqualTo("Type CD must be Numeric...");
            assertThat(screen.cursorField()).isEqualTo("transactionTypeCode");
        }
    }

    @Test
    void shortCategoryYieldsCategoryNumeric_frS0912() {
        // "1" pads to "1   " and the padded field fails NUMERIC.
        TransactionAddScreen screen = service.enter(new TransactionCreateRequest(
                ACCT, null, "01", "1", "POS TERM", "Test purchase",
                "+00000100.50", "2024-01-15", "2024-01-16", "000000001",
                "Merchant", "Boston", "02108", null));

        assertThat(screen.message()).isEqualTo("Category CD must be Numeric...");
        assertThat(screen.cursorField()).isEqualTo("transactionCategoryCode");
    }

    @Test
    void malformedAmountYieldsFormatMessage_frS0913() {
        for (String amount : new String[] {"100.00", "-100.00", "+00000100,00", "-0000A100.50"}) {
            TransactionAddScreen screen = service.enter(new TransactionCreateRequest(
                    ACCT, null, "01", "0001", "POS TERM", "Test purchase",
                    amount, "2024-01-15", "2024-01-16", "000000001",
                    "Merchant", "Boston", "02108", null));
            assertThat(screen.message()).isEqualTo("Amount should be in format -99999999.99");
            assertThat(screen.cursorField()).isEqualTo("amount");
        }
    }

    @Test
    void overWidthAmountIsRejectedAtTheApiEdge_frS0913() {
        // "+1234567890.00" is 13 bytes — impossible on the 3270 (D-3).
        assertThatThrownBy(() -> service.enter(new TransactionCreateRequest(
                ACCT, null, "01", "0001", "POS TERM", "Test purchase",
                "+1234567890.00", "2024-01-15", "2024-01-16", "000000001",
                "Merchant", "Boston", "02108", null)))
                .isInstanceOf(CobolApiException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void malformedDatesYieldLayoutMessage_frS0914() {
        TransactionAddScreen orig = service.enter(new TransactionCreateRequest(
                ACCT, null, "01", "0001", "POS TERM", "Test purchase",
                "+00000100.50", "2024/01/15", "2024-01-16", "000000001",
                "Merchant", "Boston", "02108", null));
        assertThat(orig.message()).isEqualTo("Orig Date should be in format YYYY-MM-DD");
        assertThat(orig.cursorField()).isEqualTo("originDate");

        TransactionAddScreen proc = service.enter(new TransactionCreateRequest(
                ACCT, null, "01", "0001", "POS TERM", "Test purchase",
                "+00000100.50", "2024-01-15", "20240116", "000000001",
                "Merchant", "Boston", "02108", null));
        assertThat(proc.message()).isEqualTo("Proc Date should be in format YYYY-MM-DD");
        assertThat(proc.cursorField()).isEqualTo("processDate");
    }

    @Test
    void validAmountEchoesInPaddedNumericForm_frS0915() {
        // "-00000100.50" passes the layout edit; a later failure redisplays
        // the screen with the amount already in +99999999.99 form.
        TransactionAddScreen screen = service.enter(new TransactionCreateRequest(
                ACCT, null, "01", "0001", "POS TERM", "Test purchase",
                "-00000100.50", "2024-02-30", "2024-01-16", "000000001",
                "Merchant", "Boston", "02108", null));

        assertThat(screen.message()).isEqualTo("Orig Date - Not a valid date...");
        assertThat(screen.amount()).isEqualTo("-00000100.50");

        TransactionAddScreen bad = service.enter(new TransactionCreateRequest(
                ACCT, null, "01", "0001", "POS TERM", "Test purchase",
                "+00000100.5x", "2024-01-15", "2024-01-16", "000000001",
                "Merchant", "Boston", "02108", null));
        assertThat(bad.message()).isEqualTo("Amount should be in format -99999999.99");
    }

    @Test
    void csutldtcRejectsStructurallyBadDates_frS0916() {
        for (String orig : new String[] {"2024-02-30", "2024-13-01"}) {
            TransactionAddScreen screen = service.enter(new TransactionCreateRequest(
                    ACCT, null, "01", "0001", "POS TERM", "Test purchase",
                    "+00000100.50", orig, "2024-01-16", "000000001",
                    "Merchant", "Boston", "02108", null));
            assertThat(screen.message()).isEqualTo("Orig Date - Not a valid date...");
            assertThat(screen.cursorField()).isEqualTo("originDate");
        }

        TransactionAddScreen proc = service.enter(new TransactionCreateRequest(
                ACCT, null, "01", "0001", "POS TERM", "Test purchase",
                "+00000100.50", "2024-01-15", "2024-00-10", "000000001",
                "Merchant", "Boston", "02108", null));
        assertThat(proc.message()).isEqualTo("Proc Date - Not a valid date...");
        assertThat(proc.cursorField()).isEqualTo("processDate");
    }

    @Test
    void preLillianDateIsAccepted_frS0932() {
        // CSUTLDTC answers 2513 and COTRN02C deliberately accepts it
        // (COTRN02C.cbl:400, 420).
        TransactionAddScreen screen = service.enter(new TransactionCreateRequest(
                ACCT, null, "01", "0001", "POS TERM", "Test purchase",
                "+00000100.50", "1500-01-01", "2024-01-16", "000000001",
                "Merchant", "Boston", "02108", null));

        assertThat(screen.message()).isEqualTo("Confirm to add this transaction...");
    }

    @Test
    void yearZeroDateIsRejected_frS0932() {
        // Year 0000 clears the 2513 exemption in the source but cannot be
        // stored in the target (D-2).
        TransactionAddScreen screen = service.enter(new TransactionCreateRequest(
                ACCT, null, "01", "0001", "POS TERM", "Test purchase",
                "+00000100.50", "0000-01-01", "2024-01-16", "000000001",
                "Merchant", "Boston", "02108", null));

        assertThat(screen.message()).isEqualTo("Orig Date - Not a valid date...");
    }

    @Test
    void nonDigitMerchantIdYieldsMerchantNumeric_frS0917() {
        TransactionAddScreen screen = service.enter(new TransactionCreateRequest(
                ACCT, null, "01", "0001", "POS TERM", "Test purchase",
                "+00000100.50", "2024-01-15", "2024-01-16", "12345678A",
                "Merchant", "Boston", "02108", null));

        assertThat(screen.message()).isEqualTo("Merchant ID must be Numeric...");
        assertThat(screen.cursorField()).isEqualTo("merchantId");
    }

    @Test
    void blankOrNConfirmPromptsAndKeepsValues_frS0918() {
        for (String confirm : new String[] {null, "", "N", "n"}) {
            TransactionAddScreen screen = service.enter(valid(confirm));

            assertThat(screen.message()).isEqualTo("Confirm to add this transaction...");
            assertThat(screen.cursorField()).isEqualTo("confirmation");
            assertThat(screen.source()).isEqualTo("POS TERM");
            assertThat(screen.amount()).isEqualTo("+00000100.50");
        }
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test
    void otherConfirmValueYieldsInvalidValue_frS0919() {
        TransactionAddScreen screen = service.enter(valid("X"));

        assertThat(screen.message()).isEqualTo("Invalid value. Valid values are (Y/N)...");
        assertThat(screen.cursorField()).isEqualTo("confirmation");
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test
    void confirmedAddWritesNextSequentialId_frS0920() {
        Transaction last = new Transaction();
        last.setTranId("0000000000000001");
        when(transactions.findTopByOrderByTranIdDesc()).thenReturn(last);

        TransactionAddScreen screen = service.enter(valid("Y"));

        ArgumentCaptor<Transaction> written = ArgumentCaptor.forClass(Transaction.class);
        verify(transactions).saveAndFlush(written.capture());
        Transaction row = written.getValue();
        assertThat(row.getTranId()).isEqualTo("0000000000000002");
        assertThat(row.getTranCardNumber()).isEqualTo(CARD);
        assertThat(row.getTranTypeCode()).isEqualTo("01");
        assertThat(row.getTranCategoryCode()).isEqualTo(1);
        assertThat(row.getTranSource()).isEqualTo("POS TERM");
        assertThat(row.getTranDescription()).isEqualTo("Test purchase");
        assertThat(row.getTranAmount()).isEqualByComparingTo(new BigDecimal("100.50"));
        assertThat(row.getTranOriginTimestamp()).isEqualTo(LocalDateTime.of(2024, 1, 15, 0, 0));
        assertThat(row.getTranProcessTimestamp()).isEqualTo(LocalDateTime.of(2024, 1, 16, 0, 0));
        assertThat(row.getTranMerchantId()).isEqualTo(1L);
        assertThat(row.getTranMerchantName()).isEqualTo("Merchant");
        assertThat(row.getTranMerchantCity()).isEqualTo("Boston");
        assertThat(row.getTranMerchantZip()).isEqualTo("02108");
        assertThat(screen.transactionId()).isEqualTo("0000000000000002");
    }

    @Test
    void confirmedAddOnEmptyFileWritesIdOne_frS0920() {
        when(transactions.findTopByOrderByTranIdDesc()).thenReturn(null);

        service.enter(valid("y"));

        ArgumentCaptor<Transaction> written = ArgumentCaptor.forClass(Transaction.class);
        verify(transactions).saveAndFlush(written.capture());
        assertThat(written.getValue().getTranId()).isEqualTo("0000000000000001");
    }

    @Test
    void successfulWriteClearsFieldsAndShowsGreenMessage_frS0921() {
        Transaction last = new Transaction();
        last.setTranId("0000000000000001");
        when(transactions.findTopByOrderByTranIdDesc()).thenReturn(last);

        TransactionAddScreen screen = service.enter(valid("Y"));

        assertThat(screen.message())
                .isEqualTo("Transaction added successfully.  Your Tran ID is 0000000000000002.");
        assertThat(screen.messageStyle()).isEqualTo("info");
        assertThat(screen.cursorField()).isEqualTo("accountId");
        assertThat(screen.accountId()).isEmpty();
        assertThat(screen.cardNumber()).isEmpty();
        assertThat(screen.transactionTypeCode()).isEmpty();
        assertThat(screen.transactionCategoryCode()).isEmpty();
        assertThat(screen.source()).isEmpty();
        assertThat(screen.description()).isEmpty();
        assertThat(screen.amount()).isEmpty();
        assertThat(screen.originDate()).isEmpty();
        assertThat(screen.processDate()).isEmpty();
        assertThat(screen.merchantId()).isEmpty();
        assertThat(screen.merchantName()).isEmpty();
        assertThat(screen.merchantCity()).isEmpty();
        assertThat(screen.merchantZip()).isEmpty();
        assertThat(screen.confirmation()).isEmpty();
    }

    @Test
    void duplicateKeyYieldsAlreadyExists_frS0922() {
        Transaction last = new Transaction();
        last.setTranId("0000000000000001");
        when(transactions.findTopByOrderByTranIdDesc()).thenReturn(last);
        when(transactions.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("dup"));

        TransactionAddScreen screen = service.enter(valid("Y"));

        assertThat(screen.message()).isEqualTo("Tran ID already exist...");
        assertThat(screen.cursorField()).isEqualTo("accountId");
        assertThat(screen.source()).isEqualTo("POS TERM");
    }

    @Test
    void otherWriteErrorYieldsUnableToAdd_frS0923() {
        Transaction last = new Transaction();
        last.setTranId("0000000000000001");
        when(transactions.findTopByOrderByTranIdDesc()).thenReturn(last);
        when(transactions.saveAndFlush(any()))
                .thenThrow(new DataAccessException("write failed") {
                });

        TransactionAddScreen screen = service.enter(valid("Y"));

        assertThat(screen.message()).isEqualTo("Unable to Add Transaction...");
        assertThat(screen.cursorField()).isEqualTo("accountId");
    }

    @Test
    void browseLastErrorYieldsUnableToLookup_frS0924() {
        when(transactions.findTopByOrderByTranIdDesc())
                .thenThrow(new DataAccessException("store down") {
                });

        TransactionAddScreen screen = service.enter(valid("Y"));

        assertThat(screen.message()).isEqualTo("Unable to lookup Transaction...");
        assertThat(screen.cursorField()).isEqualTo("accountId");
    }

    @Test
    void copyLastFillsDataFieldsFromHighestId_frS0927() {
        Transaction last = seededTransaction();
        when(transactions.findTopByOrderByTranIdDesc()).thenReturn(last);

        TransactionAddScreen screen = service.copyLast(new TransactionCreateRequest(
                ACCT, null, null, null, null, null, null, null, null,
                null, null, null, null, null));

        assertThat(screen.transactionTypeCode()).isEqualTo("01");
        assertThat(screen.transactionCategoryCode()).isEqualTo("0001");
        assertThat(screen.source()).isEqualTo("POS TERM");
        assertThat(screen.description()).isEqualTo("Test purchase");
        assertThat(screen.amount()).isEqualTo("+00000001.23");
        assertThat(screen.originDate()).isEqualTo("2022-06-10");
        assertThat(screen.processDate()).isEqualTo("2022-06-10");
        assertThat(screen.merchantId()).isEqualTo("000000001");
        assertThat(screen.merchantName()).isEqualTo("Merchant");
        assertThat(screen.merchantCity()).isEqualTo("Boston");
        assertThat(screen.merchantZip()).isEqualTo("02108");
        // Key fields and confirmation keep the typed values.
        assertThat(screen.accountId()).isEqualTo(ACCT);
        assertThat(screen.cardNumber()).isEqualTo(CARD);
        assertThat(screen.confirmation()).isEmpty();
        // The ENTER tail then runs and lands on the confirm prompt.
        assertThat(screen.message()).isEqualTo("Confirm to add this transaction...");
    }

    @Test
    void copyLastTruncatesToScreenWidths_frS0927() {
        Transaction last = seededTransaction();
        last.setTranDescription("D".repeat(100));
        last.setTranMerchantName("N".repeat(50));
        last.setTranMerchantCity("C".repeat(50));
        when(transactions.findTopByOrderByTranIdDesc()).thenReturn(last);

        TransactionAddScreen screen = service.copyLast(new TransactionCreateRequest(
                ACCT, null, null, null, null, null, null, null, null,
                null, null, null, null, null));

        assertThat(screen.description()).isEqualTo("D".repeat(60));
        assertThat(screen.merchantName()).isEqualTo("N".repeat(30));
        assertThat(screen.merchantCity()).isEqualTo("C".repeat(25));
    }

    @Test
    void copyLastOnEmptyFileYieldsTypeRequired_frS0928() {
        when(transactions.findTopByOrderByTranIdDesc()).thenReturn(null);

        TransactionAddScreen screen = service.copyLast(new TransactionCreateRequest(
                ACCT, null, null, null, null, null, null, null, null,
                null, null, null, null, null));

        assertThat(screen.message()).isEqualTo("Type CD can NOT be empty...");
    }

    @Test
    void copyLastWithConfirmYWritesImmediately_frS0927() {
        Transaction last = seededTransaction();
        when(transactions.findTopByOrderByTranIdDesc()).thenReturn(last);
        when(transactions.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        TransactionAddScreen screen = service.copyLast(new TransactionCreateRequest(
                ACCT, null, null, null, null, null, null, null, null,
                null, null, null, null, "Y"));

        ArgumentCaptor<Transaction> written = ArgumentCaptor.forClass(Transaction.class);
        verify(transactions).saveAndFlush(written.capture());
        // The copied fields, not blanks, land in the new row.
        assertThat(written.getValue().getTranId()).isEqualTo("0000000000000002");
        assertThat(written.getValue().getTranAmount())
                .isEqualByComparingTo(new BigDecimal("1.23"));
        assertThat(screen.message())
                .isEqualTo("Transaction added successfully.  Your Tran ID is 0000000000000002.");
    }

    private Transaction seededTransaction() {
        Transaction last = new Transaction();
        last.setTranId("0000000000000001");
        last.setTranTypeCode("01");
        last.setTranCategoryCode(1);
        last.setTranSource("POS TERM");
        last.setTranDescription("Test purchase");
        last.setTranAmount(new BigDecimal("1.23"));
        last.setTranMerchantId(1L);
        last.setTranMerchantName("Merchant");
        last.setTranMerchantCity("Boston");
        last.setTranMerchantZip("02108");
        last.setTranCardNumber(CARD);
        last.setTranOriginTimestamp(LocalDateTime.of(2022, 6, 10, 19, 27, 53));
        last.setTranProcessTimestamp(LocalDateTime.of(2022, 6, 10, 19, 27, 53));
        return last;
    }
}
