# S03 — Account update
Verdict: **bug/divergence; valid commit and fresh persistence pass after fixture repairs**.

Compared with `S03_functional_requirement.md` search/fetch, customer validations and ENTER→F5 commit states.
- Main option 2 fetched account1. First-name-only edit was blocked by unrelated seeded FICO274; subsequent validation rejected phone2 area373. Validation rules are expected; seed usability is DATA-01.
- Explicit additional edits: FICO700, ZIP27501, phone2 `(919)693-8684`; first name Devin. ENTER showed `Changes validated.Press F5 to save`; F5 showed `Changes committed to database`. Fresh S02 fetch confirmed changes.
- Middle name Madeline visually occupies Last Name column, true surname Kessler clipped (UI-02). Segmented date/phone controls stack vertically rather than a compact terminal row.
- Not tested: full field-validation matrix, cancel variants, locking/concurrent updates or rollback faults.

Evidence: `05-account-update-before.png`, `05-account-update-seed-validation.png`, `05-account-update-seed-phone-validation.png`, `05-account-update-confirm.png`, `05-account-update-saved.png`, `04-account-view-persisted.png`.
