package com.carddemo.service;

import com.carddemo.api.CardUpdateCommarea;
import com.carddemo.api.CardUpdateForm;
import com.carddemo.api.CardUpdateScreen;
import com.carddemo.model.Card;
import com.carddemo.repository.CardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * COCRDUPC lookup-then-update, one test per FR-S06 row. The repository is a
 * Mockito stub over an in-memory CARDDAT file so every derivation comes from
 * the COBOL trace (cited in each test), not from the real queries. Confidence:
 * HIGH unless noted.
 */
class CardUpdateServiceTest {

    private List<Card> file;
    private boolean failRead;
    private boolean failSave;
    private CardService service;

    @BeforeEach
    void setUp() {
        file = new ArrayList<>();
        failRead = false;
        failSave = false;
        CardRepository repository = mock(CardRepository.class);
        when(repository.findById(anyString())).thenAnswer(lookup());
        when(repository.findForUpdate(anyString())).thenAnswer(lookup());
        when(repository.save(any(Card.class))).thenAnswer(invocation -> {
            if (failSave) {
                throw new RuntimeException("REWRITE IOERR");
            }
            return invocation.getArgument(0);
        });
        service = new CardService(repository);
    }

    private Answer<Optional<Card>> lookup() {
        return invocation -> {
            if (failRead) {
                throw new RuntimeException("READ IOERR");
            }
            String key = invocation.getArgument(0);
            return file.stream()
                    .filter(card -> card.getCardNumber().equals(key))
                    .findFirst();
        };
    }

    private static Card card() {
        Card card = new Card();
        card.setCardNumber("1111222233334444");
        card.setCardAcctId(1L);
        card.setCardEmbossedName("ADA BYRON");
        card.setCardActiveStatus("Y");
        card.setCardExpirationDate(LocalDate.of(2030, 11, 30));
        card.setCardCvvCode(123);
        return card;
    }

    /** WS-THIS-PROGCOMMAREA for a fetched card at the given change-action. */
    private static CardUpdateCommarea commarea(Card card, String action) {
        LocalDate date = card.getCardExpirationDate();
        return new CardUpdateCommarea(action,
                "%011d".formatted(card.getCardAcctId()),
                card.getCardNumber(),
                card.getCardEmbossedName() == null ? null
                        : card.getCardEmbossedName().toUpperCase(),
                card.getCardActiveStatus(),
                date == null ? null : "%04d".formatted(date.getYear()),
                date == null ? null : "%02d".formatted(date.getMonthValue()),
                date == null ? null : "%02d".formatted(date.getDayOfMonth()));
    }

    private static CardUpdateForm press(String aid, CardUpdateCommarea commarea,
                                        String acct, String card, String name,
                                        String status, String month, String year,
                                        String day) {
        return new CardUpdateForm(aid, acct, card, name, status, month, year, day, commarea);
    }

    private static CardUpdateForm search(String acct, String card) {
        return press("ENTER", CardUpdateCommarea.fresh(), acct, card,
                null, null, null, null, null);
    }

    @Test
    void freshEntryShowsSearchPromptWithEditableKeys_frS0601() {
        // :502-511 — first display, no map received, search keys open.
        CardUpdateScreen screen = service.initialCardUpdate();

        assertThat(screen.changeAction()).isEmpty();
        assertThat(screen.infoMessage())
                .isEqualTo("Please enter Account and Card Number");
        assertThat(screen.errorMessage()).isNull();
        assertThat(screen.searchEditable()).isTrue();
        assertThat(screen.detailEditable()).isFalse();
        assertThat(screen.confirmPending()).isFalse();
        assertThat(screen.cursorField()).isEqualTo("acctsid");
    }

