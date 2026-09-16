package com.carddemo.service;

import com.carddemo.api.CobolMessages;
import com.carddemo.model.SecurityUser;
import com.carddemo.repository.SecurityUserRepository;
import com.carddemo.service.AdminUserService.AddForm;
import com.carddemo.service.AdminUserService.AddScreen;
import com.carddemo.service.AdminUserService.DeleteForm;
import com.carddemo.service.AdminUserService.DeleteScreen;
import com.carddemo.service.AdminUserService.EnterOutcome;
import com.carddemo.service.AdminUserService.Form;
import com.carddemo.service.AdminUserService.Page;
import com.carddemo.service.AdminUserService.Row;
import com.carddemo.service.AdminUserService.State;
import com.carddemo.service.AdminUserService.UpdateForm;
import com.carddemo.service.AdminUserService.UpdateScreen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * COUSR00C–COUSR03C branch coverage, derived from app/cbl/COUSR0*.cbl and
 * FR-S12-01..40 — expectations were traced from the COBOL, not from the new
 * code. The repository double plays the USRSEC VSAM file: an ascending key
 * browse (STARTBR/READNEXT) plus a descending predecessor read (READPREV).
 */
class UserAdminServiceTest {

    private SecurityUserRepository repository;
    private AdminUserService service;
    private List<SecurityUser> file; // ascending user-id order, like USRSEC

