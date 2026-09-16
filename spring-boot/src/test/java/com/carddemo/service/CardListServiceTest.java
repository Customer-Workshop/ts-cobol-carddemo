package com.carddemo.service;

import com.carddemo.api.CardListPageState;
import com.carddemo.api.CardListRequest;
import com.carddemo.api.CardListResponse;
import com.carddemo.api.CardListRow;
import com.carddemo.api.CobolMessages;
import com.carddemo.model.Card;
import com.carddemo.repository.CardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * COCRDLIC keyed browse, one test per FR-S04 row. The repository is a fake
 * that reproduces the key-ordered CARDDAT browse verbs over an in-memory
 * file, so every derivation comes from the COBOL trace (cited in each test),
 * not from the real queries. Confidence: HIGH unless noted.
 */
class CardListServiceTest {

    private List<Card> file;
    private CardService service;

    @BeforeEach
    void setUp() {
        file = new ArrayList<>();
        CardRepository repository = mock(CardRepository.class);
        when(repository.browseForward(anyString(), any(), any(), any(Pageable.class)))
                .thenAnswer(invocation -> file.stream()
                        .filter(card -> card.getCardNumber()
                                .compareTo(invocation.getArgument(0)) >= 0)
                        .filter(card -> invocation.getArgument(1) == null
                                || invocation.getArgument(1).equals(card.getCardAcctId()))
                        .filter(card -> invocation.getArgument(2) == null
                                || invocation.getArgument(2).equals(card.getCardNumber()))
                        .limit(((Pageable) invocation.getArgument(3)).getPageSize())
                        .toList());
        when(repository.browseBackward(anyString(), any(), any(), any(Pageable.class)))
                .thenAnswer(invocation -> file.stream()
                        .filter(card -> card.getCardNumber()
                                .compareTo((String) invocation.getArgument(0)) < 0)
                        .sorted(Comparator.comparing(Card::getCardNumber).reversed())
                        .filter(card -> invocation.getArgument(1) == null
                                || invocation.getArgument(1).equals(card.getCardAcctId()))
                        .filter(card -> invocation.getArgument(2) == null
                                || invocation.getArgument(2).equals(card.getCardNumber()))
                        .limit(((Pageable) invocation.getArgument(3)).getPageSize())
                        .toList());
        when(repository.nextAfter(anyString(), any(Pageable.class)))
                .thenAnswer(invocation -> file.stream()
                        .filter(card -> card.getCardNumber()
                                .compareTo((String) invocation.getArgument(0)) > 0)
                        .limit(((Pageable) invocation.getArgument(1)).getPageSize())
                        .toList());
        service = new CardService(repository);
    }

    private static Card card(String number, long accountId) {
        Card card = new Card();
        card.setCardNumber(number);
        card.setCardAcctId(accountId);
        card.setCardActiveStatus("Y");
        return card;
    }

    private void seed(int count) {
        for (int i = 1; i <= count; i++) {
            file.add(card("%016d".formatted(i), i <= 7 ? 1L : 2L));
        }
        file.sort(Comparator.comparing(Card::getCardNumber));
    }

    private static String key(int i) {
        return "%016d".formatted(i);
    }

    private static List<CardListRow> comareaRows(int from, int to) {
        List<CardListRow> rows = new ArrayList<>(
                java.util.Collections.nCopies(CardService.COBOL_PAGE_SIZE, null));
        for (int i = from; i <= to; i++) {
            rows.set(i - from, new CardListRow(i <= 7 ? 1L : 2L, key(i), "Y"));
        }
        return rows;
    }

    private static CardListPageState state(String first, String last, int number,
                                           boolean lastPageShown, boolean nextPageExists,
                                           List<CardListRow> rows) {
        return new CardListPageState(first, last, number, lastPageShown,
                nextPageExists, rows);
    }

