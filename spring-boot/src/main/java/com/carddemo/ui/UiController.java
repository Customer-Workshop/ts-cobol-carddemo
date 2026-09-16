package com.carddemo.ui;

import com.carddemo.api.AuthRequest;
import com.carddemo.api.AuthResponse;
import com.carddemo.api.BillPaymentScreen;
import com.carddemo.api.CardUpdateCommarea;
import com.carddemo.api.CardUpdateForm;
import com.carddemo.api.CardUpdateScreen;
import com.carddemo.api.CobolApiException;
import com.carddemo.api.CobolMessages;
import com.carddemo.api.MenuResponse;
import com.carddemo.api.MenuSelectRequest;
import com.carddemo.api.MenuSelectionResponse;
import com.carddemo.api.ReportForm;
import com.carddemo.api.ReportScreen;
import com.carddemo.api.TransactionAddScreen;
import com.carddemo.api.TransactionCreateRequest;
import com.carddemo.service.AccountUpdateForm;
import com.carddemo.service.AccountUpdateScreen;
import com.carddemo.service.AccountUpdateService;
import com.carddemo.service.AccountUpdateSnapshot;
import com.carddemo.service.AccountViewScreen;
import com.carddemo.service.AccountViewService;
import com.carddemo.service.AuthService;
import com.carddemo.service.BillingService;
import com.carddemo.service.CardService;
import com.carddemo.service.CardViewScreen;
import com.carddemo.service.MenuService;
import com.carddemo.service.ReportService;
import com.carddemo.service.TransactionListService;
import com.carddemo.service.TransactionService;
import com.carddemo.service.TransactionViewScreen;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
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
    private final AccountViewService accountViewService;
    private final AccountUpdateService accountUpdateService;
    private final TransactionListService transactionListService;
    private final TransactionService transactionService;
    private final BillingService billingService;
    private final CardService cardService;
    private final ReportService reportService;

    public UiController(AuthService authService, MenuService menuService,
                        AccountViewService accountViewService,
                        AccountUpdateService accountUpdateService,
                        TransactionListService transactionListService,
                        TransactionService transactionService,
                        BillingService billingService,
                        CardService cardService,
                        ReportService reportService) {
        this.authService = authService;
        this.menuService = menuService;
        this.accountViewService = accountViewService;
        this.accountUpdateService = accountUpdateService;
        this.transactionListService = transactionListService;
        this.transactionService = transactionService;
        this.billingService = billingService;
        this.cardService = cardService;
        this.reportService = reportService;
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

    // COCRDUPC web surface (tran CCUP): lookup-then-update of a credit
    // card (S-06). First display is the empty search screen — the source
    // sends the map without receiving one (COCRDUPC.cbl:502-511), so a
    // bare GET never runs the search edits. The list-entry seam (S06-B2)
    // pre-fetches when the keys arrive on the URL; the plan spells them
    // acctId/cardNum, COCRDLIC's U-select spells accountId/cardNumber.
    @GetMapping("/cards/update")
    public String cardUpdate(
            @RequestParam(name = "acctId", required = false) String acctId,
            @RequestParam(name = "cardNum", required = false) String cardNum,
            @RequestParam(name = "accountId", required = false) String accountId,
            @RequestParam(name = "cardNumber", required = false) String cardNumber,
            Model model) {
        String acct = acctId != null ? acctId : accountId;
        String card = cardNum != null ? cardNum : cardNumber;
        CardUpdateScreen screen = acct == null || acct.isBlank()
                || card == null || card.isBlank()
                ? cardService.initialCardUpdate()
                : cardService.cardUpdate(CardUpdateForm.search(acct, card));
        model.addAttribute("screen", screen);
        return "card-update";
    }

    // One AID press (COCRDUPC.cbl:429-543): PF3 transfers to the menu
    // (S06-B3); every other AID posts the map fields plus the echoed
    // WS-THIS-PROGCOMMAREA through the six-state machine. PF5/PF12
    // validity and the remap-to-ENTER live in the service (:413-424).
    @PostMapping("/cards/update")
    public String submitCardUpdate(
            @RequestParam(name = "aid", defaultValue = "ENTER") String aid,
            @RequestParam(name = "acctsid", required = false) String acctsid,
            @RequestParam(name = "cardsid", required = false) String cardsid,
            @RequestParam(name = "crdname", required = false) String crdname,
            @RequestParam(name = "crdstcd", required = false) String crdstcd,
            @RequestParam(name = "expmon", required = false) String expmon,
            @RequestParam(name = "expyear", required = false) String expyear,
            @RequestParam(name = "expday", required = false) String expday,
            @RequestParam(name = "changeAction", required = false) String changeAction,
            @RequestParam(name = "oldAcctId", required = false) String oldAcctId,
            @RequestParam(name = "oldCardNum", required = false) String oldCardNum,
            @RequestParam(name = "oldName", required = false) String oldName,
            @RequestParam(name = "oldStatus", required = false) String oldStatus,
            @RequestParam(name = "oldYear", required = false) String oldYear,
            @RequestParam(name = "oldMonth", required = false) String oldMonth,
            @RequestParam(name = "oldDay", required = false) String oldDay,
            Model model) {
        if ("PF3".equals(aid) || "F3".equals(aid)) {
            return "redirect:/menu";
        }
        CardUpdateCommarea commarea = changeAction == null
                ? CardUpdateCommarea.fresh()
                : new CardUpdateCommarea(changeAction, oldAcctId, oldCardNum,
                        oldName, oldStatus, oldYear, oldMonth, oldDay);
        CardUpdateScreen screen = cardService.cardUpdate(new CardUpdateForm(
                aid, acctsid, cardsid, crdname, crdstcd, expmon, expyear, expday,
                commarea));
        model.addAttribute("screen", screen);
        return "card-update";
    }

    // COACTVWC web surface (tran CAVW): view an account plus its customer.
    // First display is the fixed prompt with an empty field
    // (COACTVWC.cbl:353-360); the info line is always the prompt because
    // WS-INFORM-OUTPUT is never SET in this program.
    @GetMapping("/accounts/view")
    public String accountView(@RequestParam(name = "returnUrl", required = false) String returnUrl,
                              Model model) {
        renderAccountView(model, accountViewService.initialScreen(), returnUrl);
        return "account-view";
    }

    // AID handling (COACTVWC.cbl:306-352): PF3 exits to the caller
    // (CDEMO-FROM-PROGRAM) or the main menu; S02-B1 — every other AID is
    // forced to ENTER and re-submits. There is no invalid-key redisplay in
    // this program.
    @PostMapping("/accounts/view")
    public String submitAccountView(
            @RequestParam(name = "aid", defaultValue = "ENTER") String aid,
            @RequestParam(name = "acctId", required = false) String acctId,
            @RequestParam(name = "returnUrl", required = false) String returnUrl,
            Model model) {
        if ("PF3".equals(aid)) {
            return "redirect:" + internalRoute(returnUrl);
        }
        renderAccountView(model, accountViewService.viewScreen(acctId), returnUrl);
        return "account-view";
    }

    private void renderAccountView(Model model, AccountViewScreen screen, String returnUrl) {
        model.addAttribute("screen", screen);
        model.addAttribute("message", screen.errorMessage());
        model.addAttribute("returnUrl", internalRoute(returnUrl));
    }

    // COCRDSLC web surface (tran CCDL, map CCRDSLA). S05-B2: a menu entry
    // is the first display; accountId + cardNumber together are the
    // card-list XCTL contract (COCRDSLC.cbl:339-348) — the edits are
    // skipped, the read runs at once and the inputs render protected.
    @GetMapping("/cards/view")
    public String cardView(@RequestParam(name = "accountId", required = false) String accountId,
                           @RequestParam(name = "cardNumber", required = false) String cardNumber,
                           @RequestParam(name = "returnUrl", required = false) String returnUrl,
                           Model model) {
        boolean cardListContext = accountId != null && !accountId.isBlank()
                && cardNumber != null && !cardNumber.isBlank();
        CardViewScreen screen = cardListContext
                ? cardService.cardListScreen(accountId, cardNumber)
                : cardService.initialScreen();
        // S05-B3: the PF3 caller is COCRDLIC when the card list handed us
        // its keys (CDEMO-FROM-PROGRAM); a supplied returnUrl wins.
        String caller = returnUrl == null && cardListContext
                ? menuService.uiRouteForProgram("COCRDLIC") : returnUrl;
        renderCardView(model, screen, caller);
        return "card-view";
    }

    // AID handling (COCRDSLC.cbl:284-299): PF3 exits to the caller route.
    // Every other AID is forced to ENTER — this program has no
    // invalid-key message, unlike the S-01 screens.
    @PostMapping("/cards/view")
    public String submitCardView(
            @RequestParam(name = "aid", defaultValue = "ENTER") String aid,
            @RequestParam(name = "accountId", required = false) String accountId,
            @RequestParam(name = "cardNumber", required = false) String cardNumber,
            @RequestParam(name = "cardListContext", defaultValue = "false") boolean cardListContext,
            @RequestParam(name = "returnUrl", required = false) String returnUrl,
            Model model) {
        if ("PF3".equals(aid)) {
            return "redirect:" + internalRoute(returnUrl);
        }
        renderCardView(model,
                cardService.viewScreen(accountId, cardNumber, cardListContext), returnUrl);
        return "card-view";
    }

    private void renderCardView(Model model, CardViewScreen screen, String returnUrl) {
        model.addAttribute("screen", screen);
        model.addAttribute("message", screen.errorMessage());
        model.addAttribute("returnUrl", internalRoute(returnUrl));
    }

    // B-012: PF3 returns to the caller route — internal paths only, so an
    // external returnUrl cannot redirect off-site; the fallback is the main
    // menu (the only registry caller today).
    private String internalRoute(String returnUrl) {
        if (returnUrl != null && returnUrl.startsWith("/") && !returnUrl.startsWith("//")) {
            return returnUrl;
        }
        return "/menu";
    }

    // COACTUPC web surface (tran CAUP): lookup-then-update two-stage flow
    // driven by the hidden `state` field (ACUP-CHANGE-ACTION) and the
    // `orig.*` snapshot fields that stand in for WS-THIS-PROGCOMMAREA
    // (S03-B3 — the screen is stateless). First display is the Search state.
    @GetMapping("/accounts/update")
    public String accountUpdate(Model model) {
        model.addAttribute("screen", accountUpdateService.initialScreen());
        return "account-update";
    }

    // AID map (COACTUPC.cbl:690-910): PF3 exits to the menu; ENTER acts per
    // state (Search -> lookup, Details/Errors -> compare+edits, Confirm ->
    // redisplay, Done -> re-fetch per D3, Failed -> validate per D4); PF5 is
    // accepted only on the confirm screen; PF12 re-reads the record in any
    // fetched state; anything else redisplays with the invalid-key message.
    @PostMapping("/accounts/update")
    public String submitAccountUpdate(
            @RequestParam(name = "aid", defaultValue = "ENTER") String aid,
            @RequestParam Map<String, String> params,
            Model model) {
        if ("PF3".equals(aid)) {
            return "redirect:/menu";
        }
        // `screenState` — `state` is taken by the address-state map field.
        AccountUpdateScreen.State state = updateState(params.get("screenState"));
        AccountUpdateForm form = updateForm(params);
        AccountUpdateSnapshot original = updateSnapshot(params);
        AccountUpdateScreen screen;
        try {
            screen = dispatchUpdate(aid, state, form, original);
        } catch (CobolApiException exception) {
            screen = accountUpdateService.invalidAid(state, form, original,
                    exception.getMessage());
        }
        model.addAttribute("screen", screen);
        model.addAttribute("message", screen.errorMessage());
        return "account-update";
    }

    private AccountUpdateScreen dispatchUpdate(String aid,
                                               AccountUpdateScreen.State state,
                                               AccountUpdateForm form,
                                               AccountUpdateSnapshot original) {
        return switch (aid) {
            case "ENTER" -> switch (state) {
                case SEARCH -> accountUpdateService.lookup(form.acctId());
                case DONE -> accountUpdateService.cancel(original);   // D3
                default -> accountUpdateService.validate(original, form);
            };
            case "PF5" -> state == AccountUpdateScreen.State.CONFIRM
                    ? saveOrFailed(original, form)
                    : accountUpdateService.invalidAid(state, form, original,
                            CobolMessages.INVALID_KEY_PRESSED);
            case "PF12" -> state != AccountUpdateScreen.State.SEARCH
                    ? accountUpdateService.cancel(original)
                    : accountUpdateService.invalidAid(state, form, original,
                            CobolMessages.INVALID_KEY_PRESSED);
            default -> accountUpdateService.invalidAid(state, form, original,
                    CobolMessages.INVALID_KEY_PRESSED);
        };
    }

    // A write failure crosses the transaction boundary as an exception so
    // the proxy rolls back; unwrap the FAILED screen for display.
    private AccountUpdateScreen saveOrFailed(AccountUpdateSnapshot original,
                                             AccountUpdateForm form) {
        try {
            return accountUpdateService.save(original, form);
        } catch (AccountUpdateService.ScreenRollbackException e) {
            return e.screen();
        }
    }

    private static AccountUpdateScreen.State updateState(String raw) {
        try {
            return raw == null ? AccountUpdateScreen.State.SEARCH
                    : AccountUpdateScreen.State.valueOf(raw);
        } catch (IllegalArgumentException exception) {
            return AccountUpdateScreen.State.SEARCH;
        }
    }

    // ACUP-NEW-* fields — one HTTP parameter per map field, named after the
    // AccountUpdateForm components.
    private AccountUpdateForm updateForm(Map<String, String> p) {
        return new AccountUpdateForm(
                p.get("acctId"), p.get("activeStatus"),
                p.get("openYear"), p.get("openMon"), p.get("openDay"),
                p.get("creditLimit"),
                p.get("expYear"), p.get("expMon"), p.get("expDay"),
                p.get("cashCreditLimit"),
                p.get("risYear"), p.get("risMon"), p.get("risDay"),
                p.get("currentBalance"), p.get("accountGroup"),
                p.get("currentCycleCredit"), p.get("currentCycleDebit"),
                p.get("custId"),
                p.get("ssn1"), p.get("ssn2"), p.get("ssn3"),
                p.get("dobYear"), p.get("dobMon"), p.get("dobDay"),
                p.get("ficoScore"),
                p.get("firstName"), p.get("middleName"), p.get("lastName"),
                p.get("addrLine1"), p.get("addrLine2"), p.get("city"),
                p.get("state"), p.get("zip"), p.get("country"),
                p.get("phone1a"), p.get("phone1b"), p.get("phone1c"),
                p.get("phone2a"), p.get("phone2b"), p.get("phone2c"),
                p.get("governmentId"), p.get("eftAccountId"),
                p.get("priCardHolder"));
    }

    // The `orig.*` hidden fields ride the form back as the fetched snapshot.
    private AccountUpdateSnapshot updateSnapshot(Map<String, String> p) {
        if (p.get("orig.accountId") == null) {
            return null;
        }
        return new AccountUpdateSnapshot(
                longOrNull(p.get("orig.accountId")),
                p.get("orig.activeStatus"),
                moneyOrNull(p.get("orig.currentBalance")),
                moneyOrNull(p.get("orig.creditLimit")),
                moneyOrNull(p.get("orig.cashCreditLimit")),
                dateOrNull(p.get("orig.openDate")),
                dateOrNull(p.get("orig.expirationDate")),
                dateOrNull(p.get("orig.reissueDate")),
                moneyOrNull(p.get("orig.currentCycleCredit")),
                moneyOrNull(p.get("orig.currentCycleDebit")),
                p.get("orig.accountGroup"),
                longOrNull(p.get("orig.customerId")),
                longOrNull(p.get("orig.ssn")),
                dateOrNull(p.get("orig.dateOfBirth")),
                intOrNull(p.get("orig.ficoScore")),
                p.get("orig.firstName"), p.get("orig.middleName"),
                p.get("orig.lastName"),
                p.get("orig.addressLine1"), p.get("orig.addressLine2"),
                p.get("orig.addressLine3"),
                p.get("orig.stateCode"), p.get("orig.zip"),
                p.get("orig.countryCode"),
                p.get("orig.phoneNumber1"), p.get("orig.phoneNumber2"),
                p.get("orig.governmentIssuedId"), p.get("orig.eftAccountId"),
                p.get("orig.primaryCardHolderIndicator"));
    }

    private static Long longOrNull(String value) {
        try {
            return value == null || value.isBlank() ? null
                    : Long.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Integer intOrNull(String value) {
        try {
            return value == null || value.isBlank() ? null
                    : Integer.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static BigDecimal moneyOrNull(String value) {
        try {
            return value == null || value.isBlank() ? null
                    : new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static LocalDate dateOrNull(String value) {
        try {
            return value == null || value.isBlank() ? null
                    : LocalDate.parse(value.trim());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    // COTRN02C web surface (tran CT02). The COMMAREA card lands as the
    // cardNumber query param and runs ENTER processing at once
    // (COTRN02C.cbl:124-129); a bare entry shows the empty map.
    @GetMapping("/transactions/add")
    public String tranAdd(@RequestParam(name = "cardNumber", required = false) String cardNumber,
                          Model model) {
        TransactionAddScreen screen = TransactionAddScreen.blank();
        if (cardNumber != null && !cardNumber.isBlank()) {
            screen = transactionService.enter(new TransactionCreateRequest(
                    null, cardNumber, null, null, null, null, null, null, null,
                    null, null, null, null, null));
        }
        return tranAddView(screen, model);
    }

    // AID map (COTRN02C.cbl:133-152): ENTER validates and writes, PF3 backs
    // out to the menu, PF4 clears the map, PF5 copies the last transaction,
    // anything else redisplays with the invalid-key message.
    @PostMapping("/transactions/add")
    public String submitTranAdd(
            @RequestParam(name = "aid", defaultValue = "ENTER") String aid,
            @RequestParam(name = "accountId", required = false) String accountId,
            @RequestParam(name = "cardNumber", required = false) String cardNumber,
            @RequestParam(name = "transactionTypeCode", required = false) String typeCode,
            @RequestParam(name = "transactionCategoryCode", required = false) String categoryCode,
            @RequestParam(name = "source", required = false) String source,
            @RequestParam(name = "description", required = false) String description,
            @RequestParam(name = "amount", required = false) String amount,
            @RequestParam(name = "originDate", required = false) String originDate,
            @RequestParam(name = "processDate", required = false) String processDate,
            @RequestParam(name = "merchantId", required = false) String merchantId,
            @RequestParam(name = "merchantName", required = false) String merchantName,
            @RequestParam(name = "merchantCity", required = false) String merchantCity,
            @RequestParam(name = "merchantZip", required = false) String merchantZip,
            @RequestParam(name = "confirmation", required = false) String confirmation,
            Model model) {
        if ("PF3".equals(aid)) {
            return "redirect:/menu";
        }
        if ("PF4".equals(aid)) {
            return tranAddView(TransactionAddScreen.blank(), model);
        }
        TransactionCreateRequest request = new TransactionCreateRequest(
                accountId, cardNumber, typeCode, categoryCode, source, description,
                amount, originDate, processDate, merchantId, merchantName,
                merchantCity, merchantZip, confirmation);
        TransactionAddScreen screen;
        if ("ENTER".equals(aid)) {
            screen = runTranAdd(request, transactionService::enter);
        } else if ("PF5".equals(aid)) {
            screen = runTranAdd(request, transactionService::copyLast);
        } else {
            screen = TransactionAddScreen.preserved(request, CobolMessages.INVALID_KEY_PRESSED);
        }
        return tranAddView(screen, model);
    }

    private TransactionAddScreen runTranAdd(TransactionCreateRequest request,
            java.util.function.Function<TransactionCreateRequest, TransactionAddScreen> action) {
        try {
            return action.apply(request);
        } catch (CobolApiException exception) {
            return TransactionAddScreen.preserved(request, exception.getMessage());
        }
    }

    private String tranAddView(TransactionAddScreen screen, Model model) {
        model.addAttribute("screen", screen);
        model.addAttribute("message", screen.message());
        model.addAttribute("messageStyle", screen.messageStyle());
        return "transaction-add";
    }

    // COBIL00C web surface (tran CB00): the first display is the empty map
    // (COBIL00C.cbl:112-122); a CDEMO-CB00-TRN-SELECTED lands as the
    // accountId query param and runs ENTER processing at once (S11-B5).
    // Unsigned access is bounced to sign-on by the security entry point
    // (EIBCALEN=0, :107-109).
    @GetMapping("/bill-payment")
    public String billPayment(@RequestParam(name = "accountId", required = false) String accountId,
                              Model model) {
        BillPaymentScreen screen = BillPaymentScreen.blank();
        if (accountId != null && !accountId.isBlank()) {
            screen = billingService.enter(accountId, null, null);
        }
        return billPaymentView(screen, model);
    }

    // AID map (COBIL00C.cbl:126-141): ENTER runs PROCESS-ENTER-KEY, PF3
    // transfers back to the menu (S11-B4), PF4 clears the map, and every
    // other key redisplays the screen unchanged with the invalid-key
    // message — this program does emit it, unlike COACTVWC.
    @PostMapping("/bill-payment")
    public String submitBillPayment(
            @RequestParam(name = "aid", defaultValue = "ENTER") String aid,
            @RequestParam(name = "acctId", required = false) String acctId,
            @RequestParam(name = "currentBalance", required = false) String currentBalance,
            @RequestParam(name = "confirmation", required = false) String confirmation,
            Model model) {
        if ("PF3".equals(aid)) {
            return "redirect:/menu";
        }
        if ("PF4".equals(aid)) {
            return billPaymentView(BillPaymentScreen.blank(), model);
        }
        BillPaymentScreen screen = "ENTER".equals(aid)
                ? billingService.enter(acctId, confirmation, currentBalance)
                : BillPaymentScreen.preserved(acctId, currentBalance, confirmation,
                        CobolMessages.INVALID_KEY_PRESSED);
        return billPaymentView(screen, model);
    }

    private String billPaymentView(BillPaymentScreen screen, Model model) {
        model.addAttribute("screen", screen);
        model.addAttribute("message", screen.message());
        model.addAttribute("messageStyle", screen.messageStyle());
        return "bill-payment";
    }

    // CORPT00C web surface (tran CR00): the first display is the empty map
    // (CORPT00C.cbl:179-181); unsigned access is bounced to sign-on by the
    // security entry point (EIBCALEN=0, :172-174).
    @GetMapping("/reports")
    public String reportRequest(Model model) {
        return reportView(ReportScreen.blank(), model);
    }

    // AID map (CORPT00C.cbl:184-194): ENTER runs PROCESS-ENTER-KEY, PF3
    // transfers to COMEN01C, every other key redisplays the map unchanged
    // with the invalid-key message.
    @PostMapping("/reports")
    public String submitReportRequest(
            @RequestParam(name = "aid", defaultValue = "ENTER") String aid,
            @RequestParam(name = "monthly", required = false) String monthly,
            @RequestParam(name = "yearly", required = false) String yearly,
            @RequestParam(name = "custom", required = false) String custom,
            @RequestParam(name = "sdtmm", required = false) String sdtmm,
            @RequestParam(name = "sdtdd", required = false) String sdtdd,
            @RequestParam(name = "sdtyyyy", required = false) String sdtyyyy,
            @RequestParam(name = "edtmm", required = false) String edtmm,
            @RequestParam(name = "edtdd", required = false) String edtdd,
            @RequestParam(name = "edtyyyy", required = false) String edtyyyy,
            @RequestParam(name = "confirm", required = false) String confirm,
            Model model) {
        if ("PF3".equals(aid)) {
            return "redirect:/menu";
        }
        ReportForm form = new ReportForm(monthly, yearly, custom,
                sdtmm, sdtdd, sdtyyyy, edtmm, edtdd, edtyyyy, confirm);
        ReportScreen screen = "ENTER".equals(aid)
                ? reportService.enter(form)
                : ReportScreen.preserved(form, CobolMessages.INVALID_KEY_PRESSED,
                        "monthly");
        return reportView(screen, model);
    }

    private String reportView(ReportScreen screen, Model model) {
        model.addAttribute("screen", screen);
        model.addAttribute("message", screen.message());
        model.addAttribute("messageStyle", screen.messageStyle());
        return "reports";
    }


    // COTRN01C web surface (tran CT01): one transaction by its verbatim
    // Tran ID. First entry honors the CDEMO-CT01-TRN-SELECTED slot, which
    // arrives as the tranId query parameter (S08-B2); a bare entry shows
    // the empty map (COTRN01C.cbl:98-109).
    @GetMapping("/transactions/view")
    public String transactionView(
            @RequestParam(name = "tranId", required = false) String tranId,
            @RequestParam(name = "returnUrl", required = false) String returnUrl,
            Model model) {
        renderTransactionView(model, transactionService.openView(tranId), returnUrl);
        return "transaction-view";
    }

    // AID map (COTRN01C.cbl:112-132): ENTER runs the keyed read, PF3
    // exits to the caller (CDEMO-FROM-PROGRAM, the returnUrl slot) or the
    // menu, PF4 clears the map, PF5 transfers to COTRN00C, any other key
    // redisplays with the invalid-key message. Every detail field is
    // FSET, so the posted fields rebuild the shown record — kept on a
    // blank-id rejection, an invalid AID or the coming-soon fallback
    // (:147-152, :128-131, S08-B4).
    @PostMapping("/transactions/view")
    public String submitTransactionView(
            @RequestParam(name = "aid", defaultValue = "ENTER") String aid,
            @RequestParam(name = "trnIdIn", required = false) String trnIdIn,
            @RequestParam(name = "trnid", required = false) String trnid,
            @RequestParam(name = "cardnum", required = false) String cardnum,
            @RequestParam(name = "ttypcd", required = false) String ttypcd,
            @RequestParam(name = "tcatcd", required = false) String tcatcd,
            @RequestParam(name = "trnsrc", required = false) String trnsrc,
            @RequestParam(name = "tdesc", required = false) String tdesc,
            @RequestParam(name = "trnamt", required = false) String trnamt,
            @RequestParam(name = "torigdt", required = false) String torigdt,
            @RequestParam(name = "tprocdt", required = false) String tprocdt,
            @RequestParam(name = "mid", required = false) String mid,
            @RequestParam(name = "mname", required = false) String mname,
            @RequestParam(name = "mcity", required = false) String mcity,
            @RequestParam(name = "mzip", required = false) String mzip,
            @RequestParam(name = "returnUrl", required = false) String returnUrl,
            Model model) {
        if ("PF3".equals(aid)) {
            return "redirect:" + internalRoute(returnUrl);
        }
        if ("PF4".equals(aid)) {
            renderTransactionView(model, TransactionViewScreen.blank(), returnUrl);
            return "transaction-view";
        }
        TransactionViewScreen.Details displayed = TransactionViewScreen.Details.posted(
                trnid, cardnum, ttypcd, tcatcd, trnsrc, tdesc, trnamt,
                torigdt, tprocdt, mid, mname, mcity, mzip);
        TransactionViewScreen screen;
        switch (aid) {
            case "ENTER" -> screen = transactionService.enterView(trnIdIn, displayed);
            case "PF5" -> {
                // XCTL COTRN00C (:120-124) resolved through the route
                // registry (S08-B4): browsable -> /transactions/list,
                // otherwise the coming-soon idiom, screen retained.
                String route = menuService.uiRouteForProgram("COTRN00C");
                if (route != null) {
                    return "redirect:" + route;
                }
                screen = TransactionViewScreen.retained(trnIdIn,
                        CobolMessages.optionComingSoon(
                                menuService.programName("COTRN00C")),
                        "info", displayed);
            }
            default -> screen = TransactionViewScreen.retained(trnIdIn,
                    CobolMessages.INVALID_KEY_PRESSED, null, displayed);
        }
        renderTransactionView(model, screen, returnUrl);
        return "transaction-view";
    }

    private void renderTransactionView(Model model, TransactionViewScreen screen,
                                       String returnUrl) {
        model.addAttribute("screen", screen);
        model.addAttribute("message", screen.message());
        model.addAttribute("messageStyle", screen.messageStyle());
        model.addAttribute("returnUrl", internalRoute(returnUrl));
    }
}
