# CBACT04C Java port

Run with `mvn spring-boot:run -Dspring-boot.run.arguments="tcatbal xref discgrp acctdata transact 2025-05-01"`.
Arguments are input/output paths followed by the ten-character PARM-DATE and optional `true` for `final-update-at-eof`.

The fixed-width codecs preserve ASCII display/zoned-decimal records and tolerate the 36-byte card-xref export. TCATBAL is sorted by its key to model indexed sequential access; account rewrites preserve all untouched filler bytes.

Two COBOL fidelity details are intentional: the original loop does not flush the final account at EOF (the default); set `final-update-at-eof=true` to opt into the corrected behavior. Transactions are written once per non-zero-rate category record, inside 1300-C, rather than once per account.
Sample invocation uses PARM-DATE 2025-05-01.