    private CardListResponse aid(String key, CardListPageState state) {
        return service.browse(new CardListRequest(key, null, null, null, state));
    }

    @Test
    void freshEntryListsSevenRowsAndSeedsAnchors_frS0401() {
        // :315-343, :458-482 — fresh entry browses from file start.
        seed(10);
        CardListResponse response =
                service.browse(new CardListRequest("ENTER", null, null, null, null));

        assertThat(response.outcome()).isEqualTo("page");
        assertThat(response.screen().screenNumber()).isEqualTo(1);
        assertThat(response.screen().rows().stream().map(r -> r.cardNumber()).toList())
                .containsExactly(key(1), key(2), key(3), key(4), key(5), key(6), key(7));
        assertThat(response.pageState().firstCardNumber()).isEqualTo(key(1));
        // :1197-1214 — the unfiltered look-ahead after row 7 is the next anchor.
        assertThat(response.pageState().lastCardNumber()).isEqualTo(key(8));
        assertThat(response.pageState().nextPageExists()).isTrue();
        assertThat(response.pageState().lastPageShown()).isFalse();
    }

    @Test
    void pfSevenOnFirstPageKeepsRowsAndShowsNoPreviousPages_frS0402() {
        // :441-457 + :909-910 — PF7 on page 1 re-reads from the first anchor
        // and overrides the error line.
        seed(10);
        CardListPageState pageOne = state(key(1), key(8), 1, false, true, comareaRows(1, 7));
        CardListResponse response = aid("PF7", pageOne);

        assertThat(response.screen().errorMessage())
                .isEqualTo("NO PREVIOUS PAGES TO DISPLAY");
        assertThat(response.screen().infoMessage()).isNull();
        assertThat(response.screen().rows().stream().map(r -> r.cardNumber()).toList())
                .containsExactly(key(1), key(2), key(3), key(4), key(5), key(6), key(7));
        assertThat(response.pageState().screenNumber()).isEqualTo(1);
    }

    @Test
    void pfEightAdvancesFromLookAheadAnchor_frS0403() {
        // :484-502 — STARTBR at the look-ahead key, ADD 1 to the page number.
        seed(10);
        CardListPageState pageOne = state(key(1), key(8), 1, false, true, comareaRows(1, 7));
        CardListResponse response = aid("PF8", pageOne);

        assertThat(response.screen().screenNumber()).isEqualTo(2);
        assertThat(response.screen().rows().stream().map(r -> r == null ? null : r.cardNumber())
                .toList())
                .containsExactly(key(8), key(9), key(10), null, null, null, null);
        assertThat(response.pageState().firstCardNumber()).isEqualTo(key(8));
        assertThat(response.pageState().lastCardNumber()).isEqualTo(key(10));
        assertThat(response.pageState().nextPageExists()).isFalse();
        assertThat(response.screen().errorMessage())
                .isEqualTo("NO MORE RECORDS TO SHOW");
    }

    @Test
    void pfEightExhaustionShowsInfoThenNoMorePages_frS0404() {
        // :911-916 — first dead PF8 shows the info line and arms
        // last-page-shown; the next shows NO MORE PAGES TO DISPLAY.
        seed(10);
        CardListPageState lastPage = state(key(8), key(10), 2, false, false, comareaRows(8, 10));

        CardListResponse first = aid("PF8", lastPage);
        // The dead read still hits ENDFILE, so the error line keeps
        // 'NO MORE RECORDS TO SHOW' while the info line displays (:913-920).
        assertThat(first.screen().errorMessage()).isEqualTo("NO MORE RECORDS TO SHOW");
        assertThat(first.screen().infoMessage())
                .isEqualTo("TYPE S FOR DETAIL, U TO UPDATE ANY RECORD");
        assertThat(first.pageState().lastPageShown()).isTrue();

        CardListResponse second = aid("PF8", first.pageState());
        assertThat(second.screen().errorMessage()).isEqualTo("NO MORE PAGES TO DISPLAY");
        assertThat(second.screen().infoMessage()).isNull();
        assertThat(second.screen().screenNumber()).isEqualTo(2);
    }