    @BeforeEach
    void setUp() {
        repository = mock(SecurityUserRepository.class);
        file = new ArrayList<>();
        when(repository.findByUserIdGreaterThanEqualOrderByUserIdAsc(
                anyString(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    int size = invocation.<Pageable>getArgument(1).getPageSize();
                    return file.stream()
                            .filter(row -> row.getUserId().compareTo(key) >= 0)
                            .limit(size).toList();
                });
        when(repository.findByUserIdLessThanOrderByUserIdDesc(
                anyString(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    int size = invocation.<Pageable>getArgument(1).getPageSize();
                    return file.stream()
                            .filter(row -> row.getUserId().compareTo(key) < 0)
                            .sorted(Comparator.comparing(SecurityUser::getUserId)
                                    .reversed())
                            .limit(size).toList();
                });
        when(repository.findById(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return file.stream().filter(row -> row.getUserId().equals(id)).findFirst();
        });
        when(repository.existsById(anyString())).thenAnswer(invocation ->
                file.stream().anyMatch(row -> row.getUserId()
                        .equals(invocation.getArgument(0))));
        when(repository.saveAndFlush(any(SecurityUser.class)))
                .thenAnswer(invocation -> {
                    SecurityUser user = invocation.getArgument(0);
                    file.removeIf(row -> row.getUserId().equals(user.getUserId()));
                    file.add(user);
                    file.sort(Comparator.comparing(SecurityUser::getUserId));
                    return user;
                });
        doAnswer(invocation -> {
            SecurityUser target = invocation.getArgument(0);
            file.removeIf(row -> row.getUserId().equals(target.getUserId()));
            return null;
        }).when(repository).delete(any(SecurityUser.class));
        service = new AdminUserService(repository);
    }

    private static SecurityUser user(int seq) {
        SecurityUser user = new SecurityUser();
        user.setUserId("USR%05d".formatted(seq));
        user.setFirstName("FIRST%04d".formatted(seq));
        user.setLastName("LAST%04d".formatted(seq));
        user.setPassword("PASS%04d".formatted(seq));
        user.setUserType(seq % 2 == 0 ? "U" : "A");
        return user;
    }

    private void seed(int count) {
        IntStream.rangeClosed(1, count).mapToObj(UserAdminServiceTest::user)
                .forEach(file::add);
    }

    private static Form form(List<Row> rows, String pageDisplay, State state,
                             String searchId, List<String> sels) {
        return new Form(searchId,
                sels != null ? sels : List.of("", "", "", "", "", "", "", "", "", ""),
                rows != null ? rows
                        : List.of(Row.BLANK, Row.BLANK, Row.BLANK, Row.BLANK,
                                Row.BLANK, Row.BLANK, Row.BLANK, Row.BLANK,
                                Row.BLANK, Row.BLANK),
                pageDisplay, state.firstId(), state.lastId(),
                Long.toString(state.pageNum()), state.nextPage() ? "Y" : "");
    }

    private static List<Row> displayed(Page page) {
        return page.rows().stream().filter(row -> !row.isBlank()).toList();
    }

    private static List<String> sels(String... values) {
        List<String> sels = new ArrayList<>(
                List.of("", "", "", "", "", "", "", "", "", ""));
        for (int i = 0; i < values.length; i++) {
            sels.set(i, values[i]);
        }
        return sels;
    }

    // ===================== COUSR00C — list/browse =====================

    @Test
    void firstEntryBrowsesFromLowValuesAndSetsNextPage_frS1201() {
        // COUSR00C:216-228 + :282-331 — ENTER on an empty map browses from
        // LOW-VALUES; the look-ahead read sets NEXT-PAGE.
        seed(12);
        Page page = service.firstDisplay();

        assertThat(displayed(page)).hasSize(10);
        assertThat(displayed(page).get(0).userId()).isEqualTo("USR00001");
        assertThat(displayed(page).get(9).userId()).isEqualTo("USR00010");
        assertThat(page.pageDisplay()).isEqualTo("00000001");
        assertThat(page.pageNum()).isEqualTo(1);
        assertThat(page.firstId()).isEqualTo("USR00001");
        assertThat(page.lastId()).isEqualTo("USR00010");
        assertThat(page.nextPage()).isTrue();
        assertThat(page.message()).isNull();
        assertThat(page.searchId()).isEmpty();
        // S12-B3: the empty row slots render as blanks, not skipped.
        assertThat(page.rows()).hasSize(10);
    }

    @Test
    void firstEntryOnShortFileReachesBottom_frS1209() {
        seed(4);
        Page page = service.firstDisplay();

        assertThat(displayed(page)).hasSize(4);
        assertThat(page.pageNum()).isEqualTo(1);
        assertThat(page.nextPage()).isFalse();
        assertThat(page.message()).isEqualTo(CobolMessages.USER_REACHED_BOTTOM);
    }

    @Test
    void enterWithSearchKeyRestartsBrowse_frS1202() {
        // COUSR00C:218-228 — a non-blank USRIDIN with no selection re-keys
        // the STARTBR and resets the page to 1.
        seed(12);
        Form incoming = form(null, "00000009",
                new State("USR00009", "USR00012", 9, true), "USR00005", null);
        Page page = service.enter(incoming).page();

        assertThat(displayed(page).get(0).userId()).isEqualTo("USR00005");
        assertThat(page.pageNum()).isEqualTo(1);
        assertThat(page.searchId()).isEmpty();
    }

    @Test
    void enterWithUpdateSelectionHandsOffToCousr02c_frS1203() {
        // COUSR00C:151-199 — 'U'/'u' on a row carries the row's user id.
        seed(5);
        Page first = service.firstDisplay();
        EnterOutcome outcome = service.enter(form(first.rows(), "00000001",
                new State("USR00001", "USR00005", 1, false), "", sels("", "", "u")));

        assertThat(outcome.selection()).isEqualTo("u");
        assertThat(outcome.selectedId()).isEqualTo("USR00003");
        assertThat(outcome.page()).isNull();
    }

    @Test
    void enterWithDeleteSelectionHandsOffToCousr03c_frS1204() {
        seed(5);
        Page first = service.firstDisplay();
        EnterOutcome outcome = service.enter(form(first.rows(), "00000001",
                new State("USR00001", "USR00005", 1, false), "", sels("", "", "", "D")));

        assertThat(outcome.selection()).isEqualTo("D");
        assertThat(outcome.selectedId()).isEqualTo("USR00004");
    }

    @Test
    void enterWithInvalidSelectionRefreshesAndReports_frS1205() {
        // COUSR00C:210-228 — the message is raised, then the list re-reads
        // from the search key at page 1; it survives only when the browse
        // fills the page without hitting ENDFILE.
        seed(12);
        Page first = service.firstDisplay();
        Page page = service.enter(form(first.rows(), "00000001",
                new State("USR00001", "USR00010", 1, true),
                "", sels("X"))).page();

        assertThat(page.message()).isEqualTo(CobolMessages.USER_INVALID_SELECTION);
        assertThat(displayed(page)).hasSize(10);
        assertThat(page.pageNum()).isEqualTo(1);
        assertThat(page.nextPage()).isTrue();
    }

    @Test
    void enterHonoursFirstNonBlankSelectionOnly_frS1206() {
        seed(5);
        Page first = service.firstDisplay();
        EnterOutcome outcome = service.enter(form(first.rows(), "00000001",
                new State("USR00001", "USR00005", 1, false),
                "", sels("", "D", "", "", "U")));

        assertThat(outcome.selection()).isEqualTo("D");
        assertThat(outcome.selectedId()).isEqualTo("USR00002");
    }

    @Test
    void pf8SkipsPositionedRowAndAdvancesPage_frS1207() {
        // COUSR00C:258-278 — the forward browse starts at USRID-LAST and
        // consumes the positioned record before filling.
        seed(25);
        Form incoming = form(null, "00000001",
                new State("USR00001", "USR00010", 1, true), "", null);
        Page page = service.pf8(incoming);

        assertThat(displayed(page).get(0).userId()).isEqualTo("USR00011");
        assertThat(displayed(page).get(9).userId()).isEqualTo("USR00020");
        assertThat(page.pageNum()).isEqualTo(2);
        assertThat(page.firstId()).isEqualTo("USR00011");
        assertThat(page.lastId()).isEqualTo("USR00020");
        assertThat(page.nextPage()).isTrue();
    }

    @Test
    void pf8WithoutNextPageReportsBottom_frS1208() {
        Form incoming = form(null, "00000002",
                new State("USR00011", "USR00012", 2, false), "", null);
        Page page = service.pf8(incoming);

        assertThat(page.message()).isEqualTo(CobolMessages.USER_ALREADY_BOTTOM);
        assertThat(page.pageNum()).isEqualTo(2);
        assertThat(page.pageDisplay()).isEqualTo("00000002");
    }

    @Test
    void forwardShortPageReportsBottomAndCounts_frS1209() {
        // 12 users → page 2 holds 2 rows; ENDFILE mid-fill raises the
        // bottom message and the page still increments (:634-641).
        seed(12);
        Page page = service.pf8(form(null, "00000001",
                new State("USR00001", "USR00010", 1, true), "", null));

        assertThat(displayed(page)).hasSize(2);
        assertThat(displayed(page).get(0).userId()).isEqualTo("USR00011");
        assertThat(page.pageNum()).isEqualTo(2);
        assertThat(page.nextPage()).isFalse();
        assertThat(page.message()).isEqualTo(CobolMessages.USER_REACHED_BOTTOM);
    }

    @Test
    void pf7ReturnsToPreviousPage_frS1210() {
        seed(12);
        Page page = service.pf7(form(null, "00000002",
                new State("USR00011", "USR00012", 2, false), "", null));

        assertThat(displayed(page)).hasSize(10);
        assertThat(displayed(page).get(0).userId()).isEqualTo("USR00001");
        assertThat(page.pageNum()).isEqualTo(1);
        assertThat(page.nextPage()).isTrue(); // PF7 forces NEXT-PAGE-YES
    }

    @Test
    void pf7OnFirstPageReportsTop_frS1211() {
        seed(5);
        Form incoming = form(null, "00000001",
                new State("USR00001", "USR00005", 1, false), "", null);
        Page page = service.pf7(incoming);

        assertThat(page.message()).isEqualTo(CobolMessages.USER_ALREADY_TOP);
        assertThat(page.pageNum()).isEqualTo(1);
        // The forced NEXT-PAGE-YES persists on the outgoing state.
        assertThat(page.nextPage()).isTrue();
    }

    @Test
    void pf7ShortFillReportsTopAndKeepsPage_frS1212() {
        // Backward from a mid-file page whose predecessors under-fill the
        // screen: 'You have reached the top of the page...'; the page number
        // drops to 1 only when the look-behind found a full page.
        seed(12);
        Page page = service.pf7(form(null, "00000003",
                new State("USR00003", "USR00012", 3, false), "", null));

        // Rows fill bottom-up; the page number keeps its incoming value
        // because the peek block only runs after a full 10-row fill.
        assertThat(displayed(page)).hasSize(2);
        assertThat(page.rows().get(8).userId()).isEqualTo("USR00001");
        assertThat(page.rows().get(9).userId()).isEqualTo("USR00002");
        assertThat(page.message()).isEqualTo(CobolMessages.USER_REACHED_TOP);
        assertThat(page.pageNum()).isEqualTo(3);
    }

    @Test
    void enterWithKeyBeyondFileShowsTop_frS1213() {
        // STARTBR NOTFND (:600-605): the odd "top of the page" wording,
        // no rows, page stays at the staged 0.
        seed(5);
        Page page = service.enter(form(null, "00000000",
                State.fresh(), "ZZZ", null)).page();

        assertThat(displayed(page)).isEmpty();
        assertThat(page.message()).isEqualTo(CobolMessages.USER_AT_TOP);
        assertThat(page.pageNum()).isEqualTo(0);
        assertThat(page.nextPage()).isFalse();
    }

    @Test
    void browseStoreErrorShowsLookupFailed_frS1214() {
        when(repository.findByUserIdGreaterThanEqualOrderByUserIdAsc(
                anyString(), any(Pageable.class)))
                .thenThrow(new DataAccessException("store down") { });
        Page page = service.pf8(form(null, "00000001",
                new State("A", "B", 1, true), "", null));

        assertThat(page.message()).isEqualTo(CobolMessages.USER_LOOKUP_FAILED);
        assertThat(page.pageNum()).isEqualTo(1); // screen unchanged
    }

    @Test
    void invalidAidShowsInvalidKey_frS1216() {
        Page page = service.invalidAid(form(null, "00000001",
                new State("A", "B", 1, true), "", null));
        assertThat(page.message()).isEqualTo(CobolMessages.INVALID_KEY_PRESSED);
    }

    // ===================== COUSR01C — add =====================

    @Test
    void addEnterChecksFieldsInCobolOrder_frS1217() {
        // COUSR01C:118-147 — FNAME, LNAME, USERID, PASSWD, USRTYPE; first
        // failure wins and the cursor lands on it.
        AddScreen screen = service.addEnter(
                new AddForm("NEWID1", "", "LAST", "PASS", "U"));
        assertThat(screen.message()).isEqualTo(CobolMessages.FIRST_NAME_REQUIRED);
        assertThat(screen.cursorField()).isEqualTo("fname");

        screen = service.addEnter(new AddForm("NEWID1", "FIRST", "", "PASS", "U"));
        assertThat(screen.message()).isEqualTo(CobolMessages.LAST_NAME_REQUIRED);
        assertThat(screen.cursorField()).isEqualTo("lname");

        screen = service.addEnter(new AddForm("", "FIRST", "LAST", "PASS", "U"));
        assertThat(screen.message()).isEqualTo(CobolMessages.USER_ID_REQUIRED_EDIT);
        assertThat(screen.cursorField()).isEqualTo("userId");

        screen = service.addEnter(new AddForm("NEWID1", "FIRST", "LAST", "", "U"));
        assertThat(screen.message()).isEqualTo(CobolMessages.PASSWORD_REQUIRED_EDIT);
        assertThat(screen.cursorField()).isEqualTo("passwd");

        screen = service.addEnter(new AddForm("NEWID1", "FIRST", "LAST", "PASS", ""));
        assertThat(screen.message()).isEqualTo(CobolMessages.USER_TYPE_REQUIRED);
        assertThat(screen.cursorField()).isEqualTo("usrtype");
    }

    @Test
    void addEnterWritesClearsAndConfirms_frS1218() {
        // COUSR01C:240-259 — WRITE-USER-SEC-FILE success clears the map and
        // reports in green; the id is the key portion (DELIMITED BY SPACE).
        AddScreen screen = service.addEnter(
                new AddForm("NEWID1", "NEW", "USER", "NEWPASS", "U"));

        assertThat(screen.message()).isEqualTo("User NEWID1 has been added ...");
        assertThat(screen.messageStyle()).isEqualTo("info");
        assertThat(screen.cursorField()).isEqualTo("fname");
        assertThat(screen.userId()).isEmpty();
        SecurityUser stored = file.get(0);
        assertThat(stored.getUserId()).isEqualTo("NEWID1");
        assertThat(stored.getPassword()).isEqualTo("NEWPASS");
    }

    @Test
    void addEnterDuplicateKeepsFieldsAndFails_frS1219() {
        file.add(user(1));
        AddScreen screen = service.addEnter(
                new AddForm("USR00001", "X", "Y", "Z", "A"));

        assertThat(screen.message()).isEqualTo(CobolMessages.USER_EXISTS);
        assertThat(screen.userId()).isEqualTo("USR00001");
        assertThat(screen.firstName()).isEqualTo("X");
        assertThat(screen.cursorField()).isEqualTo("userId");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void addEnterWriteErrorFailsWithUnable_frS1220() {
        // REWRITE/WRITE OTHER (:267-273) — a store rejection that is not a
        // duplicate lands here (S12-B1 constraint violations included).
        doThrow(new DataIntegrityViolationException("check"))
                .when(repository).saveAndFlush(any(SecurityUser.class));
        AddScreen screen = service.addEnter(
                new AddForm("NEWID1", "NEW", "USER", "PASS", "X"));

        assertThat(screen.message()).isEqualTo(CobolMessages.USER_ADD_FAILED);
        assertThat(screen.cursorField()).isEqualTo("fname");
    }

    @Test
    void addInvalidAidKeepsFieldsWithCursorOnFname_frS1223() {
        AddScreen screen = service.addInvalidAid(
                new AddForm("ID", "F", "L", "P", "A"));
        assertThat(screen.message()).isEqualTo(CobolMessages.INVALID_KEY_PRESSED);
        assertThat(screen.firstName()).isEqualTo("F");
        assertThat(screen.cursorField()).isEqualTo("fname");
    }

    // ===================== COUSR02C — update =====================

    @Test
    void updateFetchRejectsBlankId_frS1225() {
        UpdateScreen screen = service.updateFetch(
                new UpdateForm("  ", "F", "L", "P", "A"));
        assertThat(screen.message()).isEqualTo(CobolMessages.USER_ID_REQUIRED_EDIT);
        assertThat(screen.cursorField()).isEqualTo("usridin");
    }

    @Test
    void updateFetchEchoesPasswordAndPrompts_frS1226() {
        // S12-B2: the stored plaintext lands in the dark PASSWD field and
        // the neutral prompt explains PF5.
        seed(1);
        UpdateScreen screen = service.updateFetch(
                new UpdateForm("USR00001", "", "", "", ""));

        assertThat(screen.firstName()).isEqualTo("FIRST0001");
        assertThat(screen.lastName()).isEqualTo("LAST0001");
        assertThat(screen.password()).isEqualTo("PASS0001");
        assertThat(screen.userType()).isEqualTo("A");
        assertThat(screen.message()).isEqualTo(CobolMessages.USER_SAVE_PROMPT);
        assertThat(screen.messageStyle()).isEqualTo("info");
    }

    @Test
    void updateFetchNotFoundReportsId_frS1227() {
        seed(1);
        UpdateScreen screen = service.updateFetch(
                new UpdateForm("NOPE0000", "KEEP", "ME", "P", "A"));

        assertThat(screen.message()).isEqualTo(CobolMessages.USER_ID_NOT_FOUND);
        assertThat(screen.cursorField()).isEqualTo("usridin");
        // The other four fields clear on the READ path.
        assertThat(screen.firstName()).isEmpty();
    }

    @Test
    void updateFetchStoreErrorShowsLookupFailed_frS1228() {
        when(repository.findById(anyString()))
                .thenThrow(new DataAccessException("down") { });
        UpdateScreen screen = service.updateFetch(
                new UpdateForm("USR00001", "", "", "", ""));

        assertThat(screen.message()).isEqualTo(CobolMessages.USER_LOOKUP_FAILED);
        assertThat(screen.cursorField()).isEqualTo("fname");
    }

    @Test
    void updateSaveChecksFieldsInCobolOrder_frS1229() {
        // COUSR02C:180-209 — USRIDIN first, then the data fields.
        UpdateScreen screen = service.updateSave(
                new UpdateForm("", "F", "L", "P", "A"));
        assertThat(screen.message()).isEqualTo(CobolMessages.USER_ID_REQUIRED_EDIT);
        assertThat(screen.cursorField()).isEqualTo("usridin");

        screen = service.updateSave(new UpdateForm("USR00001", "", "L", "P", "A"));
        assertThat(screen.message()).isEqualTo(CobolMessages.FIRST_NAME_REQUIRED);
        assertThat(screen.cursorField()).isEqualTo("fname");

        screen = service.updateSave(new UpdateForm("USR00001", "F", "", "P", "A"));
        assertThat(screen.message()).isEqualTo(CobolMessages.LAST_NAME_REQUIRED);
        assertThat(screen.cursorField()).isEqualTo("lname");

        screen = service.updateSave(new UpdateForm("USR00001", "F", "L", "", "A"));
        assertThat(screen.message()).isEqualTo(CobolMessages.PASSWORD_REQUIRED_EDIT);
        assertThat(screen.cursorField()).isEqualTo("passwd");

        screen = service.updateSave(new UpdateForm("USR00001", "F", "L", "P", ""));
        assertThat(screen.message()).isEqualTo(CobolMessages.USER_TYPE_REQUIRED);
        assertThat(screen.cursorField()).isEqualTo("usrtype");
    }

    @Test
    void updateSaveUnchangedFieldsPromptsModify_frS1230() {
        // USR-MODIFIED-NO (:217-243): byte-compare, red 'Please modify',
        // no write.
        seed(1);
        UpdateScreen screen = service.updateSave(
                new UpdateForm("USR00001", "FIRST0001", "LAST0001",
                        "PASS0001", "A"));

        assertThat(screen.message()).isEqualTo(CobolMessages.USER_MODIFY_TO_UPDATE);
        assertThat(screen.messageStyle()).isNull();
        assertThat(screen.cursorField()).isNull();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void updateSaveChangedFieldRewritesAndConfirms_frS1231() {
        seed(1);
        UpdateScreen screen = service.updateSave(
                new UpdateForm("USR00001", "CHANGED", "LAST0001",
                        "PASS0001", "A"));

        assertThat(screen.message()).isEqualTo("User USR00001 has been updated ...");
        assertThat(screen.messageStyle()).isEqualTo("info");
        assertThat(file.get(0).getFirstName()).isEqualTo("CHANGED");
    }

    @Test
    void updateSaveNotFoundAndStoreErrors_frS1232() {
        // REWRITE NOTFND → 'User ID NOT found...'; REWRITE OTHER →
        // 'Unable to Update User...' (:377-389).
        UpdateScreen screen = service.updateSave(
                new UpdateForm("NOPE0000", "F", "L", "P", "A"));
        assertThat(screen.message()).isEqualTo(CobolMessages.USER_ID_NOT_FOUND);
        assertThat(screen.cursorField()).isEqualTo("usridin");

        seed(1);
        doThrow(new DataIntegrityViolationException("check"))
                .when(repository).saveAndFlush(any(SecurityUser.class));
        screen = service.updateSave(
                new UpdateForm("USR00001", "CHANGED", "LAST0001",
                        "PASS0001", "A"));
        assertThat(screen.message()).isEqualTo(CobolMessages.USER_UPDATE_FAILED);
        assertThat(screen.cursorField()).isEqualTo("fname");
    }

    @Test
    void updateEntryPrefetchesSelectedId_frS1224() {
        seed(1);
        UpdateScreen screen = service.updateEntry("USR00001");
        assertThat(screen.firstName()).isEqualTo("FIRST0001");
        assertThat(service.updateEntry(null).message()).isNull();
        assertThat(service.updateEntry("").cursorField()).isEqualTo("usridin");
    }

    // ===================== COUSR03C — delete =====================

    @Test
    void deleteFetchOutcomes_frS1238() {
        seed(1);
        DeleteScreen screen = service.deleteFetch(new DeleteForm("", "", "", ""));
        assertThat(screen.message()).isEqualTo(CobolMessages.USER_ID_REQUIRED_EDIT);
        assertThat(screen.cursorField()).isEqualTo("usridin");

        screen = service.deleteFetch(new DeleteForm("USR00001", "", "", ""));
        assertThat(screen.firstName()).isEqualTo("FIRST0001");
        assertThat(screen.message()).isEqualTo(CobolMessages.USER_DELETE_CONFIRM);
        assertThat(screen.messageStyle()).isEqualTo("info");

        screen = service.deleteFetch(new DeleteForm("NOPE0000", "", "", ""));
        assertThat(screen.message()).isEqualTo(CobolMessages.USER_ID_NOT_FOUND);

        when(repository.findById("BROKEN00"))
                .thenThrow(new DataAccessException("down") { });
        screen = service.deleteFetch(new DeleteForm("BROKEN00", "", "", ""));
        assertThat(screen.message()).isEqualTo(CobolMessages.USER_LOOKUP_FAILED);
        assertThat(screen.cursorField()).isEqualTo("fname");
    }

    @Test
    void deleteDeleteRemovesClearsAndConfirms_frS1239() {
        seed(1);
        DeleteScreen screen = service.deleteDelete(
                new DeleteForm("USR00001", "", "", ""));
        assertThat(screen.message()).isEqualTo("User USR00001 has been deleted ...");
        assertThat(screen.messageStyle()).isEqualTo("info");
        assertThat(screen.userId()).isEmpty();
        assertThat(screen.cursorField()).isEqualTo("usridin");
        assertThat(file).isEmpty();
    }

    @Test
    void deleteDeleteNotFoundAndQuirkError_frS1239() {
        // NOTFND → 'User ID NOT found...'; DELETE OTHER keeps the source's
        // verbatim quirk 'Unable to Update User...' (:328-335).
        seed(1);
        DeleteScreen screen = service.deleteDelete(
                new DeleteForm("NOPE0000", "", "", ""));
        assertThat(screen.message()).isEqualTo(CobolMessages.USER_ID_NOT_FOUND);

        doThrow(new DataAccessException("down") { })
                .when(repository).delete(any(SecurityUser.class));
        screen = service.deleteDelete(new DeleteForm("USR00001", "", "", ""));
        assertThat(screen.message()).isEqualTo(CobolMessages.USER_UPDATE_FAILED);
        assertThat(screen.cursorField()).isEqualTo("fname");
    }

    @Test
    void deleteEntryPrefetchesSelectedId_frS1237() {
        seed(1);
        DeleteScreen screen = service.deleteEntry("USR00001");
        assertThat(screen.firstName()).isEqualTo("FIRST0001");
        assertThat(screen.message()).isEqualTo(CobolMessages.USER_DELETE_CONFIRM);
        assertThat(service.deleteEntry(null).cursorField()).isEqualTo("usridin");
    }
}