    @Test
    void unmappedAidRemapsToEnterWithoutInvalidKeyMessage_frS0602() {
        // :413-424 — PA1/F7/PF9 all become ENTER; the program never emits
        // an invalid-key message.
        CardUpdateScreen f7 = service.cardUpdate(
                press("F7", CardUpdateCommarea.fresh(), null, null,
                        null, null, null, null, null));
        CardUpdateScreen enter = service.cardUpdate(
                search(null, null));

        assertThat(f7.errorMessage()).isEqualTo(enter.errorMessage());
        assertThat(f7.changeAction()).isEmpty();

        // :420-421 — PF5 outside CHANGES-OK is ENTER too: a fetched screen
        // pressed with PF5 runs the edits, not the write.
        file.add(card());
        CardUpdateCommarea fetched = commarea(card(), "S");
        CardUpdateScreen pf5InS = service.cardUpdate(
                press("PF5", fetched, "00000000001", "1111222233334444",
                        "ADA BYRON", "Y", "11", "2030", "30"));

        assertThat(pf5InS.changeAction()).isEqualTo("S");
        assertThat(pf5InS.errorMessage())
                .isEqualTo("No change detected with respect to values fetched.");
        assertThat(file.get(0).getCardEmbossedName()).isEqualTo("ADA BYRON");
    }

    @Test
    void blankAccountShowsNotProvidedWithStarEcho_frS0604() {
        // :725-744 — LOW-VALUES/SPACES/'*'/all-zeros read as not provided.
        for (String blank : new String[] {null, "", "   ", "*", "00000000000"}) {
            CardUpdateScreen screen = service.cardUpdate(
                    search(blank, "1111222233334444"));

            assertThat(screen.errorMessage())
                    .as("account %s", blank)
                    .isEqualTo("Account number not provided");
            assertThat(screen.accountId()).isEqualTo("*");
            assertThat(screen.accountInvalid()).isTrue();
            assertThat(screen.changeAction()).isEmpty();
            assertThat(screen.cursorField()).isEqualTo("acctsid");
        }
    }

    @Test
    void nonElevenDigitAccountShowsFilterError_frS0605() {
        // :745-750 — supplied but not 11 digits is the filter complaint.
        CardUpdateScreen screen = service.cardUpdate(
                search("12345", "1111222233334444"));

        assertThat(screen.errorMessage())
                .isEqualTo("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER");
        assertThat(screen.accountId()).isEqualTo("12345");
        assertThat(screen.accountInvalid()).isTrue();
        assertThat(screen.cardInvalid()).isFalse();
        assertThat(screen.cursorField()).isEqualTo("acctsid");
    }

    @Test
    void blankCardShowsNotProvidedWithStarEcho_frS0606() {
        // :762-781 — the card key repeats the account shape, 16 digits.
        CardUpdateScreen screen = service.cardUpdate(
                search("00000000001", "0000000000000000"));

        assertThat(screen.errorMessage()).isEqualTo("Card number not provided");
        assertThat(screen.cardNumber()).isEqualTo("*");
        assertThat(screen.cardInvalid()).isTrue();
        assertThat(screen.accountInvalid()).isFalse();
        assertThat(screen.cursorField()).isEqualTo("cardsid");
    }

    @Test
    void nonSixteenDigitCardShowsFilterError_frS0607() {
        // :782-787.
        CardUpdateScreen screen = service.cardUpdate(
                search("00000000001", "123"));

        assertThat(screen.errorMessage())
                .isEqualTo("CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER");
        assertThat(screen.cardNumber()).isEqualTo("123");
        assertThat(screen.cardInvalid()).isTrue();
        assertThat(screen.cursorField()).isEqualTo("cardsid");
    }

    @Test
    void bothKeysBlankShowNoInputReceived_frS0608() {
        // :656-659 — the blank/blank pair overrides with 'No input received'.
        CardUpdateScreen screen = service.cardUpdate(search(null, null));

        assertThat(screen.errorMessage()).isEqualTo("No input received");
        assertThat(screen.accountId()).isEqualTo("*");
        assertThat(screen.cardNumber()).isEqualTo("*");
        assertThat(screen.accountInvalid()).isTrue();
        assertThat(screen.cardInvalid()).isTrue();
    }

    @Test
    void doubleKeyErrorShowsAccountMessageFirst_frS0609() {
        // WS-RETURN-MSG-OFF (:743-752) — the first failure wins the message;
        // both fields still flag and the cursor lands on the account.
        CardUpdateScreen screen = service.cardUpdate(
                search("bad", "also-bad"));

        assertThat(screen.errorMessage())
                .isEqualTo("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER");
        assertThat(screen.accountInvalid()).isTrue();
        assertThat(screen.cardInvalid()).isTrue();
        assertThat(screen.cursorField()).isEqualTo("acctsid");
    }

