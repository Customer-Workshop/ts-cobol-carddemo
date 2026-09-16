package com.carddemo.service;

import com.carddemo.api.AdminUserListResponse;
import com.carddemo.api.AdminUserRequest;
import com.carddemo.api.AdminUserResponse;
import com.carddemo.api.CobolApiException;
import com.carddemo.api.CobolMessages;
import com.carddemo.model.SecurityUser;
import com.carddemo.repository.SecurityUserRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * COUSR00C–COUSR03C (trans CU00–CU03, maps COUSR0A–COUSR3A) — the user
 * admin screens — as a client-held cursor state machine over the shared
 * SecurityUserRepository (the Postgres `users` table, the single writer per
 * S01-B5). Every screen method takes the submitted form (map fields echo
 * back because every field is FSET) plus the COMMAREA paging/caller state
 * (S12-B4) and returns the screen to render. Values are stored as typed —
 * no upper-casing (S12-B2 store-as-typed parity) — and each field is
 * truncated at its BMS width like a 3270 field. Paragraph cites below are
 * app/cbl/COUSR0*.cbl.
 */
@Service
public class AdminUserService {
    public static final int PAGE_SIZE = 10;

    private final SecurityUserRepository repository;

    public AdminUserService(SecurityUserRepository repository) {
        this.repository = repository;
    }

    // ===================== COUSR00C — user list/browse =====================

    /** One rendered row slot: SEL0n / USRID0n / FNAME0n / LNAME0n / UTYPE0n. */
    public record Row(String userId, String firstName, String lastName, String userType) {
        static final Row BLANK = new Row("", "", "", "");
        public boolean isBlank() { return userId == null || userId.isBlank(); }
    }

    /** CDEMO-CU00-INFO slice, round-tripped as hidden fields. */
    public record State(String firstId, String lastId, long pageNum, boolean nextPage) {
        public static State fresh() { return new State("", "", 0, false); }
    }

    /** The submitted screen: search input, the ten SEL fields, the ten
     * previously displayed rows (all FSET), the displayed page number and the
     * COMMAREA paging state. Missing/tampered pieces degrade to LOW-VALUES /
     * zero, matching the EIBCALEN=0 re-init behaviour. */
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

        public static List<Row> rows(List<String> ids, List<String> firsts,
                                     List<String> lasts, List<String> types) {
            List<Row> rows = new ArrayList<>(Collections.nCopies(PAGE_SIZE, Row.BLANK));
            for (int i = 0; i < PAGE_SIZE; i++) {
                rows.set(i, new Row(nvl(at(ids, i)), nvl(at(firsts, i)),
                        nvl(at(lasts, i)), nvl(at(types, i))));
            }
            return rows;
        }

        public static List<String> selections(List<String> values) {
            List<String> sels = new ArrayList<>(Collections.nCopies(PAGE_SIZE, ""));
            for (int i = 0; i < PAGE_SIZE && values != null && i < values.size(); i++) {
                sels.set(i, nvl(values.get(i)));
            }
            return sels;
        }

        private static String at(List<String> values, int i) {
            return values != null && i < values.size() ? values.get(i) : null;
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
        public static Page unchanged(Form form, State state, String message) {
            // SEND without repopulating: rows, page display, search and SEL
            // inputs keep what the client showed (FSET).
            return new Page(form.rows(), form.pageDisplay(), state.firstId(), state.lastId(),
                    state.pageNum(), state.nextPage(), form.searchId(), form.selections(),
                    message, false);
        }
    }

    /** PROCESS-ENTER-KEY result: 'U'/'D' on a row hands off to
     * COUSR02C/COUSR03C (XCTL, terminal), otherwise the page to render. */
    public record EnterOutcome(String selection, String selectedId, Page page) {
        static EnterOutcome select(String flag, String id) {
            return new EnterOutcome(flag, id, null);
        }
        static EnterOutcome show(Page page) { return new EnterOutcome(null, null, page); }
    }

