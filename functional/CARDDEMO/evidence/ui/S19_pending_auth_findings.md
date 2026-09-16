# S19 — Pending authorizations
Verdict: **display divergence; primary navigation and fraud-toggle UI pass**.

Compared with `S19_functional_requirement.md`, FR-S19-01–10.
- Main option11; account1 returned customer context, summary and five of seven seeded details. F8 showed remaining two, F7 returned first page; dates ordered newest-first in visible pages.
- S on `T00000000000401` opened matching card9680294154603697, date02/29/24 14:35:12, amount175.50; next-auth displayed the next detail.
- Bonus: F5 mark fraud then F5 remove produced corresponding messages/status changes. Removal displayed journal `R-09/16/26`; fraud journal history remains. No direct DB journal inspection was performed.
- `Acct Status:` blank even though account view shows activeY (UI-06). Summary balance/limits are a separate snapshot; not assumed erroneous merely because they differ from current account master.
- Not tested: every status value, no-summary cases, full 20-page stack, injected IMS/DB write failure/rollback.

Evidence: `14-pending-auth-list.png`, `14-pending-auth-page2.png`, `14-pending-auth-detail-before.png`, `14-pending-auth-fraud-marked.png`, `14-pending-auth-fraud-removed.png`, `14-pending-auth-next.png`.