    @Test
    void validKeysFetchOldImageAndShowDetails_frS0610() {
        // :951-957 + :1352-1367 — the read keys on the card number alone
        // (the account is format-checked but never matched, cbl:1379-1380);
        // the OLD image displays with the name upper-cased.
        file.add(card());
        CardUpdateScreen screen = service.cardUpdate(
                search("00000000002", "1111222233334444"));

        assertThat(screen.changeAction()).isEqualTo("S");
        assertThat(screen.accountId()).isEqualTo("00000000002");
        assertThat(screen.cardNumber()).isEqualTo("1111222233334444");
        assertThat(screen.embossedName()).isEqualTo("ADA BYRON");
        assertThat(screen.activeStatus()).isEqualTo("Y");
        assertThat(screen.expiryMonth()).isEqualTo("11");
        assertThat(screen.expiryYear()).isEqualTo("2030");
        assertThat(screen.expiryDay()).isEqualTo("30");
        assertThat(screen.infoMessage())
                .isEqualTo("Details of selected card shown above");
        assertThat(screen.errorMessage()).isNull();
        assertThat(screen.searchEditable()).isFalse();
        assertThat(screen.detailEditable()).isTrue();
        assertThat(screen.cursorField()).isEqualTo("crdname");
        assertThat(screen.commarea().embossedName()).isEqualTo("ADA BYRON");
        // D2 — the commarea never carries the CVV.
        assertThat(screen.commarea().toString()).doesNotContain("123");
    }

    @Test
    void missedReadFlagsBothKeysAndShowsNotFound_frS0611() {
        // :1395-1401 — NOTFND flags both keys and keeps the search open.
        CardUpdateScreen screen = service.cardUpdate(
                search("00000000001", "9999999999999999"));

        assertThat(screen.errorMessage())
                .isEqualTo("Did not find cards for this search condition");
        assertThat(screen.accountInvalid()).isTrue();
        assertThat(screen.cardInvalid()).isTrue();
        assertThat(screen.changeAction()).isEmpty();
        assertThat(screen.searchEditable()).isTrue();
    }

    @Test
    void readErrorShowsRespFileErrorTemplate_frS0612() {
        // :1403-1411 — the WS-FILE-ERROR-MESSAGE layout for READ on
        // CARDDAT; RESP/RESP2 are the fixed IOERR codes (no target
        // equivalent, FR-S06-12 assumption).
        file.add(card());
        failRead = true;
        CardUpdateScreen screen = service.cardUpdate(
                search("00000000001", "1111222233334444"));

        assertThat(screen.errorMessage()).isEqualTo(
                "File Error: READ     on CARDDAT   returned RESP 000000017 ,RESP2 000000000 ");
        assertThat(screen.errorMessage()).hasSize(75);
        assertThat(screen.accountInvalid()).isTrue();
        assertThat(screen.changeAction()).isEmpty();
    }

    @Test
    void unchangedInputShowsNoChangeDetectedAndStaysShown_frS0613() {
        // :680-683 + 9300 — the upper-cased NEW image equals OLD, so the
        // edit pass ends with the no-change message and the state stays S.
        file.add(card());
        CardUpdateCommarea fetched = commarea(card(), "S");
        CardUpdateScreen screen = service.cardUpdate(
                press("ENTER", fetched, "00000000001", "1111222233334444",
                        "ada byron", "Y", "11", "2030", "30"));

        assertThat(screen.errorMessage())
                .isEqualTo("No change detected with respect to values fetched.");
        assertThat(screen.changeAction()).isEqualTo("S");
        assertThat(screen.embossedName()).isEqualTo("ADA BYRON");
        assertThat(screen.cursorField()).isEqualTo("crdname");
    }

    @Test
    void blankNameShowsRequiredWithStarEcho_frS0614() {
        // :810-822 + :1285-1290 — a blank name flags, displays '*' and
        // takes the first message.
        file.add(card());
        CardUpdateScreen screen = service.cardUpdate(
                press("ENTER", commarea(card(), "S"), "00000000001",
                        "1111222233334444", null, "Y", "11", "2030", "30"));

        assertThat(screen.changeAction()).isEqualTo("E");
        assertThat(screen.errorMessage()).isEqualTo("Card name not provided");
        assertThat(screen.nameInvalid()).isTrue();
        assertThat(screen.embossedName()).isEqualTo("*");
        assertThat(screen.cursorField()).isEqualTo("crdname");
    }

