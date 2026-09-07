package com.carddemo;

import com.carddemo.data.DataSeeder;
import com.carddemo.model.SecurityUser;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.SecurityUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "carddemo.seed.force=true",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class DataSeederForceIntegrationTest {

    @Autowired private DataSeeder dataSeeder;
    @Autowired private AccountRepository accountRepository;
    @Autowired private SecurityUserRepository securityUserRepository;

    @Test
    @DirtiesContext
    void forcedSeedClearsExistingRowsBeforeReseeding() throws Exception {
        SecurityUser existing = new SecurityUser();
        existing.setUserId("KEEP001");
        existing.setFirstName("Keep");
        existing.setLastName("Me");
        existing.setPassword("PASSWORD");
        existing.setUserType("U");
        securityUserRepository.save(existing);

        dataSeeder.run();

        assertFalse(securityUserRepository.existsById("KEEP001"));
        assertEquals(1, accountRepository.count());
    }
}
