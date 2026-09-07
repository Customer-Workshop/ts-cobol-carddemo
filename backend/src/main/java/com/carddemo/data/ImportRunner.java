package com.carddemo.data;

import com.carddemo.model.Account;
import com.carddemo.model.CardXref;
import com.carddemo.model.Customer;
import com.carddemo.model.SecurityUser;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardXrefRepository;
import com.carddemo.repository.CustomerRepository;
import com.carddemo.repository.SecurityUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * One-shot load of the legacy extracts into PostgreSQL. Runs ONLY with
 * {@code --spring.profiles.active=import} (target state §5: never a default-profile seeder).
 *
 * Sources: app/data/ASCII/acctdata.txt, custdata.txt, cardxref.txt (fixed-width, LF-terminated)
 * and app/data/EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS (IBM037, 80-byte records, no separators).
 */
@Component
@Profile("import")
public class ImportRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ImportRunner.class);

    private final Path dataDir;
    private final Charset usrsecCharset;
    private final boolean acctdataGroupIdInZipSlot;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final CardXrefRepository cardXrefRepository;
    private final SecurityUserRepository securityUserRepository;

    public ImportRunner(
            @Value("${carddemo.import.data-dir}") String dataDir,
            @Value("${carddemo.import.usrsec-charset}") String usrsecCharset,
            @Value("${carddemo.import.acctdata-group-id-in-zip-slot}") boolean acctdataGroupIdInZipSlot,
            AccountRepository accountRepository,
            CustomerRepository customerRepository,
            CardXrefRepository cardXrefRepository,
            SecurityUserRepository securityUserRepository) {
        this.dataDir = Path.of(dataDir);
        this.usrsecCharset = Charset.forName(usrsecCharset);
        this.acctdataGroupIdInZipSlot = acctdataGroupIdInZipSlot;
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.cardXrefRepository = cardXrefRepository;
        this.securityUserRepository = securityUserRepository;
    }

    public record ImportReport(int accounts, int customers, int cardXrefs, int users,
                               int dateValuesChecked, List<DateColumnGate.Offender> dateOffenders) {
    }

    @Override
    @Transactional
    public void run(String... args) throws IOException {
        ImportReport report = importAll();
        log.info("Import complete: accounts={} customers={} card_xrefs={} users={}",
                report.accounts(), report.customers(), report.cardXrefs(), report.users());
        log.info("Q-10 date gate: {} values checked, {} offending", report.dateValuesChecked(),
                report.dateOffenders().size());
    }

    public ImportReport importAll() throws IOException {
        DateColumnGate dateGate = new DateColumnGate();
        LegacyExtractParser parser = new LegacyExtractParser(dateGate, acctdataGroupIdInZipSlot);

        List<Account> accounts = parser.parseAccounts(
                readAsciiLines("ASCII/acctdata.txt", LegacyExtractParser.ACCOUNT_RECORD_LENGTH), "acctdata.txt");
        List<Customer> customers = parser.parseCustomers(
                readAsciiLines("ASCII/custdata.txt", LegacyExtractParser.CUSTOMER_RECORD_LENGTH), "custdata.txt");
        List<CardXref> xrefs = parser.parseCardXrefs(
                readAsciiLines("ASCII/cardxref.txt", LegacyExtractParser.CARD_XREF_RECORD_LENGTH), "cardxref.txt");
        List<SecurityUser> users = parser.parseUsers(readEbcdicRecords(
                "EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS", LegacyExtractParser.USER_RECORD_LENGTH), "USRSEC");

        if (!dateGate.passed()) {
            dateGate.offenders().forEach(o -> log.error("Q-10 offender: {} record {} value '{}'",
                    o.column(), o.recordNumber(), o.value()));
            throw new ImportFormatException("app/data", 0, 0,
                    "Q-10 date gate failed: " + dateGate.offenders().size() + " non YYYY-MM-DD value(s)");
        }

        accountRepository.saveAll(accounts);
        customerRepository.saveAll(customers);
        cardXrefRepository.saveAll(xrefs);
        securityUserRepository.saveAll(users);

        return new ImportReport(accounts.size(), customers.size(), xrefs.size(), users.size(),
                dateGate.valuesChecked(), dateGate.offenders());
    }

    private List<String> readAsciiLines(String relative, int recordLength) throws IOException {
        List<String> lines = Files.readAllLines(dataDir.resolve(relative), StandardCharsets.US_ASCII);
        return lines.stream()
                .filter(line -> !line.isEmpty())
                .map(line -> line.length() >= recordLength
                        ? line.substring(0, recordLength)
                        : line + " ".repeat(recordLength - line.length()))
                .toList();
    }

    private List<String> readEbcdicRecords(String relative, int recordLength) throws IOException {
        byte[] bytes = Files.readAllBytes(dataDir.resolve(relative));
        return CobolFieldReader.splitRecords(new String(bytes, usrsecCharset), recordLength);
    }
}