    // First entry (PGM-CONTEXT enter): ENTER on an empty map — browse from
    // LOW-VALUES, page 1.
    public Page firstDisplay() {
        return enter(Form.blank()).page();
    }

    // PROCESS-ENTER-KEY: selection scan (first non-blank SEL wins; U/u or
    // D/d hands off with CDEMO-CU0n-USR-SELECTED), then the forward browse
    // from USRIDIN with the page number reset.
    public EnterOutcome enter(Form form) {
        String pending = null;
        for (int i = 0; i < PAGE_SIZE; i++) {
            String sel = selAt(form.selections(), i);
            if (sel == null) {
                continue;
            }
            String selectedId = form.rows().get(i).userId();
            if (selectedId != null && !selectedId.isBlank()) {
                char flag = sel.charAt(0);   // SEL is X(1): a longer post truncates
                if (flag == 'U' || flag == 'u' || flag == 'D' || flag == 'd') {
                    return EnterOutcome.select(String.valueOf(flag), selectedId);
                }
                // Non-blocking; a later paging message overwrites it.
                pending = CobolMessages.USER_INVALID_SELECTION;
            }
            break;
        }
        // USRIDIN is X(8); blank means LOW-VALUES.
        String key = field(form.searchId(), 8).trim();
        return EnterOutcome.show(forward(form, key, false,
                new State(form.state().firstId(), form.state().lastId(), 0,
                        form.state().nextPage()),
                pending));
    }

    // PROCESS-PF7-KEY: NEXT-PAGE-YES is forced before the page gate, so the
    // flag persists on the outgoing state.
    public Page pf7(Form form) {
        State in = new State(form.state().firstId(), form.state().lastId(),
                form.state().pageNum(), true);
        if (in.pageNum() <= 1) {
            return Page.unchanged(form, in, CobolMessages.USER_ALREADY_TOP);
        }
        String key = in.firstId().isBlank() ? "" : in.firstId();
        return backward(form, key, in);
    }

    // PROCESS-PF8-KEY: the next-page flag gates the forward page.
    public Page pf8(Form form) {
        State in = form.state();
        if (!in.nextPage()) {
            return Page.unchanged(form, in, CobolMessages.USER_ALREADY_BOTTOM);
        }
        return forward(form, in.lastId(), true, in, null);
    }

    // Any other AID (CSMSG01Y.cpy): redisplay unchanged.
    public Page invalidAid(Form form) {
        return Page.unchanged(form, form.state(), CobolMessages.INVALID_KEY_PRESSED);
    }

    // PROCESS-PAGE-FORWARD: STARTBR GTEQ at the key, an optional skip-read
    // that consumes the positioned record on non-ENTER aids, ten READNEXT
    // fills, then a peek decides the next-page flag.
    private Page forward(Form form, String key, boolean skip, State in, String pending) {
        List<SecurityUser> window;
        try {
            window = repository.findByUserIdGreaterThanEqualOrderByUserIdAsc(key,
                    PageRequest.of(0, (skip ? 1 : 0) + PAGE_SIZE + 1));
        } catch (DataAccessException exception) {
            return Page.unchanged(form, in, CobolMessages.USER_LOOKUP_FAILED);
        }
        if (window.isEmpty()) {
            // STARTBR NOTFND: "top of the page" wording even for a key beyond
            // the file's end; S12-B3 renders the empty row set; page is the
            // value the caller staged (0 on ENTER, unchanged on PF8).
            return new Page(blankRows(), formatPage(in.pageNum()), in.firstId(),
                    in.lastId(), in.pageNum(), false, "", form.selections(),
                    CobolMessages.USER_AT_TOP, false);
        }
        List<SecurityUser> remaining = skip ? window.subList(1, window.size()) : window;
        List<Row> rows = new ArrayList<>(Collections.nCopies(PAGE_SIZE, Row.BLANK));
        for (int i = 0; i < Math.min(PAGE_SIZE, remaining.size()); i++) {
            rows.set(i, row(remaining.get(i)));
        }
        // ENDFILE — mid-fill or on the peek — raises the bottom message; an
        // invalid-selection pending message survives only when the browse
        // found another page.
        boolean peek = remaining.size() > PAGE_SIZE;
        String message = peek ? pending : CobolMessages.USER_REACHED_BOTTOM;
        long newPage = in.pageNum() + (remaining.isEmpty() ? 0 : 1);
        String first = remaining.isEmpty() ? in.firstId() : remaining.get(0).getUserId();
        String last = remaining.size() >= PAGE_SIZE
                ? remaining.get(PAGE_SIZE - 1).getUserId() : in.lastId();
        // PAGENUM refreshed and USRIDIN cleared on this path.
        return new Page(rows, formatPage(newPage), first, last, newPage, peek,
                "", form.selections(), message, false);
    }

