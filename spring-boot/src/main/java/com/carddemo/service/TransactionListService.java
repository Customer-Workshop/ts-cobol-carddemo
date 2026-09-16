package com.carddemo.service;

import com.carddemo.api.CobolMessages;
import com.carddemo.model.Transaction;
import com.carddemo.repository.TransactionRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * COTRN00C (tran CT00, map COTRN0A) — the transaction-list browse — as a
 * client-held cursor state machine over the shared TransactionRepository
 * (S07-B3). Every method takes the submitted form (screen fields echo back
 * because every map field is FSET) plus the CDEMO-CT00-INFO paging state
 * (S07-B5) and returns the screen to render. Paragraph cites below are
 * app/cbl/COTRN00C.cbl.
 */
@Service
public class TransactionListService {
    private static final int PAGE_SIZE = TransactionService.COBOL_PAGE_SIZE;
    private static final DateTimeFormatter ROW_DATE = DateTimeFormatter.ofPattern("MM/dd/yy");

    // STARTBR HIGH-VALUES (:259-263): a key above every 16-char digit string.
    private static final String HIGH_VALUES = "￿".repeat(16);

    /** One rendered row slot: TRNID0n / TDATE0n / TDESC0n / TAMT00n. */
    public record Row(String tranId, String date, String description, String amount) {
        static final Row BLANK = new Row("", "", "", "");
        boolean isBlank() { return tranId == null || tranId.isBlank(); }
    }

    /** CDEMO-CT00-INFO slice (:62-70), round-tripped as hidden fields. */
    public record State(String firstId, String lastId, long pageNum, boolean nextPage) {
        public static State fresh() { return new State("", "", 0, false); }
    }

    /** The submitted screen: search input, the ten SEL fields, the ten
     * previously displayed rows (all FSET), the displayed page number and the
     * COMMAREA paging state. Missing/tampered pieces degrade to LOW-VALUES /
     * zero, matching the EIBCALEN=0 re-init behaviour (S07-B5). */
    public record Form(String searchId, List<String> selections, List<Row> rows,
                       String pageDisplay, String firstId, String lastId,
                       String pageNum, String nextPageFlg) {
        public State state() {
            long num;
            try {
                num = Long.parseLong(pageNum == null ? "" : pageNum.trim());
            } catch (NumberFormatException exception) {
                num = 0;
            }
            return new State(nvl(firstId), nvl(lastId), num, "Y".equals(nextPageFlg));
        }

        public static Form blank() {
            return new Form("", Collections.nCopies(PAGE_SIZE, ""),
                    Collections.nCopies(PAGE_SIZE, Row.BLANK), "00000000", "", "", "", "");
        }

        public static List<Row> rows(List<String> ids, List<String> dates,
                                     List<String> descriptions, List<String> amounts) {
            List<Row> rows = new ArrayList<>(Collections.nCopies(PAGE_SIZE, Row.BLANK));
            for (int i = 0; i < PAGE_SIZE; i++) {
                rows.set(i, new Row(nvl(at(ids, i)), nvl(at(dates, i)),
                        nvl(at(descriptions, i)), nvl(at(amounts, i))));
            }
            return rows;
        }

        public static List<String> selections(List<String> values) {
            List<String> sels = new ArrayList<>(Collections.nCopies(PAGE_SIZE, ""));
            for (int i = 0; i < PAGE_SIZE && i < sizeOf(values); i++) {
                sels.set(i, nvl(at(values, i)));
            }
            return sels;
        }

        private static String at(List<String> values, int i) {
            return values != null && i < values.size() ? values.get(i) : null;
        }

        private static int sizeOf(List<String> values) {
            return values == null ? 0 : values.size();
        }

        private static String nvl(String value) { return value == null ? "" : value; }
    }

    /** The screen to render: ten row slots (blank where unfilled), the
     * displayed page number, the outgoing paging state, the echoed search and
     * selection inputs, and the ERRMSG line. */
    public record Page(List<Row> rows, String pageDisplay,
                       String firstId, String lastId, long pageNum, boolean nextPage,
                       String searchId, List<String> selections,
                       String message, boolean info) {
        public static Page unchanged(Form form, State state, String message, boolean info) {
            // SEND without repopulating: rows, page display, search and SEL
            // inputs keep what the client showed (FSET).
            return new Page(form.rows(), form.pageDisplay(), state.firstId(), state.lastId(),
                    state.pageNum(), state.nextPage(), form.searchId(), form.selections(),
                    message, info);
        }
    }

    /** PROCESS-ENTER-KEY result: a selected id when a row 'S' hands off to
     * COTRN01C (XCTL, terminal — :186-195), otherwise the page to render. */
    public record EnterOutcome(String selectedId, Page page) {
        static EnterOutcome select(String id) { return new EnterOutcome(id, null); }
        static EnterOutcome show(Page page) { return new EnterOutcome(null, page); }
    }

