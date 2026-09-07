package com.carddemo.service;

import com.carddemo.api.CobolApiException;
import com.carddemo.api.CobolMessages;
import com.carddemo.model.SecurityUser;
import com.carddemo.repository.SecurityUserRepository;
import com.carddemo.security.SessionContext;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * COSGN00C sign-on (app/cbl/COSGN00C.cbl). One service per program (target state §3).
 *
 * <p>PROCESS-ENTER-KEY (:105-140) and READ-USER-SEC-FILE (:207-257) are reproduced in paragraph
 * order: blank User ID, blank Password, upper-case both, keyed read of USRSEC, password compare,
 * user type. The plaintext byte-compare of SEC-USR-PWD (:221) is the B-0026 demo substitute:
 * BCrypt via {@link PasswordEncoder} with a one-time upgrade of the legacy plaintext row
 * (D-0024 / D-0029, Q-13). No lockout, no audit — parity with the legacy exposure.
 */
@Service
public class AuthService {

    /** WS-TRANID / WS-PGMNAME, COSGN00C.cbl:24-25. */
    public static final String TRAN_ID = "CC00";
    public static final String PROGRAM_NAME = "COSGN00C";

    /**
     * B-0009 routing point. Both XCTL targets (COMEN01C for 'U', COADM01C for 'A',
     * COSGN00C.cbl:230-239) land on the menu shell in S-01 (Q-01, D-0024).
     */
    public static final String LANDING_TARGET = "/menu";

    private final SecurityUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AuthService(SecurityUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * FR-02..FR-07. Returns the identity to be stored in the session (CDEMO-USER-ID /
     * CDEMO-USER-TYPE, COSGN00C.cbl:226-227) or throws a {@link CobolApiException} carrying the
     * verbatim legacy message.
     */
    @Transactional
    public SessionContext signOn(String userId, String password) {
        if (isBlank(userId)) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST, CobolMessages.ENTER_USER_ID);
        }
        if (isBlank(password)) {
            throw new CobolApiException(HttpStatus.BAD_REQUEST, CobolMessages.ENTER_PASSWORD);
        }
        String upperUserId = upper(userId);
        String upperPassword = upper(password);

        Optional<SecurityUser> found;
        try {
            found = users.findById(upperUserId);
        } catch (DataAccessException ex) {
            throw new CobolApiException(HttpStatus.INTERNAL_SERVER_ERROR, CobolMessages.UNABLE_TO_VERIFY_USER);
        }
        SecurityUser user = found.orElseThrow(
                () -> new CobolApiException(HttpStatus.UNAUTHORIZED, CobolMessages.USER_NOT_FOUND));

        if (!passwordMatches(user, upperPassword)) {
            throw new CobolApiException(HttpStatus.UNAUTHORIZED, CobolMessages.WRONG_PASSWORD);
        }
        return new SessionContext(user.getSecUsrId(), SessionContext.UserType.fromLegacyCode(user.getSecUsrType()));
    }

    /** FR-08: the PF3 exit text (COSGN00C.cbl:162-172, CSMSG01Y.cpy:19). */
    public String signOffMessage() {
        return CobolMessages.THANK_YOU;
    }

    /** B-0009: where the SPA goes after a successful sign-on. */
    public String landingTarget(SessionContext context) {
        return LANDING_TARGET;
    }

    private boolean passwordMatches(SecurityUser user, String upperPassword) {
        String hash = user.getSecUsrPwdHash();
        if (hash != null) {
            return passwordEncoder.matches(upperPassword, hash);
        }
        String legacy = user.getSecUsrPwdLegacy();
        if (legacy == null || !rtrim(legacy).equals(rtrim(upperPassword))) {
            return false;
        }
        upgradeLegacyPassword(user, upperPassword);
        return true;
    }

    /** D-0029: verify the plaintext once, then write the BCrypt hash and clear the legacy column. */
    private void upgradeLegacyPassword(SecurityUser user, String upperPassword) {
        user.setSecUsrPwdHash(passwordEncoder.encode(upperPassword));
        user.setSecUsrPwdLegacy(null);
        try {
            users.save(user);
        } catch (DataAccessException ex) {
            throw new CobolApiException(HttpStatus.INTERNAL_SERVER_ERROR, CobolMessages.UNABLE_TO_VERIFY_USER);
        }
    }

    /** {@code = SPACES OR LOW-VALUES}, COSGN00C.cbl:117, :122. */
    private static boolean isBlank(String value) {
        if (value == null) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != ' ' && c != '\0') {
                return false;
            }
        }
        return true;
    }

    /** FUNCTION UPPER-CASE, COSGN00C.cbl:132-136. */
    private static String upper(String value) {
        return value.toUpperCase(Locale.ROOT);
    }

    /** COBOL compares PIC X fields space-padded to the same length. */
    private static String rtrim(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == ' ') {
            end--;
        }
        return value.substring(0, end);
    }
}
