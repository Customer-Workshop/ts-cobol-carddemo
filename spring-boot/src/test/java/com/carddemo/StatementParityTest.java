package com.carddemo;

import com.carddemo.batch.BatchJobService;
import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.Customer;
import com.carddemo.model.Transaction;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import com.carddemo.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CBSTM03A/CBSTM03B + CREASTMT job-level parity — FR-S16-01..15. Expected bytes
 * are hand-derived from app/cbl/CBSTM03A.CBL (ST-LINE0..15 at :86-146, HTML-Lxx
 * at :148-233) and app/jcl/CREASTMT.JCL, not from the Java implementation.
 */
@SpringBootTest
@SpringBatchTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:s16stmt;DB_CLOSE_DELAY=-1",
        "carddemo.seed.enabled=true",
        "carddemo.seed.data-dir=classpath:seed",
        "carddemo.seed.acctdata-group-id-in-zip-slot=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "carddemo.batch.output-dir=target/test-batch-s16"})
class StatementParityTest {
    private static final Path OUTPUT = Path.of("target/test-batch-s16");
    private static final String START_LINE = "*".repeat(31) + "START OF STATEMENT" + "*".repeat(31);
    private static final String END_LINE = "*".repeat(32) + "END OF STATEMENT" + "*".repeat(32);
    private static final String DASHES = "-".repeat(80);
    private static final String HTML_DOC = "<!DOCTYPE html>";
    private static final String HTML_END = "</html>";

    private static final String CARD_A = "6000000000000002"; // zero transactions
    private static final String CARD_B = "6000000000000009"; // three transactions
    private static final long CUST_A = 91L;
    private static final long ACCT_A = 91L;
    private static final long CUST_B = 92L;
    private static final long ACCT_B = 92L;

    @Autowired JobLauncherTestUtils jobs;
    @Autowired Map<String, Job> jobBeans;
    @Autowired CardXrefRepository xrefs;
    @Autowired CustomerRepository customers;
    @Autowired AccountRepository accounts;
    @Autowired TransactionRepository transactions;
    @Autowired MockMvc mockMvc;

    // --- fixtures -----------------------------------------------------------

    private void statementFixtures() {
        if (xrefs.existsById(CARD_A)) {
            return;
        }
        saveCustomer(CUST_A, "Ada", "M", "Byron", "1 Main St", "Suite 5",
                "Boston", "MA", "USA", "02108", 800);
        saveAccount(ACCT_A, "194.00");
        saveXref(CARD_A, CUST_A, ACCT_A);

        saveCustomer(CUST_B, "Grace", "", "Hopper", "2 Elm Ave", "Floor 3",
                "Cambridge", "MA", "USA", "02139", 740);
        saveAccount(ACCT_B, "1000.00");
        saveXref(CARD_B, CUST_B, ACCT_B);
        // Scrambled insert order — TRXFL order is (card, tran-id), FR-S16-13.
        saveTransaction("0000000000000109", CARD_B, "Refund issued", "-2.50");
        saveTransaction("0000000000000101", CARD_B, "Grocery run", "10.00");
        saveTransaction("0000000000000105", CARD_B, "Coffee", "1.00");
    }

    private void saveCustomer(long id, String first, String middle, String last,
                              String addr1, String addr2, String addr3, String state,
                              String country, String zip, int fico) {
        Customer customer = new Customer();
        customer.setCustId(id);
        customer.setCustFirstName(first);
        customer.setCustMiddleName(middle);
        customer.setCustLastName(last);
        customer.setCustAddrLine1(addr1);
        customer.setCustAddrLine2(addr2);
        customer.setCustAddrLine3(addr3);
        customer.setCustAddrStateCode(state);
        customer.setCustAddrCountryCode(country);
        customer.setCustAddrZip(zip);
        customer.setCustFicoCreditScore(fico);
        customers.save(customer);
    }