    private final TransactionRepository transactions;

    public TransactionListService(TransactionRepository transactions) {
        this.transactions = transactions;
    }

    // First entry (PGM-CONTEXT enter, :112-116): ENTER on an empty map —
    // browse from LOW-VALUES, page 1.
    public Page firstDisplay() {
        return enter(Form.blank()).page();
    }

    // PROCESS-ENTER-KEY (:146-229): selection scan, then the search-id edit,
    // then a forward page from the key.
    public EnterOutcome enter(Form form) {
        String pending = null;
        for (int i = 0; i < PAGE_SIZE; i++) {
            String sel = selAt(form.selections(), i);
            if (sel == null) {
                continue;
            }
            String selectedId = form.rows().get(i).tranId();
            if (selectedId != null && !selectedId.isBlank()) {
                char flag = sel.charAt(0);   // SEL is X(1): a longer post truncates
                if (flag == 'S' || flag == 's') {
                    return EnterOutcome.select(selectedId);
                }
                // :196-203 — non-blocking; a later paging message overwrites it
                pending = CobolMessages.TRANSACTION_SELECTION_INVALID;
            }
            break;
        }
        String field = padRight(form.searchId() == null ? "" : form.searchId(), 16);
        String key;
        if (field.isBlank()) {
            key = "";
        } else if (allDigits(field)) {
            key = field;
        } else {
            // :211-218, :283 — ERR-FLG skips the browse; page resets to 0 at
            // :224 but the screen keeps the displayed rows/page/search.
            return EnterOutcome.show(Page.unchanged(form,
                    new State(form.state().firstId(), form.state().lastId(), 0,
                            form.state().nextPage()),
                    CobolMessages.TRANSACTION_ID_NOT_NUMERIC, false));
        }
        // :224-225 — page number resets to 0, then a forward page from the key
        return EnterOutcome.show(forward(form, key, false,
                new State(form.state().firstId(), form.state().lastId(), 0,
                        form.state().nextPage()),
                pending));
    }

    // PROCESS-PF7-KEY (:234-252): NEXT-PAGE-YES is forced before the page gate
    // (the :242 quirk), so the flag persists on the outgoing state.
    public Page pf7(Form form) {
        State in = new State(form.state().firstId(), form.state().lastId(),
                form.state().pageNum(), true);
        if (in.pageNum() <= 1) {
            return Page.unchanged(form, in, CobolMessages.TRANSACTION_ALREADY_TOP, false);
        }
        String key = in.firstId().isBlank() ? "" : in.firstId();
        return backward(form, key, in);
    }

    // PROCESS-PF8-KEY (:257-274): the next-page flag gates the forward page.
    public Page pf8(Form form) {
        State in = form.state();
        if (!in.nextPage()) {
            return Page.unchanged(form, in, CobolMessages.TRANSACTION_ALREADY_BOTTOM, false);
        }
        String key = in.lastId().isBlank() ? HIGH_VALUES : in.lastId();
        return forward(form, key, true, in, null);
    }

    // Any other AID (:129-133, CSMSG01Y.cpy:20-21): redisplay unchanged.
    public Page invalidAid(Form form) {
        return Page.unchanged(form, form.state(), CobolMessages.INVALID_KEY_PRESSED, false);
    }

    // PROCESS-PAGE-FORWARD (:279-328): STARTBR GTEQ at the key, an optional
    // skip-read that consumes the positioned record on non-ENTER aids
    // (:285-287), ten READNEXT fills, then a peek decides the next-page flag.
    private Page forward(Form form, String key, boolean skip, State in, String pending) {
        List<Transaction> window;
        try {
            window = transactions.findByTranIdGreaterThanEqual(key,
                    PageRequest.of(0, (skip ? 1 : 0) + PAGE_SIZE + 1,
                            Sort.by(Sort.Direction.ASC, "tranId"))).getContent();
        } catch (DataAccessException exception) {
            return Page.unchanged(form, in, CobolMessages.TRANSACTION_LOOKUP_FAILED, false);
        }
        if (window.isEmpty()) {
            // STARTBR NOTFND (:605-611): "top of the page" wording even for a
            // key beyond the file's end; rows unchanged; page is the value the
            // caller staged (0 on ENTER, unchanged on PF8); next-page N.
            return new Page(form.rows(), formatPage(in.pageNum()), in.firstId(), in.lastId(),
                    in.pageNum(), false, "", form.selections(),
                    CobolMessages.TRANSACTION_AT_TOP, false);
        }
        List<Transaction> remaining = skip ? window.subList(1, window.size()) : window;
        List<Row> rows = new ArrayList<>(Collections.nCopies(PAGE_SIZE, Row.BLANK));
        for (int i = 0; i < Math.min(PAGE_SIZE, remaining.size()); i++) {
            rows.set(i, row(remaining.get(i)));
        }
        // ENDFILE — mid-fill or on the peek — raises the bottom message
        // (:639-645); an invalid-selection pending message survives only when
        // the browse found another page.
        boolean peek = remaining.size() > PAGE_SIZE;
        String message = peek ? pending : CobolMessages.TRANSACTION_BOTTOM;
        long newPage = in.pageNum() + (remaining.isEmpty() ? 0 : 1);
        String first = remaining.isEmpty() ? in.firstId() : remaining.get(0).getTranId();
        String last = remaining.size() >= PAGE_SIZE
                ? remaining.get(PAGE_SIZE - 1).getTranId() : in.lastId();
        // :324-325 — PAGENUM refreshed and TRNIDIN cleared on this path.
        return new Page(rows, formatPage(newPage), first, last, newPage, peek,
                "", form.selections(), message, false);
    }

