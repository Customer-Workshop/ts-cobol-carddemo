package com.carddemo.service;

import com.carddemo.model.Transaction;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * COTRN01C view-path unit tests: the verbatim keyed READ (S08-B1), the
 * detail-clear-before-read, the kept-details blank-id rejection, and the
 * +99999999.99 / first-ten / map-width display edits. Test names carry
 * the FR-S08 row each covers.
 */
class TransactionViewServiceTest {

    private TransactionRepository transactions;
    private TransactionService service;

    @BeforeEach
    void setUp() {
        transactions = mock(TransactionRepository.class);
        service = new TransactionService(transactions, mock(CardXrefRepository.class),
                new DateValidationService(), new TransactionIdGenerator(transactions));
    }

    @Test
    void openViewBlankShowsEmptyScreen_frS0802() {
        // null/absent, empty, all-blank and all-low-values selections all
        // show the empty map — LOW-VALUES arrive as spaces in an X(16)
        // field.
        for (TransactionViewScreen screen : new TransactionViewScreen[] {
                service.openView(null), service.openView(""),
                service.openView("   "), service.openView("")}) {
            assertThat(screen.tranIdIn()).isEmpty();
            assertThat(screen.message()).isNull();
            assertThat(screen.details()).isNull();
        }
        verifyNoInteractions(transactions);
    }

    @Test
    void openViewPreselectedRunsEnter_frS0803() {
        when(transactions.findById("0000000000683580"))
                .thenReturn(Optional.of(goldenRow()));
        TransactionViewScreen screen = service.openView("0000000000683580");
        assertThat(screen.message()).isNull();
        assertThat(screen.details()).isNotNull();
        assertThat(screen.details().tranId()).isEqualTo("0000000000683580");
    }

    @Test
    void blankIdRejectsAndKeepsDetails_frS0804() {
        TransactionViewScreen.Details displayed = details();
        for (String id : new String[] {"", "   ", " "}) {
            TransactionViewScreen screen = service.enterView(id, displayed);
            assertThat(screen.message())
                    .isEqualTo("Tran ID can NOT be empty...");
            assertThat(screen.details()).isSameAs(displayed);
        }
        verifyNoInteractions(transactions);
    }

    @Test
    void nonBlankIdClearsDetailsBeforeRead_frS0805() {
        when(transactions.findById("9999999999999999")).thenReturn(Optional.empty());
        TransactionViewScreen screen = service.enterView("9999999999999999", details());
        assertThat(screen.details()).isNull();
        assertThat(screen.message()).isEqualTo("Transaction ID NOT found...");
    }

    @Test
    void unknownIdNotFoundRetainsInput_frS0806() {
        when(transactions.findById("NOPE000000000001")).thenReturn(Optional.empty());
        TransactionViewScreen screen = service.enterView("NOPE000000000001", null);
        assertThat(screen.message()).isEqualTo("Transaction ID NOT found...");
        assertThat(screen.tranIdIn()).isEqualTo("NOPE000000000001");
        assertThat(screen.details()).isNull();
    }

    @Test
    void storeFailureShowsUnableToLookup_frS0807() {
        when(transactions.findById("0000000000000001"))
                .thenThrow(new RuntimeException("VSAM I/O"));
        TransactionViewScreen screen = service.enterView("0000000000000001", details());
        assertThat(screen.message()).isEqualTo("Unable to lookup Transaction...");
        assertThat(screen.details()).isNull();
    }

    @Test
    void goldenRowPopulatesEveryField_frS0808() {
        when(transactions.findById("0000000000683580"))
                .thenReturn(Optional.of(goldenRow()));
        TransactionViewScreen.Details details =
                service.enterView("0000000000683580", null).details();
        assertThat(details.tranId()).isEqualTo("0000000000683580");
        assertThat(details.cardNumber()).isEqualTo("4859452612877065");
        assertThat(details.typeCode()).isEqualTo("01");
        assertThat(details.categoryCode()).isEqualTo("0001");
        assertThat(details.source()).isEqualTo("POS TERM");
        assertThat(details.description()).isEqualTo("Purchase at Abshire-Lowe");
        assertThat(details.amount()).isEqualTo("+00000504.77");
        assertThat(details.originDate()).isEqualTo("2022-06-10");
        assertThat(details.processDate()).isEqualTo("");
        assertThat(details.merchantId()).isEqualTo("800000000");
        assertThat(details.merchantName()).isEqualTo("Abshire-Lowe");
        assertThat(details.merchantCity()).isEqualTo("North Enoshaven");
        assertThat(details.merchantZip()).isEqualTo("72112");
    }