    private void saveAccount(long id, String balance) {
        Account account = new Account();
        account.setAcctId(id);
        account.setAcctActiveStatus("Y");
        account.setAcctCurrBal(new BigDecimal(balance));
        accounts.save(account);
    }

    private void saveXref(String card, long cust, long acct) {
        CardXref xref = new CardXref();
        xref.setXrefCardNumber(card);
        xref.setXrefCustId(cust);
        xref.setXrefAcctId(acct);
        xrefs.save(xref);
    }

    private void saveTransaction(String id, String card, String description, String amount) {
        Transaction transaction = new Transaction();
        transaction.setTranId(id);
        transaction.setTranCardNumber(card);
        transaction.setTranDescription(description);
        transaction.setTranAmount(new BigDecimal(amount));
        transactions.save(transaction);
    }

    // --- job/output helpers ---------------------------------------------------

    private ExitStatus runJob(String jobName, JobParametersBuilder params) throws Exception {
        jobs.setJob(jobBeans.get(jobName));
        return jobs.launchJob(params.addLong("run.nano", System.nanoTime()).toJobParameters())
                .getExitStatus();
    }

    private ExitStatus runStatements() throws Exception {
        return runJob("cbstm03Job", new JobParametersBuilder());
    }

    private List<String> psLines() throws Exception {
        Path file = OUTPUT.resolve("STATEMNT.PS");
        assertTrue(Files.exists(file), "STATEMNT.PS must exist after the run");
        return Files.readAllLines(file, StandardCharsets.ISO_8859_1);
    }

    private List<String> htmlLines() throws Exception {
        Path file = OUTPUT.resolve("STATEMNT.HTML");
        assertTrue(Files.exists(file), "STATEMNT.HTML must exist after the run");
        return Files.readAllLines(file, StandardCharsets.ISO_8859_1);
    }

    /** Splits the PS file into statement blocks (START..END OF STATEMENT). */
    private List<List<String>> psStatements(List<String> lines) {
        List<List<String>> blocks = new ArrayList<>();
        List<String> current = null;
        for (String line : lines) {
            if (line.equals(START_LINE)) {
                current = new ArrayList<>();
                blocks.add(current);
            }
            if (current != null) {
                current.add(line);
            }
            if (line.equals(END_LINE)) {
                current = null;
            }
        }
        return blocks;
    }

    private List<List<String>> htmlStatements(List<String> lines) {
        List<List<String>> blocks = new ArrayList<>();
        List<String> current = null;
        for (String line : lines) {
            if (line.stripTrailing().equals(HTML_DOC)) {
                current = new ArrayList<>();
                blocks.add(current);
            }
            if (current != null) {
                current.add(line);
            }
            if (line.stripTrailing().equals(HTML_END)) {
                current = null;
            }
        }
        return blocks;
    }

    private List<String> findStatement(List<List<String>> blocks, long acctId) {
        String marker = "%011d".formatted(acctId);
        return blocks.stream()
                .filter(block -> block.stream().anyMatch(line -> line.contains(marker)))
                .findFirst().orElseThrow(() -> new AssertionError("no statement for acct " + acctId));
    }