    @Test
    void enterRelistsFromFirstAnchorNotFileStart_frS0405() {
        // :572-582 WHEN OTHER — ENTER re-browses from the CURRENT first key.
        seed(10);
        CardListPageState pageTwo = state(key(8), key(10), 2, false, false, comareaRows(8, 10));
        CardListResponse response = aid("ENTER", pageTwo);

        assertThat(response.outcome()).isEqualTo("page");
        assertThat(response.screen().rows().stream().map(r -> r == null ? null : r.cardNumber())
                .toList())
                .containsExactly(key(8), key(9), key(10), null, null, null, null);
        assertThat(response.pageState().screenNumber()).isEqualTo(2);
    }

    @Test
    void unmappedAidIsRemappedToEnter_frS0406() {
        // :370-380 — PA1/PA2/PF1 etc. all become ENTER (S04-B3).
        seed(10);
        CardListPageState pageOne = state(key(1), key(8), 1, false, true, comareaRows(1, 7));

        CardListResponse pa1 = aid("PA1", pageOne);
        CardListResponse f9 = aid("F9", pageOne);
        CardListResponse enter = aid("ENTER", pageOne);

        assertThat(pa1.screen().rows().stream().map(r -> r.cardNumber()).toList())
                .isEqualTo(enter.screen().rows().stream().map(r -> r.cardNumber()).toList());
        assertThat(f9.screen().rows().stream().map(r -> r.cardNumber()).toList())
                .isEqualTo(enter.screen().rows().stream().map(r -> r.cardNumber()).toList());
        assertThat(pa1.pageState().nextPageExists()).isTrue();
    }

    @Test
    void invalidAccountFilterRedisplaysCommareaRows_frS0407() {
        // :419-422 + :1074-1080 — a filter error never re-reads; the screen
        // keeps the COMMAREA rows and the typed value is echoed.
        seed(10);
        CardListPageState pageOne = state(key(1), key(8), 1, false, true, comareaRows(1, 7));
        CardListResponse response = service.browse(new CardListRequest(
                "ENTER", "ABCDEFGHIJK", null, null, pageOne));

        assertThat(response.screen().errorMessage())
                .isEqualTo("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER");
        assertThat(response.screen().infoMessage()).isNull();
        assertThat(response.screen().accountFilter()).isEqualTo("ABCDEFGHIJK");
        assertThat(response.screen().accountFilterInvalid()).isTrue();
        assertThat(response.screen().rows().stream().map(r -> r == null ? null : r.cardNumber())
                .toList())
                .containsExactly(key(1), key(2), key(3), key(4), key(5), key(6), key(7));
    }

    @Test
    void validAccountFilterLimitsRowsToAccount_frS0408() {
        // 9500-FILTER-RECORDS (:1382-1411) — equality on the 11-digit account.
        seed(10);
        CardListPageState pageOne = state(key(1), key(8), 1, false, true, comareaRows(1, 7));
        CardListResponse response = service.browse(new CardListRequest(
                "ENTER", "00000000002", null, null, pageOne));

        assertThat(response.screen().rows().stream().map(r -> r == null ? null : r.cardNumber())
                .toList())
                .containsExactly(key(8), key(9), key(10), null, null, null, null);
        assertThat(response.screen().accountFilter()).isEqualTo("00000000002");
        assertThat(response.screen().errorMessage())
                .isEqualTo("NO MORE RECORDS TO SHOW");

        // :1241-1245 — page 1 with no matching rows overrides the error line.
        CardListResponse none = service.browse(new CardListRequest(
                "ENTER", "00000000009", null, null, pageOne));
        assertThat(none.screen().errorMessage())
                .isEqualTo("NO RECORDS FOUND FOR THIS SEARCH CONDITION.");
        assertThat(none.screen().infoMessage()).isNull();
    }

