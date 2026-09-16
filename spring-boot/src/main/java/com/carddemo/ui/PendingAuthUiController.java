package com.carddemo.ui;

import com.carddemo.api.CobolMessages;
import com.carddemo.api.PendingAuthDetailScreen;
import com.carddemo.api.PendingAuthNavigation;
import com.carddemo.api.PendingAuthPageState;
import com.carddemo.api.PendingAuthRequest;
import com.carddemo.api.PendingAuthResponse;
import com.carddemo.service.PendingAuthDetailService;
import com.carddemo.service.PendingAuthService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * COPAU0A/COPAU1A web surface (trans CPVS/CPVD): each AID press is one POST
 * carrying the screen fields plus the CDEMO-CPVS-INFO COMMAREA as hidden
 * inputs, so the server stays stateless like the pseudo-conversational
 * original (S19-B1/S19-B8).
 */
@Controller
public class PendingAuthUiController {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final PendingAuthService pendingAuthService;
    private final PendingAuthDetailService detailService;

    public PendingAuthUiController(PendingAuthService pendingAuthService,
                                   PendingAuthDetailService detailService) {
        this.pendingAuthService = pendingAuthService;
        this.detailService = detailService;
    }

    @ModelAttribute
    void addHeaderFields(Model model) {
        LocalDateTime now = LocalDateTime.now();
        model.addAttribute("currentDate", now.format(DATE_FORMAT));
        model.addAttribute("currentTime", now.format(TIME_FORMAT));
    }

    // First display: a numeric ?acct= runs the ENTER gather immediately
    // (COPAUS0C.cbl:201-217); a bare entry shows the empty map.
    @GetMapping("/ui/pending-auth")
    public String list(@RequestParam(name = "acct", required = false) String acct, Model model) {
        PendingAuthResponse result = pendingAuthService.browse(
                new PendingAuthRequest("ENTER", acct, null, null));
        model.addAttribute("screen", result.screen());
        model.addAttribute("pageState", result.pageState());
        return "pending-auth";
    }

    @PostMapping("/ui/pending-auth")
    public String listAid(
            @RequestParam(name = "aid", defaultValue = "ENTER") String aid,
            @RequestParam(name = "acctid", required = false) String acctId,
            @RequestParam(name = "sel", required = false) List<String> selections,
            @RequestParam(name = "stateAcct", required = false) String stateAcct,
            @RequestParam(name = "pageNum", defaultValue = "0") int pageNum,
            @RequestParam(name = "prevKey", required = false) List<String> prevKeys,
            @RequestParam(name = "lastKey", required = false) String lastKey,
            @RequestParam(name = "nextPage", defaultValue = "false") boolean nextPage,
            @RequestParam(name = "rowKey", required = false) List<String> rowKeys,
            Model model) {
        PendingAuthPageState state = new PendingAuthPageState(
                numeric(stateAcct), pageNum, keys(prevKeys, PendingAuthPageState.MAX_PAGE_KEYS),
                blankToNull(lastKey), nextPage, keys(rowKeys, PendingAuthPageState.ROW_COUNT));
        PendingAuthResponse result = pendingAuthService.browse(
                new PendingAuthRequest(aid, acctId, selections, state));
        if ("exit".equals(result.outcome())) {
            return "redirect:/menu";                                    // :660-678
        }
        if ("navigate".equals(result.outcome())) {
            // S19-B2 — the 'S' XCTL hands acct + key to COPAUS1C.
            PendingAuthNavigation navigation = result.navigation();
            return "redirect:/ui/pending-auth/" + navigation.accountId()
                    + "/" + navigation.authKey();
        }
        model.addAttribute("screen", result.screen());
        model.addAttribute("pageState", result.pageState());
        return "pending-auth";
    }

    // COPAU1A first display (XCTL-in): acct + selected key are path fields.
    @GetMapping("/ui/pending-auth/{acctId}/{authKey}")
    public String detail(@PathVariable Long acctId, @PathVariable String authKey,
                         Model model) {
        model.addAttribute("screen", detailService.view(acctId, authKey));
        return "pending-auth-detail";
    }

    // AID map (COPAUS1C.cbl:183-194): ENTER re-reads, F3 backs to the list,
    // F5 toggles fraud, F8 hops to the next auth, other keys re-read and
    // redisplay the invalid-key message.
    @PostMapping("/ui/pending-auth/{acctId}/{authKey}")
    public String detailAid(@PathVariable Long acctId, @PathVariable String authKey,
                            @RequestParam(name = "aid", defaultValue = "ENTER") String aid,
                            Model model) {
        String normalized = aid == null ? "ENTER" : aid.trim().toUpperCase();
        switch (normalized) {
            case "PF3" -> {
                return "redirect:/ui/pending-auth?acct=" + acctId;
            }
            case "PF5" -> {
                model.addAttribute("screen", detailService.markFraud(acctId, authKey));
                return "pending-auth-detail";
            }
            case "PF8" -> {
                PendingAuthDetailScreen next = detailService.next(acctId, authKey);
                if (next.authKey() != null && !next.authKey().equals(authKey)) {
                    return "redirect:/ui/pending-auth/" + acctId + "/" + next.authKey();
                }
                model.addAttribute("screen", next);
                return "pending-auth-detail";
            }
            case "ENTER" -> {
                model.addAttribute("screen", detailService.view(acctId, authKey));
                return "pending-auth-detail";
            }
            default -> {
                PendingAuthDetailScreen screen = detailService.view(acctId, authKey);
                model.addAttribute("screen", new PendingAuthDetailScreen(
                        screen.acctId(), screen.authKey(), screen.cardNum(),
                        screen.authDate(), screen.authTime(), screen.authResp(),
                        screen.authRespDeclined(), screen.authReason(), screen.authCode(),
                        screen.amount(), screen.posEntryMode(), screen.messageSource(),
                        screen.mccCode(), screen.cardExpDate(), screen.authType(),
                        screen.transactionId(), screen.matchStatus(), screen.fraudStatus(),
                        screen.merchantName(), screen.merchantId(), screen.merchantCity(),
                        screen.merchantState(), screen.merchantZip(), screen.authTs(),
                        screen.hasNext(), CobolMessages.INVALID_KEY_PRESSED));
                return "pending-auth-detail";
            }
        }
    }

    private static Long numeric(String value) {
        if (value == null || value.isBlank() || !value.trim().matches("\\d+")) {
            return null;
        }
        return Long.parseLong(value.trim());
    }

    private static List<String> keys(List<String> values, int slots) {
        List<String> keys = new ArrayList<>(Collections.nCopies(slots, null));
        if (values == null) {
            return keys;
        }
        for (int i = 0; i < Math.min(slots, values.size()); i++) {
            keys.set(i, blankToNull(values.get(i)));
        }
        return keys;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