    private MockHttpSession signon(String userId, String password) throws Exception {
        var result = mockMvc.perform(post("/api/auth/signon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    // --- FR-S16-01/02/10: xref sweep — one statement per card, in card order --

    @Test
    void frS16_01_02_10_oneStatementPerXrefRowInCardOrderIncludingZeroTxnCards() throws Exception {
        statementFixtures();

        assertEquals("COMPLETED", runStatements().getExitCode());

        List<List<String>> blocks = psStatements(psLines());
        assertEquals(xrefs.count(), blocks.size(), "one statement per card_xrefs row");

        // FR-S16-10: file order follows XREFFILE (KSDS card-number key) order.
        List<String> expectedCardOrder = xrefs.findAll().stream()
                .map(CardXref::getXrefCardNumber).sorted().toList();
        List<String> fileAcctOrder = psLines().stream()
                .filter(line -> line.startsWith("Account ID         :"))
                .map(line -> line.substring(20, 40).trim()).toList();
        List<String> expectedAcctOrder = expectedCardOrder.stream()
                .map(card -> "%011d".formatted(xrefs.findById(card).orElseThrow().getXrefAcctId()))
                .toList();
        assertEquals(expectedAcctOrder, fileAcctOrder);

        // FR-S16-02: the zero-transaction card still emits a complete statement.
        List<String> zeroTxn = findStatement(blocks, ACCT_A);
        assertTrue(zeroTxn.stream().noneMatch(line -> line.startsWith("00000000000")
                        && line.charAt(16) == ' ' && line.endsWith("-")),
                "card A has no ST-LINE14 detail lines");
        assertEquals(DASHES, zeroTxn.get(zeroTxn.size() - 3),
                "dash line immediately after the empty detail section");
        assertEquals("Total EXP:" + " ".repeat(56) + "$" + "         .00 ",
                zeroTxn.get(zeroTxn.size() - 2));
    }

    // --- FR-S16-04/05/06: fixed-layout header, basic details, summary ---------

    @Test
    void frS16_04_05_06_psHeaderBlockMatchesStLineLayoutsByteForByte() throws Exception {
        statementFixtures();
        runStatements();

        List<String> block = findStatement(psStatements(psLines()), ACCT_A);
        assertEquals(19, block.size(), "header + trailer for a zero-txn card");
        assertEquals(START_LINE, block.get(0));
        assertEquals("Ada M Byron" + " ".repeat(80 - "Ada M Byron".length()), block.get(1));
        assertEquals("1 Main St" + " ".repeat(80 - "1 Main St".length()), block.get(2));
        assertEquals("Suite 5" + " ".repeat(80 - "Suite 5".length()), block.get(3));
        assertEquals("Boston MA USA 02108" + " ".repeat(80 - "Boston MA USA 02108".length()),
                block.get(4));
        assertEquals(DASHES, block.get(5));
        assertEquals(" ".repeat(33) + "Basic Details " + " ".repeat(33), block.get(6));
        assertEquals(DASHES, block.get(7));
        assertEquals("Account ID         :" + "00000000091" + " ".repeat(49), block.get(8));
        assertEquals("Current Balance    :" + "000000194.00 " + " ".repeat(47), block.get(9));
        assertEquals("FICO Score         :" + "800" + " ".repeat(57), block.get(10));
        assertEquals(DASHES, block.get(11));
        assertEquals(" ".repeat(30) + "TRANSACTION SUMMARY " + " ".repeat(30), block.get(12));
        assertEquals(DASHES, block.get(13));
        assertEquals("Tran ID         " + "Tran Details" + " ".repeat(39) + "  Tran Amount",
                block.get(14));
        assertEquals(DASHES, block.get(15));
        assertEquals(DASHES, block.get(16));
        assertEquals("Total EXP:" + " ".repeat(56) + "$" + "         .00 ", block.get(17));
        assertEquals(END_LINE, block.get(18));

        for (String line : psLines()) {
            assertEquals(80, line.length(), "every STATEMNT.PS record is 80 bytes");
        }
        for (String line : htmlLines()) {
            assertEquals(100, line.length(), "every STATEMNT.HTML record is 100 bytes");
        }
    }

    // --- FR-S16-07/08/13: detail lines, running total, TRXFL (card,id) order --

    @Test
    void frS16_07_08_13_detailLinesTotalAndCardIdSortOrder() throws Exception {
        statementFixtures();
        runStatements();

        List<String> block = findStatement(psStatements(psLines()), ACCT_B);
        // Lines 16..18 are the three ST-LINE14 details in tran-id order (the
        // inserts were scrambled: 109, 101, 105 -> emitted 101, 105, 109).
        assertEquals("0000000000000101" + " " + "Grocery run" + " ".repeat(38)
                + "$" + "       10.00 ", block.get(16));
        assertEquals("0000000000000105" + " " + "Coffee" + " ".repeat(43)
                + "$" + "        1.00 ", block.get(17));
        assertEquals("0000000000000109" + " " + "Refund issued" + " ".repeat(36)
                + "$" + "        2.50-", block.get(18));
        assertEquals(DASHES, block.get(19));
        assertEquals("Total EXP:" + " ".repeat(56) + "$" + "        8.50 ", block.get(20));
        assertEquals(END_LINE, block.get(21));
    }

    // --- FR-S16-09: HTML literal skeleton -------------------------------------

    @Test
    void frS16_09_htmlBlockMatchesTheLiteralSkeleton() throws Exception {
        statementFixtures();
        runStatements();

        List<String> raw = htmlStatements(htmlLines()).stream()
                .filter(block -> block.stream().anyMatch(line -> line.contains("00000000091")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no HTML statement for acct 91"))
                .stream().map(String::stripTrailing).toList();

        List<String> expected = List.of(
                "<!DOCTYPE html>",
                "<html lang=\"en\">",
                "<head>",
                "<meta charset=\"utf-8\">",
                "<title>HTML Table Layout</title>",
                "</head>",
                "<body style=\"margin:0px;\">",
                "<table  align=\"center\" frame=\"box\" style=\"width:70%; font:12px Segoe UI,sans-serif;\">",
                "<tr>",
                "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#1d1d96b3;\">",
                "<h3>Statement for Account Number: 00000000091         </h3>",
                "</td>",
                "</tr>",
                "<tr>",
                "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#FFAF33;\">",
                "<p style=\"font-size:16px\">Bank of XYZ</p>",
                "<p>410 Terry Ave N</p>",
                "<p>Seattle WA 99999</p>",
                "</td>",
                "</tr>",
                "<tr>",
                "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#f2f2f2;\">",
                "<p style=\"font-size:16px\">Ada M Byron  </p>",
                "<p>1 Main St  </p>",
                "<p>Suite 5  </p>",
                "<p>Boston MA USA 02108  </p>",
                "</td>",
                "</tr>",
                "<tr>",
                "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#33FFD1; text-align:center;\">",
                "<p style=\"font-size:16px\">Basic Details</p>",
                "</td>",
                "</tr>",
                "<tr>",
                "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#f2f2f2;\">",
                "<p>Account ID         : 00000000091         </p>",
                "<p>Current Balance    : 000000194.00 </p>",
                "<p>FICO Score         : 800                 </p>",
                "</td>",
                "</tr>",
                "<tr>",
                "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#33FFD1; text-align:center;\">",
                "<p style=\"font-size:16px\">Transaction Summary</p>",
                "</td>",
                "</tr>",
                "<tr>",
                "<td style=\"width:25%; padding:0px 5px; background-color:#33FF5E; text-align:left;\">",
                "<p style=\"font-size:16px\">Tran ID</p>",
                "</td>",
                "<td style=\"width:55%; padding:0px 5px; background-color:#33FF5E; text-align:left;\">",
                "<p style=\"font-size:16px\">Tran Details</p>",
                "</td>",
                "<td style=\"width:20%; padding:0px 5px; background-color:#33FF5E; text-align:right;\">",
                "<p style=\"font-size:16px\">Amount</p>",
                "</td>",
                "</tr>",
                "<tr>",
                "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#1d1d96b3;\">",
                "<h3>End of Statement</h3>",
                "</td>",
                "</tr>",
                "</table>",
                "</body>",
                "</html>");
        assertEquals(expected, raw);
    }

    @Test
    void frS16_09_htmlDetailRowsUseTheF2f2f2Columns() throws Exception {
        statementFixtures();
        runStatements();

        List<String> block = findStatement(htmlStatements(htmlLines()), ACCT_B);
        String leftTd = "<td style=\"width:25%; padding:0px 5px; "
                + "background-color:#f2f2f2; text-align:left;\">";
        int tranRow = block.indexOf(leftTd + " ".repeat(100 - leftTd.length()));
        assertTrue(tranRow > 0);
        String idCell = "<p>0000000000000101</p>";
        assertEquals(idCell + " ".repeat(100 - idCell.length()), block.get(tranRow + 1));
        assertTrue(block.get(tranRow + 4).startsWith(
                "<p>" + "Grocery run" + " ".repeat(38) + "</p>"));
        assertTrue(block.get(tranRow + 7).startsWith("<p>" + "       10.00 " + "</p>"));
    }

    // --- FR-S16-11: the 51x10 preload cap is dropped --------------------------

    @Test
    void frS16_11_streamsMoreThan51CardsWithoutLoss() throws Exception {
        for (int i = 0; i < 55; i++) {
            long id = 1000 + i;
            saveCustomer(id, "Bulk", "T", "Customer" + i, "Addr" + i, "Line2",
                    "Town", "MA", "USA", "02108", 700);
            saveAccount(id, "1.00");
            saveXref("%016d".formatted(9000000000000000L + i), id, id);
        }

        assertEquals("COMPLETED", runStatements().getExitCode());
        long count = psLines().stream().filter(START_LINE::equals).count();
        assertEquals(xrefs.count(), count);
        assertTrue(count >= 55, "expected at least the 55 added cards, got " + count);
    }

    // --- FR-S16-12: keyed-read miss fails the step ----------------------------

    @Test
    void frS16_12_missingCustomerRecordFailsTheStep() throws Exception {
        CardXref orphan = new CardXref();
        orphan.setXrefCardNumber("9999999999999999");
        orphan.setXrefCustId(424242L); // no such customer — READ-K miss -> abend
        orphan.setXrefAcctId(424242L);
        xrefs.save(orphan);
        try {
            assertNotEquals("COMPLETED", runStatements().getExitCode());
        } finally {
            xrefs.delete(orphan);
        }
    }

    // --- FR-S16-14: documented statement chain + admin launch order -----------

    @Test
    void frS16_14_chainOrderIsDocumentedAndLaunchableThroughTheAdminEndpoint() throws Exception {
        statementFixtures();
        // S16-B5/B7: CLOSEFIL->CREASTMT->TXT2PDF1->WAITSTEP->OPENFIL maps to
        // cbstm03Job -> waitStepJob (close/open are no-ops, PDF dropped).
        assertEquals(List.of("cbstm03Job", "waitStepJob"), BatchJobService.STATEMENT_CHAIN);

        MockHttpSession admin = signon("ADMIN001", "PASSWORD");
        mockMvc.perform(post("/api/admin/jobs/cbstm03Job").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mockMvc.perform(post("/api/admin/jobs/waitStepJob")
                        .param("waitCentiseconds", "1").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    // --- FR-S16-15 + S16-B3: vestigial parm, rerun overwrite -------------------

    @Test
    void frS16_15_parmHasNoEffectAndRerunOverwritesOutputs() throws Exception {
        statementFixtures();

        runJob("cbstm03Job", new JobParametersBuilder().addString("parm", "12"));
        byte[] firstPs = Files.readAllBytes(OUTPUT.resolve("STATEMNT.PS"));
        byte[] firstHtml = Files.readAllBytes(OUTPUT.resolve("STATEMNT.HTML"));

        // FR-S16-15: PARM='12' is never read — a different parm must not change
        // the output. S16-B3: rerun overwrites (no append, no GDG generation).
        runJob("cbstm03Job", new JobParametersBuilder().addString("parm", "99"));
        assertEquals(new String(firstPs, StandardCharsets.ISO_8859_1),
                Files.readString(OUTPUT.resolve("STATEMNT.PS"), StandardCharsets.ISO_8859_1));
        assertEquals(new String(firstHtml, StandardCharsets.ISO_8859_1),
                Files.readString(OUTPUT.resolve("STATEMNT.HTML"), StandardCharsets.ISO_8859_1));
    }
}
