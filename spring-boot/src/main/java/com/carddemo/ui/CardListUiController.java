package com.carddemo.ui;

import com.carddemo.api.CardListNavigation;
import com.carddemo.api.CardListPageState;
import com.carddemo.api.CardListRequest;
import com.carddemo.api.CardListResponse;
import com.carddemo.api.CardListRow;
import com.carddemo.api.CobolMessages;
import com.carddemo.service.CardService;
import com.carddemo.service.MenuService;
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
 * CCRDLIA web surface (tran CCLI): the 3270 screen arrives as one POST per
 * AID press carrying the screen fields plus the WS-THIS-PROGCOMMAREA as
 * hidden inputs, and the browse outcome is re-rendered by
 * {@code card-list.html}. The server stays stateless beyond the sign-on
 * session, exactly like the pseudo-conversational original.
 */
@Controller
public class CardListUiController {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final CardService cardService;
    private final MenuService menuService;

    public CardListUiController(CardService cardService, MenuService menuService) {
        this.cardService = cardService;
        this.menuService = menuService;
    }

    @ModelAttribute
    void addHeaderFields(Model model) {
        LocalDateTime now = LocalDateTime.now();
        model.addAttribute("currentDate", now.format(DATE_FORMAT));
        model.addAttribute("currentTime", now.format(TIME_FORMAT));
    }

    @GetMapping("/cards/list")
    public String cardList(Model model) {
        CardListResponse result =
                cardService.browse(new CardListRequest("ENTER", null, null, null, null));
        model.addAttribute("screen", result.screen());
        model.addAttribute("pageState", result.pageState());
        return "card-list";
    }

    @PostMapping("/cards/list")
    public String cardListAid(
            @RequestParam(name = "aid", defaultValue = "ENTER") String aid,
            @RequestParam(name = "acctsid", required = false) String accountFilter,
            @RequestParam(name = "cardsid", required = false) String cardFilter,
            @RequestParam(name = "crdsel", required = false) List<String> selections,
            @RequestParam(name = "firstCardNumber", required = false) String firstCardNumber,
            @RequestParam(name = "lastCardNumber", required = false) String lastCardNumber,
            @RequestParam(name = "screenNumber", defaultValue = "1") int screenNumber,
            @RequestParam(name = "lastPageShown", defaultValue = "false") boolean lastPageShown,
            @RequestParam(name = "nextPageExists", defaultValue = "false") boolean nextPageExists,
            @RequestParam(name = "rowAcct", required = false) List<String> rowAccounts,
            @RequestParam(name = "rowCard", required = false) List<String> rowCards,
            @RequestParam(name = "rowStatus", required = false) List<String> rowStatuses,
            Model model) {
        CardListPageState state = new CardListPageState(
                blankToNull(firstCardNumber), blankToNull(lastCardNumber),
                screenNumber, lastPageShown, nextPageExists,
                rows(rowAccounts, rowCards, rowStatuses));
        CardListResponse result = cardService.browse(
                new CardListRequest(aid, accountFilter, cardFilter, selections, state));
        if ("exit".equals(result.outcome())) {
            return "redirect:/menu";                                    // :384-406
        }
        if ("navigate".equals(result.outcome())) {
            // S04-B1 — the XCTL target resolves through the route registry.
            CardListNavigation navigation = result.navigation();
            String route = menuService.uiRouteForProgram(navigation.program());
            if (route != null) {
                return "redirect:" + route + "?accountId=" + navigation.accountId()
                        + "&cardNumber=" + navigation.cardNumber();
            }
            // Disabled target: same coming-soon idiom the menu renders green
            // for placeholder options (COMEN01C.cbl:169-176).
            model.addAttribute("message", CobolMessages.optionComingSoon(
                    menuService.optionNameForProgram(navigation.program())));
            model.addAttribute("messageStyle", "info");
        }
        model.addAttribute("screen", result.screen());
        model.addAttribute("pageState", result.pageState());
        return "card-list";
    }

    private static List<CardListRow> rows(List<String> accounts, List<String> cardNumbers,
                                          List<String> statuses) {
        List<CardListRow> rows = new ArrayList<>(
                Collections.nCopies(CardListPageState.ROW_COUNT, null));
        for (int i = 0; i < CardListPageState.ROW_COUNT; i++) {
            String cardNumber = value(cardNumbers, i);
            if (cardNumber == null) {
                continue;
            }
            rows.set(i, new CardListRow(parseLong(value(accounts, i)), cardNumber,
                    value(statuses, i)));
        }
        return rows;
    }

    private static String value(List<String> list, int index) {
        if (list == null || index >= list.size()) {
            return null;
        }
        return blankToNull(list.get(index));
    }

    private static Long parseLong(String value) {
        return value == null ? null : Long.parseLong(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
