package com.carddemo.cbact04c;

import com.carddemo.cbact04c.domain.Records.Account;
import com.carddemo.cbact04c.service.BatchJob;
import com.carddemo.cbact04c.service.BatchResult;
import com.carddemo.cbact04c.service.Cbact04cService;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BatchBehaviorTest {

    @Test
    void controlBreakFlushesPreviousAccountAndPreservesFiller(@TempDir Path directory)
            throws Exception {
        Account first = BatchTestSupport.account("00000000001", "GROUP     ", "10.00");
        first.raw = first.raw.substring(0, 299) + "X";
        Account second = BatchTestSupport.account("00000000002", "GROUP     ", "20.00");
        BatchJob job = BatchTestSupport.writeJob(
                directory,
                List.of(
                        BatchTestSupport.category("00000000001", "01", "0001", "120.00"),
                        BatchTestSupport.category("00000000002", "01", "0001", "120.00")),
                List.of(first, second),
                List.of(BatchTestSupport.group("GROUP     ", "01", "0001", "12.00")),
                List.of(
                        BatchTestSupport.xref("00000000001", "CARD-000000000001"),
                        BatchTestSupport.xref("00000000002", "CARD-000000000002")),
                false);

        new Cbact04cService(fixedClock()).run(job);

        Account updatedFirst = com.carddemo.cbact04c.io.RecordCodecs.decodeAccount(
                BatchTestSupport.lines(job.account()).get(0));
        Account untouchedSecond = com.carddemo.cbact04c.io.RecordCodecs.decodeAccount(
                BatchTestSupport.lines(job.account()).get(1));
        assertEquals(new BigDecimal("11.20"), updatedFirst.balance);
        assertEquals(new BigDecimal("20.00"), untouchedSecond.balance);
        assertEquals('X', BatchTestSupport.lines(job.account()).get(0).charAt(299));
        assertEquals(BigDecimal.ZERO.setScale(2), updatedFirst.currentCredit);
        assertEquals(BigDecimal.ZERO.setScale(2), updatedFirst.currentDebit);
    }

    @Test
    void zeroRateDoesNotWriteOrAccumulate(@TempDir Path directory) throws Exception {
        BatchJob job = BatchTestSupport.writeJob(
                directory,
                List.of(BatchTestSupport.category("00000000001", "01", "0001", "120.00")),
                List.of(BatchTestSupport.account("00000000001", "GROUP     ", "10.00")),
                List.of(BatchTestSupport.group("GROUP     ", "01", "0001", "0.00")),
                List.of(BatchTestSupport.xref("00000000001", "CARD-000000000001")),
                true);

        BatchResult result = new Cbact04cService(fixedClock()).run(job);

        assertEquals(0, result.transactionCount());
        assertEquals(new BigDecimal("10.00"),
                com.carddemo.cbact04c.io.RecordCodecs.decodeAccount(
                        BatchTestSupport.lines(job.account()).get(0)).balance);
        assertEquals(0, BatchTestSupport.lines(job.transact()).size());
    }

    @Test
    void missingGroupUsesDefaultRate(@TempDir Path directory) throws Exception {
        BatchJob job = BatchTestSupport.writeJob(
                directory,
                List.of(BatchTestSupport.category("00000000001", "01", "0001", "120.00")),
                List.of(BatchTestSupport.account("00000000001", "MISSING   ", "0.00")),
                List.of(BatchTestSupport.group("DEFAULT   ", "01", "0001", "12.00")),
                List.of(BatchTestSupport.xref("00000000001", "CARD-000000000001")),
                true);

        BatchResult result = new Cbact04cService(fixedClock()).run(job);

        assertEquals(1, result.transactionCount());
        assertEquals(new BigDecimal("1.20"),
                com.carddemo.cbact04c.io.RecordCodecs.decodeAccount(
                        BatchTestSupport.lines(job.account()).get(0)).balance);
    }

    @Test
    void finalUpdateFlagControlsTheKnownCobolEofQuirk(@TempDir Path directory) throws Exception {
        BatchJob faithfulJob = BatchTestSupport.writeJob(
                directory.resolve("faithful"),
                List.of(BatchTestSupport.category("00000000001", "01", "0001", "120.00")),
                List.of(BatchTestSupport.account("00000000001", "GROUP     ", "10.00")),
                List.of(BatchTestSupport.group("GROUP     ", "01", "0001", "12.00")),
                List.of(BatchTestSupport.xref("00000000001", "CARD-000000000001")),
                false);
        BatchJob correctedJob = BatchTestSupport.writeJob(
                directory.resolve("corrected"),
                List.of(BatchTestSupport.category("00000000001", "01", "0001", "120.00")),
                List.of(BatchTestSupport.account("00000000001", "GROUP     ", "10.00")),
                List.of(BatchTestSupport.group("GROUP     ", "01", "0001", "12.00")),
                List.of(BatchTestSupport.xref("00000000001", "CARD-000000000001")),
                true);

        new Cbact04cService(fixedClock()).run(faithfulJob);
        new Cbact04cService(fixedClock()).run(correctedJob);

        assertEquals(new BigDecimal("10.00"),
                com.carddemo.cbact04c.io.RecordCodecs.decodeAccount(
                        BatchTestSupport.lines(faithfulJob.account()).get(0)).balance);
        assertEquals(new BigDecimal("11.20"),
                com.carddemo.cbact04c.io.RecordCodecs.decodeAccount(
                        BatchTestSupport.lines(correctedJob.account()).get(0)).balance);
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2025-05-01T12:34:56.780Z"), ZoneOffset.UTC);
    }
}
