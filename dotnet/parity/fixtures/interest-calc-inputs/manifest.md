# Fixture: interest-calc-inputs (wave 1)

Pre-run keyed-order unloads of the two CBACT04C inputs that wave 0 did
not surface: DISCGRP (50-byte `CVTRA02Y` disclosure-group rate records)
and XREFFILE (50-byte `CVACT03Y` card cross-reference records). The
.NET wave-1 port consumes these oracle-produced bytes as inputs — the
rate table and card mapping are never re-derived in C#.

- **Inputs:** WRSEED fixtures only (no estate program run)
- **Files:** `DISCGRP.seed.unload`, `XREFFILE.seed.unload`
- **Oracle level:** recompile-to-run (GnuCOBOL 3.1.2, `-fsign=ebcdic`
  harness convention, same seeding as the wave-0 goldens)
- **Capture method:** `scripts/capture-wave1-inputs.sh`; keyed-order
  unloads via `tools/RDUNLD41.cbl` (LINE SEQUENTIAL, same convention as
  `tools/RDUNLOAD.cbl`)
- **XREFFILE order note:** the unload is in primary-key (card number)
  order. CBACT04C reads XREFFILE by the alternate key `FD-XREF-ACCT-ID`
  (`READ ... KEY IS`), which returns the first record for that account
  in primary-key order; the seed maps each account to exactly one card.
- Immutable once captured, exactly like goldens: a mismatch against
  `SHA256SUMS` means harness drift, never a reason to regenerate.
