# Traceability Matrix

| COBOL artifact | .NET artifact | Verified by |
| --- | --- | --- |
| CVACT01Y ACCOUNT-RECORD | `AccountRecord` | DecoderGoldenRoundTripTests.AccountRecords_RoundTripByteExact |
| CVTRA01Y TRAN-CAT-BAL-RECORD | `TranCatBalRecord` | DecoderGoldenRoundTripTests.TranCatBalRecords_RoundTripByteExact |
| CVACT03Y CARD-XREF-RECORD | `CardXrefRecord` | (round-trip added with first consuming wave) |
| CVTRA02Y DIS-GROUP-RECORD | `DisclosureGroupRecord` | (round-trip added with first consuming wave) |
| CVTRA05Y TRAN-RECORD | `TransactionRecord` | DecoderGoldenRoundTripTests.InterestTransactions_RoundTripByteExact |
| CBACT04C 1300-COMPUTE-INTEREST | `LegacyMoney.MonthlyInterest` | GoldenInvariantTests.InterestAmounts_MatchLegacyMoneyFormula |
| CBACT04C 1050-UPDATE-ACCOUNT (+SEM-B01) | (wave 1 port) | GoldenInvariantTests.UntouchedAccounts / BalanceDeltas |
| CBTRN03C report + SEM-B02 | (wave 2 port) | tran-report / tran-report-eof goldens |
| CBTRN03C X(10) lexicographic date window | `LegacyDate.Compare` | LegacyDateTests |
| JCL DD statements (INTCALC/TRANREPT) | `DD_*` env mapping in scripts/common.sh | verify-goldens.sh |

Waves append rows here as programs are ported.
