package com.carddemo.api;

import com.carddemo.security.SessionContext;
import com.carddemo.service.AccountViewService;
import com.carddemo.service.ScreenHeaderService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * COACTVWC (trancode CAVW) as JSON REST (ONLINE profile). The legacy screen is one RECEIVE MAP with
 * one input field, so the read is a {@code GET} keyed by that field rather than a POST body: the
 * request carries no state beyond the account filter (B-0028) and the program writes nothing
 * (COACTVWC.cbl:687-720 are three reads).
 *
 * <p>The initial {@code SEND MAP ERASE} reads no file (:261-284, :416-452), but 1100-SCREEN-INIT
 * still paints the header from {@code COTTL01Y} and {@code FUNCTION CURRENT-DATE} (:431-453), so the
 * empty map has its own header endpoint; every account read here is a re-entry with a search value.
 * The 11-digit edit lives in {@link AccountViewService} in the COBOL order (Q-14): the raw bytes of
 * ACCTSIDI reach the service unvalidated, including the blank field, which the legacy reports as E-04.
 */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountViewService accountViewService;
    private final ScreenHeaderService headerService;

    public AccountController(AccountViewService accountViewService, ScreenHeaderService headerService) {
        this.accountViewService = accountViewService;
        this.headerService = headerService;
    }

    /**
     * FR-13 / AC-ACV-01: 1100-SCREEN-INIT (COACTVWC.cbl:431-453) moves the CCDA titles, the trancode,
     * the program name and the current date and time into the map on every SEND, the first one
     * included, so the empty screen carries a live header and reads no account file.
     */
    @GetMapping("/view/header")
    public ScreenHeaderResponse header(HttpServletRequest request) {
        requireSession(request);
        return headerService.header(AccountViewService.TRAN_ID, AccountViewService.PROGRAM_NAME);
    }

    /** FR-13..FR-21: 2200-EDIT-MAP-INPUTS then 9000-READ-ACCT for the typed account filter. */
    @GetMapping("/{acctId}")
    public AccountViewResponse view(@PathVariable String acctId, HttpServletRequest request) {
        requireSession(request);
        return accountViewService.view(acctId);
    }

    /**
     * The blank ACCTSID field (COACTVWC.cbl:628-633, :640-645): an empty path segment is the only way
     * the SPA can send "nothing typed", and it gets the same E-04 the legacy paints.
     */
    @GetMapping({"", "/"})
    public AccountViewResponse viewWithoutAccount(HttpServletRequest request) {
        requireSession(request);
        return accountViewService.view(null);
    }

    /** B-0027: the account view needs the signed-on identity and never rewrites it (DV-02). */
    private SessionContext requireSession(HttpServletRequest request) {
        return SessionContext.from(request.getSession(false))
                .orElseThrow(() -> new CobolApiException(
                        HttpStatus.UNAUTHORIZED, CobolMessages.AUTHENTICATION_REQUIRED));
    }
}
