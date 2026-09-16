package com.carddemo.service;

import com.carddemo.api.CobolApiException;
import com.carddemo.api.CobolMessages;
import com.carddemo.api.TranTypeCrudRequest;
import com.carddemo.api.TranTypeListPageState;
import com.carddemo.api.TranTypeListRequest;
import com.carddemo.api.TranTypeListResponse;
import com.carddemo.api.TranTypeListRow;
import com.carddemo.api.TranTypeListScreen;
import com.carddemo.api.TranTypeMaintRequest;
import com.carddemo.api.TranTypeMaintResponse;
import com.carddemo.api.TranTypeMaintScreen;
import com.carddemo.api.TranTypeMaintState;
import com.carddemo.api.TranTypeNavigation;
import com.carddemo.api.TranTypeResponse;
import com.carddemo.model.SecurityUser;
import com.carddemo.model.TransactionType;
import com.carddemo.repository.SecurityUserRepository;
import com.carddemo.repository.TransactionCategoryRepository;
import com.carddemo.repository.TransactionTypeRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * S-21: COTRTLIC (list + flag update/delete, tran CTLI) and COTRTUPC
 * (single-record maintenance state machine, tran CTTU) over transaction_types.
 * One call per AID press; the COMMAREAs round-trip through the client as
 * pageState/state so the service stays stateless — same convention as the
 * card list (COCRDLIC).
 */
@Service
public class TranTypeService {

    private static final int PAGE_SIZE = TranTypeListPageState.ROW_COUNT;

    private final TransactionTypeRepository types;
    private final TransactionCategoryRepository categories;
    private final SecurityUserRepository users;

    public TranTypeService(TransactionTypeRepository types,
                           TransactionCategoryRepository categories,
                           SecurityUserRepository users) {
        this.types = types;
        this.categories = categories;
        this.users = users;
    }

    // ------------------------------------------------------------------
    // COTRTLIC — tran-type list browse (CTLI)
    // ------------------------------------------------------------------

    public TranTypeListResponse browse(Authentication authentication,
                                       TranTypeListRequest request) {
        requireAdmin(authentication);

        boolean received = request.pageState() != null;
        TranTypeListPageState state = received
                ? request.pageState() : TranTypeListPageState.fresh();
        ListTurn turn = new ListTurn(state);

        // :538-558 — YYYY-STORE-PFKEY maps the AID; :574-587 — anything not
        // in {ENTER,F2,F3,F7,F8,F10-with-pending} is remapped to ENTER.
        String aid = request.aid() == null ? "ENTER" : request.aid();
        aid = switch (aid) {
            case "ENTER", "PF2", "PF3", "PF7", "PF8" -> aid;
            case "PF10" -> state.deletePending() || state.updatePending()
                    ? "PF10" : "ENTER";
            default -> "ENTER";
        };

        // :657-661 — the last-page flag only survives a PF8 turn.
        boolean lastPageShown = "PF8".equals(aid) && state.lastPageShown();

        if (received) {
            // :561-566 — 1000-RECEIVE-MAP -> 1200-EDIT-INPUTS first; the
            // changed flags it produces feed the F10 remap below.
            turn.editInputs(request, state);

            if ("PF10".equals(aid) && (turn.typeChanged || turn.descChanged
                    || turn.rowChanged)) {
                // :666-678 — criteria changed under a pending action: F10
                // degrades to ENTER.
                aid = "ENTER";
            }

            // :684-691 — 9998-PRIMING-QUERY connectivity check.
            try {
                types.count();
            } catch (DataAccessException exception) {
                return TranTypeListResponse.page(turn.screen(echoRows(state),
                        state.screenNumber(), null,
                        Db2ErrorFormatter.forException("Db2 access failure",
                                exception), "trtype"), state);
            }
        }

        List<TranTypeListRow> rows;
        int screenNumber = state.screenNumber();
        String firstCode = state.firstCode();
        String lastCode = state.lastCode();
        boolean nextPageExists = state.nextPageExists();

        // :698-879 — the dispatch EVALUATE, in order. Navigation AIDs are
        // checked before the edit-error echo like the sibling lists
        // (COTRN00C dispatches EIBAID first): a filter whose edit keeps
        // failing must not trap the user on the screen — F3 still exits.
        if ("PF3".equals(aid)) {
            return new TranTypeListResponse("exit", null, null,
                    new TranTypeNavigation("COADM01C", "COTRTLIC"));
        } else if ("PF2".equals(aid)) {
            return new TranTypeListResponse("navigate", null, null,
                    new TranTypeNavigation("COTRTUPC", "COTRTLIC"));
        } else if (turn.inputError) {
            if (!turn.typeNotOk && !turn.descNotOk) {
                BrowseResult page = forward(turn, firstCode, screenNumber,
                        "PF8".equals(aid));
                rows = page.rows;
                firstCode = page.firstCode;
                lastCode = page.lastCode;
                screenNumber = page.screenNumber;
                nextPageExists = page.nextPageExists;
                turn.returnMsg(page.message);
            } else if (CobolMessages.TRTYPE_NO_RECORDS_FILTERS
                    .equals(turn.returnMsg)) {
                // UI-08 — a filter that matches nothing clears the rows:
                // echoing the last browse would claim the old rows match.
                rows = new ArrayList<>();
            } else {
                rows = echoRows(state);
            }
        } else if ("PF8".equals(aid) && nextPageExists) {
            BrowseResult page = forward(turn, lastCode, screenNumber + 1, true);
            rows = page.rows;
            firstCode = page.firstCode;
            lastCode = page.lastCode;
            screenNumber = page.screenNumber;
            nextPageExists = page.nextPageExists;
            turn.returnMsg(page.message);
            turn.selects = new String[PAGE_SIZE];
        } else if ("PF7".equals(aid) && screenNumber > 1) {
            BrowseResult page = backward(turn, firstCode);
            rows = page.rows;
            if (page.firstCode != null) {
                firstCode = page.firstCode;
            }
            lastCode = state.firstCode();
            screenNumber -= 1;
            nextPageExists = true;
            turn.returnMsg(page.message);
            turn.selects = new String[PAGE_SIZE];
        } else if ("PF10".equals(aid) && turn.deletes > 0) {
            // :810-834 — 9300-DELETE-RECORD, then the map is re-sent without
            // a re-browse; a success wipes the whole program context.
            if (deleteFlagged(turn, state)) {
                turn.deletedYes = true;
            }
            rows = echoRows(state);
        } else if ("PF10".equals(aid) && turn.updates > 0) {
            // :854-868 — 9200-UPDATE-RECORD then re-browse the current page.
            updateFlagged(turn, state, request);
            BrowseResult page = forward(turn, firstCode, screenNumber, false);
            rows = page.rows;
            firstCode = page.firstCode;
            lastCode = page.lastCode;
            nextPageExists = page.nextPageExists;
            turn.returnMsg(page.message);
        } else {
            // ENTER (incl. deletes/updates pending), PF7 on page 1, PF8 at the
            // tail, and every other case re-browse forward from the page top.
            BrowseResult page = forward(turn, firstCode, screenNumber,
                    "PF8".equals(aid));
            rows = page.rows;
            firstCode = page.firstCode;
            lastCode = page.lastCode;
            screenNumber = page.screenNumber;
            nextPageExists = page.nextPageExists;
            turn.returnMsg(page.message);
        }

        // 2300-SCREEN-ARRAY-INIT — echo flags, arm the pending COMMAREA bits,
        // and pick the rendered desc per row.
        boolean deletePending = false;
        boolean updatePending = false;
        boolean onlyOneValid = turn.validActions == 1 && !turn.badActions;
        List<TranTypeListRow> rendered = new ArrayList<>();
        for (int i = 0; i < PAGE_SIZE; i++) {
            TranTypeListRow echo = i < rows.size() ? rows.get(i) : null;
            if (echo == null || echo.code() == null) {
                rendered.add(null);
                continue;
            }
            String select = turn.selects == null ? null : turn.selects[i];
            String displayed = echo.desc();
            boolean descError = false;
            if ("D".equalsIgnoreCase(nz(select)) && onlyOneValid) {
                if (turn.deletedYes) {
                    select = "";
                } else {
                    deletePending = true;
                }
            }
            if ("U".equalsIgnoreCase(nz(select)) && onlyOneValid) {
                if (turn.updateDone) {
                    select = "";
                } else {
                    updatePending = true;
                }
                if (turn.changesOccurred) {
                    String postedDesc = value(request.rowDescs(), i);
                    displayed = turn.rowDescBlank ? "*"
                            : postedDesc == null ? "" : postedDesc;
                    descError = turn.rowDescNotOk;
                }
            }
            rendered.add(new TranTypeListRow(select == null ? "" : select.trim(),
                    echo.code(), displayed, echo.desc(), turn.protectRows,
                    turn.selectError[i], descError));
        }

        // 2500-SETUP-MESSAGE — EVALUATE order matters.
        String infoMsg = null;
        if (turn.deletedYes) {
            infoMsg = CobolMessages.TRTYPE_DELETE_DONE;
        } else if (turn.updateDone) {
            infoMsg = CobolMessages.TRTYPE_UPDATE_DONE;
        } else if (turn.typeNotOk || turn.descNotOk) {
            // CONTINUE — no info line.
        } else if ("ENTER".equals(aid) && turn.deletes > 0 && turn.actions == 1
                && turn.validActions == 1 && !turn.typeChanged && !turn.descChanged) {
            infoMsg = CobolMessages.TRTYPE_CONFIRM_DELETE;
        } else if ("ENTER".equals(aid) && turn.updates > 0 && turn.actions == 1
                && turn.validActions == 1 && !turn.typeChanged && !turn.descChanged) {
            infoMsg = CobolMessages.TRTYPE_CONFIRM_UPDATE;
        } else if ("PF7".equals(aid) && screenNumber <= 1) {
            // :1532-1540 — the page-edge MOVEs overwrite any browse message.
            turn.returnMsg = CobolMessages.TRTYPE_NO_PREVIOUS_PAGES;
        } else if ("PF8".equals(aid) && !nextPageExists && lastPageShown) {
            turn.returnMsg = CobolMessages.TRTYPE_NO_MORE_PAGES;
        } else if ("PF8".equals(aid) && !nextPageExists) {
            infoMsg = CobolMessages.TRTYPE_INFO_ACTIONS;
            lastPageShown = true;
        } else if (nextPageExists) {
            infoMsg = CobolMessages.TRTYPE_INFO_ACTIONS;
        }

        // :1575-1579 — the info line is suppressed while the
        // 'No records found' text is on the error line.
        if (CobolMessages.TRTYPE_NO_RECORDS_FOUND.equals(turn.returnMsg)) {
            infoMsg = null;
        }

        TranTypeListPageState newState;
        if (turn.deletedYes) {
            // :826-833 — a successful delete initializes the COMMAREA; the
            // next turn starts fresh on page 1.
            newState = TranTypeListPageState.fresh();
        } else {
            newState = new TranTypeListPageState(firstCode, lastCode,
                    screenNumber, lastPageShown, nextPageExists, turn.iSelected,
                    deletePending, updatePending, turn.typeFilter,
                    turn.descFilter, rendered);
        }

        return TranTypeListResponse.page(
                turn.screen(rendered, screenNumber, infoMsg, turn.returnMsg,
                        turn.cursorField()),
                newState);
    }

