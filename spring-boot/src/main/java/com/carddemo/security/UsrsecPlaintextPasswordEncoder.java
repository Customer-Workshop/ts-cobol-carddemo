package com.carddemo.security;

import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Mirrors the plaintext passwords in the legacy USRSEC dataset.
 * This is intentionally not production-grade password storage.
 */
public final class UsrsecPlaintextPasswordEncoder implements PasswordEncoder {
    @Override
    public String encode(CharSequence rawPassword) {
        return rawPassword.toString();
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return rawPassword.toString().equals(encodedPassword);
    }
}
