package com.carddemo.security;

import com.carddemo.model.SecurityUser;
import com.carddemo.repository.SecurityUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class SecurityUserDetailsService implements UserDetailsService {
    private final SecurityUserRepository repository;

    public SecurityUserDetailsService(SecurityUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalized = username == null ? "" : username.toUpperCase(Locale.ROOT);
        SecurityUser user = repository.findById(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        String role = "A".equals(user.getUserType()) ? "ROLE_ADMIN" : "ROLE_USER";
        return User.withUsername(user.getUserId())
                .password(user.getPassword())
                .authorities(new SimpleGrantedAuthority(role))
                .build();
    }
}
