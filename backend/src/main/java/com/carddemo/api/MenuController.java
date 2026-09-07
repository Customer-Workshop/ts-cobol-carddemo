package com.carddemo.api;

import com.carddemo.security.SessionContext;
import com.carddemo.service.MenuService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * COMEN01C (trancode CM00) as JSON REST (ONLINE profile). {@code GET /api/menu} is the initial
 * SEND MAP (COMEN01C.cbl:85-92) and {@code POST /api/menu/select} is the ENTER re-entry
 * (:93-95, :115-191); both require the session identity that replaces the COMMAREA (B-0027), so
 * the {@code EIBCALEN = 0} refusal (:82-84) surfaces as the 401 of {@code SecurityConfig}.
 */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api/menu")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    /** FR-09: header fields plus the 11 option labels of COMEN1A. */
    @GetMapping
    public MenuResponse menu(HttpServletRequest request) {
        requireSession(request);
        return menuService.menu();
    }

    /** FR-10 / FR-11 / Q-12: PROCESS-ENTER-KEY. Invalid options leave as 400 + E-08 (:127-134). */
    @PostMapping("/select")
    public MenuSelectionResponse select(@Valid @RequestBody MenuSelectRequest body, HttpServletRequest request) {
        requireSession(request);
        return menuService.select(body.option());
    }

    /**
     * B-0027: the menu reads the identity and never rewrites it (the moves at :181-182 are
     * commented out in the source).
     */
    private SessionContext requireSession(HttpServletRequest request) {
        return SessionContext.from(request.getSession(false))
                .orElseThrow(() -> new CobolApiException(
                        HttpStatus.UNAUTHORIZED, CobolMessages.AUTHENTICATION_REQUIRED));
    }
}