    @Test
    void cardFilterReturnsTheExactCardOnly_frS0409() {
        // :1036-1067 + 9500-FILTER-RECORDS — equality on the 16-digit card.
        seed(10);
        CardListPageState pageOne = state(key(1), key(8), 1, false, true, comareaRows(1, 7));
        CardListResponse response = service.browse(new CardListRequest(
                "ENTER", null, key(5), null, pageOne));

        assertThat(response.screen().rows().stream().map(r -> r == null ? null : r.cardNumber())
                .toList())
                .containsExactly(key(5), null, null, null, null, null, null);
        assertThat(response.pageState().nextPageExists()).isFalse();
    }

    @Test
    void blankFiltersGiveFullBrowseAndUnprotectedSelects_frS0410() {
        // :1018-1022, :1051-1055 — blanks/zeros are "not supplied"; the select
        // column stays enterable on filled rows.
        seed(10);
        CardListResponse blank = service.browse(new CardListRequest(
                "ENTER", "   ", " ", null,
                state(key(1), key(8), 1, false, true, comareaRows(1, 7))));
        assertThat(blank.screen().rows().stream().map(r -> r == null ? null : r.cardNumber())
                .toList())
                .containsExactly(key(1), key(2), key(3), key(4), key(5), key(6), key(7));
        assertThat(blank.screen().rows().stream()
                .filter(r -> r.cardNumber() != null).allMatch(r -> !r.selectProtected()))
                .isTrue();

        CardListResponse zeros = service.browse(new CardListRequest(
                "ENTER", "00000000000", "0000000000000000", null,
                state(key(1), key(8), 1, false, true, comareaRows(1, 7))));
        assertThat(zeros.screen().accountFilterInvalid()).isFalse();
        assertThat(zeros.screen().cardFilterInvalid()).isFalse();
        assertThat(zeros.screen().rows().get(0).cardNumber()).isEqualTo(key(1));
    }

    @Test
    void filterErrorProtectsEverySelectField_frS0411() {
        // :1078-1080, :768-783 — WS-PROTECT-SEL-FLGS protects rows 2-7; the
        // array init protects row 1 on any edit pass too (:714-726).
        seed(10);
        CardListPageState pageOne = state(key(1), key(8), 1, false, true, comareaRows(1, 7));
        CardListResponse response = service.browse(new CardListRequest(
                "ENTER", null, "NOT-A-CARD", null, pageOne));

        assertThat(response.screen().errorMessage())
                .isEqualTo("CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER");
        assertThat(response.screen().cardFilterInvalid()).isTrue();
        assertThat(response.screen().rows())
                .allSatisfy(row -> assertThat(row.selectProtected()).isTrue());
        assertThat(response.screen().cursorField()).isEqualTo("cardsid");
    }

    @Test
    void multipleSelectsFlagOnlyOneRecordAndRebrowseFromStart_frS0412() {
        // :1072-1094 — more than one S/U shows the 'only one' message; the
        // selection error re-browses from file start but keeps the page
        // number (S04-B2 parity quirk, :300 + :419-438).
        seed(10);
        CardListPageState pageTwo = state(key(8), key(10), 5, false, false, comareaRows(8, 10));
        CardListResponse response = service.browse(new CardListRequest(
                "ENTER", null, null, List.of("S", "", "U", "", "", "", ""), pageTwo));

        assertThat(response.screen().errorMessage())
                .isEqualTo("PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE");
        assertThat(response.screen().infoMessage())
                .isEqualTo("TYPE S FOR DETAIL, U TO UPDATE ANY RECORD");
        assertThat(response.screen().rows().get(0).selectError()).isTrue();
        assertThat(response.screen().rows().get(2).selectError()).isTrue();
        assertThat(response.screen().rows().get(1).selectError()).isFalse();
        // S04-B2 — re-browse from file start, page number preserved.
        assertThat(response.screen().rows().stream().map(r -> r.cardNumber()).toList())
                .containsExactly(key(1), key(2), key(3), key(4), key(5), key(6), key(7));
        assertThat(response.screen().screenNumber()).isEqualTo(5);
        // The typed codes echo back into the select fields.
        assertThat(response.screen().rows().get(0).select()).isEqualTo("S");
        assertThat(response.screen().rows().get(2).select()).isEqualTo("U");
    }