    // PROCESS-PAGE-BACKWARD (:333-376): STARTBR at TRNID-FIRST, one READPREV
    // consumes the current first row (:339-341), ten READPREVs fill rows
    // bottom-up, then a peek decides whether the page number decrements.
    private Page backward(Form form, String key, State in) {
        List<Transaction> positioned;
        List<Transaction> predecessors;
        try {
            positioned = transactions.findByTranIdGreaterThanEqual(key,
                    PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "tranId"))).getContent();
            if (positioned.isEmpty()) {
                // STARTBR NOTFND (:605-611): top wording, rows and state kept.
                return new Page(form.rows(), formatPage(in.pageNum()), in.firstId(),
                        in.lastId(), in.pageNum(), in.nextPage(), form.searchId(),
                        form.selections(), CobolMessages.TRANSACTION_AT_TOP, false);
            }
            predecessors = transactions.findByTranIdLessThanOrderByTranIdDesc(
                    positioned.get(0).getTranId(), PageRequest.of(0, PAGE_SIZE + 1));
        } catch (DataAccessException exception) {
            return Page.unchanged(form, in, CobolMessages.TRANSACTION_LOOKUP_FAILED, false);
        }
        int fill = Math.min(PAGE_SIZE, predecessors.size());
        boolean peek = predecessors.size() > PAGE_SIZE;
        List<Row> rows = new ArrayList<>(Collections.nCopies(PAGE_SIZE, Row.BLANK));
        for (int i = 0; i < fill; i++) {
            rows.set(PAGE_SIZE - 1 - i, row(predecessors.get(i)));
        }
        long newPage;
        if (fill < PAGE_SIZE) {
            // ENDFILE mid-fill: the peek block never runs, page keeps its value.
            newPage = in.pageNum();
        } else {
            // Full page + peek (:359-369): more predecessors and page > 1
            // decrement, otherwise the page clamps to 1 (peek ENDFILE also
            // raises the top message at :673-679).
            newPage = peek && in.pageNum() > 1 ? in.pageNum() - 1 : 1;
        }
        String first = fill == PAGE_SIZE ? predecessors.get(PAGE_SIZE - 1).getTranId()
                : in.firstId();
        String last = fill > 0 ? predecessors.get(0).getTranId() : in.lastId();
        // The backward path never clears TRNIDIN.
        return new Page(rows, formatPage(newPage), first, last, newPage, in.nextPage(),
                form.searchId(), form.selections(),
                peek ? null : CobolMessages.TRANSACTION_TOP, false);
    }

    // POPULATE-TRAN-DATA (:383-445): id as-is, date mm/dd/yy from the origin
    // timestamp (:384-388), description truncated at 26 (:395), amount
    // edited +99999999.99 (:56, :383).
    private Row row(Transaction value) {
        LocalDateTime origin = value.getTranOriginTimestamp();
        String description = value.getTranDescription();
        return new Row(value.getTranId(),
                origin == null ? "" : origin.format(ROW_DATE),
                description == null ? ""
                        : description.substring(0, Math.min(26, description.length())),
                formatAmount(value.getTranAmount()));
    }

    // PIC +99999999.99: explicit sign, 8 integer digits zero-filled (a COBOL
    // MOVE drops digits above the eighth for |amount| >= 100,000,000), 2
    // decimals.
    static String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        long cents = amount.abs().movePointRight(2).longValue();
        long intDigits = (cents / 100) % 100_000_000;
        return (amount.signum() < 0 ? "-" : "+")
                + "%08d.%02d".formatted(intDigits, cents % 100);
    }

    static String formatPage(long pageNum) {
        return "%08d".formatted(Math.max(0, Math.min(pageNum, 99_999_999)));
    }

    private static String selAt(List<String> selections, int i) {
        if (selections == null || i >= selections.size()) {
            return null;
        }
        String sel = selections.get(i);
        return sel == null || sel.isBlank() ? null : sel;
    }

    private static String padRight(String value, int length) {
        if (value.length() >= length) {
            return value.substring(0, length);
        }
        return value + " ".repeat(length - value.length());
    }

    private static boolean allDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
