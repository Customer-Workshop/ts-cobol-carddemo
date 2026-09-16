# S11 — Bill payment
Verdict: **pass for exercised flow**.

Compared with `S11_functional_requirement.md` lookup, full-balance confirmation, transaction creation and account rewrite (including FR-S11-15).
- Main option10; account `00000000001` fetched balance194.00 and confirmation.
- ConfirmY/ENTER created payment `0000000996722789`.
- Retry returned `You have nothing to pay...`; fresh account view showed zero balance.
- No concrete divergence observed. Payment and zero balance remain in demo DB.
- Not tested: negative balances, cancellation variants, duplicate IDs, write/rewrite fault rollback.

Evidence: `12-bill-payment-before.png`, `12-bill-payment-saved.png`, `12-bill-payment-zero.png`, `12-bill-payment-account-zero.png`.