    @Test
    void invalidSelectCodeShowsActionErrorAndRowCursor_frS0413() {
        // :1096-1116 — anything but S/U/blank is INVALID ACTION CODE; rows 2-7
        // take the cursor, row 1 only colours (no -1 length at :773).
        seed(10);
        CardListPageState pageOne = state(key(1), key(8), 1, false, true, comareaRows(1, 7));
        CardListResponse response = service.browse(new CardListRequest(
                "ENTER", null, null, List.of("", "", "X", "", "", "", ""), pageOne));

        assertThat(response.screen().errorMessage()).isEqualTo("INVALID ACTION CODE");
        assertThat(response.screen().rows().get(2).selectError()).isTrue();
        assertThat(response.screen().cursorField()).isEqualTo("crdsel3");
        assertThat(response.outcome()).isEqualTo("page");

        CardListResponse rowOne = service.browse(new CardListRequest(
                "ENTER", null, null, List.of("X", "", "", "", "", "", ""), pageOne));
        assertThat(rowOne.screen().rows().get(0).selectError()).isTrue();
        assertThat(rowOne.screen().cursorField()).isNull();
    }

    @Test
    void enterSelectSNavigatesToCardViewWithRowKeys_frS0414() {
        // :517-537 — ENTER + S hands the selected row's account and card to
        // COCRDSLC.
        seed(10);
        CardListPageState pageOne = state(key(1), key(8), 1, false, true, comareaRows(1, 7));
        CardListResponse response = service.browse(new CardListRequest(
                "ENTER", null, null, List.of("", "", "", "", "S", "", ""), pageOne));

        assertThat(response.outcome()).isEqualTo("navigate");
        assertThat(response.navigation().program()).isEqualTo("COCRDSLC");
        assertThat(response.navigation().action()).isEqualTo("S");
        assertThat(response.navigation().accountId()).isEqualTo(1L);
        assertThat(response.navigation().cardNumber()).isEqualTo(key(5));
    }

    @Test
    void enterSelectUNavigatesToCardUpdateWithRowKeys_frS0415() {
        // :538-569 — ENTER + U hands the selected row to COCRDUPC.
        seed(10);
        CardListPageState pageOne = state(key(1), key(8), 1, false, true, comareaRows(1, 7));
        CardListResponse response = service.browse(new CardListRequest(
                "ENTER", null, null, List.of("U", "", "", "", "", "", ""), pageOne));

        assertThat(response.outcome()).isEqualTo("navigate");
        assertThat(response.navigation().program()).isEqualTo("COCRDUPC");
        assertThat(response.navigation().action()).isEqualTo("U");
        assertThat(response.navigation().cardNumber()).isEqualTo(key(1));
    }

    @Test
    void pfSevenReadsBackwardsFromFirstAnchor_frS0416() {
        // :503-516 + :1264-1380 — the previous page ends just below the
        // current first key; the old first key becomes the next anchor.
        seed(10);
        CardListPageState pageTwo = state(key(8), key(10), 2, false, false, comareaRows(8, 10));
        CardListResponse response = aid("PF7", pageTwo);

        assertThat(response.screen().screenNumber()).isEqualTo(1);
        assertThat(response.screen().rows().stream().map(r -> r.cardNumber()).toList())
                .containsExactly(key(1), key(2), key(3), key(4), key(5), key(6), key(7));
        assertThat(response.pageState().firstCardNumber()).isEqualTo(key(1));
        assertThat(response.pageState().lastCardNumber()).isEqualTo(key(8));
        assertThat(response.pageState().nextPageExists()).isTrue();
        // PF7 lands on page 1 after the decrement — the setup message still
        // shows 'NO PREVIOUS PAGES TO DISPLAY' (1400 evaluates the NEW page
        // number, :909-910). Parity quirk, kept.
        assertThat(response.screen().errorMessage())
                .isEqualTo("NO PREVIOUS PAGES TO DISPLAY");
    }

