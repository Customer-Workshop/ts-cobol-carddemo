# S02 — Account view
Verdict: **display divergence; lookup and persistence checks pass**.

Compared with `S02_functional_requirement.md`, especially FR-S02-01,07–09,11,14.
- Main option 1 opens COACTVWC. Account `00000000001` fetched account/customer fields: customer `000000001`, Immanuel/Madeline/Kessler, balance 194.00, active Y.
- Fresh view after update showed Devin, FICO700, ZIP27501 and phone2 area919; after bill payment showed zero balance.
- Name labels and values are misaligned: Kessler starts before Last Name label (BUGS UI-02). Values exist, unlike the clipped update surname.
- Not tested: every malformed/missing account and master/xref failure path, injected read failures.

Evidence: `04-account-view-initial.png`, `04-account-view.png`, `04-account-view-persisted.png`, `12-bill-payment-account-zero.png`. Use populated full-document `04-account-view.png` as the primary initial-data evidence.
