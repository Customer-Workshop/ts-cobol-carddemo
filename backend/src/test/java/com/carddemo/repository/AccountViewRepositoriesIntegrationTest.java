package com.carddemo.repository;

import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.Customer;
import com.carddemo.model.SecurityUser;
import com.carddemo.support.PostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave-1 data seams B-0001, B-0002, B-0006, B-0007 on PostgreSQL 16 (Testcontainers) with the
 * R__seed_test_data fixture. Reads mirror the CICS READs of COACTVWC / COSGN00C.
 */
class AccountViewRepositoriesIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CardXrefRepository cardXrefRepository;

    @Autowired
    private SecurityUserRepository securityUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Flyway V1 applied on PostgreSQL 16: four tables and the CXACAIX substitute index exist")
    void flywaySchemaApplied() {
        Integer tables = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = current_schema()"
                        + " and table_name in ('accounts','customers','card_xrefs','users')", Integer.class);
        assertEquals(4, tables);
        Integer index = jdbcTemplate.queryForObject(
                "select count(*) from pg_indexes where indexname = 'idx_card_xrefs_acct_id'", Integer.class);
        assertEquals(1, index);
        String dateType = jdbcTemplate.queryForObject(
                "select data_type from information_schema.columns where table_name='accounts'"
                        + " and column_name='acct_open_date'", String.class);
        assertEquals("date", dateType);
        String moneyType = jdbcTemplate.queryForObject(
                "select data_type || '(' || numeric_precision || ',' || numeric_scale || ')'"
                        + " from information_schema.columns where table_name='accounts' and column_name='acct_curr_bal'",
                String.class);
        assertEquals("numeric(19,2)", moneyType);
    }

    @Test
    @DisplayName("B-0001 ACCTDAT: READ by ACCT-ID (COACTVWC.cbl:776-784) -> account row with exact money/date values")
    void b0001_accountReadByKey() {
        Account account = accountRepository.findById(1L).orElseThrow();

        assertEquals("Y", account.getAcctActiveStatus());
        assertEquals(new BigDecimal("194.00"), account.getAcctCurrBal());
        assertEquals(new BigDecimal("2020.00"), account.getAcctCreditLimit());
        assertEquals(LocalDate.of(2014, 11, 20), account.getAcctOpenDate());
        assertEquals("A000000000", account.getAcctGroupId());
        assertTrue(accountRepository.findById(123L).isEmpty(), "RESP 13 NOTFND equivalent");
    }

    @Test
    @DisplayName("B-0002 CUSTDAT: READ by CUST-ID (COACTVWC.cbl:826-834) -> customer row")
    void b0002_customerReadByKey() {
        Customer customer = customerRepository.findById(1L).orElseThrow();

        assertEquals("Immanuel", customer.getCustFirstName());
        assertEquals("Kessler", customer.getCustLastName());
        assertEquals(20973888L, customer.getCustSsn());
        assertEquals(LocalDate.of(1961, 6, 8), customer.getCustDobYyyyMmDd());
        assertEquals(274, customer.getCustFicoCreditScore());
        assertTrue(customerRepository.findById(999L).isEmpty());
    }

    @Test
    @DisplayName("B-0006 USRSEC: READ by SEC-USR-ID (COSGN00C.cbl:211-219) -> user row, hash empty until upgrade-on-login")
    void b0006_userReadByKey() {
        SecurityUser admin = securityUserRepository.findById("ADMIN001").orElseThrow();
        SecurityUser user = securityUserRepository.findById("USER0001").orElseThrow();

        assertEquals("A", admin.getSecUsrType());
        assertEquals("U", user.getSecUsrType());
        assertNull(admin.getSecUsrPwdHash());
        assertEquals("PASSWORD", admin.getSecUsrPwdLegacy());
        assertTrue(securityUserRepository.findById("NOBODY").isEmpty());
    }

    @Test
    @DisplayName("B-0007 CXACAIX: READ by XREF-ACCT-ID (COACTVWC.cbl:727-735) -> xref row; NOTFND when no card")
    void b0007_cardXrefReadByAccountId() {
        Optional<CardXref> xref = cardXrefRepository.findFirstByXrefAcctIdOrderByXrefCardNumberAsc(50L);

        assertTrue(xref.isPresent());
        assertEquals("0500024453765740", xref.get().getXrefCardNumber());
        assertEquals(50L, xref.get().getXrefCustId());
        assertTrue(cardXrefRepository.findFirstByXrefAcctIdOrderByXrefCardNumberAsc(1L).isEmpty(),
                "account 1 has no xref in the fixture -> E-09 path");
    }

    @Test
    @DisplayName("Q-04: account with two cards -> the LOWEST card number wins (deterministic AIX first-record substitute)")
    void q04_lowestCardNumberWinsForMultiCardAccount() {
        CardXref xref = cardXrefRepository.findFirstByXrefAcctIdOrderByXrefCardNumberAsc(99999999999L).orElseThrow();

        assertEquals("4000000000000001", xref.getXrefCardNumber());
        assertEquals(2, cardXrefRepository.findAll().stream()
                .filter(x -> Long.valueOf(99999999999L).equals(x.getXrefAcctId())).count());
    }
}
