package com.carddemo.service;

import com.carddemo.api.CardListNavigation;
import com.carddemo.api.CardListPageState;
import com.carddemo.api.CardListRequest;
import com.carddemo.api.CardListResponse;
import com.carddemo.api.CardListRow;
import com.carddemo.api.CardListRowView;
import com.carddemo.api.CardListScreenView;
import com.carddemo.api.CardResponse;
import com.carddemo.api.CardUpdateCommarea;
import com.carddemo.api.CardUpdateForm;
import com.carddemo.api.CardUpdateRequest;
import com.carddemo.api.CardUpdateScreen;
import com.carddemo.api.CobolApiException;
import com.carddemo.api.CobolMessages;
import com.carddemo.model.Card;
import com.carddemo.repository.CardRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
public class CardService {
    public static final int COBOL_PAGE_SIZE = 7;

    private static final String PROGRAM_DETAIL = "COCRDSLC";
    private static final String PROGRAM_UPDATE = "COCRDUPC";

    private final CardRepository cardRepository;

    public CardService(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    /**
     * COCRDLIC keyed browse: one call per AID press. {@code request.pageState}
     * is the echoed WS-THIS-PROGCOMMAREA; {@code null} means a fresh entry
     * (from the menu or any other program — COCRDLIC.cbl:315-343, :458-482).
     */
    public CardListResponse browse(CardListRequest request) {
        boolean reenter = request.pageState() != null;
        BrowseState state =
                new BrowseState(reenter ? request.pageState() : CardListPageState.fresh());

        String aid = normalizeAid(request.aid());                    // :370-380
        Edits edits = editInputs(request, reenter);                  // :951-1121

        // :384-406 — PF3 on re-entry transfers back to the menu (COMEN01C).
        if (reenter && "PF3".equals(aid)) {
            return new CardListResponse("exit", null, null, state.toPageState());
        }
        if (!"PF8".equals(aid)) {
            state.lastPageShown = false;                             // :410-414
        }

        // :418-583 — the dispatch EVALUATE, first match wins.
        if (edits.inputError()) {
            state.errorMessage = edits.errorMessage();
            if (!edits.acctFilterNotOk() && !edits.cardFilterNotOk()) {
                // S04-B2: selection errors flush the read RID back to spaces
                // (:300), so the redisplay re-browses from the start of the
                // file while CA-SCR-NUM is preserved (:419-438).
                readForward(state, "", edits);
            }
            return page(state, edits, aid);
        }
        if ("PF7".equals(aid) && state.screenNumber == 1) {
            readForward(state, state.firstCardNumber, edits);        // :441-457
            return page(state, edits, aid);
        }
        if ("PF3".equals(aid) || !reenter) {
            state.reset();
            readForward(state, "", edits);                           // :458-482
            return page(state, edits, aid);
        }
        if ("PF8".equals(aid) && state.nextPageExists) {
            // :484-502 — ADD 1 wraps PIC 9(1) on the 9→0 boundary (:492).
            state.screenNumber = (state.screenNumber + 1) % 10;
            readForward(state, state.lastCardNumber, edits);
            return page(state, edits, aid);
        }
        if ("PF7".equals(aid)) {
            // :503-516 — SUBTRACT 1 from PIC 9(1) keeps 0→1 unsigned (:508).
            state.screenNumber = state.screenNumber == 0 ? 1 : state.screenNumber - 1;
            readBackwards(state, edits);
            return page(state, edits, aid);
        }
        if (reenter && "ENTER".equals(aid) && edits.selectedRow() >= 0
                && "S".equals(edits.selections().get(edits.selectedRow()))) {
            return navigate(state, edits, PROGRAM_DETAIL, "S");      // :517-537
        }
        if (reenter && "ENTER".equals(aid) && edits.selectedRow() >= 0
                && "U".equals(edits.selections().get(edits.selectedRow()))) {
            return navigate(state, edits, PROGRAM_UPDATE, "U");      // :538-569
        }
        readForward(state, state.firstCardNumber, edits);            // :572-582
        return page(state, edits, aid);
    }

    private CardListResponse navigate(BrowseState state, Edits edits,
                                      String program, String action) {
        CardListRow row = state.rows.get(edits.selectedRow());
        CardListNavigation navigation = new CardListNavigation(program, action,
                row == null ? null : row.accountId(),
                row == null ? null : row.cardNumber());
        return new CardListResponse("navigate", navigation,
                screenView(state, edits, CobolMessages.CARD_INFO_ACTIONS, null),
                state.toPageState());
    }

    private CardListResponse page(BrowseState state, Edits edits, String aid) {
        // 1400-SETUP-MESSAGE (:895-931)
        String error = state.errorMessage;
        String info = null;
        if (!edits.acctFilterNotOk() && !edits.cardFilterNotOk()) {
            if ("PF7".equals(aid) && state.screenNumber == 1) {
                error = CobolMessages.CARD_NO_PREVIOUS_PAGES;        // :909-910
            } else if ("PF8".equals(aid) && !state.nextPageExists && state.lastPageShown) {
                error = CobolMessages.CARD_NO_MORE_PAGES;            // :911-912
            } else if ("PF8".equals(aid) && !state.nextPageExists) {
                info = CobolMessages.CARD_INFO_ACTIONS;
                state.lastPageShown = true;                          // :913-916
            } else {
                info = CobolMessages.CARD_INFO_ACTIONS;              // :917-920
            }
        }
        if (CobolMessages.CARD_NO_RECORDS_FOUND.equals(error)) {
            info = null;                                             // :926-930
        }
        return new CardListResponse("page", null,
                screenView(state, edits, info, error), state.toPageState());
    }

    private CardListScreenView screenView(BrowseState state, Edits edits,
                                          String info, String error) {
        List<CardListRowView> rows = new ArrayList<>(COBOL_PAGE_SIZE);
        for (int i = 0; i < COBOL_PAGE_SIZE; i++) {
            CardListRow row = state.rows.get(i);
            String select = edits.selections().get(i);
            rows.add(new CardListRowView(
                    select.isEmpty() ? null : select,
                    row == null ? null : row.accountId(),
                    row == null ? null : row.cardNumber(),
                    row == null ? null : row.activeStatus(),
                    edits.selectError()[i],
                    row == null || edits.protectSelectRows()));
        }
        return new CardListScreenView(state.screenNumber,
                edits.acctEcho(), edits.cardEcho(),
                edits.acctFilterNotOk(), edits.cardFilterNotOk(),
                rows, cursorField(edits), info, error);
    }

    // :837-889 — the field the -1 length lands on. Rows 2-7 only (:770-783);
    // an invalid code on row 1 colours but never takes the cursor.
    private static String cursorField(Edits edits) {
        if (edits.acctFilterNotOk()) {
            return "acctsid";
        }
        if (edits.cardFilterNotOk()) {
            return "cardsid";
        }
        for (int i = 1; i < COBOL_PAGE_SIZE; i++) {
            if (edits.selectError()[i]) {
                return "crdsel" + (i + 1);
            }
        }
        return edits.inputError() ? null : "acctsid";                // :884-885
    }

    // :370-380 — every AID except PF3/PF7/PF8 is ENTER (S04-B3). The web form
    // sends the physical F-key names too, so both spellings are accepted.
    private static String normalizeAid(String raw) {
        String aid = raw == null ? "" : raw.trim().toUpperCase();
        return switch (aid) {
            case "ENTER" -> "ENTER";
            case "PF3", "F3" -> "PF3";
            case "PF7", "F7" -> "PF7";
            case "PF8", "F8" -> "PF8";
            default -> "ENTER";
        };
    }

    // 2200-EDIT-INPUTS (:951-1121). Map fields are only received on re-entry;
    // the REST surface also accepts the filters on a fresh GET.
    private Edits editInputs(CardListRequest request, boolean reenter) {
        List<String> selections = normalizeSelections(request.selections());
        boolean[] selectError = new boolean[COBOL_PAGE_SIZE];
        if (!reenter && request.accountFilter() == null && request.cardFilter() == null) {
            return new Edits(null, null, null, null, false, false, false,
                    false, null, selections, selectError, -1);
        }
        boolean inputError = false;
        String error = null;
        Long acctFilter = null;
        String cardFilter = null;
        boolean acctNotOk = false;
        boolean cardNotOk = false;
        boolean protect = false;

        // 2210-EDIT-ACCOUNT (:1003-1030)
        String acctIn = request.accountFilter();
        if (supplied(acctIn, 11)) {
            if (!acctIn.matches("\\d{11}")) {
                inputError = true;
                acctNotOk = true;
                    protect = true;
                error = CobolMessages.CARD_ACCOUNT_FILTER_INVALID;
            } else {
                acctFilter = Long.parseLong(acctIn);
            }
        }
        // 2220-EDIT-CARD (:1036-1067)
        String cardIn = request.cardFilter();
        if (supplied(cardIn, 16)) {
            if (!cardIn.matches("\\d{16}")) {
                inputError = true;
                cardNotOk = true;
                protect = true;
                if (error == null) {
                    error = CobolMessages.CARD_FILTER_INVALID;
                }
            } else {
                cardFilter = cardIn;
            }
        }
        // 2230-EDIT-ARRAY (:1072-1116) — skipped on filter error (:1076).
        int selected = -1;
        if (reenter && !inputError) {
            long selectCount = selections.stream()
                    .filter(s -> "S".equals(s) || "U".equals(s)).count();
            if (selectCount > 1) {
                inputError = true;                                   // :1085-1089
                error = CobolMessages.CARD_SELECT_ONE;
            }
            for (int i = 0; i < COBOL_PAGE_SIZE; i++) {
                String s = selections.get(i);
                if ("S".equals(s) || "U".equals(s)) {
                    selected = i;                                    // I-SELECTED = last S/U
                    if (selectCount > 1) {
                        selectError[i] = true;
                    }
                } else if (s.isBlank()) {
                    // SELECT-BLANK — space, empty or LOW-VALUES
                } else {
                    inputError = true;
                    selectError[i] = true;
                    if (error == null) {
                        error = CobolMessages.CARD_INVALID_ACTION;
                    }
                }
            }
        }
        return new Edits(acctFilter, cardFilter,
                blankToNull(acctIn), blankToNull(cardIn),
                acctNotOk, cardNotOk, protect, inputError, error,
                selections, selectError, selected);
    }

    // "Supplied" per the FR: not LOW-VALUES, not SPACES and not the all-zero
    // value of the full X(11)/X(16) field (COCRDLIC.cbl:1018-1022, :1051-1055).
    private static boolean supplied(String raw, int length) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        return !raw.equals("0".repeat(length));
    }