    @Test
    void nonAlphaNameShowsAlphabetError_frS0615() {
        // :824-838 — INSPECT survives only alphabets and spaces.
        file.add(card());
        CardUpdateScreen screen = service.cardUpdate(
                press("ENTER", commarea(card(), "S"), "00000000001",
                        "1111222233334444", "Ada123", "Y", "11", "2030", "30"));

        assertThat(screen.errorMessage())
                .isEqualTo("Card name can only contain alphabets and spaces");
        assertThat(screen.nameInvalid()).isTrue();
        assertThat(screen.embossedName()).isEqualTo("Ada123");
        assertThat(screen.changeAction()).isEqualTo("E");
    }

    @Test
    void nonYnStatusShowsStatusError_frS0616() {
        // :850-874 — the X(1) field must read exactly 'Y' or 'N';
        // lowercase 'y' fails the literal compare. A changed name makes
        // the edit ladder run; 'y' alone would collapse to NO-CHANGE in
        // 9300's upper-cased compare (:1502-1511).
        file.add(card());
        for (String bad : new String[] {"X", "y", "n"}) {
            CardUpdateScreen screen = service.cardUpdate(
                    press("ENTER", commarea(card(), "S"), "00000000001",
                            "1111222233334444", "ADA LOVELACE", bad, "11", "2030", "30"));

            assertThat(screen.errorMessage())
                    .as("status %s", bad)
                    .isEqualTo("Card Active Status must be Y or N");
            assertThat(screen.statusInvalid()).isTrue();
            assertThat(screen.changeAction()).isEqualTo("E");
            assertThat(screen.cursorField()).isEqualTo("crdstcd");
        }
    }

    @Test
    void monthOutOfRangeShowsMonthError_frS0617() {
        // :887-910 — JUSTIFY RIGHT zero-fills first; '5' becomes '05' and
        // is a valid month, '13' and '00' fail.
        file.add(card());
        CardUpdateCommarea fetched = commarea(card(), "S");

        CardUpdateScreen bad = service.cardUpdate(
                press("ENTER", fetched, "00000000001", "1111222233334444",
                        "ADA BYRON", "Y", "13", "2030", "30"));
        assertThat(bad.errorMessage())
                .isEqualTo("Card expiry month must be between 1 and 12");
        assertThat(bad.monthInvalid()).isTrue();
        assertThat(bad.expiryMonth()).isEqualTo("13");
        assertThat(bad.cursorField()).isEqualTo("expmon");

        CardUpdateScreen zeros = service.cardUpdate(
                press("ENTER", fetched, "00000000001", "1111222233334444",
                        "ADA BYRON", "Y", "0", "2030", "30"));
        assertThat(zeros.expiryMonth()).isEqualTo("*");
        assertThat(zeros.monthInvalid()).isTrue();

        CardUpdateScreen singleDigit = service.cardUpdate(
                press("ENTER", fetched, "00000000001", "1111222233334444",
                        "ADA BYRON", "Y", "5", "2030", "30"));
        assertThat(singleDigit.errorMessage()).isNull();
        assertThat(singleDigit.changeAction()).isEqualTo("N");
        assertThat(singleDigit.expiryMonth()).isEqualTo("05");
    }

    @Test
    void yearOutOfRangeShowsYearError_frS0618() {
        // :920-944 — '25' right-justifies to '0025' and fails 1950-2099.
        file.add(card());
        CardUpdateScreen screen = service.cardUpdate(
                press("ENTER", commarea(card(), "S"), "00000000001",
                        "1111222233334444", "ADA BYRON", "Y", "11", "25", "30"));

        assertThat(screen.errorMessage()).isEqualTo("Invalid card expiry year");
        assertThat(screen.yearInvalid()).isTrue();
        assertThat(screen.expiryYear()).isEqualTo("0025");
        assertThat(screen.changeAction()).isEqualTo("E");
        assertThat(screen.cursorField()).isEqualTo("expyear");
    }

