# S06 — Card update
Verdict: **selected-entry bug; manual lookup/save persistence pass**.

Compared with `S06_functional_requirement.md`, especially selected-entry FR-S06-28 and validate/save states.
- Main option 5 reached update. List U-entry failed due to unpadded account `50` (UI-01).
- Manually padding to `00000000050` fetched card `0500024453765740`. Original embossed name `ANIYA VON`, active Y, expiry03/2023.
- Changed only embossed name to `DEVIN TEST CARD`; ENTER validation then F5 committed. Fresh card view confirmed exact new value.
- Not tested: every date/name/status validation, cancel/conflict and rollback variants.

Evidence: `08-card-update-menu.png`, `08-card-update-list-seam-bug.png`, `08-card-update-before.png`, `08-card-update-confirm.png`, `08-card-update-saved.png`, `07-card-view-persisted.png`.
