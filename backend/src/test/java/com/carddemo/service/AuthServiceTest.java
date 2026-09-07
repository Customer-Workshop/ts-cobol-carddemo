package com.carddemo.service;

import com.carddemo.api.CobolApiException;
import com.carddemo.api.CobolMessages;
import com.carddemo.model.SecurityUser;
import com.carddemo.repository.SecurityUserRepository;
import com.carddemo.security.SessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * COSGN00C PROCESS-ENTER-KEY (app/cbl/COSGN00C.cbl:105-140) and READ-USER-SEC-FILE (:207-257),
 * one test per branch, expectations derived from the COBOL and
 * functional/CardDemo/programs/COSGN00C_functional_requirement.md (never from the implementation).
 * Confidence HIGH unless stated.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Mock
    private SecurityUserRepository users;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, encoder);
    }

    private static SecurityUser legacyUser(String id, String legacyPassword, String type) {
        SecurityUser user = new SecurityUser();
        user.setSecUsrId(id);
        user.setSecUsrFname("FIRST");
        user.setSecUsrLname("LAST");
        user.setSecUsrPwdHash(null);
        user.setSecUsrPwdLegacy(legacyPassword);
        user.setSecUsrType(type);
        return user;
    }

    private CobolApiException signOnFails(String userId, String password) {
        return assertThrows(CobolApiException.class, () -> service.signOn(userId, password));
    }

    @Test
    @DisplayName("FR-02 / SGN-B03 — blank User ID -> E-01 (COSGN00C.cbl:117-120), USRSEC never read")
    void fr02BlankUserId() {
        CobolApiException ex = signOnFails("        ", "PASSWORD");
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(CobolMessages.ENTER_USER_ID, ex.getMessage());
        verify(users, never()).findById(anyString());
    }

    @Test
    @DisplayName("FR-02 — null User ID is LOW-VALUES/SPACES -> E-01 (COSGN00C.cbl:117)")
    void fr02NullUserId() {
        CobolApiException ex = signOnFails(null, "PASSWORD");
        assertEquals(CobolMessages.ENTER_USER_ID, ex.getMessage());
    }

    @Test
    @DisplayName("FR-03 / SGN-B04 — blank Password -> E-02 (COSGN00C.cbl:122-125)")
    void fr03BlankPassword() {
        CobolApiException ex = signOnFails("USER0001", "");
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(CobolMessages.ENTER_PASSWORD, ex.getMessage());
        verify(users, never()).findById(anyString());
    }

    @Test
    @DisplayName("FR-02 before FR-03 — both blank reports User ID first (COSGN00C.cbl:117-127 order)")
    void fr02WinsOverFr03WhenBothBlank() {
        CobolApiException ex = signOnFails("", "");
        assertEquals(CobolMessages.ENTER_USER_ID, ex.getMessage());
    }

    @Test
    @DisplayName("FR-06 / SGN-B09 — NOTFND (RESP 13) -> E-07 401 (COSGN00C.cbl:247-251)")
    void fr06UserNotFound() {
        when(users.findById("NOBODY")).thenReturn(Optional.empty());
        CobolApiException ex = signOnFails("nobody", "PASSWORD");
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals(CobolMessages.USER_NOT_FOUND, ex.getMessage());
    }

    @Test
    @DisplayName("FR-06 / SGN-B08 — wrong password -> E-06 401, row untouched (COSGN00C.cbl:241-246)")
    void fr06WrongPassword() {
        SecurityUser user = legacyUser("USER0001", "PASSWORD", "U");
        when(users.findById("USER0001")).thenReturn(Optional.of(user));
        CobolApiException ex = signOnFails("USER0001", "WRONGPWD");
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals(CobolMessages.WRONG_PASSWORD, ex.getMessage());
        verify(users, never()).save(any());
        assertEquals("PASSWORD", user.getSecUsrPwdLegacy());
        assertNull(user.getSecUsrPwdHash());
    }

    @Test
    @DisplayName("FR-05 / SGN-B06 — match, SEC-USR-TYPE 'U' -> session U, landing /menu (COSGN00C.cbl:221-233)")
    void fr05RegularUser() {
        when(users.findById("USER0001")).thenReturn(Optional.of(legacyUser("USER0001", "PASSWORD", "U")));
        SessionContext context = service.signOn("USER0001", "PASSWORD");
        assertEquals("USER0001", context.userId());
        assertEquals(SessionContext.UserType.USER, context.userType());
        assertEquals("/menu", service.landingTarget(context));
    }

    @Test
    @DisplayName("FR-05 / SGN-B07 + Q-01 — match, SEC-USR-TYPE 'A' -> session A, landing /menu not COADM01C (COSGN00C.cbl:234-239)")
    void fr05AdminUserLandsOnMenu() {
        when(users.findById("ADMIN001")).thenReturn(Optional.of(legacyUser("ADMIN001", "PASSWORD", "A")));
        SessionContext context = service.signOn("ADMIN001", "PASSWORD");
        assertEquals(SessionContext.UserType.ADMIN, context.userType());
        assertTrue(context.isAdmin());
        assertEquals("/menu", service.landingTarget(context));
    }

    @Test
    @DisplayName("FR-07 / SGN-B10 — OTHER RESP -> E-13 500 (COSGN00C.cbl:252-257)")
    void fr07RepositoryFailure() {
        when(users.findById("USER0001")).thenThrow(new DataAccessResourceFailureException("connection refused"));
        CobolApiException ex = signOnFails("USER0001", "PASSWORD");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
        assertEquals(CobolMessages.UNABLE_TO_VERIFY_USER, ex.getMessage());
    }

    @Test
    @DisplayName("FR-22 / SGN-B10 — datastore unreachable (no transaction can be opened) -> E-13 500 (COSGN00C.cbl:252-257)")
    void fr22DatastoreUnreachable() {
        when(users.findById("USER0001")).thenThrow(
                new CannotCreateTransactionException("Could not open JPA EntityManager for transaction"));
        CobolApiException ex = signOnFails("USER0001", "PASSWORD");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
        assertEquals(CobolMessages.UNABLE_TO_VERIFY_USER, ex.getMessage());
    }

    @Test
    @DisplayName("FR-04 / Q-13 — input upper-cased before the read and the compare (COSGN00C.cbl:132-136)")
    void fr04UpperCasesUserIdAndPassword() {
        when(users.findById("USER0001")).thenReturn(Optional.of(legacyUser("USER0001", "PASSWORD", "U")));
        SessionContext context = service.signOn("user0001", "password");
        assertEquals("USER0001", context.userId());
        verify(users).findById("USER0001");
    }

    @Test
    @DisplayName("FR-04 — stored lower-case legacy password can never match an upper-cased input (COSGN00C.cbl:136, :221) — MEDIUM (legacy data quirk)")
    void fr04LowerCaseStoredPasswordNeverMatches() {
        when(users.findById("USER0001")).thenReturn(Optional.of(legacyUser("USER0001", "password", "U")));
        CobolApiException ex = signOnFails("USER0001", "password");
        assertEquals(CobolMessages.WRONG_PASSWORD, ex.getMessage());
    }

    @Test
    @DisplayName("B-0026 / D-0029 — first sign-on of a legacy row writes a BCrypt hash and clears sec_usr_pwd_legacy exactly once")
    void b0026UpgradeOnLogin() {
        SecurityUser user = legacyUser("USER0001", "PASSWORD", "U");
        when(users.findById("USER0001")).thenReturn(Optional.of(user));

        service.signOn("USER0001", "PASSWORD");

        verify(users, times(1)).save(user);
        assertNull(user.getSecUsrPwdLegacy());
        assertTrue(user.getSecUsrPwdHash().startsWith("{bcrypt}"));
        assertTrue(encoder.matches("PASSWORD", user.getSecUsrPwdHash()));
    }

    @Test
    @DisplayName("B-0026 — second sign-on verifies against the hash and performs no further write")
    void b0026SecondLoginUsesHash() {
        SecurityUser user = legacyUser("USER0001", null, "U");
        user.setSecUsrPwdHash(encoder.encode("PASSWORD"));
        when(users.findById("USER0001")).thenReturn(Optional.of(user));

        SessionContext context = service.signOn("USER0001", "password");

        assertEquals("USER0001", context.userId());
        verify(users, never()).save(any());
    }

    @Test
    @DisplayName("B-0026 — hashed row rejects a wrong password with E-06")
    void b0026HashedRowWrongPassword() {
        SecurityUser user = legacyUser("USER0001", null, "U");
        user.setSecUsrPwdHash(encoder.encode("PASSWORD"));
        when(users.findById("USER0001")).thenReturn(Optional.of(user));
        CobolApiException ex = signOnFails("USER0001", "OTHER");
        assertEquals(CobolMessages.WRONG_PASSWORD, ex.getMessage());
    }

    @Test
    @DisplayName("B-0026 — a row with neither hash nor legacy value cannot match -> E-06 — MEDIUM (no legacy analogue)")
    void b0026RowWithoutAnyCredential() {
        when(users.findById("USER0001")).thenReturn(Optional.of(legacyUser("USER0001", null, "U")));
        CobolApiException ex = signOnFails("USER0001", "PASSWORD");
        assertEquals(CobolMessages.WRONG_PASSWORD, ex.getMessage());
    }

    @Test
    @DisplayName("FR-07 — failure while writing the upgraded hash -> E-13 500")
    void fr07SaveFailureDuringUpgrade() {
        SecurityUser user = legacyUser("USER0001", "PASSWORD", "U");
        when(users.findById("USER0001")).thenReturn(Optional.of(user));
        when(users.save(user)).thenThrow(new DataAccessResourceFailureException("write failed"));
        CobolApiException ex = signOnFails("USER0001", "PASSWORD");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
        assertEquals(CobolMessages.UNABLE_TO_VERIFY_USER, ex.getMessage());
    }

    @Test
    @DisplayName("AC-SGN-12 / B-0026 — no lockout: the fifth wrong password still returns E-06 and the row is unchanged")
    void b0026NoLockout() {
        SecurityUser user = legacyUser("USER0001", "PASSWORD", "U");
        when(users.findById("USER0001")).thenReturn(Optional.of(user));
        for (int i = 0; i < 5; i++) {
            assertEquals(CobolMessages.WRONG_PASSWORD, signOnFails("USER0001", "BAD" + i).getMessage());
        }
        SessionContext context = service.signOn("USER0001", "PASSWORD");
        assertEquals("USER0001", context.userId());
    }

    @Test
    @DisplayName("FR-08 — sign-off text is CCDA-MSG-THANK-YOU verbatim (CSMSG01Y.cpy:19, COSGN00C.cbl:86-88)")
    void fr08SignOffMessage() {
        assertEquals("Thank you for using CardDemo application...      ", service.signOffMessage());
        assertEquals(CobolMessages.THANK_YOU, service.signOffMessage());
    }

    @Test
    @DisplayName("FR-01 — WS-TRANID 'CC00' and WS-PGMNAME 'COSGN00C' (COSGN00C.cbl:24-25)")
    void fr01Constants() {
        assertEquals("CC00", AuthService.TRAN_ID);
        assertEquals("COSGN00C", AuthService.PROGRAM_NAME);
    }
}
