# S08 — Transaction view
Verdict: **display bug; lookup/selection pass**.

Compared with `S08_functional_requirement.md` fetched-detail surface and selection entry.
- Main option 7 reached COTRN01C. S-selection from list fetched exact ID, card, description and merchant data for seed and new transaction.
- Amount and origin/processing dates clip to prefixes; Type CD/Category labels also clip. For new ID `0000000996722788`, DOM contains full amount `+00000001.23` and dates `2026-09-16`, but screen does not show them fully (UI-03).
- No claim of persisted corruption: list and generated report show correct transaction amount.
- Not tested: all clear/browse shortcuts, invalid/not-found ID or read failures.

Evidence: `10-transaction-view-menu.png`, `10-transaction-view-selected.png`, `10-transaction-view-added.png`.
