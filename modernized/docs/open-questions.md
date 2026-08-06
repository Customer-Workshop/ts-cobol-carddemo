# Open questions and migration decisions

These are intentionally unresolved; the inventory and specifications do not invent answers.

1. **Missing README components:** The README names pending-authorization, transaction-type DB2, and MQ transactions/programs that are absent from base `app/`; decide whether optional-module source trees are in Phase 1 scope and identify authoritative transaction IDs/maps.
2. **Data fixtures and encodings:** ASCII fixtures have widths matching layouts, but EBCDIC files do not carry self-describing metadata. Confirm code page (likely IBM EBCDIC variant), signed/zoned numeric decoding, and COMP-3 handling before migration.
3. **VSAM semantics:** Preserve KSDS key ordering and alternate-index behavior (`CARDXREF` by account, transaction browsing) in PostgreSQL indexes; confirm duplicate-key and record-lock equivalence.
4. **CICS pseudo-conversational state:** COMMAREA fields (`CDEMO-PGM-CONTEXT`, previous/next program, user identity, last map) are the state machine. Decide whether REST sessions/JWT or server-side session storage is authoritative.
5. **EBCDIC collation:** Browse order and STARTBR/READNEXT/READPREV depend on mainframe collating sequence; confirm whether PostgreSQL ordering must emulate EBCDIC rather than UTF-8.
6. **Interest rounding:** `CBACT04C` contains source arithmetic and packed/zoned fields whose exact interim precision and ROUNDED scope must be transcribed from the complete paragraphs before implementation; do not use floating point.
7. **Batch stream ambiguity:** Control-M has conditions spanning folders (notably transaction types/disclosure groups); obtain deployed export or scheduler runbook to confirm cross-folder trigger direction and calendar semantics.
8. **GDG generations:** Backup and report datasets use `(+1)` generations. Define retention, replay, idempotency, and PostgreSQL/object-storage equivalents.
9. **MQ/IMS semantics:** Optional authorization and VSAM/MQ modules depend on request/reply correlation, commit scope, and IMS segment status codes not represented by the base source.
10. **Assembler waits/date formats:** `MVSWAIT` delay units and `COBDATFT` input/output type contract should be confirmed against the caller copybook and runtime documentation.
11. **Abends and return codes:** Batch source often calls `CEE3ABD`; map abend codes, file status, and `RETURN-CODE=4` rejects into job-runner retry/quarantine policy.
12. **Statement outputs:** `CBSTM03A` delegates to `CBSTM03B` and writes statement/HTML artifacts; decide whether target produces PDFs, HTML, or both and preserve exact totals/formatting.
