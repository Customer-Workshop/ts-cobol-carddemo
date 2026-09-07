package com.carddemo.service;

import com.carddemo.api.CobolApiException;
import com.carddemo.api.CobolMessages;
import com.carddemo.data.CobolFieldReader;
import com.carddemo.data.DateColumnGate;
import com.carddemo.data.LegacyExtractParser;
import com.carddemo.model.SecurityUser;
import com.carddemo.repository.SecurityUserRepository;
import com.carddemo.security.SessionContext;
import com.carddemo.support.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-0026 / D-0029 on PostgreSQL 16 (Testcontainers): the imported USRSEC row
 * (sec_usr_pwd_hash NULL, sec_usr_pwd_legacy 'PASSWORD') is upgraded exactly once on the first
 * successful sign-on and the second sign-on verifies against the BCrypt hash.
 * Fixture: R__seed_test_data (ADMIN001 / USER0001) plus the real extract
 * app/data/EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS parsed by the wave-1 importer.
 */
class AuthUpgradeOnLoginIntegrationTest extends PostgresIntegrationTest {

    private static final Path USRSEC_EXTRACT = Path.of("../app/data/EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS");

    @Autowired
    private AuthService authService;

    @Autowired
    private SecurityUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** The fixture is shared with the wave-1 repository tests: restore it around every test. */
    @BeforeEach
    @AfterEach
    void restoreFixture() {
        jdbcTemplate.update("DELETE FROM users WHERE sec_usr_id NOT IN ('ADMIN001', 'USER0001')");
        jdbcTemplate.update("UPDATE users SET sec_usr_pwd_hash = NULL, sec_usr_pwd_legacy = 'PASSWORD'");
    }

    private Map<String, Object> row(String userId) {
        return jdbcTemplate.queryForMap(
                "SELECT sec_usr_id, sec_usr_pwd_hash, sec_usr_pwd_legacy, sec_usr_type FROM users WHERE sec_usr_id = ?",
                userId);
    }

    @Test
    @DisplayName("B-0026 — USER0001: legacy 'PASSWORD' before; {bcrypt} hash + legacy NULL after; second login uses the hash")
    void b0026UpgradeOnLoginRegularUser() {
        Map<String, Object> before = row("USER0001");
        assertNull(before.get("sec_usr_pwd_hash"));
        assertEquals("PASSWORD", before.get("sec_usr_pwd_legacy"));
        assertEquals("U", before.get("sec_usr_type"));

        SessionContext first = authService.signOn("user0001", "password");
        assertEquals("USER0001", first.userId());
        assertEquals(SessionContext.UserType.USER, first.userType());

        Map<String, Object> after = row("USER0001");
        String hash = (String) after.get("sec_usr_pwd_hash");
        assertTrue(hash.startsWith("{bcrypt}$2"), hash);
        assertNull(after.get("sec_usr_pwd_legacy"));
        assertTrue(passwordEncoder.matches("PASSWORD", hash));

        SessionContext second = authService.signOn("USER0001", "PASSWORD");
        assertEquals("USER0001", second.userId());
        assertEquals(hash, row("USER0001").get("sec_usr_pwd_hash"), "hash written exactly once");

        CobolApiException wrong = assertThrows(CobolApiException.class, () -> authService.signOn("USER0001", "WRONG"));
        assertEquals(CobolMessages.WRONG_PASSWORD, wrong.getMessage());
        assertEquals(hash, row("USER0001").get("sec_usr_pwd_hash"));
    }

    @Test
    @DisplayName("B-0026 / Q-01 — ADMIN001 ('A') upgrades the same way and is routed to /menu")
    void b0026UpgradeOnLoginAdmin() {
        assertEquals("PASSWORD", row("ADMIN001").get("sec_usr_pwd_legacy"));

        SessionContext context = authService.signOn("ADMIN001", "PASSWORD");

        assertTrue(context.isAdmin());
        assertEquals("/menu", authService.landingTarget(context));
        Map<String, Object> after = row("ADMIN001");
        assertNull(after.get("sec_usr_pwd_legacy"));
        assertTrue(((String) after.get("sec_usr_pwd_hash")).startsWith("{bcrypt}"));
    }

    @Test
    @DisplayName("FR-06 — a wrong password against a legacy row leaves the row exactly as imported")
    void fr06WrongPasswordDoesNotUpgrade() {
        List<SecurityUser> imported = importedUsers();
        SecurityUser sample = imported.stream().filter(u -> !u.getSecUsrId().equals("USER0001")
                && !u.getSecUsrId().equals("ADMIN001")).findFirst().orElseThrow();
        users.save(sample);

        CobolApiException ex = assertThrows(CobolApiException.class,
                () -> authService.signOn(sample.getSecUsrId(), "NOTIT"));
        assertEquals(CobolMessages.WRONG_PASSWORD, ex.getMessage());

        Map<String, Object> after = row(sample.getSecUsrId());
        assertNull(after.get("sec_usr_pwd_hash"));
        assertEquals(sample.getSecUsrPwdLegacy(), after.get("sec_usr_pwd_legacy"));
    }

    @Test
    @DisplayName("FR-06 — an id absent from USRSEC -> E-07 (RESP 13) on PostgreSQL")
    void fr06NotFoundOnPostgres() {
        CobolApiException ex = assertThrows(CobolApiException.class, () -> authService.signOn("NOSUCHID", "PASSWORD"));
        assertEquals(CobolMessages.USER_NOT_FOUND, ex.getMessage());
    }

    @Test
    @DisplayName("B-0006 — every id of the real USRSEC extract signs on with its legacy password and is upgraded once")
    void b0006RealExtractUsersSignOn() {
        List<SecurityUser> imported = importedUsers();
        assertTrue(imported.size() >= 2, "extract must hold users");
        users.deleteAll();
        users.saveAll(imported);

        for (SecurityUser user : imported) {
            SessionContext context = authService.signOn(user.getSecUsrId(), user.getSecUsrPwdLegacy());
            assertEquals(user.getSecUsrId(), context.userId());
            assertEquals(user.getSecUsrType(), String.valueOf(context.userType().legacyCode()));
        }
        Integer legacyLeft = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE sec_usr_pwd_legacy IS NOT NULL OR sec_usr_pwd_hash IS NULL", Integer.class);
        assertEquals(0, legacyLeft);
    }

    private static List<SecurityUser> importedUsers() {
        try {
            byte[] bytes = Files.readAllBytes(USRSEC_EXTRACT);
            List<String> records = CobolFieldReader.splitRecords(
                    new String(bytes, Charset.forName("IBM037")), LegacyExtractParser.USER_RECORD_LENGTH);
            return new LegacyExtractParser(new DateColumnGate(), true).parseUsers(records, "USRSEC");
        } catch (IOException ex) {
            throw new IllegalStateException("cannot read " + USRSEC_EXTRACT, ex);
        }
    }
}