    // 9200-READ-NEXT-PAGE (:1123-1263): STARTBR GTEQ + filtered READNEXT ×7,
    // then the raw unfiltered look-ahead that decides the next page (:1197-1214).
    private void readForward(BrowseState state, String startKey, Edits edits) {
        List<Card> fetched = cardRepository.browseForward(
                startKey == null ? "" : startKey,
                edits.acctFilter(), edits.cardFilter(),
                PageRequest.of(0, COBOL_PAGE_SIZE));
        List<CardListRow> rows = new ArrayList<>(Collections.nCopies(COBOL_PAGE_SIZE, null));
        for (int i = 0; i < fetched.size(); i++) {
            Card card = fetched.get(i);
            rows.set(i, new CardListRow(card.getCardAcctId(), card.getCardNumber(),
                    card.getCardActiveStatus()));
        }
        state.rows = rows;
        if (!fetched.isEmpty()) {
            state.firstCardNumber = fetched.get(0).getCardNumber();
            if (state.screenNumber == 0) {
                state.screenNumber = 1;                              // :1177-1178
            }
        }
        if (fetched.size() == COBOL_PAGE_SIZE) {
            List<Card> lookAhead = cardRepository.nextAfter(
                    fetched.get(COBOL_PAGE_SIZE - 1).getCardNumber(), PageRequest.of(0, 1));
            if (lookAhead.isEmpty()) {
                state.nextPageExists = false;
                state.lastCardNumber = fetched.get(COBOL_PAGE_SIZE - 1).getCardNumber();
                if (state.errorMessage == null) {
                    state.errorMessage = CobolMessages.CARD_NO_MORE_RECORDS;
                }
            } else {
                state.nextPageExists = true;
                state.lastCardNumber = lookAhead.get(0).getCardNumber();
            }
        } else {
            state.nextPageExists = false;
            if (!fetched.isEmpty()) {
                state.lastCardNumber = fetched.get(fetched.size() - 1).getCardNumber();
            }
            if (state.errorMessage == null) {
                state.errorMessage = CobolMessages.CARD_NO_MORE_RECORDS;
            }
            if (state.screenNumber == 1 && fetched.isEmpty()) {
                state.errorMessage = CobolMessages.CARD_NO_RECORDS_FOUND;   // :1241-1245
            }
        }
    }

