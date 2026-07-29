package com.carddemo.cbact04c;

import com.carddemo.cbact04c.domain.Records.Account;
import com.carddemo.cbact04c.service.BatchJob;
import com.carddemo.cbact04c.service.Cbact04cService;
import com.carddemo.cbact04c.util.ZonedDecimal;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionGenerationTest {

    @Test
    void writesEveryCategoryWithExpected350ByteFields(@TempDir Path directory) throws Exception {
        Account account = BatchTestSupport.account("00000000001", "GROUP     ", "0.00");
        BatchJob job = BatchTestSupport.writeJob(
                directory,
                List.of(
                        BatchTestSupport.category("00000000001", "01", "0001", "100.00"),
                        BatchTestSupport.category("00000000001", "01", "0002", "200.00")),
                List.of(account),
                List.of(
                        BatchTestSupport.group("GROUP     ", "01", "0001", "12.00"),
                        BatchTestSupport.group("GROUP     ", "01", "0002", "6.00")),
                List.of(BatchTestSupport.xref("00000000001", "CARD000000000001")),
                true);

        new Cbact04cService(fixedClock()).run(job);

        List<String> transactions = BatchTestSupport.lines(job.transact());
        assertEquals(2, transactions.size());
        assertEquals(350, transactions.get(0).length());
        assertEquals("2025-05-01000001", transactions.get(0).substring(0, 16));
        assertEquals("2025-05-01000002", transactions.get(1).substring(0, 16));
        assertEquals("01", transactions.get(0).substring(16, 18));
        assertEquals("0005", transactions.get(0).substring(18, 22));
        assertEquals("System    ", transactions.get(0).substring(22, 32));
        assertEquals("Int. for a/c 00000000001",
                transactions.get(0).substring(32, 132).trim());
        assertEquals(new BigDecimal("1.00"),
                ZonedDecimal.parse(transactions.get(0).substring(132, 143)));
        assertEquals("000000000",
                transactions.get(0).substring(143, 152));
        assertEquals("CARD000000000001",
                transactions.get(0).substring(262, 278).trim());
        assertEquals("2025-05-01-12.34.56.780000",
                transactions.get(0).substring(278, 304));
        assertEquals(transactions.get(0).substring(278, 304),
                transactions.get(0).substring(304, 330));
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2025-05-01T12:34:56.780Z"), ZoneOffset.UTC);
    }
}
