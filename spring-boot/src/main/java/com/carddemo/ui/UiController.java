package com.carddemo.ui;

import com.carddemo.api.AuthRequest;
import com.carddemo.api.AuthResponse;
import com.carddemo.api.CobolApiException;
import com.carddemo.api.CobolMessages;
import com.carddemo.api.MenuResponse;
import com.carddemo.api.MenuSelectRequest;
import com.carddemo.api.MenuSelectionResponse;
import com.carddemo.service.AuthService;
import com.carddemo.service.MenuService;
import com.carddemo.service.TransactionListService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

/**
 * Thin web surface over the same services the REST API uses: one logic path,
 * two surfaces. The 3270 AID key arrives as the form field {@code aid} —
 * ENTER signs on, PF3 exits to the plain-text farewell, anything else is an
 * invalid key (COSGN00C.cbl:85-95). Outcomes mirror READ-USER-SEC-FILE
 * (COSGN00C.cbl:221-257): success routes by user type, failures redisplay the
 * screen with the verbatim message.
 */
@Controller
public class UiController {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final AuthService authService;
    private final MenuService menuService;
    private final TransactionListService transactionListService;

    public UiController(AuthService authService, MenuService menuService,
                        TransactionListService transactionListService) {
        this.authService = authService;
        this.menuService = menuService;
        this.transactionListService = transactionListService;
    }

