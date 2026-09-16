# S09 — Add transaction
Verdict: **pass for exercised flow**.

Compared with `S09_functional_requirement.md`, including exact amount format FR-S09-13.
- Main option 8 reached add. Used card `0500024453765740`, type01/category0001, sourceDEVTEST, description `DEVIN UI SIGNOFF`, origin/process `2026-09-16`, merchant800000000/DEVIN TEST SHOP/RALEIGH/27501.
- Initial `1.23` rejected with `Amount should be in format -99999999.99`: expected FR behavior, not a bug. Corrected to `+00000001.23`.
- Y confirmation/ENTER created ID `0000000996722788`; fresh list/search/selection proved persistence and exact1.23.
- No add-specific divergence observed. Transaction remains in demo DB.
- Not tested: copy-last/PF5, all field validations, duplicates/concurrent IDs, invalid foreign keys or fault rollback.

Evidence: `11-transaction-add-initial.png`, `11-transaction-add-before.png`, `11-transaction-add-amount-validation.png`, `11-transaction-add-saved.png`, `09-transaction-list-added.png`.
