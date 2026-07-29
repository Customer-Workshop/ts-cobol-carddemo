package com.carddemo.cbact04c;

import com.carddemo.cbact04c.domain.Records.Account;
import com.carddemo.cbact04c.service.BatchJob;
import com.carddemo.cbact04c.service.BatchResult;
import com.carddemo.cbact04c.service.Cbact04cService;
import com.carddemo.cbact04c.util.ZonedDecimal;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InterestCalculationTest {

    @Test
    void truncatesInterestTowardZeroInsteadOfHalfUp(@TempDir Path directory) throws Exception {
        Account account = BatchTestSupport.account("00000000001", "GROUP     ", "0.00");
        BatchJob job = BatchTestSupport.writeJob(
                directory,
                List.of(BatchTestSupport.category("00000000001", "01", "0001", "100.00")),
                List.of(account),
                List.of(BatchTestSupport.group("GROUP     ", "01", "0001", "1.00")),
                List.of(BatchTestSupport.xref("00000000001", "CARD-000000000001")),
                true);

        BatchResult result = service().run(job);

        assertEquals(1, result.transactionCount());
        assertEquals(new BigDecimal("0.08"),
                com.carddemo.cbact04c.io.RecordCodecs.decodeAccount(
                        BatchTestSupport.lines(job.account()).get(0)).balance);
        assertEquals(new BigDecimal("0.08"),
                ZonedDecimal.parse(BatchTestSupport.lines(job.transact()).get(0)
                        .substring(132, 143)));
    }

    @Test
    void accumulatesMultipleCategoriesInOneAccount(@TempDir Path directory) throws Exception {
        BatchJob job = BatchTestSupport.writeJob(
                directory,
                List.of(
                        BatchTestSupport.category("00000000001", "01", "0001", "120.00"),
                        BatchTestSupport.category("00000000001", "01", "0002", "240.00")),
                List.of(BatchTestSupport.account("00000000001", "GROUP     ", "10.00")),
                List.of(
                        BatchTestSupport.group("GROUP     ", "01", "0001", "12.00"),
                        BatchTestSupport.group("GROUP     ", "01", "0002", "6.00")),
                List.of(BatchTestSupport.xref("00000000001", "CARD-000000000001")),
                true);

        service().run(job);

        Account updated = com.carddemo.cbact04c.io.RecordCodecs.decodeAccount(
                BatchTestSupport.lines(job.account()).get(0));
        assertEquals(new BigDecimal("12.40"), updated.balance);
    }

    private Cbact04cService service() {
        return new Cbact04cService(
                Clock.fixed(Instant.parse("2025-05-01T12:34:56.780Z"), ZoneOffset.UTC));
    }
}