    /** Rows shown when the failed-filter path skips the re-browse. */
    private List<TranTypeListRow> echoRows(TranTypeListPageState state) {
        List<TranTypeListRow> rows = new ArrayList<>();
        for (TranTypeListRow row : state.rows()) {
            rows.add(row == null ? null
                    : new TranTypeListRow("", row.code(), row.origDesc(),
                    row.origDesc(), false, false, false));
        }
        return rows;
    }

    /** 9300-DELETE-RECORD (:1900-1939). */
    private boolean deleteFlagged(ListTurn turn, TranTypeListPageState state) {
        int index = turn.iSelected - 1;
        TranTypeListRow row = index >= 0 && index < state.rows().size()
                ? state.rows().get(index) : null;
        String code = row == null ? null : row.code();
        try {
            if (code == null || !types.existsById(code)) {
                turn.returnMsg = Db2ErrorFormatter.format(
                        "Delete failed with message:", 100, "");
                return false;
            }
            if (categories.countByIdTranTypeCode(code) > 0) {
                // S21-B4 — RESTRICT parity: the child guard surfaces -532.
                turn.returnMsg = Db2ErrorFormatter.format(
                        CobolMessages.TRTYPE_CHILD_RECORDS, -532,
                        "transaction categories reference the type");
                return false;
            }
            types.deleteById(code);
            return true;
        } catch (DataAccessException exception) {
            int sqlcode = Db2ErrorFormatter.sqlcodeFor(exception);
            turn.returnMsg = Db2ErrorFormatter.format(
                    sqlcode == -532 ? CobolMessages.TRTYPE_CHILD_RECORDS
                            : "Delete failed with message:",
                    sqlcode, sqlerrm(exception));
            return false;
        }
    }

