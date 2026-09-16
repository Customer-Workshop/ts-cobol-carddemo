# S21 — Transaction types
Verdict: **FR/presentation divergences; add/delete persistence pass**.

Compared with `S21_functional_requirement.md`, FR-S21-01–04,07,09,10,12.
- Admin option5 listed seven seed types01–07; F8 produced `No more pages for these search conditions`. Type99 filter before add produced expected no-match message but retained dim stale rows01–07 (UI-08).
- F2 from cleared filter reached maintenance, but search state rather than FR-S21-07 create state (UI-07). Needed absent-code99→ENTER→F5 before entering description.
- Description `DEVIN UI TEST`, ENTER validated; F5 committed. Fresh list filter99 displayed exact code/description.
- Admin option6 explicitly dispatched maintenance; fresh99 fetch confirmed saved data.
- F4 entered delete-confirm state, second F4 deleted; fresh option6 lookup99 returned `No record found for this key in database`. Scratch type cleaned up.
- Not tested: real multipage traversal (only seven seed rows at paging check), description filter, list F10 U/D, multiple-action errors, maintenance update/cancel, FK child guard, batch apply/extract.

Evidence: `21-tran-types-list.png`, `21-tran-types-no-match-before.png`, `21-tran-types-persisted.png`, `22-tran-type-maint-before.png`, `22-tran-type-maint-confirm.png`, `22-tran-type-maint-saved.png`, `22-tran-type-maint-menu-persisted.png`, `22-tran-type-delete-before.png`, `22-tran-type-deleted.png`, `22-tran-type-delete-verified.png`.
