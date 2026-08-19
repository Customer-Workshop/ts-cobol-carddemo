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
| CBTRN03C report + SEM-B02 | `TranReportJob.Run` (main loop + out-of-window break) | TranReportParityTests.TranReport_MatchesGolden_ByteExact / TranReport_TrailingOutOfWindowRecord_SuppressesGrandTotal |
| CBTRN03C EOF stale-record totals (SEM-B03) | `TranReportJob.Run` EOF branch | TranReportParityTests.TranReportEof_CleanEof_WritesFinalPageAndGrandTotals + tran-report-eof golden |
| CBTRN03C 1100-WRITE-TRANSACTION-REPORT / 1120-WRITE-DETAIL | `TranReportJob.WriteDetailLine`, `ReportLayout.Detail` | TranReportParityTests (byte-exact) |
| CBTRN03C 1110-WRITE-PAGE-TOTALS / 1110-WRITE-GRAND-TOTALS | `TranReportJob.WritePageTotals`, `ReportLayout.PageTotal/GrandTotal` | tran-report-eof golden |
| CBTRN03C 1120-WRITE-ACCOUNT-TOTALS | `TranReportJob.WriteDetailLine` card-break branch, `ReportLayout.AccountTotal` | tran-report golden |
| CBTRN03C 1120-WRITE-HEADERS | `TranReportJob.WriteHeaders`, `ReportLayout.NameHeader/ColumnHeader` | tran-report golden |
| CVTRA07Y edited PICs (SEM-031) | `EditedPic.MinusEdited/PlusEdited` | EditedPicTests (values verbatim from golden bytes) |
| CVTRA03Y TRAN-TYPE-RECORD | `TranTypeRecord` | TranReportParityTests via TRANTYPE.unload fixture |
| CVTRA04Y TRAN-CAT-RECORD | `TranCategoryRecord` | TranReportParityTests via TRANCATG.unload fixture |
| CVACT03Y CARD-XREF-RECORD (first consumer) | `CardXrefRecord` (wave-0, consumed read-only) | TranReportParityTests via CARDXREF.unload fixture |
| TRANREPT.jcl DD statements | `CardDemo.Modules.TranReport.Batch` DD_* env mapping | capture-tran-report-inputs.sh + TranReportParityTests |
| CBTRN03C X(10) lexicographic date window | `LegacyDate.Compare` | LegacyDateTests |
| JCL DD statements (INTCALC/TRANREPT) | `DD_*` env mapping in scripts/common.sh | verify-goldens.sh |

Waves append rows here as programs are ported.
