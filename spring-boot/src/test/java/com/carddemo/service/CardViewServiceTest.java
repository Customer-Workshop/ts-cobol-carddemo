package com.carddemo.service;

import com.carddemo.api.CobolApiException;
import com.carddemo.model.Card;
import com.carddemo.repository.CardRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * COCRDSLC resolution-path parity: every FR-S05 row maps to a named test,
 * with expectations derived from the COBOL line range cited on the row —
 * never from this implementation.
 */
class CardViewServiceTest {

    private final CardRepository cardRepository = mock(CardRepository.class);
    private final CardService service = new CardService(cardRepository);

    @Test
    void initialScreenIsPromptOnly_frS0501() {
        // :349-356 — PGM-ENTER first display: blank fields, fixed prompt.
        CardViewScreen screen = service.initialScreen();

        assertThat(screen.accountEcho()).isEmpty();
        assertThat(screen.cardEcho()).isEmpty();
        assertThat(screen.accountFieldRed()).isFalse();
        assertThat(screen.cardFieldRed()).isFalse();
        assertThat(screen.inputsProtected()).isFalse();
        assertThat(screen.cursorField()).isEqualTo("acctsid");
        assertThat(screen.infoMessage()).isEqualTo("Please enter Account and Card Number");
        assertThat(screen.errorMessage()).isNull();
        assertThat(screen.card()).isNull();
    }