    /** 9200-UPDATE-RECORD (:1841-1897). */
    private void updateFlagged(ListTurn turn, TranTypeListPageState state,
                               TranTypeListRequest request) {
        int index = turn.iSelected - 1;
        TranTypeListRow row = index >= 0 && index < state.rows().size()
                ? state.rows().get(index) : null;
        String code = row == null ? null : row.code();
        String newDesc = index >= 0 ? value(request.rowDescs(), index) : null;
        if (code == null) {
            turn.returnMsg = Db2ErrorFormatter.format("Update failed with", -1, "");
            return;
        }
        try {
            var existing = types.findById(code);
            if (existing.isEmpty()) {
                turn.returnMsg = Db2ErrorFormatter.format(
                        CobolMessages.TRTYPE_RECORD_GONE, 100, "");
                return;
            }
            TransactionType entity = existing.get();
            entity.setDescription(newDesc == null ? "" : newDesc.trim());
            types.save(entity);
            turn.updateDone = true;
        } catch (DataAccessException exception) {
            int sqlcode = Db2ErrorFormatter.sqlcodeFor(exception);
            if (sqlcode == -911) {
                turn.inputError = true;
                turn.returnMsg = Db2ErrorFormatter.format(
                        CobolMessages.TRTYPE_DEADLOCK, -911, sqlerrm(exception));
            } else {
                turn.returnMsg = Db2ErrorFormatter.format("Update failed with",
                        sqlcode, sqlerrm(exception));
            }
        }
    }

    /** 8000-READ-FORWARD: fill 7 rows from the start key, peek the 8th. */
    private BrowseResult forward(ListTurn turn, String start, int screenNumber,
                                 boolean aidIsPf8) {
        List<TransactionType> page = types.pageForward(turn.typeFilter,
                likePattern(turn.descFilter),
                start == null || start.isBlank() ? "" : start,
                PageRequest.of(0, PAGE_SIZE + 1));
        BrowseResult result = new BrowseResult();
        result.nextPageExists = page.size() > PAGE_SIZE;
        List<TransactionType> shown = page.subList(0, Math.min(page.size(), PAGE_SIZE));
        List<TranTypeListRow> rows = new ArrayList<>();
        for (TransactionType type : shown) {
            rows.add(TranTypeListRow.of(type.getTranType(), type.getDescription()));
        }
        while (rows.size() < PAGE_SIZE) {
            rows.add(null);
        }
        result.rows = rows;
        result.screenNumber = screenNumber;
        if (shown.isEmpty()) {
            // :1694-1705 — first fetch +100 on page 1 vs deeper.
            if (screenNumber <= 1) {
                result.screenNumber = Math.max(screenNumber, 1);
                result.message = CobolMessages.TRTYPE_NO_RECORDS_FOUND;
            } else if (aidIsPf8) {
                result.message = CobolMessages.TRTYPE_NO_MORE_RECORDS;
            }
            return result;
        }
        result.firstCode = shown.get(0).getTranType();
        if (result.screenNumber == 0) {
            result.screenNumber = 1;
        }
        if (result.nextPageExists) {
            result.lastCode = page.get(PAGE_SIZE).getTranType();
        } else if (aidIsPf8) {
            // :1674-1680 — the peek +100 arms the boundary message on PF8.
            result.message = CobolMessages.TRTYPE_NO_MORE_RECORDS;
        }
        return result;
    }

    /** 8100-READ-BACKWARDS: rows strictly below the anchor, filled bottom-up. */
    private BrowseResult backward(ListTurn turn, String firstCode) {
        List<TransactionType> page = types.pageBackward(turn.typeFilter,
                likePattern(turn.descFilter), firstCode == null ? "" : firstCode,
                PageRequest.of(0, PAGE_SIZE));
        BrowseResult result = new BrowseResult();
        result.nextPageExists = true;
        TranTypeListRow[] rows = new TranTypeListRow[PAGE_SIZE];
        int slot = PAGE_SIZE - 1;
        for (TransactionType type : page) {
            rows[slot--] = TranTypeListRow.of(type.getTranType(), type.getDescription());
        }
        result.rows = new ArrayList<>(java.util.Arrays.asList(rows));
        if (page.size() == PAGE_SIZE) {
            result.firstCode = page.get(PAGE_SIZE - 1).getTranType();
        } else {
            // :1777-1789 — +100 mid-page is reported as a cursor error and
            // the stale first-key anchor is kept.
            result.message = Db2ErrorFormatter.format(
                    "Error on fetch Cursor C-TR-TYPE-BACKWARD", 100, "");
        }
        return result;
    }

    private static class BrowseResult {
        List<TranTypeListRow> rows = List.of();
        String firstCode;
        String lastCode;
        int screenNumber;
        boolean nextPageExists;
        String message;
    }

    /** WS-MISC-STORAGE for one list turn — every field resets per call. */
    private final class ListTurn {
        boolean inputError;
        boolean typeNotOk;
        boolean descNotOk;
        boolean protectRows;
        boolean typeChanged;
        boolean descChanged;
        boolean rowChanged;
        boolean badActions;
        boolean changesOccurred;
        boolean rowDescNotOk;
        boolean rowDescBlank;
        boolean deletedYes;
        boolean updateDone;
        int deletes;
        int updates;
        int actions;
        int validActions;
        int iSelected;
        boolean[] selectError = new boolean[PAGE_SIZE];
        String[] selects;
        String typeFilter;
        String descFilter;
        String typeInput = "";
        String descInput = "";
        String returnMsg = "";

        ListTurn(TranTypeListPageState state) {
            this.typeFilter = state.typeFilter();
            this.descFilter = state.descFilter();
        }

        void returnMsg(String message) {
            if (returnMsg.isEmpty() && message != null) {
                returnMsg = message;
            }
        }

        void editInputs(TranTypeListRequest request, TranTypeListPageState state) {
            String postedType = request.trType() == null ? "" : request.trType().trim();
            String postedDesc = request.trDesc() == null ? "" : request.trDesc().trim();
            typeInput = postedType;
            descInput = postedDesc;

            boolean typeSupplied = !(postedType.isEmpty() || "00".equals(postedType));
            boolean descSupplied = !postedDesc.isEmpty();

            // 1220-EXIT/1230-EXIT: a posted filter that differs from the
            // COMMAREA copy flags CHANGED; a blank/'00' post matches a blank
            // COMMAREA without flagging.
            String caType = state.typeFilter() == null ? "" : state.typeFilter();
            String caDesc = state.descFilter() == null ? "" : state.descFilter();
            String normType = typeSupplied ? postedType : "";
            String normDesc = descSupplied ? postedDesc : "";
            typeChanged = !normType.equals(caType);
            descChanged = !normDesc.equals(caDesc);
            typeFilter = typeSupplied ? postedType : null;
            descFilter = descSupplied ? postedDesc : null;

            // :991-994's filter-change skip is dead code in shipped COBOL —
            // the CHANGED flags are only SET inside 1220/1230-EXIT, which
            // run after 1210-EDIT-ARRAY (:965-972) — so the array edit runs
            // on every turn and posted row flags are still counted/edited.
            editArray(request, state);

            // 1220-EDIT-TYPECD.
            if (typeSupplied && !postedType.matches("\\d{1,2}")) {
                inputError = true;
                typeNotOk = true;
                protectRows = true;
                returnMsg(CobolMessages.TRTYPE_FILTER_INVALID);
            }

            // 1290-CROSS-EDITS — only when a filter is valid.
            if ((typeFilter != null || descFilter != null) && !typeNotOk) {
                if (types.countFiltered(typeFilter, likePattern(descFilter)) == 0) {
                    inputError = true;
                    typeNotOk = typeFilter != null || typeNotOk;
                    descNotOk = descFilter != null || descNotOk;
                    protectRows = true;
                    returnMsg(CobolMessages.TRTYPE_NO_RECORDS_FILTERS);
                }
            }
        }