    @Test
    void pageNumberWrapsPastNineAndClampsBelowZero_frS0417() {
        // PIC 9(1) arithmetic (:492, :508, :1177-1181): ADD 1 on 9 stores 0,
        // but the first-row fill immediately rescues 0 back to 1; only an
        // empty read leaves the wrapped 0 visible. SUBTRACT 1 on 0 stores 1
        // unsigned.
        seed(10);
        CardListPageState pageNine = state(key(8), key(9), 9, false, true, comareaRows(8, 10));
        CardListResponse wrapped = aid("PF8", pageNine);
        assertThat(wrapped.pageState().screenNumber()).isEqualTo(1);
        assertThat(wrapped.screen().rows().get(0).cardNumber()).isEqualTo(key(9));

        CardListPageState emptyWrap = state(key(8), "9999999999999999", 9, false, true,
                comareaRows(8, 10));
        CardListResponse noRows = aid("PF8", emptyWrap);
        assertThat(noRows.pageState().screenNumber()).isEqualTo(0);

        CardListPageState pageZero = state(key(5), key(10), 0, false, true, comareaRows(5, 7));
        CardListResponse clamped = aid("PF7", pageZero);
        assertThat(clamped.pageState().screenNumber()).isEqualTo(1);
        // Landing on page 1 shows the no-previous-pages message (:909-910).
        assertThat(clamped.screen().errorMessage())
                .isEqualTo("NO PREVIOUS PAGES TO DISPLAY");
    }

    @Test
    void commareaRoundTripsRowsAnchorsAndFlags_frS0418() {
        // :229-260 — everything the redisplay needs lives in the echoed
        // COMMAREA record.
        seed(10);
        CardListPageState pageOne = state(key(1), key(8), 1, false, true, comareaRows(1, 7));
        CardListResponse response = aid("PF8", pageOne);

        CardListPageState next = response.pageState();
        assertThat(next.screenNumber()).isEqualTo(2);
        assertThat(next.firstCardNumber()).isEqualTo(key(8));
        assertThat(next.lastCardNumber()).isEqualTo(key(10));
        assertThat(next.nextPageExists()).isFalse();
        assertThat(next.rows().stream().map(r -> r == null ? null : r.cardNumber()).toList())
                .containsExactly(key(8), key(9), key(10), null, null, null, null);
    }

    @Test
    void infoLineShowsByDefaultAndSuppressesOnErrors_frS0419() {
        // :895-931 — the info line shows unless a filter error, PF7 on page
        // 1, NO MORE PAGES, or a no-records result suppresses it.
        seed(10);
        CardListPageState pageOne = state(key(1), key(8), 1, false, true, comareaRows(1, 7));

        CardListResponse normal = aid("ENTER", pageOne);
        assertThat(normal.screen().infoMessage())
                .isEqualTo("TYPE S FOR DETAIL, U TO UPDATE ANY RECORD");

        CardListResponse filterError = service.browse(new CardListRequest(
                "ENTER", "BAD", null, null, pageOne));
        assertThat(filterError.screen().infoMessage()).isNull();

        CardListResponse pf7PageOne = aid("PF7", pageOne);
        assertThat(pf7PageOne.screen().infoMessage()).isNull();
    }