    // 9300-READ-PREV-PAGE (:1264-1380): READPREV writes the newest record into
    // SCRN-COUNTER 8, then 7, ... so a partial page stays bottom-aligned.
    private void readBackwards(BrowseState state, Edits edits) {
        state.lastCardNumber = state.firstCardNumber;                  // :1268
        state.nextPageExists = true;                                   // :1287
        List<Card> fetched = state.firstCardNumber == null
                ? List.of()
                : cardRepository.browseBackward(state.firstCardNumber,
                        edits.acctFilter(), edits.cardFilter(),
                        PageRequest.of(0, COBOL_PAGE_SIZE));
        List<CardListRow> rows = new ArrayList<>(Collections.nCopies(COBOL_PAGE_SIZE, null));
        for (int i = 0; i < fetched.size(); i++) {
            Card card = fetched.get(i);
            rows.set(COBOL_PAGE_SIZE - 1 - i,
                    new CardListRow(card.getCardAcctId(), card.getCardNumber(),
                            card.getCardActiveStatus()));
        }
        state.rows = rows;
        if (fetched.size() == COBOL_PAGE_SIZE) {
            state.firstCardNumber =
                    fetched.get(COBOL_PAGE_SIZE - 1).getCardNumber();  // :1335-1341
        } else {
            // S04-B4 — READPREV hit BOF mid-page (:1361-1369): the first
            // anchor is unchanged and the file-error layout is rendered.
            state.errorMessage = CobolMessages.CARD_FILE_ERROR_READ;
        }
    }

