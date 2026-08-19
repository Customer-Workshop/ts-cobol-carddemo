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
| CBACT04C main loop + 1050-UPDATE-ACCOUNT (+SEM-B01) | `InterestCalcJob.Run` | InterestCalcParityTests (TRANSACT/ACCTFILE/TCATBAL byte-for-byte), InterestCalcJobTests.FinalAccount_TransactionsWritten_ButRecordNeverRewritten_SemB01 |
| CBACT04C 1200-GET-INTEREST-RATE + 1200-A-GET-DEFAULT-INT-RATE (SEM-031) | `InterestCalcJob.GetInterestRate` | InterestCalcJobTests.MissingDisclosureGroup_FallsBackToDefaultGroupRate |
| CBACT04C 1110-GET-XREF-DATA (SEM-032) | `InterestCalcJob.FirstXrefPerAccount` | InterestCalcJobTests.XrefAlternateKeyRead_PicksFirstCardInPrimaryKeyOrder |
| CBACT04C 1300-B-WRITE-TX + Z-GET-DB2-FORMAT-TIMESTAMP | `InterestCalcJob.BuildInterestTransaction` | InterestCalcParityTests.TransactFile_MatchesGolden_ByteForByte, InterestCalcJobTests.InterestTransaction_FieldsMatchCbact04cLayout |
| CVACT03Y CARD-XREF-RECORD (wave-1 round-trip) | `CardXrefRecord` | InterestCalcParityTests.WaveOneInputFixtures_RoundTripByteExact |
| CVTRA02Y DIS-GROUP-RECORD (wave-1 round-trip) | `DisclosureGroupRecord` | InterestCalcParityTests.WaveOneInputFixtures_RoundTripByteExact |

Waves append rows here as programs are ported.