        // 1210-EDIT-ARRAY — scan bottom-up so I-SELECTED lands on the first
        // flagged row; extra selections and bad codes mark their rows.
        private void editArray(TranTypeListRequest request,
                               TranTypeListPageState state) {
            selects = new String[PAGE_SIZE];
            for (int i = 0; i < PAGE_SIZE; i++) {
                selects[i] = value(request.selects(), i);
            }
            for (String select : selects) {
                String flag = select == null ? "" : select.trim().toUpperCase();
                if ("D".equals(flag)) {
                    deletes++;
                } else if ("U".equals(flag)) {
                    updates++;
                }
            }
            validActions = deletes + updates;
            actions = (int) java.util.Arrays.stream(selects)
                    .filter(s -> s != null && !s.isBlank()).count();

            for (int i = PAGE_SIZE - 1; i >= 0; i--) {
                String flag = selects[i] == null ? "" : selects[i].trim().toUpperCase();
                if ("D".equals(flag) || "U".equals(flag)) {
                    iSelected = i + 1;
                    if (actions > 1) {
                        selectError[i] = true;
                        badActions = true;
                    }
                    if ("U".equals(flag)) {
                        editRowDesc(i, request, state);
                    }
                } else if (!flag.isEmpty()) {
                    inputError = true;
                    selectError[i] = true;
                    badActions = true;
                    returnMsg(CobolMessages.TRTYPE_ACTION_INVALID);
                }
            }

            rowChanged = iSelected != state.rowSelected();
            if (actions > 1) {
                inputError = true;
                // :549+ — the multi-select MOVE wins over earlier scan text.
                returnMsg = CobolMessages.TRTYPE_SELECT_ONLY_ONE;
            }
        }

        // 1211-EDIT-ARRAY-DESC — unchanged desc is a message, not an error.
        private void editRowDesc(int i, TranTypeListRequest request,
                                 TranTypeListPageState state) {
            String posted = value(request.rowDescs(), i);
            String original = i < state.rows().size() && state.rows().get(i) != null
                    ? state.rows().get(i).origDesc() : null;
            String newDesc = posted == null ? "" : posted.trim();
            String oldDesc = original == null ? "" : original.trim();
            if (newDesc.equalsIgnoreCase(oldDesc) && newDesc.length() == oldDesc.length()) {
                returnMsg(CobolMessages.TRTYPE_NO_CHANGES);
                return;
            }
            changesOccurred = true;
            rowDescNotOk = true;
            if (newDesc.isEmpty()) {
                inputError = true;
                rowDescBlank = true;
                returnMsg(CobolMessages.TTUP_DESC_REQUIRED);
                return;
            }
            if (newDesc.length() > 50 || !newDesc.chars().allMatch(c ->
                    Character.isLetterOrDigit(c) || c == ' ')) {
                inputError = true;
                returnMsg(CobolMessages.TTUP_DESC_ALPHANUM);
                return;
            }
            rowDescNotOk = false;
        }

        String cursorField() {
            if (typeNotOk) {
                return "trtype";
            }
            if (descNotOk) {
                return "trdesc";
            }
            if (actions > 0 && selects != null) {
                for (int i = 0; i < PAGE_SIZE; i++) {
                    if (selectError[i]) {
                        return "trtsel" + (i + 1);
                    }
                }
                for (int i = 0; i < PAGE_SIZE; i++) {
                    if ("U".equalsIgnoreCase(nz(selects[i]).trim())) {
                        return "trtypd" + (i + 1);
                    }
                }
            }
            return "trtype";
        }

        TranTypeListScreen screen(List<TranTypeListRow> rows, int screenNumber,
                                  String info, String error, String cursor) {
            List<TranTypeListRow> padded = new ArrayList<>(rows);
            while (padded.size() < PAGE_SIZE) {
                padded.add(null);
            }
            // :1444-1459 — a pending action locks the filters (blue echo); an
            // invalid filter echoes the posted text back with error styling.
            boolean filtersLocked = actions > 0;
            return new TranTypeListScreen(
                    filtersLocked || typeNotOk ? nz(typeInput) : nz(typeFilter),
                    filtersLocked || descNotOk ? nz(descInput) : nz(descFilter),
                    typeNotOk, descNotOk, filtersLocked,
                    screenNumber, padded, info, error, cursor);
        }
    }

    // ------------------------------------------------------------------
    // COTRTUPC — single-record maintenance (CTTU)
    // ------------------------------------------------------------------

