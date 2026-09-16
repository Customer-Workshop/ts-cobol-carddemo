package com.carddemo.ui;

import com.carddemo.api.CobolMessages;
import com.carddemo.api.TranTypeListPageState;
import com.carddemo.api.TranTypeListRequest;
import com.carddemo.api.TranTypeListResponse;
import com.carddemo.api.TranTypeListRow;
import com.carddemo.api.TranTypeMaintRequest;
import com.carddemo.api.TranTypeMaintResponse;
import com.carddemo.api.TranTypeMaintState;
import com.carddemo.api.TranTypeNavigation;
import com.carddemo.service.MenuService;
import com.carddemo.service.TranTypeService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CTLI/CTTU web surfaces: one POST per AID press carrying the screen fields
 * plus the program COMMAREA as hidden inputs — the pseudo-conversational
 * round-trip, same convention as CardListUiController. PF3 exits to the
 * caller (admin menu by default); the list's PF2 XCTL resolves through the
 * MenuService route registry.
 */
@Controller
public class TranTypeUiController {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final TranTypeService service;
    private final MenuService menuService;

    public TranTypeUiController(TranTypeService service, MenuService menuService) {
        this.service = service;
        this.menuService = menuService;
    }

    @ModelAttribute
    void addHeaderFields(Model model) {
        LocalDateTime now = LocalDateTime.now();
        model.addAttribute("currentDate", now.format(DATE_FORMAT));
        model.addAttribute("currentTime", now.format(TIME_FORMAT));
    }

    // ---- CTLI — transaction type list ------------------------------------

    @GetMapping("/ui/tran-types")
    public String tranTypeList(Authentication authentication, Model model) {
        TranTypeListResponse result = service.browse(authentication,
                new TranTypeListRequest("ENTER", null, null, null, null, null));
        return listView(result, model);
    }

    @PostMapping("/ui/tran-types")
    public String tranTypeListAid(
            @RequestParam(name = "aid", defaultValue = "ENTER") String aid,
            @RequestParam(name = "trtype", required = false) String typeFilter,
            @RequestParam(name = "trdesc", required = false) String descFilter,
            @RequestParam(name = "trtsel", required = false) List<String> selects,
            @RequestParam(name = "rowDesc", required = false) List<String> rowDescs,
            @RequestParam(name = "firstCode", required = false) String firstCode,
            @RequestParam(name = "lastCode", required = false) String lastCode,
            @RequestParam(name = "screenNumber", defaultValue = "1") int screenNumber,
            @RequestParam(name = "lastPageShown", defaultValue = "false") boolean lastPageShown,
            @RequestParam(name = "nextPageExists", defaultValue = "false") boolean nextPageExists,
            @RequestParam(name = "rowSelected", defaultValue = "0") int rowSelected,
            @RequestParam(name = "deletePending", defaultValue = "false") boolean deletePending,
            @RequestParam(name = "updatePending", defaultValue = "false") boolean updatePending,
            @RequestParam(name = "caTypeFilter", required = false) String caTypeFilter,
            @RequestParam(name = "caDescFilter", required = false) String caDescFilter,
            @RequestParam(name = "rowCode", required = false) List<String> rowCodes,
            @RequestParam(name = "rowOrig", required = false) List<String> rowOrigs,
            Authentication authentication, Model model) {
        TranTypeListPageState state = new TranTypeListPageState(
                blankToNull(firstCode), blankToNull(lastCode), screenNumber,
                lastPageShown, nextPageExists, rowSelected, deletePending,
                updatePending, blankToNull(caTypeFilter), blankToNull(caDescFilter),
                rows(rowCodes, rowOrigs));
        TranTypeListResponse result = service.browse(authentication,
                new TranTypeListRequest(aid, typeFilter, descFilter, selects,
                        rowDescs, state));
        if ("exit".equals(result.outcome())) {
            return "redirect:/admin/menu";
        }
        if ("navigate".equals(result.outcome())) {
            // S21-B1 — the F2 XCTL target resolves through the route registry.
            TranTypeNavigation navigation = result.navigation();
            String route = menuService.uiRouteForProgram(navigation.program());
            if (route != null) {
                return "redirect:" + route + "?from=list";
            }
            model.addAttribute("message", CobolMessages.optionComingSoon(
                    menuService.programName(navigation.program())));
            model.addAttribute("messageStyle", "info");
            model.addAttribute("screen", result.screen());
            model.addAttribute("pageState", result.pageState());
            return "tran-types";
        }
        return listView(result, model);
    }

    private String listView(TranTypeListResponse result, Model model) {
        model.addAttribute("screen", result.screen());
        model.addAttribute("pageState", result.pageState());
        return "tran-types";
    }

    private static List<TranTypeListRow> rows(List<String> codes, List<String> origs) {
        List<TranTypeListRow> rows = new ArrayList<>(
                Collections.nCopies(TranTypeListPageState.ROW_COUNT, null));
        for (int i = 0; i < TranTypeListPageState.ROW_COUNT; i++) {
            String code = value(codes, i);
            if (code == null) {
                continue;
            }
            String orig = value(origs, i);
            rows.set(i, new TranTypeListRow("", code, orig, orig,
                    false, false, false));
        }
        return rows;
    }

    // ---- CTTU — transaction type maintenance ------------------------------

    @GetMapping("/ui/tran-types/maint")
    public String tranTypeMaint(
            @RequestParam(name = "from", required = false) String from,
            Authentication authentication, Model model) {
        String fromProgram = "list".equals(from) ? "COTRTLIC" : "COADM01C";
        TranTypeMaintResponse result = service.maintain(authentication,
                new TranTypeMaintRequest("ENTER", null, null,
                        TranTypeMaintState.fresh(fromProgram)));
        model.addAttribute("screen", result.screen());
        model.addAttribute("state", result.state());
        return "tran-type-maint";
    }

    @PostMapping("/ui/tran-types/maint")
    public String tranTypeMaintAid(
            @RequestParam(name = "aid", defaultValue = "ENTER") String aid,
            @RequestParam(name = "trtypcd", required = false) String code,
            @RequestParam(name = "trtydsc", required = false) String desc,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "oldType", required = false) String oldType,
            @RequestParam(name = "oldDesc", required = false) String oldDesc,
            @RequestParam(name = "newType", required = false) String newType,
            @RequestParam(name = "newDesc", required = false) String newDesc,
            @RequestParam(name = "reenter", defaultValue = "true") boolean reenter,
            @RequestParam(name = "fromProgram", required = false) String fromProgram,
            Authentication authentication, Model model) {
        TranTypeMaintState state = new TranTypeMaintState(
                blankToNull(action), blankToNull(oldType), blankToNull(oldDesc),
                blankToNull(newType), blankToNull(newDesc), reenter, fromProgram);
        TranTypeMaintResponse result = service.maintain(authentication,
                new TranTypeMaintRequest(aid, code, desc, state));
        if ("exit".equals(result.outcome())) {
            // :435-445 — PF3 transfers back to the calling program.
            String route = menuService.uiRouteForProgram(result.navigation().program());
            return "redirect:" + (route != null ? route : "/admin/menu");
        }
        model.addAttribute("screen", result.screen());
        model.addAttribute("state", result.state());
        return "tran-type-maint";
    }

    private static String value(List<String> list, int index) {
        if (list == null || index >= list.size()) {
            return null;
        }
        return blankToNull(list.get(index));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