    // PROCESS-PAGE-BACKWARD: STARTBR at USRID-FIRST, one READPREV consumes
    // the current first row, ten READPREVs fill rows bottom-up, then a peek
    // decides whether the page number decrements.
    private Page backward(Form form, String key, State in) {
        List<SecurityUser> positioned;
        List<SecurityUser> predecessors;
        try {
            positioned = repository.findByUserIdGreaterThanEqualOrderByUserIdAsc(key,
                    PageRequest.of(0, 1));
            if (positioned.isEmpty()) {
                // STARTBR NOTFND: top wording, rows and state kept.
                return new Page(form.rows(), formatPage(in.pageNum()), in.firstId(),
                        in.lastId(), in.pageNum(), in.nextPage(), form.searchId(),
                        form.selections(), CobolMessages.USER_AT_TOP, false);
            }
            predecessors = repository.findByUserIdLessThanOrderByUserIdDesc(
                    positioned.get(0).getUserId(), PageRequest.of(0, PAGE_SIZE + 1));
        } catch (DataAccessException exception) {
            return Page.unchanged(form, in, CobolMessages.USER_LOOKUP_FAILED);
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
            // Full page + peek: more predecessors and page > 1 decrement,
            // otherwise the page clamps to 1 (peek ENDFILE also raises the
            // top message).
            newPage = peek && in.pageNum() > 1 ? in.pageNum() - 1 : 1;
        }
        String first = fill == PAGE_SIZE ? predecessors.get(PAGE_SIZE - 1).getUserId()
                : in.firstId();
        String last = fill > 0 ? predecessors.get(0).getUserId() : in.lastId();
        // The backward path never clears USRIDIN.
        return new Page(rows, formatPage(newPage), first, last, newPage, in.nextPage(),
                form.searchId(), form.selections(),
                peek ? null : CobolMessages.USER_REACHED_TOP, false);
    }

    // POPULATE-USER-DATA: id and names as stored (plain text, X(8)/X(20)).
    private Row row(SecurityUser user) {
        return new Row(user.getUserId(), user.getFirstName(),
                user.getLastName(), user.getUserType());
    }

    // ======================= COUSR01C — add user ===========================

    /** The submitted add map (FNAMEI/LNAMEI/USERIDI/PASSWDI/USRTYPEI). */
    public record AddForm(String userId, String firstName, String lastName,
                          String password, String userType) {
        public static AddForm blank() { return new AddForm("", "", "", "", ""); }
    }

    /** The add screen to render: echoed fields, message, colour and cursor. */
    public record AddScreen(String userId, String firstName, String lastName,
                            String password, String userType,
                            String message, String messageStyle, String cursorField) {
        public static AddScreen blank() {
            return new AddScreen("", "", "", "", "", null, null, "fname");
        }

        static AddScreen of(AddForm form, String message, String style, String cursor) {
            return new AddScreen(form.userId(), form.firstName(), form.lastName(),
                    form.password(), form.userType(), message, style, cursor);
        }
    }