    public TranTypeMaintResponse maintain(Authentication authentication,
                                          TranTypeMaintRequest request) {
        requireAdmin(authentication);

        TranTypeMaintState state = request.state() == null
                ? TranTypeMaintState.fresh(null) : request.state();
        String aid = request.aid() == null ? "ENTER" : request.aid();
        String action = state.action() == null ? TranTypeMaintState.NOT_FETCHED
                : state.action();
        String oldType = state.oldType();
        String oldDesc = state.oldDesc();
        String newType = state.newType();
        String newDesc = state.newDesc();
        boolean reenter = state.reenter();
        String fromProgram = state.fromProgram();
        String returnMsg = "";

        // :405-419 — the reset EVALUATE runs before the key gate applies.
        if (("PF12".equals(aid) && (TranTypeMaintState.SHOW.equals(action)
                || TranTypeMaintState.CREATE.equals(action)
                || TranTypeMaintState.NOT_FOUND.equals(action)))
                || TranTypeMaintState.DONE.equals(action)
                || TranTypeMaintState.LOCK_ERROR.equals(action)
                || TranTypeMaintState.FAILED.equals(action)
                || (TranTypeMaintState.BACKED_OUT.equals(action)
                && (oldType == null || oldType.isBlank()))
                || TranTypeMaintState.DELETE_DONE.equals(action)
                || TranTypeMaintState.DELETE_FAILED.equals(action)) {
            action = TranTypeMaintState.NOT_FETCHED;
            if (!"PF3".equals(aid)) {
                // PGM-ENTER — falls into the fresh-map branch below.
                reenter = false;
            }
        }

        // 0001-CHECK-PFKEYS — AID validity is state-dependent (:577-623),
        // evaluated on the post-reset action.
        boolean invalidKey = !validAid(aid, action);

        if ("PF3".equals(aid)) {
            // :429-460 — XCTL back to the caller (admin menu by default).
            return new TranTypeMaintResponse("exit", null, null,
                    new TranTypeNavigation(
                            fromProgram == null || fromProgram.isBlank()
                                    ? "COADM01C" : fromProgram,
                            "COTRTUPC"));
        }

        if (!reenter && TranTypeMaintState.NOT_FETCHED.equals(action)) {
            // :465-478 — PGM-ENTER: fresh map. FR-S21-07 — entry via the
            // list's F2 XCTL opens in create state; the admin-menu entry
            // keeps the search prompt.
            action = "COTRTLIC".equals(fromProgram)
                    ? TranTypeMaintState.CREATE : TranTypeMaintState.NOT_FETCHED;
            MaintTurn view = new MaintTurn();
            view.action = action;
            return TranTypeMaintResponse.page(maintScreen(view),
                    new TranTypeMaintState(action, oldType, oldDesc, null, null,
                            true, fromProgram));
        } else if ("PF4".equals(aid) && TranTypeMaintState.CONFIRM_DELETE.equals(action)) {
            // :482-489 — F4 confirmation runs 9800-DELETE-PROCESSING.
            action = TranTypeMaintState.START_DELETE;
            DeleteOutcome outcome = maintDelete(oldType);
            if (outcome.ok()) {
                action = TranTypeMaintState.DELETE_DONE;
            } else if (outcome.restrict()) {
                // :1638-1649 — -532 keeps START-DELETE and writes the child
                // guard text; TTUP-DELETE-FAILED is not set.
                returnMsg = outcome.message();
            } else {
                action = TranTypeMaintState.DELETE_FAILED;
                returnMsg = outcome.message();
            }
        } else if ("PF4".equals(aid) && TranTypeMaintState.SHOW.equals(action)) {
            action = TranTypeMaintState.CONFIRM_DELETE;
        } else if ("PF5".equals(aid) && TranTypeMaintState.NOT_FOUND.equals(action)) {
            action = TranTypeMaintState.CREATE;
        } else if ("PF5".equals(aid) && TranTypeMaintState.OK_NOT_CONFIRMED.equals(action)) {
            // :514-520 — F5 runs 9600-WRITE-PROCESSING.
            WriteOutcome outcome = maintWrite(newType, newDesc);
            action = outcome.action();
            returnMsg = outcome.message();
        } else if ("PF12".equals(aid) && (TranTypeMaintState.OK_NOT_CONFIRMED.equals(action)
                || TranTypeMaintState.CONFIRM_DELETE.equals(action)
                || TranTypeMaintState.SHOW.equals(action))) {
            // :524-533 -> 2000-DECIDE-ACTION PF12 leg — the per-turn
            // tranfilter flag is never set here, so the cancel messages fire.
            if (TranTypeMaintState.CONFIRM_DELETE.equals(action)) {
                returnMsg = CobolMessages.TTUP_DELETE_CANCELLED;
                action = TranTypeMaintState.NOT_FETCHED;
            } else if (TranTypeMaintState.OK_NOT_CONFIRMED.equals(action)) {
                returnMsg = CobolMessages.TTUP_UPDATE_CANCELLED;
                action = TranTypeMaintState.BACKED_OUT;
            } else {
                action = TranTypeMaintState.NOT_FETCHED;
            }
        } else if (invalidKey) {
            returnMsg = CobolMessages.TTUP_INVALID_KEY;
        } else {
            // :548-555 — OTHER runs 1000-PROCESS-INPUTS + 2000-DECIDE-ACTION.
            MaintTurn turn = processMaintInputs(aid, request, action, oldType,
                    oldDesc, newType, newDesc);
            action = turn.action;
            oldType = turn.oldType;
            oldDesc = turn.oldDesc;
            newType = turn.newType;
            newDesc = turn.newDesc;
            returnMsg = turn.returnMsg;

            return TranTypeMaintResponse.page(maintScreen(turn),
                    new TranTypeMaintState(action, oldType, oldDesc, newType,
                            newDesc, true, fromProgram));
        }

        MaintTurn view = new MaintTurn();
        view.action = action;
        view.oldType = oldType;
        view.oldDesc = oldDesc;
        view.newType = newType;
        view.newDesc = newDesc;
        view.returnMsg = returnMsg;
        return TranTypeMaintResponse.page(maintScreen(view),
                new TranTypeMaintState(action, oldType, oldDesc, newType, newDesc,
                        true, fromProgram));
    }

    /** 0001-CHECK-PFKEYS (:577-623). */
    private boolean validAid(String aid, String action) {
        return switch (aid) {
            case "PF3" -> true;
            case "ENTER" -> !TranTypeMaintState.CONFIRM_DELETE.equals(action);
            case "PF4" -> TranTypeMaintState.SHOW.equals(action)
                    || TranTypeMaintState.CONFIRM_DELETE.equals(action);
            case "PF5" -> TranTypeMaintState.OK_NOT_CONFIRMED.equals(action)
                    || TranTypeMaintState.NOT_FOUND.equals(action)
                    // :588-593 — the gate admits the whole delete-in-progress
                    // range '9'..'6'; '9' then falls to the OTHER leg.
                    || TranTypeMaintState.CONFIRM_DELETE.equals(action)
                    || TranTypeMaintState.START_DELETE.equals(action)
                    || TranTypeMaintState.DELETE_DONE.equals(action)
                    || TranTypeMaintState.DELETE_FAILED.equals(action);
            case "PF12" -> TranTypeMaintState.OK_NOT_CONFIRMED.equals(action)
                    || TranTypeMaintState.SHOW.equals(action)
                    || TranTypeMaintState.NOT_FOUND.equals(action)
                    || TranTypeMaintState.CONFIRM_DELETE.equals(action)
                    || TranTypeMaintState.CREATE.equals(action);
            default -> false;
        };
    }

    private record DeleteOutcome(boolean ok, boolean restrict, String message) {
    }

