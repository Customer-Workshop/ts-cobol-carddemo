package com.carddemo.ui;

import com.carddemo.api.AuthRequest;
import com.carddemo.api.AuthResponse;
import com.carddemo.api.CobolApiException;
import com.carddemo.api.CobolMessages;
import com.carddemo.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

    public UiController(AuthService authService) {
        this.authService = authService;
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
}