    // PROCESS-ENTER-KEY + WRITE-USER-SEC-FILE: validation order FNAME →
    // LNAME → USERID → PASSWD → USRTYPE; DUPKEY/DUPREC → USER_EXISTS; OTHER
    // → 'Unable to Add User...'; success clears the map (cursor FNAME) with
    // the green 'User <id> has been added ...'.
    public AddScreen addEnter(AddForm form) {
        AddForm fields = new AddForm(field(form.userId(), 8), field(form.firstName(), 20),
                field(form.lastName(), 20), field(form.password(), 8),
                field(form.userType(), 1));
        if (blank(fields.firstName())) {
            return AddScreen.of(fields, CobolMessages.FIRST_NAME_REQUIRED, null, "fname");
        }
        if (blank(fields.lastName())) {
            return AddScreen.of(fields, CobolMessages.LAST_NAME_REQUIRED, null, "lname");
        }
        if (blank(fields.userId())) {
            return AddScreen.of(fields, CobolMessages.USER_ID_REQUIRED_EDIT, null, "userId");
        }
        if (blank(fields.password())) {
            return AddScreen.of(fields, CobolMessages.PASSWORD_REQUIRED_EDIT, null, "passwd");
        }
        if (blank(fields.userType())) {
            return AddScreen.of(fields, CobolMessages.USER_TYPE_REQUIRED, null, "usrtype");
        }
        return addWrite(fields);
    }

    private AddScreen addWrite(AddForm fields) {
        String id = fields.userId().trim();
        try {
            if (repository.existsById(id)) {
                return AddScreen.of(fields, CobolMessages.USER_EXISTS, null, "userId");
            }
            SecurityUser user = new SecurityUser();
            user.setUserId(id);
            user.setFirstName(fields.firstName().trim());
            user.setLastName(fields.lastName().trim());
            user.setPassword(fields.password().trim());
            user.setUserType(fields.userType().trim());
            repository.saveAndFlush(user);
            return new AddScreen("", "", "", "", "",
                    CobolMessages.userAdded(id), "info", "fname");
        } catch (DataIntegrityViolationException exception) {
            // Constraint violation (S12-B1) or a lost duplicate race.
            if (repository.existsById(id)) {
                return AddScreen.of(fields, CobolMessages.USER_EXISTS, null, "userId");
            }
            return AddScreen.of(fields, CobolMessages.USER_ADD_FAILED, null, "fname");
        } catch (DataAccessException exception) {
            return AddScreen.of(fields, CobolMessages.USER_ADD_FAILED, null, "fname");
        }
    }

    // PF4 — CLEAR-CURRENT-SCREEN (INITIALIZE-ALL-FIELDS, cursor FNAME).
    public AddScreen addClear() {
        return AddScreen.blank();
    }

    // Other AID — invalid key, fields retained, cursor FNAME.
    public AddScreen addInvalidAid(AddForm form) {
        return AddScreen.of(form, CobolMessages.INVALID_KEY_PRESSED, null, "fname");
    }

    // ====================== COUSR02C — update user =========================

    /** The submitted update map (USRIDINI/FNAMEI/LNAMEI/PASSWDI/USRTYPEI). */
    public record UpdateForm(String userId, String firstName, String lastName,
                             String password, String userType) {
        public static UpdateForm blank() { return new UpdateForm("", "", "", "", ""); }
    }

    /** The update screen to render. Password echoes the stored value into a
     * type=password input (S12-B2 plaintext parity). */
    public record UpdateScreen(String userId, String firstName, String lastName,
                               String password, String userType,
                               String message, String messageStyle, String cursorField) {
        public static UpdateScreen blank() {
            return new UpdateScreen("", "", "", "", "", null, null, "usridin");
        }

        static UpdateScreen of(UpdateForm form, String message, String style, String cursor) {
            return new UpdateScreen(form.userId(), form.firstName(), form.lastName(),
                    form.password(), form.userType(), message, style, cursor);
        }
    }

    // First entry: a populated CDEMO-CU02-USR-SELECTED (S12-B4 userId param)
    // runs ENTER processing at once; a bare entry shows the empty map with
    // the cursor on USRIDIN.
    public UpdateScreen updateEntry(String selectedId) {
        if (selectedId != null && !selectedId.isBlank()) {
            return updateFetch(new UpdateForm(selectedId, "", "", "", ""));
        }
        return UpdateScreen.blank();
    }

