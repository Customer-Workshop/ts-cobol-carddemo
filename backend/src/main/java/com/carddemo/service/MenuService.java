package com.carddemo.service;

import com.carddemo.api.CobolMessages;
import com.carddemo.api.InvalidMenuOptionException;
import com.carddemo.api.MenuOption;
import com.carddemo.api.MenuResponse;
import com.carddemo.api.MenuSelectionResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * COMEN01C main menu (app/cbl/COMEN01C.cbl). One service per program (target state §3).
 *
 * <p>Reads no file (FR §6): the whole program is the static option table of
 * app/cpy/COMEN02Y.cpy plus PROCESS-ENTER-KEY (:115-191). The pseudo-conversation (:107-111) is
 * dropped for self-contained requests (B-0028) and the {@code XCTL} of :184-187 becomes the route
 * returned by {@link #select(String)} (B-0010).
 */
@Service
public class MenuService {

    /** WS-TRANID / WS-PGMNAME, COMEN01C.cbl:26-27. */
    public static final String TRAN_ID = "CM00";
    public static final String PROGRAM_NAME = "COMEN01C";

    /** SPA route of the only migrated option (D-0025; business-name route, target state §4). */
    public static final String ACCOUNT_VIEW_ROUTE = "/accounts/view";

    /**
     * B-0033: CARDDEMO-MAIN-MENU-OPTIONS ported verbatim (app/cpy/COMEN02Y.cpy:19-90). The OCCURS is
     * 12 but CDEMO-MENU-OPT-COUNT is 11 (:21), so row 12 does not exist here either. {@code endpoint}
     * and {@code route} are non-null only while {@code implemented} is true: every later stream flips
     * one row instead of touching the dispatcher.
     */
    private static final List<MenuOption> OPTIONS = List.of(
            new MenuOption(1, "Account View", "COACTVWC", "/api/accounts/{acctId}", ACCOUNT_VIEW_ROUTE, true, "U"),
            new MenuOption(2, "Account Update", "COACTUPC", null, null, false, "U"),
            new MenuOption(3, "Credit Card List", "COCRDLIC", null, null, false, "U"),
            new MenuOption(4, "Credit Card View", "COCRDSLC", null, null, false, "U"),
            new MenuOption(5, "Credit Card Update", "COCRDUPC", null, null, false, "U"),
            new MenuOption(6, "Transaction List", "COTRN00C", null, null, false, "U"),
            new MenuOption(7, "Transaction View", "COTRN01C", null, null, false, "U"),
            new MenuOption(8, "Transaction Add", "COTRN02C", null, null, false, "U"),
            new MenuOption(9, "Transaction Reports", "CORPT00C", null, null, false, "U"),
            new MenuOption(10, "Bill Payment", "COBIL00C", null, null, false, "U"),
            new MenuOption(11, "Pending Authorization View", "COPAUS0C", null, null, false, "U"));

    /** CDEMO-MENU-OPT-COUNT, app/cpy/COMEN02Y.cpy:21. */
    public static final int OPTION_COUNT = OPTIONS.size();

    private final ScreenHeaderService headerService;

    public MenuService(ScreenHeaderService headerService) {
        this.headerService = headerService;
    }

    /** MEN-01 / FR-09: the initial SEND MAP of COMEN1A (COMEN01C.cbl:85-92, :208-303). */
    public MenuResponse menu() {
        return new MenuResponse(headerService.header(TRAN_ID, PROGRAM_NAME), OPTIONS);
    }

    public List<MenuOption> options() {
        return OPTIONS;
    }

    /**
     * MEN-02..MEN-06: PROCESS-ENTER-KEY (COMEN01C.cbl:115-191) in source order — normalise (V-1),
     * validate (V-2), then index the table. The legacy indexes with the rejected subscript at
     * :136-137 after having sent the error map; that defect is not reproduced (V-3 / R-07).
     *
     * <p>The admin-only guard of :136-143 is dead code (every COMEN02Y row is {@code USRTYPE='U'},
     * V-4) and has no target form; the {@code INQUIRE PROGRAM} probe of option 11 (:159-173) is
     * folded into the unavailable facade (B-0032), as is the {@code DUMMY} 'coming soon' path.
     */
    public MenuSelectionResponse select(String rawOption) {
        String normalized = normalize(rawOption);

        if (!isTwoDigits(normalized)) {
            throw new InvalidMenuOptionException(normalized);
        }
        int option = Integer.parseInt(normalized);
        if (option == 0 || option > OPTION_COUNT) {
            throw new InvalidMenuOptionException(normalized);
        }

        MenuOption selected = OPTIONS.get(option - 1);
        if (!selected.implemented()) {
            return new MenuSelectionResponse(normalized, selected.program(), null, null, false,
                    CobolMessages.OPTION_NOT_AVAILABLE);
        }
        return new MenuSelectionResponse(normalized, selected.program(), selected.endpoint(),
                selected.route(), true, null);
    }

    /**
     * V-1, COMEN01C.cbl:117-125. The PERFORM VARYING scans OPTIONI (X(02)) from the right for the
     * last non-blank, so WS-IDX is 2 unless the second byte is blank, in which case it stops at 1.
     * {@code OPTIONI(1:WS-IDX)} then lands in {@code WS-OPTION-X PIC X(02) JUST RIGHT} — a
     * one-byte move is right-justified and left-space-padded — and {@code INSPECT … REPLACING ALL
     * ' ' BY '0'} turns every remaining blank into a zero. Hence {@code '1 '} and {@code ' 1'} both
     * give {@code 01}, an empty field gives {@code 00} and {@code 'A '} gives {@code 0A}.
     */
    public String normalize(String rawOption) {
        String field = rawOption == null ? "" : rawOption;
        if (field.length() > 2) {
            throw new IllegalArgumentException("OPTIONI is PIC X(02)");
        }
        String option = (field + "  ").substring(0, 2);

        String scanned = option.charAt(1) == ' ' ? option.substring(0, 1) : option;
        String justifiedRight = scanned.length() == 1 ? " " + scanned : scanned;
        return justifiedRight.replace(' ', '0');
    }

    private static boolean isTwoDigits(String value) {
        return Character.isDigit(value.charAt(0)) && Character.isDigit(value.charAt(1));
    }
}
