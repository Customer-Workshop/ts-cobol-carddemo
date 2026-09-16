package com.carddemo;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.carddemo.batch.BatchJobService;
import com.carddemo.model.Account;
import com.carddemo.model.Card;
import com.carddemo.model.CardXref;
import com.carddemo.model.Customer;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Job-level parity tests for the S-17 read/verify jobs (S17-FR-01..12):
 * READACCT/CBACT01C, READCARD/CBACT02C, READCUST/CBCUS01C, READXREF/CBACT03C.
 * Expected bytes and log lines are derived from the COBOL sources and
 * copybook layouts, not from the implementation.
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = {
        "carddemo.seed.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:readverify;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "carddemo.batch.output-dir=target/test-readverify-output"
})
class ReadVerifyJobTest {

    private static final Path OUTPUT = Path.of("target/test-readverify-output");
    private static final String NL = System.lineSeparator();

    @Autowired
    private JobLauncherTestUtils jobs;

    @Autowired
    private Map<String, Job> jobBeans;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private CardRepository cards;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private CardXrefRepository xrefs;

    private Logger sysout;
    private ListAppender<ILoggingEvent> sysoutEvents;
    private JobExecution execution;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(OUTPUT);
        try (var paths = Files.list(OUTPUT)) {
            paths.forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
        accounts.deleteAll();
        cards.deleteAll();
        customers.deleteAll();
        xrefs.deleteAll();
        sysout = (Logger) LoggerFactory.getLogger("carddemo.sysout");
        sysoutEvents = new ListAppender<>();
        sysoutEvents.start();
        sysout.detachAppender("readverify");
        sysout.addAppender(sysoutEvents);
    }

    private void launch(String jobName) throws Exception {
        jobs.setJob(jobBeans.get(jobName));
        execution = jobs.launchJob(new JobParametersBuilder()
                .addLong("run", System.nanoTime()).toJobParameters());
    }

