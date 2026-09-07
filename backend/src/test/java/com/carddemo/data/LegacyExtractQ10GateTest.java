package com.carddemo.data;

import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.Customer;
import com.carddemo.model.SecurityUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Q-10 date gate (D-0035) and importer parsing evidence over the FULL legacy extracts under app/data
 * (read-only). Skipped when the repository data directory is not present (e.g. backend built alone).
 */
@EnabledIf("dataDirPresent")
class LegacyExtractQ10GateTest {

    static final Path DATA_DIR = Path.of("..", "app", "data");

    static boolean dataDirPresent() {
        return Files.isDirectory(DATA_DIR.resolve("ASCII")) && Files.isDirectory(DATA_DIR.resolve("EBCDIC"));
    }

    private static List<String> ascii(String name, int recordLength) throws IOException {
        return Files.readAllLines(DATA_DIR.resolve("ASCII").resolve(name), StandardCharsets.US_ASCII).stream()
                .filter(line -> !line.isEmpty())
                .map(line -> line.length() >= recordLength ? line.substring(0, recordLength)
                        : line + " ".repeat(recordLength - line.length()))
                .toList();
    }

    @Test
    @DisplayName("Q-10: every ACCT-OPEN-DATE / ACCT-EXPIRAION-DATE / ACCT-REISSUE-DATE / CUST-DOB-YYYY-MM-DD value is YYYY-MM-DD")
    void q10_allDateColumnsAreIsoDates() throws IOException {
        DateColumnGate gate = new DateColumnGate();
        LegacyExtractParser parser = new LegacyExtractParser(gate, true);

        List<Account> accounts = parser.parseAccounts(
                ascii("acctdata.txt", LegacyExtractParser.ACCOUNT_RECORD_LENGTH), "acctdata.txt");
        List<Customer> customers = parser.parseCustomers(
                ascii("custdata.txt", LegacyExtractParser.CUSTOMER_RECORD_LENGTH), "custdata.txt");

        System.out.println("Q-10 gate: rows accounts=" + accounts.size() + " customers=" + customers.size()
                + " date values checked=" + gate.valuesChecked() + " offenders=" + gate.offenders());
        assertEquals(accounts.size() * 3 + customers.size(), gate.valuesChecked());
        assertTrue(gate.passed(), "Q-10 offenders: " + gate.offenders());
        assertEquals(50, accounts.size());
        assertEquals(50, customers.size());
    }

    @Test
    @DisplayName("acctdata.txt row 1 decodes per CVACT01Y.cpy (overpunch money, dates, group-id-in-zip-slot quirk)")
    void accountRowOneDecodes() throws IOException {
        LegacyExtractParser parser = new LegacyExtractParser(new DateColumnGate(), true);
        Account first = parser.parseAccounts(
                ascii("acctdata.txt", LegacyExtractParser.ACCOUNT_RECORD_LENGTH), "acctdata.txt").get(0);

        assertEquals(1L, first.getAcctId());
        assertEquals("Y", first.getAcctActiveStatus());
        assertEquals(new BigDecimal("194.00"), first.getAcctCurrBal());
        assertEquals(new BigDecimal("2020.00"), first.getAcctCreditLimit());
        assertEquals(new BigDecimal("1020.00"), first.getAcctCashCreditLimit());
        assertEquals(LocalDate.of(2014, 11, 20), first.getAcctOpenDate());
        assertEquals(LocalDate.of(2025, 5, 20), first.getAcctExpiraionDate());
        assertEquals(new BigDecimal("0.00"), first.getAcctCurrCycCredit());
        assertEquals("A000000000", first.getAcctGroupId());
        assertNull(first.getAcctAddrZip());
    }

    @Test
    @DisplayName("cardxref.txt: 50 rows over 50 distinct account ids (plan §3 B-0007 measurement)")
    void cardXrefRowsDecode() throws IOException {
        LegacyExtractParser parser = new LegacyExtractParser(new DateColumnGate(), true);
        List<CardXref> xrefs = parser.parseCardXrefs(
                ascii("cardxref.txt", LegacyExtractParser.CARD_XREF_RECORD_LENGTH), "cardxref.txt");

        assertEquals(50, xrefs.size());
        assertEquals(50, xrefs.stream().map(CardXref::getXrefAcctId).distinct().count());
        assertEquals("0500024453765740", xrefs.get(0).getXrefCardNumber());
        assertEquals(50L, xrefs.get(0).getXrefCustId());
        assertEquals(50L, xrefs.get(0).getXrefAcctId());
    }

    @Test
    @DisplayName("USRSEC EBCDIC (IBM037) decodes to 10 users per CSUSR01Y.cpy; password lands in the legacy column only")
    void usrsecDecodesFromIbm037() throws IOException {
        byte[] bytes = Files.readAllBytes(DATA_DIR.resolve("EBCDIC").resolve("AWS.M2.CARDDEMO.USRSEC.PS"));
        List<String> records = CobolFieldReader.splitRecords(
                new String(bytes, Charset.forName("IBM037")), LegacyExtractParser.USER_RECORD_LENGTH);
        List<SecurityUser> users = new LegacyExtractParser(new DateColumnGate(), true).parseUsers(records, "USRSEC");

        assertEquals(10, users.size());
        assertEquals("ADMIN001", users.get(0).getSecUsrId());
        assertEquals("A", users.get(0).getSecUsrType());
        assertEquals("USER0001", users.get(5).getSecUsrId());
        assertEquals("U", users.get(5).getSecUsrType());
        assertNull(users.get(0).getSecUsrPwdHash());
        assertEquals(8, users.get(0).getSecUsrPwdLegacy().length());
    }
}
