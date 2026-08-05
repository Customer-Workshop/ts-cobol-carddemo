# CardDemo data layer

This module maps the fixed-width COBOL copybooks to Spring Data JPA entities and
loads the ASCII sample files. EBCDIC files are deliberately skipped.

| Copybook | Entity | Table | Fields (copybook PIC → Java) |
|---|---|---|---|
| CVACT01Y | Account | ACCOUNT | ACCT-ID 9(11) → Long acctId; status X(1) → String; five S9(10)V99 → BigDecimal(12,2); three dates X(10), ZIP/group X(10) → String |
| CVACT02Y | Card | CARD | CARD-NUM X(16) → String key; CARD-ACCT-ID 9(11) → Account FK; CVV 9(3) → Integer; name X(50), date X(10), status X(1) |
| CVACT03Y | CardXref | CARD_XREF | card X(16) key; customer 9(9) and account 9(11) → FKs |
| CVCUS01Y | Customer | CUSTOMER | cust ID 9(9) → Long; names/addresses/phones/government ID/DOB/etc. retain X lengths; SSN 9(9) String; FICO 9(3) Integer |
| CVTRA05Y | Transaction | TRANSACTION | ID X(16) key; type X(2), category 9(4), source X(10), description X(100), amount S9(9)V99 → BigDecimal(11,2), merchant fields, card FK, timestamps X(26) |
| CSUSR01Y | UserSecurity | USER_SECURITY | ID X(8) key; fname/lname X(20), password X(8), type X(1) |
| CVTRA01Y | TransactionCategoryBalance | TRAN_CAT_BAL | composite acct 9(11), type X(2), category 9(4); balance S9(9)V99 → BigDecimal(11,2) |
| CVTRA03Y | TransactionType | TRAN_TYPE | type X(2) key; description X(50) |
| CVTRA04Y | TransactionCategory | TRAN_CATEGORY | composite type X(2), category 9(4); description X(50) |

All signed packed-looking ASCII numeric fields are represented by `BigDecimal`,
never floating point, preserving an exact scale of two. The loader understands
the ASCII overpunch sign convention (`{`/`}` and `A`–`R`).

Run with `mvn spring-boot:run -Dspring-boot.run.arguments=--carddemo.loader.enabled=true`.
The data directory defaults to `../../app/data/ASCII` and can be changed with
`carddemo.loader.data-directory` or `CARDDEMO_DATA_DIRECTORY`. Datasource
properties are configurable for a later PostgreSQL swap. H2 local files are
written under `data/` and ignored by Git.

There is no ASCII user-security file in the repository, so USER_SECURITY is not
loaded. `discgrp.txt` has no target entity and is skipped. EBCDIC files and the
`CVEXPORT.cpy` COMP/COMP-3 bridge are deliberately out of scope. Legacy
passwords are plaintext; **TODO: hash passwords when a security migration is
defined, while this loader/entity preserve them as-is.**