    @Test
    void multipleDetailFailuresFlagAllAndShowFirstMessage_frS0619() {
        // :702-712 — all four edits run; the name (first in the ladder)
        // wins the message and every bad field flags red.
        file.add(card());
        CardUpdateScreen screen = service.cardUpdate(
                press("ENTER", commarea(card(), "S"), "00000000001",
                        "1111222233334444", "", "X", "13", "1800", "30"));

        assertThat(screen.changeAction()).isEqualTo("E");
        assertThat(screen.errorMessage()).isEqualTo("Card name not provided");
        assertThat(screen.nameInvalid()).isTrue();
        assertThat(screen.statusInvalid()).isTrue();
        assertThat(screen.monthInvalid()).isTrue();
        assertThat(screen.yearInvalid()).isTrue();
        assertThat(screen.embossedName()).isEqualTo("*");
        assertThat(screen.activeStatus()).isEqualTo("X");
        assertThat(screen.expiryMonth()).isEqualTo("13");
        assertThat(screen.expiryYear()).isEqualTo("1800");
        assertThat(screen.infoMessage())
                .isEqualTo("Update card details presented above.");
        assertThat(screen.cursorField()).isEqualTo("crdname");
        assertThat(screen.searchEditable()).isFalse();
    }

    @Test
    void allEditsPassShowValidatedAwaitingF5_frS0620() {
        // :709-713 + :971-975 — a clean edit pass promotes to N; the
        // F5=Save/F12=Cancel legend goes bright and everything protects.
        file.add(card());
        CardUpdateScreen screen = service.cardUpdate(
                press("ENTER", commarea(card(), "S"), "00000000001",
                        "1111222233334444", "ADA LOVELACE", "Y", "11", "2030", "30"));

        assertThat(screen.changeAction()).isEqualTo("N");
        assertThat(screen.confirmPending()).isTrue();
        assertThat(screen.infoMessage())
                .isEqualTo("Changes validated.Press F5 to save");
        assertThat(screen.errorMessage()).isNull();
        assertThat(screen.detailEditable()).isFalse();
        assertThat(screen.searchEditable()).isFalse();
        assertThat(screen.embossedName()).isEqualTo("ADA LOVELACE");
    }

    @Test
    void enterInValidatedStateStaysAwaitingF5_frS0621() {
        // :685-688 + :971-973 — ENTER while N re-displays N; only PF5
        // writes.
        file.add(card());
        CardUpdateScreen screen = service.cardUpdate(
                press("ENTER", commarea(card(), "N"), "00000000001",
                        "1111222233334444", "ADA LOVELACE", "Y", "11", "2030", "30"));

        assertThat(screen.changeAction()).isEqualTo("N");
        assertThat(screen.confirmPending()).isTrue();
        assertThat(file.get(0).getCardEmbossedName()).isEqualTo("ADA BYRON");
    }

    @Test
    void pfFiveOnUnchangedRecordCommitsAndPreservesCvv_frS0622() {
        // :1427-1496 — FOR UPDATE read, compare, REWRITE. D1 keeps the
        // stored CVV and account id; D3 uses the OLD day unclamped.
        file.add(card());
        CardUpdateScreen screen = service.cardUpdate(
                press("PF5", commarea(card(), "N"), "00000000001",
                        "1111222233334444", "ADA LOVELACE", "N", "12", "2031", "30"));

        assertThat(screen.changeAction()).isEqualTo("C");
        assertThat(screen.infoMessage())
                .isEqualTo("Changes committed to database");
        assertThat(screen.searchEditable()).isFalse();
        Card saved = file.get(0);
        assertThat(saved.getCardEmbossedName()).isEqualTo("ADA LOVELACE");
        assertThat(saved.getCardActiveStatus()).isEqualTo("N");
        assertThat(saved.getCardExpirationDate()).isEqualTo(LocalDate.of(2031, 12, 30));
        assertThat(saved.getCardCvvCode()).isEqualTo(123);
        assertThat(saved.getCardAcctId()).isEqualTo(1L);
    }

    @Test
    void pfFiveOnChangedRecordShowsReviewAndRefreshesOld_frS0623() {
        // :1453-1456 + 9300 — the locked record differs from OLD, the
        // OLD image refreshes from the store and the state drops to S.
        Card stored = card();
        file.add(stored);
        CardUpdateCommarea fetched = commarea(stored, "N");
        stored.setCardEmbossedName("GRACE HOPPER");

        CardUpdateScreen screen = service.cardUpdate(
                press("PF5", fetched, "00000000001", "1111222233334444",
                        "ADA LOVELACE", "Y", "11", "2030", "30"));

        assertThat(screen.errorMessage())
                .isEqualTo("Record changed by some one else. Please review");
        assertThat(screen.changeAction()).isEqualTo("S");
        assertThat(screen.embossedName()).isEqualTo("GRACE HOPPER");
        assertThat(screen.commarea().embossedName()).isEqualTo("GRACE HOPPER");
        assertThat(screen.detailEditable()).isTrue();
    }

