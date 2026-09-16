# S12 — Admin users
Verdict: **pass for exercised CRUD/paging; denial presentation divergence**.

Compared with `S12_functional_requirement.md`, FR-S12-01/03/07/10,18,26/27/31/34,38/39.
- Admin menu options1–4 reached list/add/update/delete respectively.
- List paging worked (10 rows then short page). U-selection populated chosen user.
- Added scratch `DEVTEST1`, Devin/Test, typeU, supplied demo password; success message.
- Changed surname to Verified using F5; fresh fetch displayed Verified.
- Fetched/deleted scratch user using F5; fresh update lookup returned `User ID NOT found...`. Seed users were not intentionally edited/deleted.
- Regular direct access denied403 via Whitelabel page (UI-05).
- Not tested: sign-in as scratch user, complete duplicate/invalid-field/other-AID matrix or injected failures.

Evidence: `15-users-list-before.png`, `15-users-page2.png`, `16-user-add-before.png`, `16-user-add-saved.png`, `17-user-update-before.png`, `17-user-update-saved.png`, `17-user-update-persisted.png`, `18-user-delete-before.png`, `18-user-delete-saved.png`, `18-user-delete-verified.png`, `12-users-denied-user.png`.