    @ModelAttribute
    void addHeaderFields(Model model) {
        LocalDateTime now = LocalDateTime.now();
        model.addAttribute("currentDate", now.format(DATE_FORMAT));
        model.addAttribute("currentTime", now.format(TIME_FORMAT));
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/signon")
    public String signon() {
        return "signon";
    }

    @PostMapping("/signon")
    public String submitSignon(
            @RequestParam(name = "aid", defaultValue = "ENTER") String aid,
            @RequestParam(name = "userid", required = false) String userId,
            @RequestParam(name = "passwd", required = false) String password,
            Model model, HttpServletRequest request, HttpServletResponse response) {
        if ("PF3".equals(aid)) {
            authService.signoff(request);
            model.addAttribute("message", CobolMessages.THANK_YOU);
            return "exit";
        }
        if (!"ENTER".equals(aid)) {
            model.addAttribute("message", CobolMessages.INVALID_KEY_PRESSED);
            model.addAttribute("userId", userId);
            return "signon";
        }
        try {
            AuthResponse auth = authService.signon(new AuthRequest(userId, password), request, response);
            return "redirect:" + ("A".equals(auth.userType()) ? "/admin/menu" : "/menu");
        } catch (CobolApiException exception) {
            model.addAttribute("message", exception.getMessage());
        } catch (RuntimeException exception) {
            model.addAttribute("message", CobolMessages.USER_VERIFY_FAILED);
        }
        model.addAttribute("userId", userId);
        return "signon";
    }

    // COMEN01C web surface (tran CM00): lists the COMEN02Y catalogue in order
    // (COMEN01C.cbl:262-303). Unauthenticated access is bounced to sign-on by
    // the security entry point, mirroring the EIBCALEN=0 bounce (:76-81).
    @GetMapping("/menu")
    public String menu(Authentication authentication, Model model) {
        model.addAttribute("menu", menuService.mainMenu(authentication));
        return "menu";
    }

    // COADM01C web surface (tran CA00): six COADM02Y rows (:229-256). Spring
    // Security's /admin/** guard does the role check.
    @GetMapping("/admin/menu")
    public String adminMenu(Model model) {
        model.addAttribute("menu", menuService.adminMenu());
        return "admin-menu";
    }

    @PostMapping("/menu/select")
    public String selectMenuOption(
            @RequestParam(name = "aid", defaultValue = "ENTER") String aid,
            @RequestParam(name = "option", required = false) String option,
            Authentication authentication,
            Model model, HttpServletRequest request) {
        return selectOption(aid, option, model, request, "menu",
                () -> menuService.selectMain(new MenuSelectRequest(option), authentication),
                () -> menuService.mainMenu(authentication));
    }

    // COTRN00C web surface (tran CT00): the first display is an ENTER on an
    // empty map — page 1 browsed from the start of the file (COTRN00C.cbl:
    // 112-116). Unsigned navigation is bounced to sign-on by the security
    // entry point (EIBCALEN=0, :107-109).
    @GetMapping("/transactions/list")
    public String transactionList(Model model) {
        model.addAttribute("page", transactionListService.firstDisplay());
        return "transaction-list";
    }

    // Pseudo-conversational turn: the form carries the screen back (every map
    // field is FSET) plus the CDEMO-CT00-INFO paging state as hidden fields
    // (:62-70, S07-B5). ENTER runs the selection scan and forward browse,
    // PF7/PF8 page backward/forward, PF3 transfers to the menu (:122-124).
    @PostMapping("/transactions/list")
    public String submitTransactionList(
            @RequestParam(name = "aid", defaultValue = "ENTER") String aid,
            @RequestParam(name = "trnIdIn", required = false) String trnIdIn,
            @RequestParam(name = "sel", required = false) java.util.List<String> sels,
            @RequestParam(name = "trnId", required = false) java.util.List<String> trnIds,
            @RequestParam(name = "tdate", required = false) java.util.List<String> tdates,
            @RequestParam(name = "tdesc", required = false) java.util.List<String> tdescs,
            @RequestParam(name = "tamt", required = false) java.util.List<String> tamts,
            @RequestParam(name = "pageDisplay", required = false) String pageDisplay,
            @RequestParam(name = "firstId", required = false) String firstId,
            @RequestParam(name = "lastId", required = false) String lastId,
            @RequestParam(name = "pageNum", required = false) String pageNum,
            @RequestParam(name = "nextPage", required = false) String nextPage,
            Model model) {
        if ("PF3".equals(aid)) {
            return "redirect:/menu";
        }
        TransactionListService.Form form = new TransactionListService.Form(
                trnIdIn == null ? "" : trnIdIn,
                TransactionListService.Form.selections(sels),
                TransactionListService.Form.rows(trnIds, tdates, tdescs, tamts),
                pageDisplay == null ? "" : pageDisplay,
                firstId, lastId, pageNum, nextPage);
        TransactionListService.Page page;
        switch (aid) {
            case "ENTER" -> {
                TransactionListService.EnterOutcome outcome =
                        transactionListService.enter(form);
                if (outcome.selectedId() != null) {
                    // XCTL COTRN01C (:186-195) resolved through the route
                    // registry (S07-B1): browsable -> /transactions/view with
                    // the id, otherwise the coming-soon idiom.
                    String route = menuService.uiRouteForProgram("COTRN01C");
                    if (route != null) {
                        return "redirect:" + route + "?tranId=" + outcome.selectedId();
                    }
                    page = TransactionListService.Page.unchanged(form, form.state(),
                            CobolMessages.optionComingSoon(
                                    menuService.programName("COTRN01C")),
                            true);
                } else {
                    page = outcome.page();
                }
            }
            case "PF7" -> page = transactionListService.pf7(form);
            case "PF8" -> page = transactionListService.pf8(form);
            default -> page = transactionListService.invalidAid(form);
        }
        model.addAttribute("page", page);
        model.addAttribute("message", page.message());
        model.addAttribute("messageStyle", page.info() ? "info" : null);
        return "transaction-list";
    }

    @PostMapping("/admin/menu/select")
    public String selectAdminOption(
            @RequestParam(name = "aid", defaultValue = "ENTER") String aid,
            @RequestParam(name = "option", required = false) String option,
            Model model, HttpServletRequest request) {
        return selectOption(aid, option, model, request, "admin-menu",
                () -> menuService.selectAdmin(new MenuSelectRequest(option)),
                menuService::adminMenu);
    }

    // Shared AID handling for both menus (COMEN01C.cbl:93-103,
    // COADM01C.cbl:97-107): PF3 exits to the sign-on screen, ENTER validates
    // and dispatches, any other key redisplays with the invalid-key message.
    private String selectOption(String aid, String option, Model model,
                                HttpServletRequest request, String view,
                                Supplier<MenuSelectionResponse> selection,
                                Supplier<MenuResponse> menu) {
        if ("PF3".equals(aid)) {
            authService.signoff(request);
            return "redirect:/signon";
        }
        model.addAttribute("menu", menu.get());
        model.addAttribute("selectedOption", option);
        if (!"ENTER".equals(aid)) {
            model.addAttribute("message", CobolMessages.INVALID_KEY_PRESSED);
            return view;
        }
        try {
            MenuSelectionResponse selected = selection.get();
            if (selected.message() != null) {
                model.addAttribute("message", selected.message());
                if (selected.implemented() && !selected.available()) {
                    // Placeholder target renders green (COMEN01C.cbl:169-176).
                    model.addAttribute("messageStyle", "info");
                }
                return view;
            }
            String route = menuService.uiRoute(selected);
            if (route != null) {
                return "redirect:" + route;
            }
            model.addAttribute("message", CobolMessages.optionNotInstalled(selected.name()));
        } catch (CobolApiException exception) {
            model.addAttribute("message", exception.getMessage());
        }
        return view;
    }
}