    @Test
    void amountRunsThroughCobolEdit_frS0809() {
        assertThat(viewAmount(new BigDecimal("-919.00"))).isEqualTo("-00000919.00");
        // The ninth integer digit drops, as a MOVE into +99999999.99 does.
        assertThat(viewAmount(new BigDecimal("123456789.12"))).isEqualTo("+23456789.12");
        assertThat(viewAmount(BigDecimal.ZERO)).isEqualTo("+00000000.00");
    }

    @Test
    void timestampsKeepFirstTenCharacters_frS0810() {
        Transaction row = new Transaction();
        row.setTranId("0000000000000001");
        row.setTranOriginTimestamp(LocalDateTime.of(2022, 6, 10, 13, 45, 30, 123456789));
        row.setTranProcessTimestamp(LocalDateTime.of(2022, 7, 4, 23, 59, 59));
        when(transactions.findById("0000000000000001")).thenReturn(Optional.of(row));
        TransactionViewScreen.Details details =
                service.enterView("0000000000000001", null).details();
        assertThat(details.originDate()).isEqualTo("2022-06-10");
        assertThat(details.processDate()).isEqualTo("2022-07-04");
    }

    @Test
    void longTextTruncatesToMapWidths_frS0811() {
        Transaction row = new Transaction();
        row.setTranId("0000000000000002");
        row.setTranDescription("D".repeat(100));
        row.setTranMerchantName("N".repeat(50));
        row.setTranMerchantCity("C".repeat(50));
        when(transactions.findById("0000000000000002")).thenReturn(Optional.of(row));
        TransactionViewScreen.Details details =
                service.enterView("0000000000000002", null).details();
        assertThat(details.description()).isEqualTo("D".repeat(60));
        assertThat(details.merchantName()).isEqualTo("N".repeat(30));
        assertThat(details.merchantCity()).isEqualTo("C".repeat(25));
    }

    @Test
    void keyIsVerbatimSixteenWithTrailingBlankTrim_frS0812() {
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        when(transactions.findById("abcdef")).thenReturn(Optional.empty());
        // Trailing blanks are key padding; lowercase passes through verbatim.
        service.enterView("abcdef            ", null);
        verify(transactions).findById(key.capture());
        assertThat(key.getValue()).isEqualTo("abcdef");
    }

    @Test
    void leadingBlanksAndCaseAreSignificant_frS0812() {
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        when(transactions.findById(" 000000000068358")).thenReturn(Optional.empty());
        service.enterView(" 0000000000683580", null);
        verify(transactions).findById(key.capture());
        // 17-char input truncates to the X(16) field; the leading blank is
        // part of the key, so this is not the seeded row's id.
        assertThat(key.getValue()).isEqualTo(" 000000000068358");
    }

    private String viewAmount(BigDecimal amount) {
        Transaction row = new Transaction();
        row.setTranId("0000000000000009");
        row.setTranAmount(amount);
        when(transactions.findById("0000000000000009")).thenReturn(Optional.of(row));
        return service.enterView("0000000000000009", null).details().amount();
    }

    private Transaction goldenRow() {
        Transaction row = new Transaction();
        row.setTranId("0000000000683580");
        row.setTranCardNumber("4859452612877065");
        row.setTranTypeCode("01");
        row.setTranCategoryCode(1);
        row.setTranSource("POS TERM");
        row.setTranDescription("Purchase at Abshire-Lowe");
        row.setTranAmount(new BigDecimal("504.77"));
        row.setTranOriginTimestamp(LocalDateTime.of(2022, 6, 10, 0, 0));
        row.setTranMerchantId(800000000L);
        row.setTranMerchantName("Abshire-Lowe");
        row.setTranMerchantCity("North Enoshaven");
        row.setTranMerchantZip("72112");
        return row;
    }

    private TransactionViewScreen.Details details() {
        return new TransactionViewScreen.Details("0000000000683580", "4859452612877065",
                "01", "0001", "POS TERM", "Purchase at Abshire-Lowe", "+00000504.77",
                "2022-06-10", "", "800000000", "Abshire-Lowe", "North Enoshaven", "72112");
    }
}