    @Test
    void pfFiveOnDeletedRecordShowsLockError_frS0624() {
        // :1436-1447 — READ UPDATE comes back NOTFND: lock error, state L,
        // the OTHER attribute branch re-opens the search keys.
        Card stored = card();
        file.add(stored);
        CardUpdateCommarea fetched = commarea(stored, "N");
        file.clear();

        CardUpdateScreen screen = service.cardUpdate(
                press("PF5", fetched, "00000000001", "1111222233334444",
                        "ADA LOVELACE", "Y", "11", "2030", "30"));

        assertThat(screen.errorMessage()).isEqualTo("Could not lock record for update");
        assertThat(screen.changeAction()).isEqualTo("L");
        assertThat(screen.infoMessage())
                .isEqualTo("Changes unsuccessful. Please try again");
        assertThat(screen.searchEditable()).isTrue();
        assertThat(screen.detailEditable()).isFalse();
        assertThat(screen.embossedName()).isEqualTo("ADA LOVELACE");
    }

    @Test
    void rewriteFailureShowsUpdateFailed_frS0625() {
        // :1488-1492 — any other REWRITE outcome is the update-failed
        // message and state F.
        file.add(card());
        failSave = true;
        CardUpdateScreen screen = service.cardUpdate(
                press("PF5", commarea(card(), "N"), "00000000001",
                        "1111222233334444", "ADA LOVELACE", "Y", "11", "2030", "30"));

        assertThat(screen.errorMessage()).isEqualTo("Update of record failed");
        assertThat(screen.changeAction()).isEqualTo("F");
        assertThat(screen.searchEditable()).isTrue();
    }

    @Test
    void nonCalendarExpiryFailsAsRewriteError_frS0625d3() {
        // D3 — LocalDate.of(2030, 2, 30) has no calendar day: the REWRITE
        // fails, no clamp. Confidence: the deviation register is explicit.
        file.add(card());
        CardUpdateScreen screen = service.cardUpdate(
                press("PF5", commarea(card(), "N"), "00000000001",
                        "1111222233334444", "ADA BYRON", "Y", "02", "2030", "30"));

        assertThat(screen.errorMessage()).isEqualTo("Update of record failed");
        assertThat(screen.changeAction()).isEqualTo("F");
        assertThat(file.get(0).getCardExpirationDate())
                .isEqualTo(LocalDate.of(2030, 11, 30));
    }

    @Test
    void anyAidAfterDoneResetsToFreshSearch_frS0626() {
        // :517-528 — C/L/F re-initialise on every remaining AID.
        for (String done : new String[] {"C", "L", "F"}) {
            CardUpdateScreen screen = service.cardUpdate(
                    press("ENTER", commarea(card(), done), "00000000001",
                            "1111222233334444", "ADA LOVELACE", "Y",
                            "11", "2030", "30"));

            assertThat(screen.changeAction()).as("from %s", done).isEmpty();
            assertThat(screen.infoMessage())
                    .isEqualTo("Please enter Account and Card Number");
            assertThat(screen.searchEditable()).isTrue();
        }
    }

    @Test
    void pfTwelveReReadsAndRestoresOldKeepingTheMessage_frS0627() {
        // :958-966 — F12 while fetched re-reads the record; the edit-pass
        // message stays on the redisplayed OLD image.
        file.add(card());
        CardUpdateScreen screen = service.cardUpdate(
                press("PF12", commarea(card(), "E"), "00000000001",
                        "1111222233334444", null, "X", "11", "2030", "30"));

        assertThat(screen.changeAction()).isEqualTo("S");
        assertThat(screen.embossedName()).isEqualTo("ADA BYRON");
        assertThat(screen.activeStatus()).isEqualTo("Y");
        assertThat(screen.errorMessage()).isEqualTo("Card name not provided");
        assertThat(screen.infoMessage())
                .isEqualTo("Details of selected card shown above");
    }
}
