package com.carddemo.service;

import com.carddemo.api.CobolMessages;
import com.carddemo.model.Transaction;
import com.carddemo.repository.TransactionRepository;
import com.carddemo.service.TransactionListService.Form;
import com.carddemo.service.TransactionListService.Page;
import com.carddemo.service.TransactionListService.Row;
import com.carddemo.service.TransactionListService.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * COTRN00C branch coverage, derived from app/cbl/COTRN00C.cbl and
 * FR-S07-01..21 — expectations were traced from the COBOL, not from the new
 * code. The repository double plays the VSAM file: an ascending key browse
 * plus a descending predecessor read for READPREV.
 */
class TransactionListServiceTest {

    private TransactionRepository repository;
    private TransactionListService service;
    private List<Transaction> file; // ascending tran-id order, like TRANSACT

    @BeforeEach
    void setUp() {
        repository = mock(TransactionRepository.class);
        file = new ArrayList<>();
        when(repository.findByTranIdGreaterThanEqual(anyString(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    return new PageImpl<>(file.stream()
                            .filter(row -> row.getTranId().compareTo(key) >= 0).toList());
                });
        when(repository.findByTranIdLessThanOrderByTranIdDesc(anyString(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    return file.stream().filter(row -> row.getTranId().compareTo(key) < 0)
                            .sorted(Comparator.comparing(Transaction::getTranId).reversed())
                            .toList();
                });
        service = new TransactionListService(repository);
    }

    private static Transaction tx(int seq, String amount, String description) {
        Transaction value = new Transaction();
        value.setTranId("%016d".formatted(seq));
        value.setTranAmount(new BigDecimal(amount));
        value.setTranDescription(description);
        value.setTranOriginTimestamp(LocalDateTime.of(2025, 3, 4, 1, 2, 3));
        return value;
    }

    private void seed(int count) {
        for (int i = 1; i <= count; i++) {
            file.add(tx(i, "49.50", "PURCHASE AT STORE NUMBER " + i));
        }
    }

    private static Form form(List<Row> rows, String pageDisplay, State state,
                             String searchId, List<String> sels) {
        return new Form(searchId,
                sels != null ? sels : Collections.nCopies(10, ""),
                rows != null ? rows : Collections.nCopies(10, Row.BLANK),
                pageDisplay, state.firstId(), state.lastId(),
                Long.toString(state.pageNum()), state.nextPage() ? "Y" : "");
    }

    private static List<Row> displayed(Page page) {
        return page.rows().stream().filter(row -> !row.isBlank()).toList();
    }

    @Test
    void firstEntryBrowsesFromLowValuesAndSetsNextPage_frS0702_frS0704() {
        // PGM-CONTEXT initial branch (:112-116) == ENTER with LOW-VALUES key.
        seed(15);
        Page page = service.firstDisplay();

        assertThat(displayed(page)).hasSize(10);
        assertThat(displayed(page).get(0).tranId()).isEqualTo("0000000000000001");
        assertThat(displayed(page).get(9).tranId()).isEqualTo("0000000000000010");
        assertThat(page.pageDisplay()).isEqualTo("00000001");
        assertThat(page.pageNum()).isEqualTo(1);
        assertThat(page.firstId()).isEqualTo("0000000000000001");
        assertThat(page.lastId()).isEqualTo("0000000000000010");
        assertThat(page.nextPage()).isTrue();   // peek found row 11 (:315-317)
        assertThat(page.message()).isNull();
    }

    @Test
    void rowFieldsFollowTheCobolEdits_frS0703() {
        // :383-445 — date mm/dd/yy, description truncated at 26, amount
        // +99999999.99 with the high-order digit dropped past 8 integer digits.
        file.add(tx(1, "1234.50", "A VERY LONG DESCRIPTION FIELD OVER TWENTY SIX CHARS"));
        file.add(tx(2, "-1.23", "NEGATIVE"));
        file.add(tx(3, "123456789.12", "HUGE"));

        Page page = service.firstDisplay();

        assertThat(page.rows().get(0).date()).isEqualTo("03/04/25");
        assertThat(page.rows().get(0).description()).hasSize(26)
                .isEqualTo("A VERY LONG DESCRIPTION FI");
        assertThat(page.rows().get(0).amount()).isEqualTo("+00001234.50");
        assertThat(page.rows().get(1).amount()).isEqualTo("-00000001.23");
        assertThat(page.rows().get(2).amount()).isEqualTo("+23456789.12");
        assertThat(page.pageNum()).isEqualTo(1);      // partial fill still counts a page
        assertThat(page.nextPage()).isFalse();
        assertThat(page.message()).isEqualTo(CobolMessages.TRANSACTION_BOTTOM); // :639-645
    }

    @Test
    void blankFileShowsTopOfPage_frS0719() {
        // STARTBR NOTFND (:605-611): "at the top" wording, zero page.
        Page page = service.firstDisplay();

        assertThat(displayed(page)).isEmpty();
        assertThat(page.pageDisplay()).isEqualTo("00000000");
        assertThat(page.message()).isEqualTo(CobolMessages.TRANSACTION_AT_TOP);
        assertThat(page.nextPage()).isFalse();
    }

    @Test
    void enterWithNumericSearchPositionsTheBrowse_frS0705() {
        // :209-213 — IS NUMERIC over the full 16-char field moves to the key.
        seed(15);
        Form form = form(null, "00000001", State.fresh(), "0000000000000005", null);

        Page page = service.enter(form).page();

        assertThat(displayed(page).get(0).tranId()).isEqualTo("0000000000000005");
        assertThat(page.pageDisplay()).isEqualTo("00000001");
        assertThat(page.searchId()).isEmpty();        // TRNIDIN cleared (:324-325)
        assertThat(page.message()).isNull();
    }

    @Test
    void shortSearchInputIsNonNumericUnderFullFieldWidth_frS0706() {
        // The edit is IS NUMERIC over the whole X(16) field: "12" padded with
        // spaces is not numeric, so a short id fails the same way letters do.
        seed(15);
        Form form = form(null, "00000001", State.fresh(), "12", null);

        Page page = service.enter(form).page();

        assertThat(page.message()).isEqualTo("Tran ID must be Numeric ...");
        assertThat(page.pageNum()).isEqualTo(0);
    }

    @Test
    void nonNumericSearchEchoesScreenAndResetsStatePageOnly_frS0706() {
        // :211-218 + :224: the error skips the browse, the screen keeps its
        // rows/page/search (SEND echoes), but the staged state page is the 0
        // from :224 — display and state diverge by design.
        seed(15);
        List<Row> shown = List.of(new Row("0000000000000001", "03/04/25", "D", "+00000001.00"),
                new Row("0000000000000002", "03/04/25", "D", "+00000001.00"));
        List<Row> rows = new ArrayList<>(Collections.nCopies(10, Row.BLANK));
        for (int i = 0; i < shown.size(); i++) {
            rows.set(i, shown.get(i));
        }
        Form form = form(rows, "00000002",
                new State("0000000000000001", "0000000000000002", 2, true),
                "12AB", null);

        Page page = service.enter(form).page();

        assertThat(page.message()).isEqualTo("Tran ID must be Numeric ...");
        assertThat(page.pageDisplay()).isEqualTo("00000002"); // screen echo
        assertThat(page.pageNum()).isEqualTo(0);             // state staged by :224
        assertThat(page.rows()).isEqualTo(rows);
        assertThat(page.searchId()).isEqualTo("12AB");
        assertThat(page.nextPage()).isTrue();                // flag survives the error
    }

    @Test
    void selectWithSOnARowHandsOffItsTranId_frS0707() {
        // :180-195 — the first non-blank SEL wins; 'S' (or 's') transfers the
        // row's TRNID0n to COTRN01C.
        seed(15);
        Page first = service.firstDisplay();
        Form form = form(first.rows(), first.pageDisplay(),
                new State(first.firstId(), first.lastId(), 1, true), "",
                List.of("S", "", "", "", "", "", "", "", "", ""));

        assertThat(service.enter(form).selectedId()).isEqualTo("0000000000000001");

        Form lower = form(first.rows(), first.pageDisplay(),
                new State(first.firstId(), first.lastId(), 1, true), "",
                List.of("", "", "s", "", "", "", "", "", "", ""));
        assertThat(service.enter(lower).selectedId()).isEqualTo("0000000000000003");
    }

    @Test
    void invalidSelectionIsNonBlockingAndBrowseContinues_frS0708() {
        // :196-203 — the selection error does not stop the ENTER flow: a
        // forward browse still runs and the pending message is what renders.
        seed(15);
        Page first = service.firstDisplay();
        Form form = form(first.rows(), first.pageDisplay(),
                new State(first.firstId(), first.lastId(), 1, true), "",
                List.of("X", "", "", "", "", "", "", "", "", ""));

        Page page = service.enter(form).page();

        assertThat(page.message()).isEqualTo("Invalid selection. Valid value is S");
        assertThat(displayed(page).get(0).tranId()).isEqualTo("0000000000000001");
        assertThat(page.pageNum()).isEqualTo(1);
    }

    @Test
    void pf8PagesForwardOverTheSkipRead_frS0710() {
        // PF8 -> forward from TRNID-LAST with the :285-287 skip-read that
        // consumes the positioned (already displayed) record.
        seed(15);
        Form form = form(null, "00000001",
                new State("0000000000000001", "0000000000000010", 1, true), "", null);

        Page page = service.pf8(form);

        assertThat(displayed(page)).hasSize(5);
        assertThat(displayed(page).get(0).tranId()).isEqualTo("0000000000000011");
        assertThat(page.pageDisplay()).isEqualTo("00000002");
        assertThat(page.firstId()).isEqualTo("0000000000000011");
        assertThat(page.lastId()).isEqualTo("0000000000000010"); // row10 blank: kept
        assertThat(page.nextPage()).isFalse();
        assertThat(page.message()).isEqualTo(CobolMessages.TRANSACTION_BOTTOM);
    }

    @Test
    void pf8WithoutNextPageEchoesAlreadyAtBottom_frS0714() {
        // :269-273 — NEXT-PAGE-N gate, screen redisplayed unchanged.
        seed(15);
        List<Row> rows = List.copyOf(service.firstDisplay().rows());
        Form form = form(rows, "00000002",
                new State("0000000000000011", "0000000000000015", 2, false), "", null);

        Page page = service.pf8(form);

        assertThat(page.message()).isEqualTo("You are already at the bottom of the page...");
        assertThat(page.rows()).isEqualTo(rows);
        assertThat(page.pageNum()).isEqualTo(2);
    }

    @Test
    void pf7PagesBackwardWithDescendingFill_frS0711() {
        // :333-376 — skip-read consumes TRNID-FIRST, ten READPREVs fill rows
        // bottom-up, peek decrements the page number.
        seed(15);
        List<Row> current = List.copyOf(service.pf8(
                form(null, "00000001",
                        new State("0000000000000001", "0000000000000010", 1, true), "", null))
                .rows());
        Form form = form(current, "00000002",
                new State("0000000000000011", "0000000000000010", 2, false), "", null);

        Page page = service.pf7(form);

        assertThat(displayed(page)).hasSize(10);
        assertThat(displayed(page).get(0).tranId()).isEqualTo("0000000000000001");
        assertThat(displayed(page).get(9).tranId()).isEqualTo("0000000000000010");
        assertThat(page.pageDisplay()).isEqualTo("00000001");
        assertThat(page.pageNum()).isEqualTo(1);
        assertThat(page.firstId()).isEqualTo("0000000000000001");
        assertThat(page.lastId()).isEqualTo("0000000000000010");
        assertThat(page.message()).isEqualTo(CobolMessages.TRANSACTION_TOP);
    }

    @Test
    void pf7AtTopEchoesAndForcesTheNextPageFlag_frS0713() {
        // :244-251 + the :242 quirk — NEXT-PAGE-YES is forced before the page
        // gate, so the forced flag persists on the outgoing state even though
        // the screen redisplays unchanged.
        seed(15);
        List<Row> rows = List.copyOf(service.firstDisplay().rows());
        Form form = form(rows, "00000001",
                new State("0000000000000001", "0000000000000010", 1, false), "", null);

        Page page = service.pf7(form);

        assertThat(page.message()).isEqualTo("You are already at the top of the page...");
        assertThat(page.rows()).isEqualTo(rows);
        assertThat(page.nextPage()).isTrue();   // forced flag (:242)
    }

    @Test
    void pf7WithFewerPredecessorsKeepsPageAndShowsTop() {
        // ENDFILE mid-fill (:673-679): the peek block never runs, so the page
        // number keeps its value and rows 7-10 hold the whole head of file.
        seed(8);
        Form form = form(null, "00000002",
                new State("0000000000000005", "0000000000000008", 2, true), "", null);

        Page page = service.pf7(form);

        assertThat(displayed(page)).hasSize(4);
        assertThat(page.rows().get(6).tranId()).isEqualTo("0000000000000001");
        assertThat(page.rows().get(9).tranId()).isEqualTo("0000000000000004");
        assertThat(page.pageNum()).isEqualTo(2);         // unchanged (:359 not reached)
        assertThat(page.firstId()).isEqualTo("0000000000000005"); // row1 blank: kept
        assertThat(page.message()).isEqualTo(CobolMessages.TRANSACTION_TOP);
    }

    @Test
    void pf8AfterForcedTopEchoesForwardAgain() {
        // The PF7 forced-flag quirk composed: page-1 PF7 sets NEXT-YES, so a
        // following PF8 pages forward instead of reporting the bottom.
        seed(15);
        Page first = service.firstDisplay();
        Form echoed = form(first.rows(), first.pageDisplay(),
                new State(first.firstId(), first.lastId(), 1, true), "", null);

        Page page = service.pf8(echoed);

        assertThat(displayed(page).get(0).tranId()).isEqualTo("0000000000000011");
        assertThat(page.pageNum()).isEqualTo(2);
    }

    @Test
    void forwardBrowseNotFoundKeepsScreenAndShowsTop_frS0719() {
        // ENTER at a numeric key past the last record: STARTBR NOTFND.
        seed(15);
        List<Row> rows = List.copyOf(service.firstDisplay().rows());
        Form form = form(rows, "00000001", State.fresh(), "9999999999999999", null);

        Page page = service.enter(form).page();

        assertThat(page.message()).isEqualTo("You are at the top of the page...");
        assertThat(page.rows()).isEqualTo(rows);
        assertThat(page.pageDisplay()).isEqualTo("00000000"); // staged state page
        assertThat(page.nextPage()).isFalse();
    }

    @Test
    void backwardStartbrNotFoundEchoesTop_frS0719() {
        // PF7 whose saved TRNID-FIRST no longer exists above the file end.
        seed(5);
        Form form = form(null, "00000002",
                new State("9999999999999999", "", 2, true), "", null);

        Page page = service.pf7(form);

        assertThat(page.message()).isEqualTo("You are at the top of the page...");
        assertThat(page.pageNum()).isEqualTo(2);
    }

    @Test
    void invalidAidEchoesScreenWithInvalidKey_frS0718() {
        // :129-133 + CSMSG01Y.cpy:20-21.
        seed(5);
        List<Row> rows = List.copyOf(service.firstDisplay().rows());
        Form form = form(rows, "00000001",
                new State("0000000000000001", "0000000000000005", 1, false), "", null);

        Page page = service.invalidAid(form);

        assertThat(page.message()).isEqualTo("Invalid key pressed. Please see below...");
        assertThat(page.rows()).isEqualTo(rows);
    }

    @Test
    void storeErrorEchoesUnableToLookup_frS0720() {
        // RESP "other" on the STARTBR (:578-583).
        when(repository.findByTranIdGreaterThanEqual(anyString(), any(Pageable.class)))
                .thenThrow(new DataAccessResourceFailureException("store unavailable"));

        Page page = service.firstDisplay();

        assertThat(page.message()).isEqualTo("Unable to lookup transaction...");
        assertThat(displayed(page)).isEmpty();
    }

    @Test
    void tamperedPagingStateDegradesToFreshValues() {
        // S07-B5: a client-held state that cannot be parsed behaves like the
        // EIBCALEN=0 re-init — LOW-VALUES keys and page zero.
        seed(3);
        Form form = new Form("", Collections.nCopies(10, ""),
                Collections.nCopies(10, Row.BLANK), "zzzz", "bad-first", "bad-last",
                "not-a-number", "Q");

        Page page = service.firstDisplay(); // sanity
        assertThat(page.pageNum()).isEqualTo(1);

        Page pf8 = service.pf8(form);       // nextPage 'Q' -> not Y -> bottom echo
        assertThat(pf8.message()).isEqualTo("You are already at the bottom of the page...");
        assertThat(pf8.pageNum()).isEqualTo(0);
    }
}