    // PROCESS-ENTER-KEY: blank USRIDIN → error; the other four fields clear,
    // then READ; NOTFND → 'User ID NOT found...', OTHER → 'Unable to lookup
    // User...', found → fields + stored password echo + neutral save prompt.
    public UpdateScreen updateFetch(UpdateForm form) {
        String id = field(form.userId(), 8);
        if (blank(id)) {
            return UpdateScreen.of(form, CobolMessages.USER_ID_REQUIRED_EDIT, null, "usridin");
        }
        SecurityUser user;
        try {
            user = repository.findById(id.trim()).orElse(null);
        } catch (DataAccessException exception) {
            return UpdateScreen.of(new UpdateForm(id, "", "", "", ""),
                    CobolMessages.USER_LOOKUP_FAILED, null, "fname");
        }
        if (user == null) {
            return UpdateScreen.of(new UpdateForm(id, "", "", "", ""),
                    CobolMessages.USER_ID_NOT_FOUND, null, "usridin");
        }
        return new UpdateScreen(id, nvl(user.getFirstName()), nvl(user.getLastName()),
                nvl(user.getPassword()), nvl(user.getUserType()),
                CobolMessages.USER_SAVE_PROMPT, "info", null);
    }

    // UPDATE-USER-INFO: validation order USRIDIN → FNAME → LNAME → PASSWD →
    // USRTYPE; re-read (READ-USER-SEC-FILE), byte-compare the four fields
    // (S12-B2), REWRITE when different. NOTFND/OTHER messages are shared
    // with the fetch path.
    public UpdateScreen updateSave(UpdateForm form) {
        UpdateForm fields = new UpdateForm(field(form.userId(), 8),
                field(form.firstName(), 20), field(form.lastName(), 20),
                field(form.password(), 8), field(form.userType(), 1));
        if (blank(fields.userId())) {
            return UpdateScreen.of(fields, CobolMessages.USER_ID_REQUIRED_EDIT, null, "usridin");
        }
        if (blank(fields.firstName())) {
            return UpdateScreen.of(fields, CobolMessages.FIRST_NAME_REQUIRED, null, "fname");
        }
        if (blank(fields.lastName())) {
            return UpdateScreen.of(fields, CobolMessages.LAST_NAME_REQUIRED, null, "lname");
        }
        if (blank(fields.password())) {
            return UpdateScreen.of(fields, CobolMessages.PASSWORD_REQUIRED_EDIT, null, "passwd");
        }
        if (blank(fields.userType())) {
            return UpdateScreen.of(fields, CobolMessages.USER_TYPE_REQUIRED, null, "usrtype");
        }
        String id = fields.userId().trim();
        SecurityUser user;
        try {
            user = repository.findById(id).orElse(null);
        } catch (DataAccessException exception) {
            return UpdateScreen.of(fields, CobolMessages.USER_LOOKUP_FAILED, null, "fname");
        }
        if (user == null) {
            return UpdateScreen.of(fields, CobolMessages.USER_ID_NOT_FOUND, null, "usridin");
        }
        if (user.getFirstName().equals(fields.firstName().trim())
                && user.getLastName().equals(fields.lastName().trim())
                && user.getPassword().equals(fields.password().trim())
                && user.getUserType().equals(fields.userType().trim())) {
            // USR-MODIFIED-NO: red message, fields stay, no cursor move.
            return UpdateScreen.of(fields, CobolMessages.USER_MODIFY_TO_UPDATE, null, null);
        }
        user.setFirstName(fields.firstName().trim());
        user.setLastName(fields.lastName().trim());
        user.setPassword(fields.password().trim());
        user.setUserType(fields.userType().trim());
        try {
            repository.saveAndFlush(user);
            return UpdateScreen.of(fields, CobolMessages.userUpdated(id), "info", null);
        } catch (DataIntegrityViolationException exception) {
            // REWRITE OTHER (S12-B1): out-of-domain user_type lands here.
            return UpdateScreen.of(fields, CobolMessages.USER_UPDATE_FAILED, null, "fname");
        } catch (DataAccessException exception) {
            if (!repository.existsById(id)) {
                return UpdateScreen.of(fields, CobolMessages.USER_ID_NOT_FOUND, null, "usridin");
            }
            return UpdateScreen.of(fields, CobolMessages.USER_UPDATE_FAILED, null, "fname");
        }
    }