    private List<String> sysoutLines() {
        return sysoutEvents.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static Account account(long id, String balance, String creditLimit,
                                   String cashLimit, String cycCredit, String cycDebit,
                                   LocalDate reissue) {
        Account account = new Account();
        account.setAcctId(id);
        account.setAcctActiveStatus("Y");
        account.setAcctCurrBal(new BigDecimal(balance));
        account.setAcctCreditLimit(new BigDecimal(creditLimit));
        account.setAcctCashCreditLimit(new BigDecimal(cashLimit));
        account.setAcctOpenDate(LocalDate.of(2020, 1, 15));
        account.setAcctExpirationDate(LocalDate.of(2030, 1, 14));
        account.setAcctReissueDate(reissue);
        account.setAcctCurrCycCredit(new BigDecimal(cycCredit));
        account.setAcctCurrCycDebit(new BigDecimal(cycDebit));
        account.setAcctAddrZip("12345-6789");
        account.setAcctGroupId("GRP001");
        return account;
    }

    // S17-FR-03..06: PSCOMP/ARRYPS/VBPS byte-faithful output + SYSOUT dump.
    @Test
    void readacctWritesByteFaithfulFilesAndDumps() throws Exception {
        // acct 101: non-zero debit -> field keeps the program-start record
        // area (binary zeros: FD initial content, MEDIUM confidence quirk).
        accounts.save(account(101L, "1234.56", "5000.00", "1000.00", "10.00", "500.00",
                LocalDate.of(2030, 12, 31)));
        // acct 102: zero debit -> 2525.00 substitute (CBACT01C.cbl:236-238).
        accounts.save(account(102L, "0.00", "0.00", "0.00", "0.00", "0.00",
                LocalDate.of(2025, 6, 30)));
        // acct 103: non-zero debit -> retains previous record's 2525.00 bytes.
        accounts.save(account(103L, "1.00", "2.00", "3.00", "4.00", "700.00",
                LocalDate.of(2026, 3, 1)));

        launch("readacctJob");
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        // ---- PSCOMP: FB LRECL=107 (fixed stride; packed bytes can hold
        // raw 0x0A/0x0D, so split-on-newline is invalid) ----
        byte[] pscompBytes = Files.readAllBytes(OUTPUT.resolve("ACCTDATA.PSCOMP"));
        int pscompStride = 107 + NL.length();
        assertEquals(3 * pscompStride, pscompBytes.length);
        String pscomp = new String(pscompBytes, StandardCharsets.ISO_8859_1);
        String line1 = pscomp.substring(0, 107);
        assertEquals("00000000101", line1.substring(0, 11));       // ACCT-ID
        assertEquals("Y", line1.substring(11, 12));               // STATUS
        assertEquals("00000012345F", line1.substring(12, 24));    // CURR-BAL 1234.56 overpunch
        assertEquals("00000050000{", line1.substring(24, 36));    // CREDIT-LIMIT 5000.00
        assertEquals("2020-01-15", line1.substring(48, 58));      // OPEN-DATE
        assertEquals("2030-01-14", line1.substring(58, 68));      // EXPIRAION-DATE
        // REISSUE = COBDATFT '2'->'2' output, first 10 bytes (YYYYMMDD + 2 spaces)
        assertEquals("20301231  ", line1.substring(68, 78));
        assertEquals("00000000100{", line1.substring(78, 90));    // CYC-CREDIT 10.00
        // debit nonzero: initial record area = 7 binary zero bytes
        byte[] debit1 = new byte[7];
        System.arraycopy(pscompBytes, 90, debit1, 0, 7);
        assertArrayEquals(new byte[7], debit1);
        assertEquals("GRP001    ", line1.substring(97, 107));

        // acct 102 zero debit -> packed 2525.00 = 00 00 00 02 52 50 0C
        byte[] expected2525 = {0, 0, 0, 0x02, 0x52, 0x50, 0x0C};
        byte[] debit2 = new byte[7];
        System.arraycopy(pscompBytes, pscompStride + 90, debit2, 0, 7);
        assertArrayEquals(expected2525, debit2);

        // acct 103 nonzero debit -> stale carryover = previous record's 2525.00
        byte[] debit3 = new byte[7];
        System.arraycopy(pscompBytes, 2 * pscompStride + 90, debit3, 0, 7);
        assertArrayEquals(expected2525, debit3);

        // ---- ARRYPS: FB LRECL=110 ----
        byte[] arrypsBytes = Files.readAllBytes(OUTPUT.resolve("ACCTDATA.ARRYPS"));
        assertEquals(3 * (110 + NL.length()), arrypsBytes.length);
        String arr1 = new String(arrypsBytes, 0, 110, StandardCharsets.ISO_8859_1);
        assertEquals("00000000101", arr1.substring(0, 11));
        // occurs 1: bal = curr-bal zoned, deb = packed 1005.00
        assertEquals("00000012345F", arr1.substring(11, 23));
        assertEquals(packed("1005.00"), arr1.substring(23, 30));
        // occurs 3: bal -1025.00, deb -2500.00
        assertEquals("00000010250}", arr1.substring(49, 61));
        assertEquals(packed("-2500.00"), arr1.substring(61, 68));
        // occurs 4-5 stay INITIALIZE zeros; last 4 bytes are ARR-FILLER spaces
        assertEquals("    ", arr1.substring(106, 110));

        // ---- VBPS: variable-length 12- and 39-byte records ----
        List<String> vbps = Files.readAllLines(OUTPUT.resolve("ACCTDATA.VBPS"),
                StandardCharsets.ISO_8859_1);
        assertEquals(6, vbps.size());
        for (int i = 0; i < vbps.size(); i += 2) {
            assertEquals(12, vbps.get(i).length());
            assertEquals(39, vbps.get(i + 1).length());
        }
        assertEquals("00000000101" + "Y", vbps.get(0));
        assertEquals("00000000101" + "00000012345F" + "00000050000{" + "2030", vbps.get(1));

        // ---- SYSOUT: labelled dump, VBRC displays, raw record ----
        List<String> log = sysoutLines();
        assertEquals("START OF EXECUTION OF PROGRAM CBACT01C", log.get(0));
        assertTrue(log.contains("ACCT-ID                 :00000000101"));
        assertTrue(log.contains("ACCT-CURR-BAL           :00000012345F"));
        assertTrue(log.contains("ACCT-GROUP-ID           :GRP001    "));
        assertTrue(log.contains("-".repeat(49)));
        assertTrue(log.contains("VBRC-REC1:00000000101Y"));
        assertTrue(log.contains("VBRC-REC2:" + vbps.get(1)));
        int first101 = log.indexOf("START OF EXECUTION OF PROGRAM CBACT01C");
        assertTrue(log.subList(first101, log.size()).stream().anyMatch(
                line -> line.startsWith("00000000101Y00000012345F")));
        assertEquals("END OF EXECUTION OF PROGRAM CBACT01C", log.get(log.size() - 1));
    }

    private static String packed(String digits) {
        boolean negative = digits.startsWith("-");
        String abs = negative ? digits.substring(1) : digits;
        String scaled = new BigDecimal(abs).movePointRight(2).toPlainString();
        String hex = "0".repeat(12 - scaled.length()) + scaled + (negative ? "D" : "C");
        if (hex.length() % 2 == 1) {
            hex = "0" + hex;
        }
        StringBuilder packed = new StringBuilder(7);
        for (int i = 0; i < hex.length(); i += 2) {
            packed.append((char) Integer.parseInt(hex.substring(i, i + 2), 16));
        }
        return packed.toString();
    }

    // S17-FR-07: READCARD displays each CARD-RECORD once (in-read DISPLAY is
    // commented out at CBACT02C.cbl:96).
    @Test
    void readcardDisplaysEachRecordOnce() throws Exception {
        Card card = new Card();
        card.setCardNumber("4111111111111111");
        card.setCardAcctId(101L);
        card.setCardCvvCode(123);
        card.setCardEmbossedName("JOHN Q PUBLIC");
        card.setCardExpirationDate(LocalDate.of(2028, 12, 31));
        card.setCardActiveStatus("Y");
        cards.save(card);

        launch("readcardJob");
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        String expected = "4111111111111111" + "00000000101" + "123"
                + "JOHN Q PUBLIC" + " ".repeat(37) + "2028-12-31" + "Y" + " ".repeat(59);
        List<String> matches = sysoutLines().stream()
                .filter(line -> line.equals(expected)).toList();
        assertEquals(1, matches.size());
    }

    // S17-FR-08: READCUST double-DISPLAY quirk (CBCUS01C.cbl:78,:96).
    @Test
    void readcustDisplaysEachRecordTwice() throws Exception {
        Customer customer = new Customer();
        customer.setCustId(42L);
        customer.setCustFirstName("JANE");
        customer.setCustLastName("DOE");
        customer.setCustAddrStateCode("NJ");
        customer.setCustAddrCountryCode("USA");
        customer.setCustDob(LocalDate.of(1990, 5, 17));
        customers.save(customer);

        launch("readcustJob");
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        String expected = "000000042" + "JANE" + " ".repeat(21) + " ".repeat(25)
                + "DOE" + " ".repeat(22) + " ".repeat(150) + "NJ" + "USA"
                + " ".repeat(10) + " ".repeat(30) + " ".repeat(9) + " ".repeat(20)
                + "1990-05-17" + " ".repeat(10) + " " + " ".repeat(3) + " ".repeat(168);
        List<String> log = sysoutLines();
        assertEquals("START OF EXECUTION OF PROGRAM CBCUS01C", log.get(0));
        int first = log.indexOf(expected);
        assertTrue(first > 0);
        assertEquals(expected, log.get(first + 1));
    }

    // S17-FR-09: READXREF double-DISPLAY quirk (CBACT03C.cbl:78,:96).
    @Test
    void readxrefDisplaysEachRecordTwice() throws Exception {
        CardXref xref = new CardXref();
        xref.setXrefCardNumber("4222222222222222");
        xref.setXrefCustId(42L);
        xref.setXrefAcctId(101L);
        xrefs.save(xref);

        launch("readxrefJob");
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        String expected = "4222222222222222" + "000000042" + "00000000101" + " ".repeat(14);
        List<String> log = sysoutLines();
        int first = log.indexOf(expected);
        assertTrue(first > 0);
        assertEquals(expected, log.get(first + 1));
    }

    // S17-FR-10/11: scheduler chain order documented (not enforced).
    @Test
    void jobOrderDocumentsCa7Schid030Chain() {
        assertEquals(List.of("readacctJob", "readcardJob", "readcustJob", "readxrefJob"),
                BatchJobService.JOB_ORDER.get("SCHID-030-READ-VERIFY"));
        assertTrue(jobBeans.keySet().containsAll(
                List.of("readacctJob", "readcardJob", "readcustJob", "readxrefJob")));
    }

    // S17-FR-12: any step failure maps to the CEE3ABD 999 equivalent.
    @Test
    void stepFailureAbendsWith999() throws Exception {
        // A negative id cannot render in PIC 9(11): forces a step failure.
        accounts.save(account(-5L, "1.00", "1.00", "1.00", "1.00", "1.00",
                LocalDate.of(2030, 1, 1)));

        launch("readacctJob");

        assertEquals(BatchStatus.FAILED, execution.getStatus());
        assertEquals("999", execution.getExitStatus().getExitCode());
        List<String> log = sysoutLines();
        assertTrue(log.contains("ABENDING PROGRAM"));
        assertEquals("999", execution.getStepExecutions().iterator().next()
                .getExitStatus().getExitCode());
    }
}
