# S04 — Card list
Verdict: **bug on U navigation; list, paging and S selection pass**.

Compared with `S04_functional_requirement.md` browse/paging and S/U selection seams.
- Main option 3 rendered populated list; F8 showed page2, F7 returned page1.
- S on card `0500024453765740` reached matching detail.
- U on that row sends `accountId=50`, not `00000000050`; update immediately rejects key and leaves details blank (BUGS UI-01).
- Not tested: all filter/invalid-selection combinations or database errors.

Evidence: `06-card-list-page1.png`, `06-card-list-page2.png`, `07-card-view-selected.png`, `08-card-update-list-seam-bug.png`.