    // PF4 — CLEAR-CURRENT-SCREEN (cursor USRIDIN).
    public UpdateScreen updateClear() {
        return UpdateScreen.blank();
    }

    // Other AID — invalid key, fields retained, no cursor move.
    public UpdateScreen updateInvalidAid(UpdateForm form) {
        return UpdateScreen.of(form, CobolMessages.INVALID_KEY_PRESSED, null, null);
    }

    // ====================== COUSR03C — delete user =========================

    /** The submitted delete map (USRIDINI/FNAMEI/LNAMEI/USRTYPEI). */
    public record DeleteForm(String userId, String firstName, String lastName,
                             String userType) {
        public static DeleteForm blank() { return new DeleteForm("", "", "", ""); }
    }

    /** The delete screen to render (no password field on COUSR3A). */
    public record DeleteScreen(String userId, String firstName, String lastName,
                               String userType,
                               String message, String messageStyle, String cursorField) {
        public static DeleteScreen blank() {
            return new DeleteScreen("", "", "", "", null, null, "usridin");
        }

        static DeleteScreen of(DeleteForm form, String message, String style, String cursor) {
            return new DeleteScreen(form.userId(), form.firstName(), form.lastName(),
                    form.userType(), message, style, cursor);
        }
    }

    // First entry: a populated CDEMO-CU03-USR-SELECTED runs ENTER processing
    // at once; a bare entry shows the empty map with the cursor on USRIDIN.
    public DeleteScreen deleteEntry(String selectedId) {
        if (selectedId != null && !selectedId.isBlank()) {
            return deleteFetch(new DeleteForm(selectedId, "", "", ""));
        }
        return DeleteScreen.blank();
    }

    // PROCESS-ENTER-KEY: blank USRIDIN → error; the three display fields
    // clear, then READ; NOTFND → 'User ID NOT found...', OTHER → 'Unable to
    // lookup User...', found → fields + neutral delete prompt.
    public DeleteScreen deleteFetch(DeleteForm form) {
        String id = field(form.userId(), 8);
        if (blank(id)) {
            return DeleteScreen.of(form, CobolMessages.USER_ID_REQUIRED_EDIT, null, "usridin");
        }
        SecurityUser user;
        try {
            user = repository.findById(id.trim()).orElse(null);
        } catch (DataAccessException exception) {
            return DeleteScreen.of(new DeleteForm(id, "", "", ""),
                    CobolMessages.USER_LOOKUP_FAILED, null, "fname");
        }
        if (user == null) {
            return DeleteScreen.of(new DeleteForm(id, "", "", ""),
                    CobolMessages.USER_ID_NOT_FOUND, null, "usridin");
        }
        return new DeleteScreen(id, nvl(user.getFirstName()), nvl(user.getLastName()),
                nvl(user.getUserType()), CobolMessages.USER_DELETE_CONFIRM, "info", null);
    }

    // DELETE-USER-INFO: blank check, then READ then DELETE unconditionally —
    // the DELETE's message wins. DELETE NOTFND → 'User ID NOT found...';
    // DELETE OTHER → the verbatim quirk 'Unable to Update User...'; NORMAL
    // clears the map (cursor USRIDIN) with the green deleted message.
    public DeleteScreen deleteDelete(DeleteForm form) {
        String id = field(form.userId(), 8);
        if (blank(id)) {
            return DeleteScreen.of(form, CobolMessages.USER_ID_REQUIRED_EDIT, null, "usridin");
        }
        SecurityUser user;
        try {
            user = repository.findById(id.trim()).orElse(null);
        } catch (DataAccessException exception) {
            return DeleteScreen.of(form, CobolMessages.USER_LOOKUP_FAILED,
                    null, "fname");
        }
        if (user == null) {
            return DeleteScreen.of(form, CobolMessages.USER_ID_NOT_FOUND,
                    null, "usridin");
        }
        try {
            repository.delete(user);
            return new DeleteScreen("", "", "", "",
                    CobolMessages.userDeleted(id.trim()), "info", "usridin");
        } catch (DataAccessException exception) {
            return DeleteScreen.of(form, CobolMessages.USER_UPDATE_FAILED, null, "fname");
        }
    }

