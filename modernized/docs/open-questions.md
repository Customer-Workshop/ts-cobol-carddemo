# Open questions and migration decisions

These items remain unresolved after source and fixture review. Resolved arithmetic and record-width facts are documented in the business-rule and inventory documents rather than repeated here.

1. **Optional README components:** The root README names pending-authorization, transaction-type DB2, and MQ programs/maps that are absent from the base `app/` tree. Decide whether the optional module trees are in Phase 1 scope and establish their authoritative transaction IDs/maps.
2. **ASCII card-xref padding:** `ASCII/cardxref.txt` contains 50 records of 36 data bytes, while `CVACT03Y` and `CARDXREF.PS` are 50 bytes. The missing 14 bytes are exactly the copybook filler. Phase 1 must right-pad 14 spaces when importing this ASCII fixture; it must not reject the record solely for being short by filler.
3. **Other ASCII fixture widths:** The other ASCII fixtures retain their full declared record widths: account 300, card 150, customer 500, daily transaction 350, disclosure group 50, category balance 50, category 60, and type 60. No equivalent filler stripping was found in those files.
4. **Encoding and numeric representation:** Confirm the EBCDIC code page and decoding rules for signed zoned/display numerics and any packed-decimal fields before migration. The fixed widths and record counts are documented in `inventory.md`.
5. **VSAM semantics:** Preserve KSDS key ordering, alternate-index behavior (`CARDXREF` by account and card AIX paths), duplicate-key handling, and record-lock/update semantics in PostgreSQL.
6. **CICS pseudo-conversational state:** COMMAREA fields such as caller/next program, user identity, last map, and local program state form the navigation state machine. Decide whether REST sessions/JWT or server-side session storage is authoritative.
7. **`TRANFILE` replacement semantics:** `POSTTRAN.jcl` invokes `CBTRN02C`, whose `TRANSACT-FILE` is opened `OUTPUT`. Confirm that the target batch job intentionally replaces the transaction output rather than appending, and define rerun/idempotency behavior.
8. **CSD resource entries without matching base flows:** `CDV1`, `CCDM`, and `CCT1`–`CCT4` are defined by CSD/JCL resources but have no distinct matching base COBOL/BMS business program in the supplied tree. Determine whether they are deployment aliases, test resources, or omitted source.
9. **`CBSTM03B` source gaps:** The linkage declares `K` (keyed read), `W` (write), and `Z` (rewrite), but the visible dispatch implements open/read/close paths. Obtain the compiled/source variant or runtime evidence before implementing those operations.
10. **EBCDIC collation:** Browse order and `STARTBR`/`READNEXT`/`READPREV` behavior may depend on mainframe collation. Decide whether PostgreSQL ordering must emulate EBCDIC.
11. **Batch scheduler boundary:** Control-M provides four evidenced chains, including the cross-folder `MNTTRDB2 → TRANEXTR` relationship. Confirm deployed calendar and cross-folder condition semantics from the scheduler runbook.
12. **GDG generations and outputs:** Backup, reject, report, statement, FTP, and PDF datasets use generation or downstream utility conventions. Define retention, replay, idempotency, and object-storage equivalents.
13. **Optional IMS/MQ semantics:** Authorization modules depend on request/reply correlation, commit scope, and IMS segment status codes not represented by the base application.
14. **Assembler/date utility contracts:** Confirm `MVSWAIT` delay units and the complete `COBDATFT` input/output contract against runtime documentation.
15. **Abends and return codes:** Map `CEE3ABD`, file status values, and `RETURN-CODE=4` rejects into job-runner retry, quarantine, and operator-alert policy.
16. **Statement artifacts:** `CBSTM03A` writes both fixed-width statement and HTML outputs. Decide whether the target retains both, adds PDF conversion, or treats one as an intermediate artifact.
