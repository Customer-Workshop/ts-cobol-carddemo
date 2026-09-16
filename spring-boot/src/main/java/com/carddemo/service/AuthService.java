package com.carddemo.service;

import com.carddemo.api.AuthRequest;
import com.carddemo.api.AuthResponse;
import com.carddemo.api.CobolApiException;
import com.carddemo.api.CobolMessages;
import com.carddemo.api.SessionResponse;
import com.carddemo.model.SecurityUser;
import com.carddemo.repository.SecurityUserRepository;
import com.carddemo.security.SecurityUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthService {
    private final SecurityUserRepository repository;
    private final SecurityUserDetailsService detailsService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository contextRepository;

    public AuthService(SecurityUserRepository repository, SecurityUserDetailsService detailsService,
                       PasswordEncoder passwordEncoder, SecurityContextRepository contextRepository) {
        this.repository = repository;
        this.detailsService = detailsService;
        this.passwordEncoder = passwordEncoder;
        this.contextRepository = contextRepository;
    }

    public AuthResponse signon(AuthRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        if (request.userId() == null || request.userId().isBlank()) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST, CobolMessages.USER_ID_REQUIRED);
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST, CobolMessages.PASSWORD_REQUIRED);
        }
        String userId = request.userId().toUpperCase(Locale.ROOT);
        String password = request.password().toUpperCase(Locale.ROOT);
        SecurityUser user = repository.findById(userId)
                .orElseThrow(() -> new CobolApiException(HttpStatus.UNAUTHORIZED, CobolMessages.USER_NOT_FOUND));
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new CobolApiException(HttpStatus.UNAUTHORIZED, CobolMessages.WRONG_PASSWORD);
        }
        var details = detailsService.loadUserByUsername(userId);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                details, null, details.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, httpRequest, httpResponse);
        String landing = "A".equals(user.getUserType()) ? "/api/admin/menu" : "/api/menu";
        return new AuthResponse(user.getUserId(), user.getUserType(), landing);
    }

    public SessionResponse session(Authentication authentication) {
        SecurityUser user = repository.findById(authentication.getName())
                .orElseThrow(() -> new CobolApiException(HttpStatus.UNAUTHORIZED, CobolMessages.USER_VERIFY_FAILED));
        return new SessionResponse(user.getUserId(), user.getUserType());
    }

    public void signoff(HttpServletRequest request) {
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
