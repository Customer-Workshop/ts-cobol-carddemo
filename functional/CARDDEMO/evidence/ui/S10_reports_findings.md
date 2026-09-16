# S10 — Reports / TRANREPT
Verdict: **bug in generated totals; submission and output generation pass**.

Compared with `S10_functional_requirement.md` monthly/Y submit and report-detail/totals contract.
- Main option9 opened reports; Monthly S + confirmY + ENTER displayed `Monthly report submitted for printing ...`.
- Generated `spring-boot/target/carddemo-batch/cbtrn03-report.txt` covered2026-09-01..30 and contained newly added transaction `0000000996722788`, amount1.23.
- Exactly one printed detail, but Page Total and Grand Total2.46, expected1.23 (UI-04). Output preserved before bill-payment mutation.
- Not tested: yearly/custom and invalid-date permutations, multi-page totals, output failure.

Evidence: `13-reports-before.png`, `13-reports-submitted.png`, `13-TRANREPT-output.txt`.