    private static List<String> normalizeSelections(List<String> selections) {
        List<String> normalized = new ArrayList<>(Collections.nCopies(COBOL_PAGE_SIZE, ""));
        if (selections != null) {
            for (int i = 0; i < Math.min(selections.size(), COBOL_PAGE_SIZE); i++) {
                String s = selections.get(i);
                normalized.set(i, s == null ? "" : s);
            }
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record Edits(Long acctFilter, String cardFilter,
                         String acctEcho, String cardEcho,
                         boolean acctFilterNotOk, boolean cardFilterNotOk,
                         boolean protectSelectRows, boolean inputError,
                         String errorMessage, List<String> selections,
                         boolean[] selectError, int selectedRow) {
    }

    private static final class BrowseState {
        String firstCardNumber;
        String lastCardNumber;
        int screenNumber;
        boolean lastPageShown;
        boolean nextPageExists;
        String errorMessage;
        List<CardListRow> rows;

        BrowseState(CardListPageState state) {
            firstCardNumber = blankToNull(state.firstCardNumber());
            lastCardNumber = blankToNull(state.lastCardNumber());
            screenNumber = state.screenNumber();
            lastPageShown = state.lastPageShown();
            nextPageExists = state.nextPageExists();
            List<CardListRow> supplied = state.rows() == null ? List.of() : state.rows();
            rows = new ArrayList<>(Collections.nCopies(COBOL_PAGE_SIZE, null));
            for (int i = 0; i < Math.min(supplied.size(), COBOL_PAGE_SIZE); i++) {
                rows.set(i, supplied.get(i));
            }
        }

        // INITIALIZE WS-THIS-PROGCOMMAREA WS-COMMAREA TO DEFAULT (:461-470).
        void reset() {
            firstCardNumber = null;
            lastCardNumber = null;
            screenNumber = 1;
            lastPageShown = false;
            nextPageExists = false;
            errorMessage = null;
            rows = new ArrayList<>(Collections.nCopies(COBOL_PAGE_SIZE, null));
        }

        CardListPageState toPageState() {
            return new CardListPageState(firstCardNumber, lastCardNumber, screenNumber,
                    lastPageShown, nextPageExists, Collections.unmodifiableList(rows));
        }
    }

    /* ====================== S-06 COCRDUPC card update ====================== */

    /**
     * First display of CCRDUPA (COCRDUPC.cbl:502-511, FR-S06-01): the
     * search screen with the prompt line, before any map is received.
     */
    public CardUpdateScreen initialCardUpdate() {
        return sendUpdateMap(UpdateState.fresh(), NewInput.blank(), UpdateEdits.NONE);
    }

    /**
     * One AID press of COCRDUPC (tran CCUP) — the 0000-MAINLINE EVALUATE
     * (cbl:429-543), 1100-RECEIVE-MAP (:578-635), 1200-EDIT-MAP-INPUTS
     * (:641-715) and 2000-DECIDE-ACTION (:948-1027) in one call. The
     * change-action is the six-state machine: search / S show / E edit
     * errors / N awaiting F5 / C committed / L,F failed.
     */
    @Transactional
    public CardUpdateScreen cardUpdate(CardUpdateForm form) {
        UpdateState state = new UpdateState(form.commarea());
        String aid = normalizeUpdateAid(form.aid(), state);        // :413-424

        // :517-528 — once a save is done (C) or failed (L/F) every
        // remaining AID (PF3 is already branched out) re-initialises to
        // the fresh search screen (FR-S06-26).
        if (state.done()) {
            return sendUpdateMap(UpdateState.fresh(), NewInput.blank(), UpdateEdits.NONE);
        }

        NewInput input = NewInput.receive(form);                   // :586-635
        UpdateEdits edits = editUpdateInputs(state, input);        // :641-714

        // 2000-DECIDE-ACTION — EVALUATE order (:950-975): not-fetched and
        // PF12 re-read; state N + PF5 writes; S/E/N otherwise fall through.
        if (!state.fetched() || "PF12".equals(aid)) {
            if (edits.accountValid && edits.cardValid) {
                readCardForUpdate(state, input, edits);            // 9000
            }
        } else if (state.is("N") && "PF5".equals(aid)) {
            writeCardForUpdate(state, input, edits);               // 9200
        }
        return sendUpdateMap(state, input, edits);
    }

    // :413-424 — ENTER and PF3 always; PF5 only while N; PF12 only after
    // the record is fetched. Every other AID silently becomes ENTER — no
    // invalid-key message is ever emitted (FR-S06-02).
    private static String normalizeUpdateAid(String raw, UpdateState state) {
        String aid = raw == null ? "" : raw.trim().toUpperCase();
        aid = switch (aid) {
            case "F3" -> "PF3";
            case "F5" -> "PF5";
            case "F12" -> "PF12";
            default -> aid;
        };
        boolean valid = "ENTER".equals(aid) || "PF3".equals(aid)
                || ("PF5".equals(aid) && state.is("N"))
                || ("PF12".equals(aid) && state.fetched());
        return valid ? aid : "ENTER";
    }

    // 1200-EDIT-MAP-INPUTS (:641-715): search edits run only while
    // not-fetched; afterwards the four detail edits run all four — no
    // short-circuit — and the first failure message wins
    // (WS-RETURN-MSG-OFF, :743-752).
    private UpdateEdits editUpdateInputs(UpdateState state, NewInput input) {
        UpdateEdits edits = new UpdateEdits();
        if (!state.fetched()) {                                    // :645-661
            editUpdateAccount(edits, input.accountId);             // 1210
            editUpdateCard(edits, input.cardNumber);               // 1220
            if (edits.accountBlank && edits.cardBlank) {
                edits.message = CobolMessages.NO_INPUT_RECEIVED;   // :656-659
            }
            return edits;
        }
        // :667-714 — the search keys passed their edits on the fetch
        // turn; the OLD keys display in their fields (:1104-1109).
        edits.accountValid = true;
        edits.cardValid = true;
        if (state.sameData(input)) {                               // :680-683
            edits.message = CobolMessages.NO_CHANGES_DETECTED;
            edits.noChange = true;
            return edits;
        }
        if (state.is("N") || state.is("C")) {                       // :685-688
            return edits;
        }
        state.changeAction = "E";                                 // :696
        editUpdateName(edits, input.name);                         // 1230
        editUpdateStatus(edits, input.status);                     // 1240
        editUpdateMonth(edits, input.month);                       // 1250
        editUpdateYear(edits, input.year);                         // 1260
        if (!edits.inputError) {
            state.changeAction = "N";                             // :713
        }
        return edits;
    }

    // 1210-EDIT-ACCOUNT (:721-756): blank/*/spaces/all-zeros is "not
    // provided"; anything else must be exactly 11 digits.
    private void editUpdateAccount(UpdateEdits e, String value) {
        if (value == null || value.equals("0".repeat(11))) {
            e.inputError = true;
            e.accountBlank = true;
            e.setMessage(CobolMessages.CARD_ACCOUNT_NOT_PROVIDED);
            return;
        }
        if (!value.matches("\\d{11}")) {
            e.inputError = true;
            e.accountNotOk = true;
            e.setMessage(CobolMessages.CARD_ACCOUNT_FILTER_INVALID);
            return;
        }
        e.accountValid = true;
    }

    // 1220-EDIT-CARD (:758-799): same shape, 16 digits.
    private void editUpdateCard(UpdateEdits e, String value) {
        if (value == null || value.equals("0".repeat(16))) {
            e.inputError = true;
            e.cardBlank = true;
            e.setMessage(CobolMessages.CARD_NUMBER_NOT_PROVIDED);
            return;
        }
        if (!value.matches("\\d{16}")) {
            e.inputError = true;
            e.cardNotOk = true;
            e.setMessage(CobolMessages.CARD_FILTER_INVALID);
            return;
        }
        e.cardValid = true;
    }

    // 1230-EDIT-NAME (:806-840): blank is required-error; otherwise INSPECT
    // leaves only alphabets and spaces.
    private void editUpdateName(UpdateEdits e, String value) {
        if (value == null) {
            e.inputError = true;
            e.nameBlank = true;
            e.setMessage(CobolMessages.CARD_NAME_REQUIRED);
            return;
        }
        if (!value.matches("[A-Za-z ]+")) {
            e.inputError = true;
            e.nameNotOk = true;
            e.setMessage(CobolMessages.CARD_NAME_ALPHA);
            return;
        }
        e.nameValid = true;
    }

    // 1240-EDIT-STATUS (:843-881): the X(1) field must be exactly Y or N.
    private void editUpdateStatus(UpdateEdits e, String value) {
        if (value == null) {
            e.inputError = true;
            e.statusBlank = true;
            e.setMessage(CobolMessages.CARD_STATUS_INVALID);
            return;
        }
        String first = value.substring(0, Math.min(1, value.length()));
        if (!"Y".equals(first) && !"N".equals(first)) {
            e.inputError = true;
            e.statusNotOk = true;
            e.setMessage(CobolMessages.CARD_STATUS_INVALID);
            return;
        }
        e.statusValid = true;
    }

    // 1250-EDIT-MONTH (:883-914): all-zeros counts as blank; then 1..12.
    private void editUpdateMonth(UpdateEdits e, String value) {
        if (value == null || value.equals("00")) {
            e.inputError = true;
            e.monthBlank = true;
            e.setMessage(CobolMessages.CARD_EXPIRY_MONTH_INVALID);
            return;
        }
        if (!value.matches("\\d{1,2}") || Integer.parseInt(value) < 1
                || Integer.parseInt(value) > 12) {
            e.inputError = true;
            e.monthNotOk = true;
            e.setMessage(CobolMessages.CARD_EXPIRY_MONTH_INVALID);
            return;
        }
        e.monthValid = true;
    }

    // 1260-EDIT-YEAR (:916-947): all-zeros counts as blank; then 1950-2099.
    private void editUpdateYear(UpdateEdits e, String value) {
        if (value == null || value.equals("0000")) {
            e.inputError = true;
            e.yearBlank = true;
            e.setMessage(CobolMessages.CARD_EXPIRY_YEAR_INVALID);
            return;
        }
        if (!value.matches("\\d{1,4}") || Integer.parseInt(value) < 1950
                || Integer.parseInt(value) > 2099) {
            e.inputError = true;
            e.yearNotOk = true;
            e.setMessage(CobolMessages.CARD_EXPIRY_YEAR_INVALID);
            return;
        }
        e.yearValid = true;
    }

    // 9000-READ-DATA + 9100-GETCARD-BY-ACCTCARD (:1343-1417): the plain
    // keyed read by card number only; the account is format-checked at
    // edit time but never compared against the stored record
    // (cbl:1379-1380 is commented out — plan risk 3).
    private void readCardForUpdate(UpdateState state, NewInput input, UpdateEdits edits) {
        state.setKeys(input.accountId, input.cardNumber);          // :1345-1347
        Card card;
        try {
            card = cardRepository.findById(input.cardNumber).orElse(null);
        } catch (RuntimeException exception) {
            // :1403-1411 — other IOERR: the acct flag only colours when
            // no message is set yet; the file-error text overwrites.
            edits.inputError = true;
            if (edits.message == null) {
                edits.accountNotOk = true;
            }
            edits.message = CobolMessages.CARD_UPDATE_FILE_ERROR_READ;
            return;
        }
        if (card == null) {                                        // :1395-1401
            edits.inputError = true;
            edits.accountNotOk = true;
            edits.cardNotOk = true;
            edits.setMessage(CobolMessages.CARD_COMBINATION_NOT_FOUND);
            return;
        }
        state.setOldImage(card);                                   // :1352-1367
        state.changeAction = "S";
    }

    // 9200-WRITE-PROCESSING (:1420-1496): FOR UPDATE read, five-field
    // compare, REWRITE — the same unit saveLocked runs for REST.
    private void writeCardForUpdate(UpdateState state, NewInput input, UpdateEdits edits) {
        SaveOutcome outcome = saveLocked(input.cardNumber,
                state.oldName, state.oldStatus,
                parseOrNull(state.oldYear), parseOrNull(state.oldMonth),
                parseOrNull(state.oldDay),
                input.name, input.status,
                parseOrNull(input.year), parseOrNull(input.month),
                parseOrNull(input.day));
        switch (outcome.kind()) {
            case COMMITTED -> state.changeAction = "C";            // :999-1000
            case CHANGED -> {
                edits.setMessage(CobolMessages.RECORD_CHANGED);    // :1453-1456
                state.refreshOldImage(outcome.card());
                state.changeAction = "S";
            }
            case LOCK_ERROR -> {
                edits.inputError = true;
                edits.setMessage(CobolMessages.CARD_COULD_NOT_LOCK);
                state.changeAction = "L";                          // :1442-1444
            }
            case NO_CHANGE -> {
                edits.setMessage(CobolMessages.NO_CHANGES_DETECTED);
                state.changeAction = "S";
            }
            default -> {
                edits.inputError = true;
                edits.setMessage(CobolMessages.UPDATE_FAILED);     // :1488-1492
                state.changeAction = "F";
            }
        }
    }

    // 3000-SEND-MAP (:1035-1317): echoes (3200, :1082-1130), protection
    // (:1168-1208), cursor (:1211-1235), flagging (:1243-1307), INFOMSG
    // (3250, :1138-1159).
    private CardUpdateScreen sendUpdateMap(UpdateState state, NewInput input,
                                           UpdateEdits edits) {
        String action = state.changeAction;
        boolean fetched = state.fetched();
        boolean changesNotOk = "E".equals(action);

        // :1178-1184 + :1254-1256 — a zero key is blanked on echo; a
        // blank-flagged key redisplays '*' when re-entering.
        String acctEcho = input.accountId == null || input.accountId.equals("0".repeat(11))
                ? "" : input.accountId;
        String cardEcho = input.cardNumber == null || input.cardNumber.equals("0".repeat(16))
                ? "" : input.cardNumber;
        if (edits.accountBlank) {
            acctEcho = "*";
        }
        if (edits.cardBlank) {
            cardEcho = "*";
        }

        // 3200-SETUP-SCREEN-VARS (:1099-1130): OLD image in S, NEW image
        // otherwise; the expiry day always shows the OLD day.
        String nameEcho = "", statusEcho = "", monthEcho = "", yearEcho = "";
        if (fetched) {
            if ("S".equals(action)) {
                nameEcho = nullToEmpty(state.oldName);
                statusEcho = nullToEmpty(state.oldStatus);
                monthEcho = nullToEmpty(state.oldMonth);
                yearEcho = nullToEmpty(state.oldYear);
            } else {
                nameEcho = nullToEmpty(input.name);
                statusEcho = nullToEmpty(input.status);
                monthEcho = nullToEmpty(input.month);
                yearEcho = nullToEmpty(input.year);
                // :1285-1290 — blank-flagged detail fields show '*' only
                // while CHANGES-NOT-OK.
                if (changesNotOk) {
                    if (edits.nameBlank) {
                        nameEcho = "*";
                    }
                    if (edits.statusBlank) {
                        statusEcho = "*";
                    }
                    if (edits.monthBlank) {
                        monthEcho = "*";
                    }
                    if (edits.yearBlank) {
                        yearEcho = "*";
                    }
                }
            }
        }
        String dayEcho = nullToEmpty(state.oldDay);

        // :1243-1307 — search fields red on either flag; detail fields
        // colour only in CHANGES-NOT-OK.
        boolean acctRed = edits.accountNotOk || edits.accountBlank;
        boolean cardRed = edits.cardNotOk || edits.cardBlank;
        boolean nameRed = changesNotOk && (edits.nameNotOk || edits.nameBlank);
        boolean statusRed = changesNotOk && (edits.statusNotOk || edits.statusBlank);
        boolean monthRed = changesNotOk && (edits.monthNotOk || edits.monthBlank);
        boolean yearRed = changesNotOk && (edits.yearNotOk || edits.yearBlank);

        // :1211-1235 — cursor: FOUND / NO-CHANGES lands on the name;
        // otherwise the first flagged field; default is the account key.
        String cursor = "acctsid";
        if ("S".equals(action) || edits.noChange) {
            cursor = "crdname";
        } else if (acctRed) {
            cursor = "acctsid";
        } else if (cardRed) {
            cursor = "cardsid";
        } else if (nameRed) {
            cursor = "crdname";
        } else if (statusRed) {
            cursor = "crdstcd";
        } else if (monthRed) {
            cursor = "expmon";
        } else if (yearRed) {
            cursor = "expyear";
        }

        // 3250-SETUP-INFOMSG (:1138-1159) — verbatim per state.
        String info = switch (action == null ? "" : action) {
            case "S" -> CobolMessages.CARD_UPDATE_DETAILS_SHOWN;
            case "E" -> CobolMessages.CARD_UPDATE_PROMPT_CHANGES;
            case "N" -> CobolMessages.CARD_UPDATE_PROMPT_CONFIRM;
            case "C" -> CobolMessages.CARD_UPDATE_COMMITTED;
            case "L", "F" -> CobolMessages.CARD_UPDATE_UNSUCCESSFUL;
            default -> CobolMessages.CARD_UPDATE_PROMPT_KEYS;
        };

        return new CardUpdateScreen(action == null ? "" : action,
                acctEcho, acctRed, cardEcho, cardRed,
                nameEcho, nameRed, statusEcho, statusRed,
                monthEcho, monthRed, yearEcho, yearRed,
                dayEcho, cursor, info, edits.message,
                state.toCommarea());
    }

    private static Integer parseOrNull(String value) {
        if (value == null || value.isBlank() || !value.strip().matches("\\d+")) {
            return null;
        }
        try {
            return Integer.valueOf(value.strip());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    // The received CCRDUPA map (:586-635): '*' or all-spaces reads as
    // LOW-VALUES; month/year/day are JUSTIFY RIGHT so they left-pad with
    // zeros to the BMS length before any edit runs (bms:129-139, FR-S06-06).
    private record NewInput(String accountId, String cardNumber, String name,
                            String status, String month, String year, String day) {

        static NewInput blank() {
            return new NewInput(null, null, null, null, null, null, null);
        }

        static NewInput receive(CardUpdateForm form) {
            return new NewInput(
                    lowValuesOnStar(truncate(form.accountId(), 11)),
                    lowValuesOnStar(truncate(form.cardNumber(), 16)),
                    lowValuesOnStar(truncate(form.embossedName(), 50)),
                    lowValuesOnStar(truncate(form.activeStatus(), 1)),
                    zeroFill(lowValuesOnStar(truncate(form.expiryMonth(), 2)), 2),
                    zeroFill(lowValuesOnStar(truncate(form.expiryYear(), 4)), 4),
                    zeroFill(lowValuesOnStar(truncate(form.expiryDay(), 2)), 2));
        }

        private static String truncate(String value, int length) {
            return value == null ? null : value.substring(0, Math.min(length, value.length()));
        }

        private static String lowValuesOnStar(String value) {
            if (value == null || value.isBlank()
                    || (value.startsWith("*") && value.substring(1).isBlank())) {
                return null;
            }
            return value;
        }

        private static String zeroFill(String value, int length) {
            return value == null ? null : "0".repeat(length - value.length()) + value;
        }
    }

    // The flags 1200-EDIT-* set: blank vs non-blank failure, INPUT-ERROR,
    // NO-CHANGES, and the first-wins return message.
    private static final class UpdateEdits {
        static final UpdateEdits NONE = new UpdateEdits();
        boolean inputError;
        boolean noChange;
        boolean accountValid, cardValid;
        boolean accountBlank, cardBlank;
        boolean accountNotOk, cardNotOk;
        boolean nameValid, statusValid, monthValid, yearValid;
        boolean nameBlank, statusBlank, monthBlank, yearBlank;
        boolean nameNotOk, statusNotOk, monthNotOk, yearNotOk;
        String message;

        void setMessage(String value) {
            if (message == null) {
                message = value;
            }
        }
    }

    // WS-THIS-PROGCOMMAREA (:276-313): the change-action plus the OLD
    // image — the keys that fetched the record and its displayed fields.
    private static final class UpdateState {
        String changeAction;
        String oldAcctId, oldCardId;
        String oldName, oldStatus, oldYear, oldMonth, oldDay;

        UpdateState(CardUpdateCommarea commarea) {
            if (commarea == null) {
                changeAction = "";
                return;
            }
            changeAction = commarea.changeAction() == null ? "" : commarea.changeAction();
            oldAcctId = commarea.accountId();
            oldCardId = commarea.cardNumber();
            oldName = commarea.embossedName();
            oldStatus = commarea.activeStatus();
            oldYear = commarea.expiryYear();
            oldMonth = commarea.expiryMonth();
            oldDay = commarea.expiryDay();
        }

        static UpdateState fresh() {
            return new UpdateState(null);
        }

        boolean fetched() {
            return !changeAction.isBlank();
        }

        boolean is(String action) {
            return changeAction.equals(action);
        }

        boolean done() {
            return is("C") || is("L") || is("F");
        }

        // :1345-1347 + INITIALIZE CCUP-OLD-DETAILS: the OLD keys are the
        // typed search keys even before the read answers.
        void setKeys(String acctId, String cardNumber) {
            oldAcctId = acctId;
            oldCardId = cardNumber;
            oldName = oldStatus = oldYear = oldMonth = oldDay = null;
        }

        // :1352-1367 — name upper-cased, the stored date split into
        // zero-padded year/month/day, CVV deliberately not echoed (D2).
        void setOldImage(Card card) {
            oldName = card.getCardEmbossedName() == null ? null
                    : card.getCardEmbossedName().toUpperCase().trim();
            LocalDate expiry = card.getCardExpirationDate();
            oldYear = expiry == null ? null : "%04d".formatted(expiry.getYear());
            oldMonth = expiry == null ? null : "%02d".formatted(expiry.getMonthValue());
            oldDay = expiry == null ? null : "%02d".formatted(expiry.getDayOfMonth());
            oldStatus = card.getCardActiveStatus() == null ? null
                    : card.getCardActiveStatus().trim();
        }

        // The PF12/changed-refresh: same OLD image, keys kept.
        void refreshOldImage(Card card) {
            setOldImage(card);
        }

        // 9300-CHECK-CHANGE (:1498-1519): upper-cased NEW against OLD over
        // the five displayed fields.
        boolean sameData(NewInput input) {
            return sameIgnoreCase(input.name(), oldName)
                    && sameIgnoreCase(input.status(), oldStatus)
                    && equalsNull(input.year(), oldYear)
                    && equalsNull(input.month(), oldMonth)
                    && equalsNull(input.day(), oldDay);
        }

        private static boolean equalsNull(String left, String right) {
            return left == null ? right == null : left.equals(right);
        }

        CardUpdateCommarea toCommarea() {
            return new CardUpdateCommarea(changeAction, oldAcctId, oldCardId,
                    oldName, oldStatus, oldYear, oldMonth, oldDay);
        }
    }

    /**
     * COCRDSLC (transaction CCDL, map CCRDSLA) — the view-only card detail
     * screen as a value object. The keyed read uses the card number only
     * (S05-B1): the account edit must pass before the read runs, but the
     * account is never compared with the card's owner (FR-S05-12,
     * COCRDSLC.cbl:739-750 — a documented source defect kept verbatim).
     */
    public CardViewScreen initialScreen() {
        // PGM-ENTER first display (:349-356): empty fields, fixed prompt.
        return new CardViewScreen("", "", false, false, false, "acctsid",
                CobolMessages.CARD_VIEW_PROMPT, null, null);
    }

    // PGM-ENTER from COCRDLIC (:339-348): the edits are skipped — the
    // COMMAREA keys echo zero-padded (:342-343), the read runs at once and
    // both inputs are protected (:505-508).
    public CardViewScreen cardListScreen(String rawAccountId, String rawCardNumber) {
        ViewEdits keys = new ViewEdits(Filter.VALID, Filter.VALID,
                commareaEcho(rawAccountId, 11), commareaEcho(rawCardNumber, 16), null);
        return cardView(keys, true);
    }

    // PGM-REENTER (:357-371): receive + 2200-EDIT-MAP-INPUTS; the keyed
    // read runs only when INPUT-OK. cardListContext preserves the
    // protected-input attribute across re-entries — the COMMAREA context
    // (:505-508) round-trips as a hidden form field.
    public CardViewScreen viewScreen(String rawAccountId, String rawCardNumber,
                                     boolean cardListContext) {
        return cardView(editCardViewInputs(rawAccountId, rawCardNumber), cardListContext);
    }

    // 1200-SETUP-SCREEN-VARS + 1300-SETUP-SCREEN-ATTRS (:457-557).
    private CardViewScreen cardView(ViewEdits edits, boolean cardListContext) {
        boolean acctRed = edits.acctFlag() != Filter.VALID;
        boolean cardRed = edits.cardFlag() != Filter.VALID;
        String error = edits.message();
        Card found = null;
        if (!edits.inputError()) {
            // 9100-GETCARD-BYACCTCARD (:736-773): keyed by card number only.
            try {
                found = cardRepository.findById(edits.cardNumber()).orElse(null);
            } catch (RuntimeException exception) {
                // :762-771 — RESP OTHER: file-error frame; only the account
                // flag is forced because WS-RETURN-MSG was still off.
                error = CobolMessages.fileError("CARDDAT");
                acctRed = true;
            }
            if (found == null && error == null) {
                // :755-761 — NOTFND flags both, entered values retained.
                error = CobolMessages.CARD_COMBINATION_NOT_FOUND;
                acctRed = true;
                cardRed = true;
            }
        }
        String info = found != null ? CobolMessages.CARD_VIEW_FOUND
                : CobolMessages.CARD_VIEW_PROMPT;
        // :515-524 — the -1 length lands on account, else card, else account.
        String cursor = acctRed ? "acctsid" : cardRed ? "cardsid" : "acctsid";
        return new CardViewScreen(
                echo(edits.acctFlag(), edits.accountId()),
                echo(edits.cardFlag(), edits.cardNumber()),
                acctRed, cardRed, cardListContext, cursor, info, error,
                toCardBlock(found));
    }

    // :462-471, :533-551 — BLANK redisplays `*` red on re-entry; NOT-OK
    // redisplays cleared because the COMMAREA key was zeroed; a valid or
    // read-failed field keeps its entered value.
    private static String echo(Filter flag, String entered) {
        return switch (flag) {
            case BLANK -> "*";
            case NOT_OK -> "";
            case VALID -> entered;
        };
    }

    private enum Filter { VALID, BLANK, NOT_OK }

    private record ViewEdits(Filter acctFlag, Filter cardFlag,
                             String accountId, String cardNumber, String message) {
        boolean inputError() {
            return acctFlag != Filter.VALID || cardFlag != Filter.VALID;
        }
    }

    // 2200-EDIT-MAP-INPUTS (:608-641): account edit then card edit. The
    // message slot is first-writer-wins (IF WS-RETURN-MSG-OFF, :657/:696)
    // and the both-blank case replaces it with NO-INPUT-RECEIVED (:637-640).
    private ViewEdits editCardViewInputs(String rawAccountId, String rawCardNumber) {
        String acct = mapField(rawAccountId, 11);
        String card = mapField(rawCardNumber, 16);
        String message = null;

        // 2210-EDIT-ACCOUNT (:647-679)
        Filter acctFlag;
        if (unsupplied(acct)) {
            acctFlag = Filter.BLANK;                                   // :651-660
            message = CobolMessages.CARD_ACCOUNT_REQUIRED;
        } else if (!acct.matches("\\d{11}")) {
            acctFlag = Filter.NOT_OK;                                  // :665-674
            message = CobolMessages.CARD_ACCOUNT_FILTER_INVALID;
        } else {
            acctFlag = Filter.VALID;
        }

        // 2220-EDIT-CARD (:685-719)
        Filter cardFlag;
        if (unsupplied(card)) {
            cardFlag = Filter.BLANK;                                   // :691-701
            if (message == null) {
                message = CobolMessages.CARD_NUMBER_REQUIRED;
            }
        } else if (!card.matches("\\d{16}")) {
            cardFlag = Filter.NOT_OK;                                  // :706-715
            if (message == null) {
                message = CobolMessages.CARD_FILTER_INVALID;
            }
        } else {
            cardFlag = Filter.VALID;
        }

        if (acctFlag == Filter.BLANK && cardFlag == Filter.BLANK) {
            message = CobolMessages.NO_INPUT_RECEIVED;                 // :637-640
        }
        return new ViewEdits(acctFlag, cardFlag, acct, card, message);
    }

    // Map input arrives inside an X(11)/X(16) field: longer input
    // truncates before the edits (the S-02 X(11) convention).
    private static String mapField(String raw, int width) {
        return raw == null ? "" : raw.substring(0, Math.min(raw.length(), width));
    }

    // "Not supplied" (:613-620, :651-653, :691-693): `*` followed only by
    // blanks clears to LOW-VALUES, and an all-zero field packs to zero
    // digits — any length of only '0' characters counts as blank. A `*`
    // in leading position followed by other input is NOT-OK, not blank.
    private static boolean unsupplied(String field) {
        if (field.isBlank() || (field.startsWith("*") && field.substring(1).isBlank())) {
            return true;
        }
        return field.chars().allMatch(c -> c == '0');
    }

    // CDEMO-ACCT-ID/CDEMO-CARD-NUM are 9(11)/9(16) numerics: a card-list
    // hand-off echoes them zero-padded (:342-343). Non-numeric input keeps
    // its text — the keyed read then uses the card number as passed.
    private static String commareaEcho(String raw, int width) {
        if (raw == null || raw.isBlank() || !raw.matches("\\d+")) {
            return raw == null ? "" : raw;
        }
        String digits = raw.length() > width ? raw.substring(0, width) : raw;
        return "0".repeat(width - digits.length()) + digits;
    }

    private static CardViewScreen.CardBlock toCardBlock(Card card) {
        if (card == null) {
            return null;
        }
        LocalDate expiry = card.getCardExpirationDate();
        return new CardViewScreen.CardBlock(
                card.getCardEmbossedName(),
                expiry == null ? "" : "%02d".formatted(expiry.getMonthValue()),
                expiry == null ? "" : "%04d".formatted(expiry.getYear()),
                card.getCardActiveStatus());
    }

    /**
     * REST surface of the same view-path resolution (FR-S05-02..13):
     * failures surface as HTTP-mapped exceptions instead of screen state.
     * The read is keyed by card number only — no account cross-check
     * (FR-S05-12).
     */
    public CardResponse detail(String rawAccountId, String rawCardNumber) {
        ViewEdits edits = editCardViewInputs(rawAccountId, rawCardNumber);
        if (edits.inputError()) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST, edits.message());
        }
        Card card = cardRepository.findById(edits.cardNumber()).orElse(null);
        if (card == null) {
            throw new CobolApiException(HttpStatus.NOT_FOUND,
                    CobolMessages.CARD_COMBINATION_NOT_FOUND);
        }
        return response(card);
    }

    /**
     * COCRDUPC's save over REST (9200-WRITE-PROCESSING, COCRDUPC.cbl:
     * 1420-1496): the snapshot round-trips as the OLD image, the row is
     * re-read FOR UPDATE and compared before the REWRITE. Per the source
     * the account is format-checked but never matched against the stored
     * card (cbl:1379-1380 is commented out), and the lock/read/compare/
     * write all happen in one unit — the four 88-level outcomes map to
     * 200/409/500 (FR-S06-22..25).
     */
    @Transactional
    public CardResponse update(String rawAccountId, String rawCardNumber,
                               CardUpdateRequest request) {
        validateAccount(rawAccountId, true);
        String cardNumber = validateCard(rawCardNumber, true);
        requireName(request.embossedName());
        String active = requireStatus(request.activeStatus());
        requireExpiryMonth(request.expiryMonth());
        requireExpiryYear(request.expiryYear());

        CardUpdateRequest.CardSnapshot original = request.original();
        if (original == null || original.embossedName() == null
                || original.activeStatus() == null || original.expiryMonth() == null
                || original.expiryYear() == null) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST, CobolMessages.SNAPSHOT_REQUIRED);
        }
        SaveOutcome outcome = saveLocked(cardNumber,
                original.embossedName(), original.activeStatus(),
                original.expiryYear(), original.expiryMonth(), original.expiryDay(),
                request.embossedName(), active,
                request.expiryYear(), request.expiryMonth(), original.expiryDay());
        return switch (outcome.kind()) {
            case COMMITTED -> response(outcome.card());
            case CHANGED -> throw changed();
            case NO_CHANGE -> throw invalid(CobolMessages.NO_CHANGES_DETECTED);
            case LOCK_ERROR -> throw new CobolApiException(
                    HttpStatus.CONFLICT, CobolMessages.CARD_COULD_NOT_LOCK);
            default -> throw new CobolApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR, CobolMessages.UPDATE_FAILED);
        };
    }

    // :1453-1456 + 9300-CHECK-CHANGE (:1498-1519): the five displayed
    // fields only — CVV is never fetched into the map, so it is not
    // compared (deviation D2). Deviation D1 keeps the stored CVV and
    // account id; the legacy source overwrote CVV with 000 and the
    // account with the typed filter.
    private SaveOutcome saveLocked(String cardNumber,
                                   String oldName, String oldStatus,
                                   Integer oldYear, Integer oldMonth, Integer oldDay,
                                   String newName, String newStatus,
                                   Integer newYear, Integer newMonth, Integer newDay) {
        Card card;
        try {
            card = cardRepository.findForUpdate(cardNumber).orElse(null);
        } catch (RuntimeException exception) {
            return new SaveOutcome(SaveKind.LOCK_ERROR, null);
        }
        if (card == null) {                                        // :1436-1447
            return new SaveOutcome(SaveKind.LOCK_ERROR, null);
        }
        LocalDate expiry = card.getCardExpirationDate();
        // A null stored expiry has no date to compare against — the date
        // fields are skipped, as the baseline contract established.
        boolean recordUnchanged = sameIgnoreCase(card.getCardEmbossedName(), oldName)
                && same(card.getCardActiveStatus(), oldStatus)
                && (oldYear == null || expiry == null || expiry.getYear() == oldYear)
                && (oldMonth == null || expiry == null || expiry.getMonthValue() == oldMonth)
                && (oldDay == null || expiry == null || expiry.getDayOfMonth() == oldDay);
        if (!recordUnchanged) {
            return new SaveOutcome(SaveKind.CHANGED, card);        // :1455
        }
        // NEW == OLD over the five editable fields (9300 equivalent on the
        // REST surface — the screen path always reaches N with a change).
        if (sameIgnoreCase(newName, oldName) && sameIgnoreCase(newStatus, oldStatus)
                && Objects.equals(newYear, oldYear)
                && Objects.equals(newMonth, oldMonth)) {
            return new SaveOutcome(SaveKind.NO_CHANGE, card);
        }
        String storedName = card.getCardEmbossedName();
        String storedStatus = card.getCardActiveStatus();
        try {
            // Deviation D3: LocalDate.of(year, month, OLD day) with no
            // day-clamp — a non-calendar combination fails the REWRITE
            // before any managed field mutates.
            int day = newDay != null ? newDay
                    : expiry == null ? 1 : expiry.getDayOfMonth();
            LocalDate newExpiry = LocalDate.of(newYear, newMonth, day);
            card.setCardEmbossedName(newName.trim());
            card.setCardActiveStatus(newStatus.trim());
            card.setCardExpirationDate(newExpiry);
            cardRepository.save(card);
            return new SaveOutcome(SaveKind.COMMITTED, card);
        } catch (RuntimeException exception) {                     // :1488-1492
            // A failed REWRITE rolls the row image back so the enclosing
            // commit has nothing to flush.
            card.setCardEmbossedName(storedName);
            card.setCardActiveStatus(storedStatus);
            card.setCardExpirationDate(expiry);
            return new SaveOutcome(SaveKind.UPDATE_FAILED, null);
        }
    }

    private enum SaveKind {COMMITTED, CHANGED, NO_CHANGE, LOCK_ERROR, UPDATE_FAILED}

    private record SaveOutcome(SaveKind kind, Card card) {
    }

    private static boolean sameIgnoreCase(String left, String right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    private CardResponse response(Card card) {
        return new CardResponse(card.getCardNumber(), card.getCardAcctId(),
                card.getCardCvvCode() == null ? null : "%03d".formatted(card.getCardCvvCode()),
                card.getCardEmbossedName(), card.getCardExpirationDate(), card.getCardActiveStatus());
    }

    private Long validateAccount(String raw, boolean required) {
        if (raw == null || raw.isBlank()) {
            if (required) {
                throw invalid(CobolMessages.ACCOUNT_NUMBER_INVALID);
            }
            return null;
        }
        if (!raw.matches("\\d{1,11}") || raw.chars().allMatch(c -> c == '0')) {
            throw invalid(CobolMessages.CARD_ACCOUNT_FILTER_INVALID);
        }
        return Long.parseLong(raw);
    }

    private String validateCard(String raw, boolean required) {
        if (raw == null || raw.isBlank()) {
            if (required) {
                throw invalid(CobolMessages.CARD_FILTER_INVALID);
            }
            return null;
        }
        if (!raw.matches("\\d{16}") || raw.chars().allMatch(c -> c == '0')) {
            throw invalid(CobolMessages.CARD_FILTER_INVALID);
        }
        return raw;
    }

    private void requireName(String value) {
        if (value == null || value.isBlank()) {
            throw invalid(CobolMessages.CARD_NAME_REQUIRED);
        }
        if (!value.matches("[A-Za-z ]+")) {
            throw invalid(CobolMessages.CARD_NAME_ALPHA);
        }
    }

    // 1240-EDIT-STATUS (:850-879): the X(1) field must read exactly 'Y'
    // or 'N' — lowercase fails the literal compare (FR-S06-16).
    private String requireStatus(String value) {
        if (value == null || value.isBlank()) {
            throw invalid(CobolMessages.CARD_STATUS_INVALID);
        }
        String normalized = value.trim();
        if (!normalized.equals("Y") && !normalized.equals("N")) {
            throw invalid(CobolMessages.CARD_STATUS_INVALID);
        }
        return normalized;
    }

    private void requireExpiryMonth(Integer value) {
        if (value == null || value < 1 || value > 12) {
            throw invalid(CobolMessages.CARD_EXPIRY_MONTH_INVALID);
        }
    }

    private void requireExpiryYear(Integer value) {
        if (value == null || value < 1950 || value > 2099) {
            throw invalid(CobolMessages.CARD_EXPIRY_YEAR_INVALID);
        }
    }

    private boolean same(String left, String right) {
        return left == null ? right == null : left.trim().equals(right == null ? null : right.trim());
    }

    private CobolApiException changed() {
        return new CobolApiException(HttpStatus.CONFLICT, CobolMessages.RECORD_CHANGED);
    }

    private CobolApiException invalid(String message) {
        return new CobolApiException(HttpStatus.BAD_REQUEST, message);
    }
}
