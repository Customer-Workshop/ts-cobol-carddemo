package com.carddemo.api;

import com.carddemo.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/signon")
    public AuthResponse signon(@Valid @RequestBody AuthRequest request,
                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return service.signon(request, httpRequest, httpResponse);
    }

    @GetMapping("/session")
    public SessionResponse session(Authentication authentication) {
        return service.session(authentication);
    }

    @PostMapping("/signoff")
    public void signoff(HttpServletRequest request) {
        service.signoff(request);
    }
}