    // PF4 — CLEAR-CURRENT-SCREEN (cursor USRIDIN).
    public DeleteScreen deleteClear() {
        return DeleteScreen.blank();
    }

    // Other AID — invalid key, fields retained, no cursor move.
    public DeleteScreen deleteInvalidAid(DeleteForm form) {
        return DeleteScreen.of(form, CobolMessages.INVALID_KEY_PRESSED, null, null);
    }

    // ========================= REST surface ================================
    // Same endpoints as the baseline contract, same screen semantics keyed
    // by page number (offset browsing — the REST idiom, matching
    // TransactionService), values stored as typed per S12-B2.

    public AdminUserListResponse list(String filter, int page) {
        if (page < 0) throw bad(CobolMessages.USER_ALREADY_TOP);
        Pageable pageable = PageRequest.of(page, PAGE_SIZE,
                Sort.by(Sort.Direction.ASC, "userId"));
        org.springframework.data.domain.Page<SecurityUser> users = filter == null
                || filter.isBlank()
                ? repository.findAll(pageable)
                : repository.findByUserIdGreaterThanEqual(field(filter, 8).trim(), pageable);
        if (users.isEmpty()) throw notFound(CobolMessages.USER_REACHED_BOTTOM);
        return new AdminUserListResponse(page, PAGE_SIZE, users.hasNext(), page > 0,
                users.getContent().stream().map(this::response).toList());
    }

    public AdminUserResponse add(AdminUserRequest request) {
        if (request == null) throw bad(CobolMessages.USER_ID_REQUIRED_EDIT);
        requireField(request.firstName(), CobolMessages.FIRST_NAME_REQUIRED);
        requireField(request.lastName(), CobolMessages.LAST_NAME_REQUIRED);
        requireField(request.userId(), CobolMessages.USER_ID_REQUIRED_EDIT);
        requireField(request.password(), CobolMessages.PASSWORD_REQUIRED_EDIT);
        requireField(request.userType(), CobolMessages.USER_TYPE_REQUIRED);
        // REST has no 3270 field to truncate the input — the BMS width is
        // enforced as a 400 instead (baseline contract retained).
        requireLength(request.userId(), 8, CobolMessages.USER_ID_TOO_LONG);
        requireLength(request.firstName(), 20, CobolMessages.FIRST_NAME_TOO_LONG);
        requireLength(request.lastName(), 20, CobolMessages.LAST_NAME_TOO_LONG);
        requireLength(request.password(), 8, CobolMessages.PASSWORD_TOO_LONG);
        String id = request.userId().trim();
        try {
            if (repository.existsById(id)) throw bad(CobolMessages.USER_EXISTS);
            SecurityUser user = new SecurityUser();
            fill(user, request);
            repository.saveAndFlush(user);
            return response(user);
        } catch (DataIntegrityViolationException exception) {
            if (repository.existsById(id)) throw bad(CobolMessages.USER_EXISTS);
            throw bad(CobolMessages.USER_ADD_FAILED);
        } catch (DataAccessException exception) {
            throw bad(CobolMessages.USER_ADD_FAILED);
        }
    }

