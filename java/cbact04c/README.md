# CBACT04C Java 17 port

This module ports the CardDemo COBOL interest calculator to a Spring Boot 3
batch application. Records are ASCII fixed-width display records; dates and
timestamps remain strings, and monetary values are `BigDecimal` values with
scale two.

## COBOL to Java mapping

| COBOL | Java |
| --- | --- |
| `CVTRA01Y` TRAN-CAT-BAL-RECORD | `Records.TranCat` and `RecordCodecs` |
| `CVACT03Y` CARD-XREF-RECORD | `Records.Xref` and `RecordCodecs` |
| `CVTRA02Y` DIS-GROUP-RECORD | `Records.DiscGroup`, `DiscKey`, and `RecordCodecs` |
| `CVACT01Y` ACCOUNT-RECORD | `Records.Account` and `RecordCodecs` |
| `CVTRA05Y` TRAN-RECORD | `Records.Transaction` and `RecordCodecs` |
| `1000-TCATBALF-GET-NEXT` | `Cbact04cService.getNextTranCatBal` |
| `1050-UPDATE-ACCOUNT` | `Cbact04cService.updateAccount` |
| `1100-GET-ACCT-DATA` | `Cbact04cService.getAcctData` |
| `1110-GET-XREF-DATA` | `Cbact04cService.getXrefData` |
| `1200-GET-INTEREST-RATE` / `1200-A` | `Cbact04cService.getInterestRate` |
| `1300-COMPUTE-INTEREST` / `1300-B` | `Cbact04cService.computeInterest` and transaction gateway |
| `1400-COMPUTE-FEES` | `Cbact04cService.computeFees` no-op |

`FileGateways` models the COBOL FDs: TCATBAL is read and key-sorted,
XREF and DISCGRP are loaded by alternate/random key, ACCOUNT is rewritten
through on every control break, and TRANSACT is truncated at open and streamed
record by record.

## Running

Run the full test suite:

```bash
mvn -q clean test -Dmaven.wagon.http.retryHandler.count=5
```

Run with positional job arguments:

```bash
mvn spring-boot:run \
  -Dspring-boot.run.arguments="samples/tcatbal.txt samples/cardxref.txt samples/discgrp.txt samples/acctdata.txt /tmp/transact.txt 2025-05-01 true"
```

Run with Spring properties:

```bash
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--cbact04c.tcatbal=samples/tcatbal.txt --cbact04c.xref=samples/cardxref.txt --cbact04c.discgrp=samples/discgrp.txt --cbact04c.account=samples/acctdata.txt --cbact04c.transact=/tmp/transact.txt --cbact04c.parm-date=2025-05-01 --cbact04c.final-update-at-eof=true"
```

Build and run the executable jar:

```bash
mvn -q clean package
java -jar target/cbact04c-1.0.0.jar \
  samples/tcatbal.txt samples/cardxref.txt samples/discgrp.txt \
  samples/acctdata.txt /tmp/transact.txt 2025-05-01 true
```

The application prints the COBOL `DISPLAY` messages to stdout and maps an
`AbendException` to process exit code 999.

## Fidelity notes

* The original `PERFORM UNTIL` tests EOF before entering the body, making the
  `ELSE PERFORM 1050-UPDATE-ACCOUNT` branch unreachable. The default
  `cbact04c.final-update-at-eof=false` preserves that behavior, so the final
  account is not flushed. Set it to `true` for the corrected behavior.
* Transactions are written once per non-zero-rate category inside
  `1300-COMPUTE-INTEREST`, not once per account.
* The original disclosure-group working-storage area is not cleared after a
  status `23` read. The Java service only replaces its mutable rate holder on
  a successful requested or DEFAULT read; genuine I/O failures abend.
* The original `1400-COMPUTE-FEES` paragraph says `To be implemented`; the Java
  `computeFees` method is an explicit no-op and is still called at the same
  point.
