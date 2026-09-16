package com.carddemo.service;

import com.carddemo.api.CardListNavigation;
import com.carddemo.api.CardListPageState;
import com.carddemo.api.CardListRequest;
import com.carddemo.api.CardListResponse;
import com.carddemo.api.CardListRow;
import com.carddemo.api.CardListRowView;
import com.carddemo.api.CardListScreenView;
import com.carddemo.api.CardResponse;
import com.carddemo.api.CardUpdateRequest;
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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    @Transactional
    public CardResponse update(String rawAccountId, String rawCardNumber,
                               CardUpdateRequest request) {
        Long accountId = validateAccount(rawAccountId, true);
        String cardNumber = validateCard(rawCardNumber, true);
        Card card = cardRepository.findById(cardNumber).orElseThrow(
                () -> new CobolApiException(HttpStatus.NOT_FOUND,
                        CobolMessages.CARD_COMBINATION_NOT_FOUND));
        if (!accountId.equals(card.getCardAcctId())) {
            throw new CobolApiException(HttpStatus.NOT_FOUND,
                    CobolMessages.CARD_COMBINATION_NOT_FOUND);
        }
        requireName(request.embossedName());
        String active = requireStatus(request.activeStatus());
        requireExpiryMonth(request.expiryMonth());
        requireExpiryYear(request.expiryYear());
        LocalDate existingExpiry = card.getCardExpirationDate();

        CardUpdateRequest.CardSnapshot original = request.original();
        if (original == null || original.embossedName() == null
                || original.activeStatus() == null || original.expiryMonth() == null
                || original.expiryYear() == null) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST, CobolMessages.SNAPSHOT_REQUIRED);
        }
        if (!same(original.embossedName(), card.getCardEmbossedName())) {
            throw changed();
        }
        if (!same(original.activeStatus(), card.getCardActiveStatus())) {
            throw changed();
        }
        if (existingExpiry != null
                && !original.expiryMonth().equals(existingExpiry.getMonthValue())) {
            throw changed();
        }
        if (existingExpiry != null
                && !original.expiryYear().equals(existingExpiry.getYear())) {
            throw changed();
        }
        if (same(request.embossedName(), card.getCardEmbossedName())
                && same(active, card.getCardActiveStatus())
                && existingExpiry != null
                && request.expiryMonth().equals(existingExpiry.getMonthValue())
                && request.expiryYear().equals(existingExpiry.getYear())) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST, CobolMessages.NO_CHANGES_DETECTED);
        }
        card.setCardEmbossedName(request.embossedName().trim());
        card.setCardActiveStatus(active);
        YearMonth expiry = YearMonth.of(request.expiryYear(), request.expiryMonth());
        int day = existingExpiry == null
                ? 1
                : Math.min(existingExpiry.getDayOfMonth(), expiry.lengthOfMonth());
        card.setCardExpirationDate(expiry.atDay(day));
        cardRepository.save(card);
        return response(card);
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

    private String requireStatus(String value) {
        if (value == null || value.isBlank()) {
            throw invalid(CobolMessages.CARD_STATUS_INVALID);
        }
        String normalized = value.trim().toUpperCase();
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