    public AdminUserResponse update(String rawId, AdminUserRequest request) {
        if (rawId == null || rawId.isBlank()) throw bad(CobolMessages.USER_ID_REQUIRED);
        String id = rawId.trim();
        requireLength(id, 8, CobolMessages.USER_ID_TOO_LONG);
        if (request == null) throw bad(CobolMessages.USER_ID_REQUIRED_EDIT);
        requireField(request.firstName(), CobolMessages.FIRST_NAME_REQUIRED);
        requireField(request.lastName(), CobolMessages.LAST_NAME_REQUIRED);
        requireField(request.password(), CobolMessages.PASSWORD_REQUIRED_EDIT);
        requireField(request.userType(), CobolMessages.USER_TYPE_REQUIRED);
        requireLength(request.userId() == null ? "" : request.userId(), 8,
                CobolMessages.USER_ID_TOO_LONG);
        requireLength(request.firstName(), 20, CobolMessages.FIRST_NAME_TOO_LONG);
        requireLength(request.lastName(), 20, CobolMessages.LAST_NAME_TOO_LONG);
        requireLength(request.password(), 8, CobolMessages.PASSWORD_TOO_LONG);
        requireField(request.userId(), CobolMessages.USER_ID_REQUIRED_EDIT);
        SecurityUser user = repository.findById(id)
                .orElseThrow(() -> notFound(CobolMessages.USER_ID_NOT_FOUND));
        if (user.getFirstName().equals(request.firstName().trim())
                && user.getLastName().equals(request.lastName().trim())
                && user.getPassword().equals(request.password().trim())
                && user.getUserType().equals(request.userType().trim())) {
            throw bad(CobolMessages.USER_MODIFY_TO_UPDATE);
        }
        fill(user, request);
        user.setUserId(id);
        try {
            repository.saveAndFlush(user);
            return response(user);
        } catch (DataIntegrityViolationException exception) {
            throw bad(CobolMessages.USER_UPDATE_FAILED);
        } catch (DataAccessException exception) {
            if (!repository.existsById(id)) throw notFound(CobolMessages.USER_ID_NOT_FOUND);
            throw bad(CobolMessages.USER_UPDATE_FAILED);
        }
    }

    public void delete(String rawId) {
        if (rawId == null || rawId.isBlank()) throw bad(CobolMessages.USER_ID_REQUIRED);
        String id = rawId.trim();
        requireLength(id, 8, CobolMessages.USER_ID_TOO_LONG);
        SecurityUser user = repository.findById(id)
                .orElseThrow(() -> notFound(CobolMessages.USER_ID_NOT_FOUND));
        try {
            repository.delete(user);
        } catch (DataAccessException exception) {
            // COUSR03C.cbl:331-334 — DELETE OTHER shows 'Unable to Update
            // User...' verbatim.
            throw bad(CobolMessages.USER_UPDATE_FAILED);
        }
    }

    private void fill(SecurityUser user, AdminUserRequest request) {
        user.setUserId(request.userId().trim());
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPassword(request.password().trim());
        user.setUserType(request.userType().trim());
    }

    // ========================== helpers ====================================

    // A submitted value as the X-field holds it: truncated to the BMS width.
    private static String field(String value, int width) {
        if (value == null) {
            return "";
        }
        return value.length() <= width ? value : value.substring(0, width);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }

    private static String selAt(List<String> selections, int i) {
        if (selections == null || i >= selections.size()) {
            return null;
        }
        String sel = selections.get(i);
        return sel == null || sel.isBlank() ? null : sel;
    }

    static String formatPage(long pageNum) {
        return "%08d".formatted(Math.max(0, Math.min(pageNum, 99_999_999)));
    }

    private static List<Row> blankRows() {
        return new ArrayList<>(Collections.nCopies(PAGE_SIZE, Row.BLANK));
    }

    private void requireField(String value, String message) {
        if (blank(value)) throw bad(message);
    }

    private void requireLength(String value, int max, String message) {
        if (value.trim().length() > max) throw bad(message);
    }

    private AdminUserResponse response(SecurityUser user) {
        return new AdminUserResponse(user.getUserId(), user.getFirstName(),
                user.getLastName(), user.getUserType());
    }

    private CobolApiException bad(String message) {
        return new CobolApiException(HttpStatus.BAD_REQUEST, message);
    }

    private CobolApiException notFound(String message) {
        return new CobolApiException(HttpStatus.NOT_FOUND, message);
    }
}
