package com.carddemo;

import com.carddemo.data.CobolFieldReader;
import com.carddemo.model.SecurityUser;
import com.carddemo.repository.SecurityUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S01-B5 seed-parity check: the seeded {@code users} table must match the legacy
 * USRSEC fixture byte-for-byte per key. Runs against the real fixture under
 * {@code app/data} (the same file the DataSeeder reads), not the trimmed
 * classpath seed set.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.generate-unique-name=true",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UsersSeedParityIntegrationTest {

    @Autowired private SecurityUserRepository securityUserRepository;
    @Value("${carddemo.seed.data-dir:../app/data}") private String dataDir;
    @Value("${carddemo.seed.usrsec-charset:IBM037}") private String usrsecCharset;

    @Test
    void seededUsersMatchUsrsecFixturePerKey() throws IOException {
        Map<String, SecurityUser> expected = readUsrsecFixture();
        assertThat(expected).hasSize(10);

        Map<String, SecurityUser> actual = securityUserRepository.findAll().stream()
                .collect(Collectors.toMap(SecurityUser::getUserId, u -> u,
                        (a, b) -> a, LinkedHashMap::new));
        assertThat(actual).hasSameSizeAs(expected);

        expected.forEach((userId, want) -> {
            SecurityUser got = actual.get(userId);
            assertThat(got).as("user %s exists", userId).isNotNull();
            assertThat(got.getFirstName()).isEqualTo(want.getFirstName());
            assertThat(got.getLastName()).isEqualTo(want.getLastName());
            assertThat(got.getPassword()).isEqualTo(want.getPassword());
            assertThat(got.getUserType()).isEqualTo(want.getUserType());
        });

        assertThat(actual.keySet()).containsExactlyInAnyOrder(
                "ADMIN001", "ADMIN002", "ADMIN003", "ADMIN004", "ADMIN005",
                "USER0001", "USER0002", "USER0003", "USER0004", "USER0005");
        assertThat(actual.values())
                .filteredOn(u -> u.getUserId().startsWith("ADMIN"))
                .allSatisfy(u -> assertThat(u.getUserType()).isEqualTo("A"));
        assertThat(actual.values())
                .filteredOn(u -> u.getUserId().startsWith("USER"))
                .allSatisfy(u -> assertThat(u.getUserType()).isEqualTo("U"));
    }

    private Map<String, SecurityUser> readUsrsecFixture() throws IOException {
        String text = new String(
                Files.readAllBytes(Path.of(dataDir, "EBCDIC", "AWS.M2.CARDDEMO.USRSEC.PS")),
                Charset.forName(usrsecCharset));
        Map<String, SecurityUser> users = new LinkedHashMap<>();
        for (int offset = 0; offset < text.length(); offset += 80) {
            String record = text.substring(offset, Math.min(text.length(), offset + 80));
            if (record.isBlank()) {
                continue;
            }
            SecurityUser user = new SecurityUser();
            user.setUserId(record.substring(0, 8).stripTrailing());
            user.setFirstName(CobolFieldReader.text(record, 8, 20));
            user.setLastName(CobolFieldReader.text(record, 28, 20));
            user.setPassword(CobolFieldReader.text(record, 48, 8));
            user.setUserType(CobolFieldReader.text(record, 56, 1));
            users.put(user.getUserId(), user);
        }
        return users;
    }
}
