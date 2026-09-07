package com.carddemo.api;

import com.carddemo.security.SessionContext;
import com.carddemo.service.AuthService;
import com.carddemo.service.ScreenHeaderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;

import java.util.List;

/**
 * COSGN00C (trancode CC00) as JSON REST (ONLINE profile). The pseudo-conversation
 * (RETURN TRANSID, COSGN00C.cbl:98-102) is replaced by self-contained requests (B-0028); the
 * COMMAREA written on success (:222-229) becomes the HTTP session (B-0027).
 */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final ScreenHeaderService headerService;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(
            AuthService authService,
            ScreenHeaderService headerService,
            SecurityContextRepository securityContextRepository) {
        this.authService = authService;
        this.headerService = headerService;
        this.securityContextRepository = securityContextRepository;
    }

    /** FR-01: header fields of the initial SEND MAP (EIBCALEN = 0, COSGN00C.cbl:80-84). */
    @GetMapping("/header")
    public ScreenHeaderResponse header() {
        return headerService.header(AuthService.TRAN_ID, AuthService.PROGRAM_NAME);
    }

    /** FR-02..FR-07: PROCESS-ENTER-KEY. A second successful sign-on replaces the session. */
    @PostMapping("/signon")
    public AuthResponse signOn(
            @Valid @RequestBody AuthRequest body, HttpServletRequest request, HttpServletResponse response) {
        SessionContext context = authService.signOn(body.userId(), body.password());

        HttpSession existing = request.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        HttpSession session = request.getSession(true);
        context.store(session);

        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                context.userId(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + context.userType().name())));
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
        securityContextRepository.saveContext(securityContext, request, response);

        return new AuthResponse(context.userId(), String.valueOf(context.userType().legacyCode()),
                authService.landingTarget(context));
    }

    /** B-0027: the identity the session carries, or 401 when there is none. */
    @GetMapping("/session")
    public SessionResponse session(HttpServletRequest request) {
        SessionContext context = SessionContext.from(request.getSession(false))
                .orElseThrow(() -> new CobolApiException(HttpStatus.UNAUTHORIZED, CobolMessages.AUTHENTICATION_REQUIRED));
        return new SessionResponse(context.userId(), String.valueOf(context.userType().legacyCode()));
    }

    /** FR-08: PF3 exit (COSGN00C.cbl:86-88, :162-172). Idempotent: works with or without a session. */
    @PostMapping("/signoff")
    public SignoffResponse signOff(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return new SignoffResponse(authService.signOffMessage());
    }
}
