package com.carddemo;

import com.carddemo.model.Account;
import com.carddemo.model.SecurityUser;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import com.carddemo.repository.DisclosureGroupRepository;
import com.carddemo.repository.SecurityUserRepository;
import com.carddemo.repository.TransactionCategoryBalanceRepository;
import com.carddemo.repository.TransactionCategoryRepository;
import com.carddemo.repository.TransactionRepository;
import com.carddemo.repository.TransactionTypeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestPropertySource(properties = {
        "carddemo.seed.data-dir=classpath:seed",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class DataSeederIntegrationTest {

    @Autowired private AccountRepository accountRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private CardXrefRepository cardXrefRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private DisclosureGroupRepository disclosureGroupRepository;
    @Autowired private TransactionTypeRepository transactionTypeRepository;
    @Autowired private TransactionCategoryRepository transactionCategoryRepository;
    @Autowired private TransactionCategoryBalanceRepository transactionCategoryBalanceRepository;
    @Autowired private SecurityUserRepository securityUserRepository;

    @Test
    void seedsFixtureAndPreservesValues() {
        assertEquals(1, accountRepository.count());
        assertEquals(1, customerRepository.count());
        assertEquals(1, cardRepository.count());
        assertEquals(1, cardXrefRepository.count());
        assertEquals(1, transactionRepository.count());
        assertEquals(1, disclosureGroupRepository.count());
        assertEquals(1, transactionTypeRepository.count());
        assertEquals(1, transactionCategoryRepository.count());
        assertEquals(1, transactionCategoryBalanceRepository.count());
        assertEquals(1, securityUserRepository.count());

        Account account = accountRepository.findById(1L).orElseThrow();
        assertEquals(new BigDecimal("194.00"), account.getAcctCurrBal());
        assertEquals(new BigDecimal("15.00"),
                disclosureGroupRepository.findAll().getFirst().getInterestRate());
        assertEquals(new BigDecimal("-1234.00"),
                transactionCategoryBalanceRepository.findAll().getFirst().getBalance());
        SecurityUser user = securityUserRepository.findById("ADMIN001").orElseThrow();
        assertEquals("A", user.getUserType());
    }
}