    @Test
    void blankAccountIsStarRedAndFieldMessage_frS0502() {
        // :613-620, :651-660 — `*`/spaces pack to zero digits, all-zero
        // counts as blank too; the flag is BLANK so the field redisplays
        // `*` red while the supplied card keeps its value.
        for (String acct : new String[] {"", "   ", "*", "*   ", "00000000000", "0"}) {
            CardViewScreen screen = service.viewScreen(acct, "1111222233334444", false);

            assertThat(screen.errorMessage()).isEqualTo("Account number not provided");
            assertThat(screen.accountEcho()).isEqualTo("*");
            assertThat(screen.accountFieldRed()).isTrue();
            assertThat(screen.cardFieldRed()).isFalse();
            assertThat(screen.cardEcho()).isEqualTo("1111222233334444");
            assertThat(screen.cursorField()).isEqualTo("acctsid");
            assertThat(screen.card()).isNull();
        }
        verify(cardRepository, never()).findById(anyString());
        assertThatThrownBy(() -> service.detail(null, "1111222233334444"))
                .isInstanceOfSatisfying(CobolApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo("Account number not provided");
                });
    }

    @Test
    void shortOrAlphaAccountIsClearedRedFilterError_frS0503() {
        // :665-674 — NOT-OK fields redisplay cleared (the COMMAREA key was
        // zeroed) and flagged red; exactly 11 digits required.
        for (String acct : new String[] {"123", "1234567890a", "0000000001 "}) {
            CardViewScreen screen = service.viewScreen(acct, "1111222233334444", false);

            assertThat(screen.errorMessage())
                    .isEqualTo("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER");
            assertThat(screen.accountEcho()).isEmpty();
            assertThat(screen.accountFieldRed()).isTrue();
            assertThat(screen.cardFieldRed()).isFalse();
            assertThat(screen.cardEcho()).isEqualTo("1111222233334444");
            assertThat(screen.card()).isNull();
        }
        verify(cardRepository, never()).findById(anyString());
        assertThatThrownBy(() -> service.detail("123", "1111222233334444"))
                .isInstanceOfSatisfying(CobolApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage())
                            .isEqualTo("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER");
                });

        // Map input is an X(11) field: "000000000012" truncates to
        // "00000000001" before the edits and reaches the file read.
        when(cardRepository.findById("1111222233334444"))
                .thenReturn(Optional.of(card(1L)));
        CardViewScreen truncated = service.viewScreen("000000000012", "1111222233334444", false);
        assertThat(truncated.accountEcho()).isEqualTo("00000000001");
        assertThat(truncated.card()).isNotNull();
    }

    @Test
    void blankCardIsStarRedAndFieldMessage_frS0504() {
        // :691-701 — same rules on the 16-byte card field; the cursor moves
        // to cardsid when only the card flag is set (:520-524).
        for (String cardNum : new String[] {"", "   ", "*", "0000000000000000"}) {
            CardViewScreen screen = service.viewScreen("00000000001", cardNum, false);

            assertThat(screen.errorMessage()).isEqualTo("Card number not provided");
            assertThat(screen.cardEcho()).isEqualTo("*");
            assertThat(screen.cardFieldRed()).isTrue();
            assertThat(screen.accountFieldRed()).isFalse();
            assertThat(screen.accountEcho()).isEqualTo("00000000001");
            assertThat(screen.cursorField()).isEqualTo("cardsid");
            assertThat(screen.card()).isNull();
        }
        verify(cardRepository, never()).findById(anyString());
        assertThatThrownBy(() -> service.detail("00000000001", ""))
                .isInstanceOfSatisfying(CobolApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo("Card number not provided");
                });
    }

    @Test
    void shortOrAlphaCardIsClearedRedFilterError_frS0505() {
        // :706-715 — exactly 16 digits required.
        for (String cardNum : new String[] {"123", "111122223333444x"}) {
            CardViewScreen screen = service.viewScreen("00000000001", cardNum, false);

            assertThat(screen.errorMessage())
                    .isEqualTo("CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER");
            assertThat(screen.cardEcho()).isEmpty();
            assertThat(screen.cardFieldRed()).isTrue();
            assertThat(screen.accountFieldRed()).isFalse();
            assertThat(screen.accountEcho()).isEqualTo("00000000001");
            assertThat(screen.cursorField()).isEqualTo("cardsid");
            assertThat(screen.card()).isNull();
        }
        // X(16) truncation: 17 digits read as the first 16 and proceed.
        when(cardRepository.findById("1111222233334444"))
                .thenReturn(Optional.of(card(1L)));
        CardViewScreen truncated = service.viewScreen("00000000001", "11112222333344445", false);
        assertThat(truncated.cardEcho()).isEqualTo("1111222233334444");
        assertThat(truncated.card()).isNotNull();
    }

    @Test
    void bothBlankIsNoInputReceived_frS0506() {
        // :637-640 — both-blank replaces the earlier field message with
        // NO-INPUT-RECEIVED; both fields flag and redisplay `*`.
        CardViewScreen screen = service.viewScreen("", "", false);

        assertThat(screen.errorMessage()).isEqualTo("No input received");
        assertThat(screen.accountEcho()).isEqualTo("*");
        assertThat(screen.cardEcho()).isEqualTo("*");
        assertThat(screen.accountFieldRed()).isTrue();
        assertThat(screen.cardFieldRed()).isTrue();
        assertThat(screen.card()).isNull();
        assertThatThrownBy(() -> service.detail(null, null))
                .isInstanceOfSatisfying(CobolApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo("No input received");
                });
    }

    @Test
    void accountMessageWinsOverCard_frS0507() {
        // :657/:696 — the message slot is first-writer-wins: the account
        // edit writes first and the card edit cannot overwrite it.
        CardViewScreen blankAcctBadCard = service.viewScreen("", "123", false);
        assertThat(blankAcctBadCard.errorMessage()).isEqualTo("Account number not provided");

        CardViewScreen bothBad = service.viewScreen("123", "123", false);
        assertThat(bothBad.errorMessage())
                .isEqualTo("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER");
        // The card flag still marks — both fields flag even though only
        // the account message shows.
        assertThat(bothBad.accountFieldRed()).isTrue();
        assertThat(bothBad.cardFieldRed()).isTrue();
        assertThat(bothBad.accountEcho()).isEmpty();
        assertThat(bothBad.cardEcho()).isEmpty();
    }

    @Test
    void starWithTextIsInputNotBlank_frS0508() {
        // :613-620 — only `*` + all blanks clears to LOW-VALUES; `*` with
        // other input is data, so it fails the numeric edit (NOT-OK).
        CardViewScreen screen = service.viewScreen("*1234567890", "1111222233334444", false);

        assertThat(screen.errorMessage())
                .isEqualTo("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER");
        assertThat(screen.accountEcho()).isEmpty();
        assertThat(screen.accountFieldRed()).isTrue();
    }

    @Test
    void foundCardDisplaysDetailsAndInfoLine_frS0509() {
        // :755-760, :474-484 — FOUND moves the card block and sets the
        // three-space-led info message; the read is keyed on the card.
        when(cardRepository.findById("1111222233334444"))
                .thenReturn(Optional.of(card(1L)));

        CardViewScreen screen = service.viewScreen("00000000001", "1111222233334444", false);

        assertThat(screen.errorMessage()).isNull();
        assertThat(screen.infoMessage()).isEqualTo("   Displaying requested details");
        assertThat(screen.accountEcho()).isEqualTo("00000000001");
        assertThat(screen.cardEcho()).isEqualTo("1111222233334444");
        assertThat(screen.accountFieldRed()).isFalse();
        assertThat(screen.cardFieldRed()).isFalse();
        assertThat(screen.inputsProtected()).isFalse();
        assertThat(screen.cursorField()).isEqualTo("acctsid");
        assertThat(screen.card().embossedName()).isEqualTo("Ada Byron");
        assertThat(screen.card().expiryMonth()).isEqualTo("01");
        assertThat(screen.card().expiryYear()).isEqualTo("2025");
        assertThat(screen.card().activeStatus()).isEqualTo("Y");

        assertThat(service.detail("00000000001", "1111222233334444").embossedName())
                .isEqualTo("Ada Byron");
    }

    @Test
    void notFoundFlagsBothAndKeepsEchoes_frS0510() {
        // :755-761 — NOTFND flags both fields; the entered values stay.
        when(cardRepository.findById("9999999999999999")).thenReturn(Optional.empty());

        CardViewScreen screen = service.viewScreen("00000000001", "9999999999999999", false);

        assertThat(screen.errorMessage())
                .isEqualTo("Did not find cards for this search condition");
        assertThat(screen.accountFieldRed()).isTrue();
        assertThat(screen.cardFieldRed()).isTrue();
        assertThat(screen.accountEcho()).isEqualTo("00000000001");
        assertThat(screen.cardEcho()).isEqualTo("9999999999999999");
        assertThat(screen.infoMessage()).isEqualTo("Please enter Account and Card Number");
        assertThat(screen.card()).isNull();
        assertThatThrownBy(() -> service.detail("00000000001", "9999999999999999"))
                .isInstanceOfSatisfying(CobolApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getMessage())
                            .isEqualTo("Did not find cards for this search condition");
                });
    }

    @Test
    void storeErrorRendersFileErrorFrame_frS0511() {
        // :762-771 / S05-B4 — RESP OTHER renders the fixed file-error
        // frame; only the account flag is forced because WS-RETURN-MSG was
        // still off. The REST surface propagates the store failure.
        when(cardRepository.findById("1111222233334444"))
                .thenThrow(new DataAccessResourceFailureException("store down"));

        CardViewScreen screen = service.viewScreen("00000000001", "1111222233334444", false);

        assertThat(screen.errorMessage()).isEqualTo(
                "File Error: READ     on CARDDAT   returned RESP 000000017 ,RESP2 000000120 ");
        assertThat(screen.accountFieldRed()).isTrue();
        assertThat(screen.cardFieldRed()).isFalse();
        assertThat(screen.accountEcho()).isEqualTo("00000000001");
        assertThat(screen.cardEcho()).isEqualTo("1111222233334444");
        assertThat(screen.card()).isNull();
        assertThatThrownBy(() -> service.detail("00000000001", "1111222233334444"))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void accountIsNeverCrossCheckedAgainstCard_frS0512() {
        // :739-750 — the read is keyed on the card number alone; the owner
        // on the record is never compared (documented source defect kept).
        when(cardRepository.findById("1111222233334444"))
                .thenReturn(Optional.of(card(2L)));

        CardViewScreen screen = service.viewScreen("00000000001", "1111222233334444", false);

        assertThat(screen.errorMessage()).isNull();
        assertThat(screen.card()).isNotNull();
        assertThat(service.detail("00000000001", "1111222233334444").accountId())
                .isEqualTo(2L);
    }

    @Test
    void cardListContextSkipsEditsAndProtects_frS0513() {
        // :339-348, :505-508 — the card-list XCTL keys echo zero-padded
        // (9(11)/9(16) numerics), the edits are skipped, the read runs at
        // once, and both inputs render protected.
        when(cardRepository.findById("1111222233334444"))
                .thenReturn(Optional.of(card(1L)));

        CardViewScreen screen = service.cardListScreen("1", "1111222233334444");

        assertThat(screen.accountEcho()).isEqualTo("00000000001");
        assertThat(screen.cardEcho()).isEqualTo("1111222233334444");
        assertThat(screen.inputsProtected()).isTrue();
        assertThat(screen.accountFieldRed()).isFalse();
        assertThat(screen.cardFieldRed()).isFalse();
        assertThat(screen.card()).isNotNull();
        assertThat(screen.infoMessage()).isEqualTo("   Displaying requested details");
    }

    private Card card(long acctId) {
        Card card = new Card();
        card.setCardNumber("1111222233334444");
        card.setCardAcctId(acctId);
        card.setCardCvvCode(123);
        card.setCardEmbossedName("Ada Byron");
        card.setCardExpirationDate(LocalDate.of(2025, 1, 1));
        card.setCardActiveStatus("Y");
        return card;
    }
}