    @Test
    void emptyRowSlotsRenderProtectedSelects_frS0420() {
        // :692-743 — only filled rows get an enterable select; the rest are
        // protected blanks.
        seed(3);
        CardListResponse response =
                service.browse(new CardListRequest("ENTER", null, null, null, null));

        assertThat(response.screen().rows().stream().map(r -> r.selectProtected()).toList())
                .containsExactly(false, false, false, true, true, true, true);
        assertThat(response.screen().errorMessage())
                .isEqualTo("NO MORE RECORDS TO SHOW");
    }

    @Test
    void backwardBrowseExhaustionRendersFileErrorLayout_s04B4() {
        // :1361-1369 + :153-171 — READPREV hitting BOF before filling a page
        // keeps the first anchor, bottom-aligns the partial rows, and shows
        // the 75-byte WS-FILE-ERROR-MESSAGE verbatim. It only renders when
        // PF7 lands on a page above 1 — landing on 1 is overridden by
        // 'NO PREVIOUS PAGES TO DISPLAY' (:909-910).
        seed(9);
        CardListPageState midPage = state(key(3), key(9), 3, false, true, comareaRows(3, 9));
        CardListResponse response = aid("PF7", midPage);

        assertThat(response.screen().errorMessage()).isEqualTo(
                "File Error: READ     on CARDDAT   returned RESP 000000020 ,RESP2 000000090 ");
        assertThat(response.screen().errorMessage()).hasSize(75);
        assertThat(response.pageState().firstCardNumber()).isEqualTo(key(3));
        assertThat(response.screen().rows().stream().map(r -> r == null ? null : r.cardNumber())
                .toList())
                .containsExactly(null, null, null, null, null, key(1), key(2));
        assertThat(response.pageState().screenNumber()).isEqualTo(2);
    }

    @Test
    void pfThreeOnReentryExitsToMenu_frS0422() {
        // :384-406 — PF3 while the program owns the conversation XCTLs back
        // to COMEN01C. On a fresh entry it is just another fresh browse.
        seed(10);
        CardListResponse exited = aid("PF3",
                state(key(1), key(8), 1, false, true, comareaRows(1, 7)));
        assertThat(exited.outcome()).isEqualTo("exit");

        CardListResponse fresh = service.browse(
                new CardListRequest("PF3", null, null, null, null));
        assertThat(fresh.outcome()).isEqualTo("page");
        assertThat(fresh.screen().rows().get(0).cardNumber()).isEqualTo(key(1));
    }

    @Test
    void verbatimMessagesMatchCobolWorkingStorage_frS0423() {
        // :112-126, :153-171 — the message catalog must match WS-MISC-STORAGE
        // byte for byte.
        assertThat(CobolMessages.CARD_SELECT_ONE)
                .isEqualTo("PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE");
        assertThat(CobolMessages.CARD_INVALID_ACTION).isEqualTo("INVALID ACTION CODE");
        assertThat(CobolMessages.CARD_NO_RECORDS_FOUND)
                .isEqualTo("NO RECORDS FOUND FOR THIS SEARCH CONDITION.");
        assertThat(CobolMessages.CARD_NO_MORE_RECORDS)
                .isEqualTo("NO MORE RECORDS TO SHOW");
        assertThat(CobolMessages.CARD_NO_PREVIOUS_PAGES)
                .isEqualTo("NO PREVIOUS PAGES TO DISPLAY");
        assertThat(CobolMessages.CARD_NO_MORE_PAGES)
                .isEqualTo("NO MORE PAGES TO DISPLAY");
        assertThat(CobolMessages.CARD_INFO_ACTIONS)
                .isEqualTo("TYPE S FOR DETAIL, U TO UPDATE ANY RECORD");
        assertThat(CobolMessages.CARD_FILE_ERROR_READ)
                .isEqualTo("File Error: READ     on CARDDAT   "
                        + "returned RESP 000000020 ,RESP2 000000090 ");
    }
}