    /** 9800-DELETE-PROCESSING (:1624-1665). */
    private DeleteOutcome maintDelete(String oldType) {
        try {
            if (oldType == null || oldType.isBlank() || !types.existsById(oldType)) {
                return new DeleteOutcome(false, false,
                        CobolMessages.TTUP_DELETE_FAILED_PREFIX
                                + Db2ErrorFormatter.sqlcodeDisplay(100) + ":");
            }
            if (categories.countByIdTranTypeCode(oldType) > 0) {
                return new DeleteOutcome(false, true,
                        CobolMessages.TRTYPE_CHILD_RECORDS + "SQLCODE :"
                                + Db2ErrorFormatter.sqlcodeDisplay(-532)
                                + ":child transaction categories exist");
            }
            types.deleteById(oldType);
            return new DeleteOutcome(true, false, "");
        } catch (DataAccessException exception) {
            int sqlcode = Db2ErrorFormatter.sqlcodeFor(exception);
            if (sqlcode == -532) {
                return new DeleteOutcome(false, true,
                        CobolMessages.TRTYPE_CHILD_RECORDS + "SQLCODE :"
                                + Db2ErrorFormatter.sqlcodeDisplay(-532) + ":"
                                + sqlerrm(exception));
            }
            return new DeleteOutcome(false, false,
                    CobolMessages.TTUP_DELETE_FAILED_PREFIX
                            + Db2ErrorFormatter.sqlcodeDisplay(sqlcode) + ":"
                            + sqlerrm(exception));
        }
    }

    private record WriteOutcome(String action, String message) {
    }

    /** 9600-WRITE-PROCESSING + 9700-INSERT-RECORD (:1531-1622). */
    private WriteOutcome maintWrite(String newType, String newDesc) {
        String code = newType == null ? "" : newType;
        String desc = newDesc == null ? "" : newDesc.trim();
        try {
            var existing = types.findById(code);
            if (existing.isPresent()) {
                TransactionType entity = existing.get();
                entity.setDescription(desc);
                types.save(entity);
                return new WriteOutcome(TranTypeMaintState.DONE, "");
            }
            // +100 on UPDATE falls into 9700-INSERT-RECORD.
            TransactionType entity = new TransactionType();
            entity.setTranType(code);
            entity.setDescription(desc);
            try {
                types.save(entity);
                return new WriteOutcome(TranTypeMaintState.DONE, "");
            } catch (DataAccessException insertError) {
                return new WriteOutcome(TranTypeMaintState.FAILED,
                        CobolMessages.TTUP_INSERT_FAILED_PREFIX
                                + Db2ErrorFormatter.sqlcodeDisplay(
                                Db2ErrorFormatter.sqlcodeFor(insertError))
                                + ":" + sqlerrm(insertError));
            }
        } catch (DataAccessException exception) {
            int sqlcode = Db2ErrorFormatter.sqlcodeFor(exception);
            if (sqlcode == -911) {
                return new WriteOutcome(TranTypeMaintState.LOCK_ERROR,
                        CobolMessages.TTUP_LOCK_FAILED);
            }
            return new WriteOutcome(TranTypeMaintState.FAILED,
                    CobolMessages.TTUP_UPDATE_FAILED_PREFIX
                            + Db2ErrorFormatter.sqlcodeDisplay(sqlcode)
                            + ":" + sqlerrm(exception));
        }
    }

    private static String sqlerrm(DataAccessException exception) {
        var cause = exception.getMostSpecificCause();
        String message = cause == null ? exception.getMessage() : cause.getMessage();
        return message == null ? "" : message.trim();
    }

    /** 1000-PROCESS-INPUTS -> 1200-EDIT-MAP-INPUTS -> 2000-DECIDE-ACTION. */
    private MaintTurn processMaintInputs(String aid, TranTypeMaintRequest request,
                                         String action, String oldType,
                                         String oldDesc, String newType,
                                         String newDesc) {
        MaintTurn turn = new MaintTurn();
        turn.action = action;
        turn.oldType = oldType;
        turn.oldDesc = oldDesc;

        String postedCode = request.trtypcd() == null ? "" : request.trtypcd().trim();
        String postedDesc = request.trtydsc() == null ? "" : request.trtydsc().trim();

        // 1150-STORE-MAP-IN-NEW: in not-found with the same key re-sent (and
        // not F5) the stored new-details survive.
        boolean sameNotFoundKey = TranTypeMaintState.NOT_FOUND.equals(action)
                && postedCode.equals(newType == null ? "" : newType)
                && !"PF5".equals(aid);
        if (sameNotFoundKey) {
            turn.newType = newType;
            turn.newDesc = newDesc;
        } else {
            turn.newType = postedCode.isEmpty() || "*".equals(postedCode)
                    ? null : postedCode;
            turn.newDesc = postedDesc.isEmpty() || "*".equals(postedDesc)
                    ? null : postedDesc;
        }

        // 1200-EDIT-MAP-INPUTS.
        boolean proceed = true;
        if (TranTypeMaintState.NOT_FOUND.equals(action)
                && postedCode.equals(turn.newType == null ? "" : turn.newType)
                && !"PF5".equals(aid)) {
            // :698-707 — same key while not-found: reset to not-fetched.
            turn.action = TranTypeMaintState.NOT_FETCHED;
            turn.filterValid = true;
            proceed = false;
        } else if (TranTypeMaintState.CREATE.equals(action)) {
            // FR-S21-07 — the F2-entry's type field is user-typed (no
            // search ran first), so it takes the 1210 edit; an existing
            // key shows the record like the shipped search hit would.
            editSearchKey(turn);
            if (turn.codeBlank || turn.codeNotOk) {
                proceed = false;
            } else {
                var found = types.findById(turn.newType);
                if (found.isPresent()) {
                    turn.action = TranTypeMaintState.SHOW;
                    turn.oldType = found.get().getTranType();
                    turn.oldDesc = found.get().getDescription();
                    // A fresh hit displays the record — no edit-compare
                    // (posted inputs vs the empty incoming old fields
                    // would falsely flag CHANGES-NOT-OK) and the decide
                    // leg's noChanges guard blocks SHOW->OK_NOT_CONFIRMED.
                    turn.noChanges = true;
                    proceed = false;
                }
            }
        } else if (TranTypeMaintState.OK_NOT_CONFIRMED.equals(action)) {
            turn.filterValid = true;
        } else {
            editSearchKey(turn);
            if (turn.codeBlank) {
                if (turn.returnMsg.isEmpty()) {
                    turn.returnMsg = CobolMessages.TTUP_NO_INPUT;
                }
                turn.action = TranTypeMaintState.NOT_FETCHED;
                proceed = false;
            } else if (turn.codeNotOk) {
                // :728-731 — INVALID-SEARCH-KEYS then NOT-FETCHED: the second
                // SET on the action byte wins, leaving not-fetched.
                turn.action = TranTypeMaintState.NOT_FETCHED;
                proceed = false;
            }
        }

        if (proceed) {
            turn.filterValid = true;
            if (!TranTypeMaintState.NOT_FETCHED.equals(turn.action)) {
                // :734-736 — a still-not-fetched byte exits edit-inputs
                // before the compare; the read below shows the record.
                String cmpNew = (turn.newType == null ? "" : turn.newType)
                        + "|" + (turn.newDesc == null ? "" : turn.newDesc);
                String cmpOld = (oldType == null ? "" : oldType)
                        + "|" + (oldDesc == null ? "" : oldDesc);
                if (cmpNew.equalsIgnoreCase(cmpOld)) {
                    turn.noChanges = true;
                    turn.returnMsg(CobolMessages.NO_CHANGES_DETECTED);
                } else {
                    turn.action = TranTypeMaintState.CHANGES_NOT_OK;
                    editDescription(turn);
                    if (!turn.inputError) {
                        turn.action = TranTypeMaintState.OK_NOT_CONFIRMED;
                    }
                }
            }
        }

        // 2000-DECIDE-ACTION leg 1 (:984) — 'GET THEM': not-fetched with a
        // valid search key reads the record; the shipped COBOL body is empty
        // so the FR-documented S/X states are restored here.
        if (TranTypeMaintState.NOT_FETCHED.equals(turn.action)
                && turn.filterValid) {
            var found = types.findById(
                    turn.newType == null ? "" : turn.newType);
            if (found.isPresent()) {
                turn.action = TranTypeMaintState.SHOW;
                turn.oldType = found.get().getTranType();
                turn.oldDesc = found.get().getDescription();
            } else {
                turn.action = TranTypeMaintState.NOT_FOUND;
                turn.returnMsg(CobolMessages.TTUP_RECORD_NOT_FOUND);
            }
        } else if (TranTypeMaintState.BACKED_OUT.equals(turn.action)) {
            turn.action = TranTypeMaintState.CHANGES_NOT_OK;
        } else if (TranTypeMaintState.SHOW.equals(turn.action)
                && !turn.inputError && !turn.noChanges) {
            turn.action = TranTypeMaintState.OK_NOT_CONFIRMED;
        }
        return turn;
    }

    /** 1210-EDIT-TRANTYPE -> 1245-EDIT-NUM-REQD + NUMVAL zero-pad. */
    private void editSearchKey(MaintTurn turn) {
        String code = turn.newType;
        turn.codeNotOk = true;
        if (code == null || code.isBlank()) {
            turn.inputError = true;
            turn.codeBlank = true;
            turn.returnMsg(CobolMessages.TTUP_CODE_REQUIRED);
            return;
        }
        try {
            int value = Integer.parseInt(code.trim());
            if (value == 0) {
                turn.inputError = true;
                turn.returnMsg(CobolMessages.TTUP_CODE_NOT_ZERO);
                return;
            }
            turn.newType = "%02d".formatted(value);
            turn.codeNotOk = false;
        } catch (NumberFormatException exception) {
            turn.inputError = true;
            turn.returnMsg(CobolMessages.TTUP_CODE_NUMERIC);
        }
    }

    /** 1230-EDIT-ALPHANUM-REQD over 'Transaction Desc' length 50. */
    private void editDescription(MaintTurn turn) {
        String desc = turn.newDesc;
        if (desc == null || desc.isBlank()) {
            turn.inputError = true;
            turn.descBlank = true;
            turn.returnMsg(CobolMessages.TTUP_DESC_REQUIRED);
            return;
        }
        String trimmed = desc.trim();
        if (trimmed.length() > 50 || !trimmed.chars().allMatch(c ->
                Character.isLetterOrDigit(c) || c == ' ')) {
            turn.inputError = true;
            turn.descNotOk = true;
            turn.returnMsg(CobolMessages.TTUP_DESC_ALPHANUM);
        }
    }

    /** Per-turn working storage for the maint program. */
    private static final class MaintTurn {
        String action;
        String oldType;
        String oldDesc;
        String newType;
        String newDesc;
        String returnMsg = "";
        boolean inputError;
        boolean codeBlank;
        boolean codeNotOk;
        boolean descBlank;
        boolean descNotOk;
        boolean noChanges;
        boolean filterValid;

        void returnMsg(String message) {
            if (returnMsg.isEmpty() && message != null) {
                returnMsg = message;
            }
        }
    }

    /** 3250-SETUP-INFOMSG — per-state info line. */
    private String infoFor(String action) {
        if (action == null || TranTypeMaintState.NOT_FETCHED.equals(action)
                || TranTypeMaintState.INVALID_SEARCH.equals(action)) {
            return CobolMessages.TTUP_SEARCH_KEYS;
        }
        return switch (action) {
            case TranTypeMaintState.NOT_FOUND -> CobolMessages.TTUP_CREATE_PROMPT;
            case TranTypeMaintState.CONFIRM_DELETE -> CobolMessages.TTUP_DELETE_CONFIRM;
            case TranTypeMaintState.DELETE_DONE -> CobolMessages.TTUP_DELETE_DONE;
            case TranTypeMaintState.DELETE_FAILED -> CobolMessages.TTUP_FAILURE;
            case TranTypeMaintState.CREATE -> CobolMessages.TTUP_NEW_DATA;
            case TranTypeMaintState.OK_NOT_CONFIRMED -> CobolMessages.TTUP_CONFIRM_SAVE;
            case TranTypeMaintState.DONE -> CobolMessages.TTUP_COMMIT_DONE;
            case TranTypeMaintState.LOCK_ERROR, TranTypeMaintState.FAILED ->
                    CobolMessages.TTUP_FAILURE;
            case TranTypeMaintState.SHOW -> CobolMessages.TTUP_SEARCH_KEYS;
            case TranTypeMaintState.BACKED_OUT, TranTypeMaintState.CHANGES_NOT_OK ->
                    CobolMessages.TTUP_CHANGES_PROMPT;
            default -> CobolMessages.TTUP_SEARCH_KEYS;
        };
    }

    /** 3300-SETUP-SCREEN-ATTRS: search-key field editability. */
    private boolean codeEditableFor(String action, String oldType) {
        return TranTypeMaintState.NOT_FETCHED.equals(action)
                || TranTypeMaintState.INVALID_SEARCH.equals(action)
                || TranTypeMaintState.NOT_FOUND.equals(action)
                || (TranTypeMaintState.BACKED_OUT.equals(action)
                && (oldType == null || oldType.isBlank()));
    }

    /** 3200-SETUP-SCREEN-VARS + 3300-SETUP-SCREEN-ATTRS. */
    private TranTypeMaintScreen maintScreen(MaintTurn turn) {
        String action = turn.action;
        // 3200: originals show for S/9/6/7/B; delete-in-progress '8' is not
        // listed and falls to OTHER, which also shows the originals.
        boolean showOriginal = TranTypeMaintState.SHOW.equals(action)
                || TranTypeMaintState.CONFIRM_DELETE.equals(action)
                || TranTypeMaintState.DELETE_DONE.equals(action)
                || TranTypeMaintState.DELETE_FAILED.equals(action)
                || TranTypeMaintState.BACKED_OUT.equals(action)
                || TranTypeMaintState.START_DELETE.equals(action);
        boolean codeEditable = codeEditableFor(action, turn.oldType)
                // FR-S21-07 — an F2-entry create hasn't picked a key yet (or
                // the posted one failed), so the type field stays editable.
                || (TranTypeMaintState.CREATE.equals(action)
                && (turn.newType == null || turn.newType.isBlank()
                || turn.codeNotOk || turn.codeBlank));
        boolean descEditable = TranTypeMaintState.SHOW.equals(action)
                || TranTypeMaintState.CHANGES_NOT_OK.equals(action)
                || TranTypeMaintState.CREATE.equals(action)
                || TranTypeMaintState.BACKED_OUT.equals(action);
        String code = showOriginal ? nz(turn.oldType) : nz(turn.newType);
        String desc = showOriginal ? nz(turn.oldDesc) : nz(turn.newDesc);
        // Cursor order (:1304-1324): code field for the search/blank/done
        // states, desc for the data-entry states.
        String cursor = descEditable || turn.descNotOk || turn.descBlank
                || turn.noChanges ? "trtydsc" : "trtypcd";
        if (turn.codeNotOk || turn.codeBlank
                || TranTypeMaintState.DONE.equals(action)
                || (TranTypeMaintState.CREATE.equals(action) && codeEditable)
                || (TranTypeMaintState.BACKED_OUT.equals(action)
                && (turn.oldType == null || turn.oldType.isBlank()))) {
            cursor = "trtypcd";
        }
        return new TranTypeMaintScreen(code, desc, codeEditable, descEditable,
                infoFor(action), turn.returnMsg, cursor);
    }

    // ------------------------------------------------------------------
    // REST CRUD surface (/api/tran-types)
    // ------------------------------------------------------------------

    public TranTypeListResponse list(Authentication authentication,
                                   String type, String desc, String after,
                                   String dir) {
        if ("prev".equalsIgnoreCase(dir)) {
            return browse(authentication, new TranTypeListRequest("PF7", type, desc,
                    List.of(), List.of(), new TranTypeListPageState(after, null, 2,
                    false, true, 0, false, false, type, desc, List.of())));
        }
        TranTypeListPageState state = "next".equalsIgnoreCase(dir)
                ? new TranTypeListPageState(null, after, 1, false, true, 0,
                false, false, type, desc, List.of())
                : new TranTypeListPageState(after, null, 0, false, true, 0,
                false, false, type, desc, List.of());
        String aid = "next".equalsIgnoreCase(dir) ? "PF8" : "ENTER";
        return browse(authentication,
                new TranTypeListRequest(aid, type, desc, List.of(), List.of(), state));
    }

    public TranTypeResponse detail(Authentication authentication, String code) {
        requireAdmin(authentication);
        TransactionType type = types.findById(pad(code)).orElseThrow(
                () -> new CobolApiException(HttpStatus.NOT_FOUND,
                        CobolMessages.TTUP_RECORD_NOT_FOUND));
        return new TranTypeResponse(type.getTranType(), type.getDescription());
    }

    public TranTypeResponse create(Authentication authentication,
                                   TranTypeCrudRequest request) {
        requireAdmin(authentication);
        String code = validateCode(request.tranType());
        String desc = validateDesc(request.description());
        if (types.existsById(code)) {
            throw new CobolApiException(HttpStatus.CONFLICT,
                    CobolMessages.TTUP_INSERT_FAILED_PREFIX
                            + Db2ErrorFormatter.sqlcodeDisplay(-803) + ":duplicate key");
        }
        TransactionType entity = new TransactionType();
        entity.setTranType(code);
        entity.setDescription(desc);
        types.save(entity);
        return new TranTypeResponse(entity.getTranType(), entity.getDescription());
    }

    public TranTypeResponse update(Authentication authentication, String code,
                                   TranTypeCrudRequest request) {
        requireAdmin(authentication);
        String normalized = pad(code);
        String desc = validateDesc(request.description());
        WriteOutcome outcome = maintWrite(normalized, desc);
        if (!TranTypeMaintState.DONE.equals(outcome.action())) {
            throw new CobolApiException(HttpStatus.CONFLICT, outcome.message());
        }
        TransactionType entity = types.findById(normalized).orElseThrow();
        return new TranTypeResponse(entity.getTranType(), entity.getDescription());
    }

    public void delete(Authentication authentication, String code) {
        requireAdmin(authentication);
        DeleteOutcome outcome = maintDelete(pad(code));
        if (!outcome.ok()) {
            throw new CobolApiException(
                    outcome.restrict() ? HttpStatus.CONFLICT : HttpStatus.NOT_FOUND,
                    outcome.message());
        }
    }

    private String validateCode(String code) {
        if (code == null || code.isBlank()) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST,
                    CobolMessages.TTUP_CODE_REQUIRED);
        }
        String trimmed = code.trim();
        if (!trimmed.matches("\\d+")) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST,
                    CobolMessages.TTUP_CODE_NUMERIC);
        }
        if (Integer.parseInt(trimmed) == 0) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST,
                    CobolMessages.TTUP_CODE_NOT_ZERO);
        }
        return pad(trimmed);
    }

    private String validateDesc(String desc) {
        if (desc == null || desc.isBlank()) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST,
                    CobolMessages.TTUP_DESC_REQUIRED);
        }
        String trimmed = desc.trim();
        if (trimmed.length() > 50 || !trimmed.chars().allMatch(c ->
                Character.isLetterOrDigit(c) || c == ' ')) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST,
                    CobolMessages.TTUP_DESC_ALPHANUM);
        }
        return trimmed;
    }

    private static String pad(String code) {
        if (code == null) {
            return "";
        }
        String trimmed = code.trim();
        return trimmed.matches("\\d+") && trimmed.length() < 2
                ? "%02d".formatted(Integer.parseInt(trimmed)) : trimmed;
    }

    private static String likePattern(String desc) {
        String filter = blankToNull(desc);
        return filter == null ? null : "%" + filter.trim() + "%";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String value(List<String> list, int index) {
        if (list == null || index >= list.size()) {
            return null;
        }
        return list.get(index);
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private void requireAdmin(Authentication authentication) {
        String name = authentication == null ? null : authentication.getName();
        String userType = name == null ? "" : users.findById(name)
                .map(SecurityUser::getUserType).orElse("");
        if (!"A".equals(userType)) {
            // COTRTLIC.cbl:515-525 — the program's own admin gate.
            throw new CobolApiException(HttpStatus.FORBIDDEN, CobolMessages.ADMIN_ONLY);
        }
    }
}
