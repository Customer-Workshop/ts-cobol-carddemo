# COACTVWC parity cases (derived BEFORE opening backend/ or frontend/)

Sources: `app/cbl/COACTVWC.cbl` (cited `cbl:`), `app/bms/COACTVW.bms` (`bms:`), `app/cpy-bms/COACTVW.CPY` (`CPY:`),
copybooks `CVACT01Y`, `CVACT03Y`, `CVCUS01Y`, `CVCRD01Y`, `COCOM01Y`, `CSMSG01Y`, `CSSTRPFY`,
`functional/CardDemo/programs/COACTVWC_functional_requirement.md` (`FR:`), raw `app/data/ASCII/*.txt`.
No customer-supplied cases exist for this program (stream context: behaviour is source-derived).
Expected values for the fixture accounts are produced by `derive_expected.py` from the raw bytes (`expected_fixture.json`).

## Message literals (byte-exact expectations)

| Id | Text | Cite |
|---|---|---|
| INFO | `Enter or update id of account to display` (40) — the only info text ever displayed; `WS-INFORM-OUTPUT` is dead | `cbl:112-115`, `:456-458`, FR §7 dead 88-levels |
| E-04 | `No input received` | `cbl:121-122`, set at `:640-642` (overwrites `WS-PROMPT-FOR-ACCT` set at `:657`) |
| E-05 | `Account Filter must  be a non-zero 11 digit number` (two spaces after `must`) | `cbl:671-672` |
| E-09 | `Account:` + 11 + ` not found in` + ` Cross ref file.  Resp:` + RESP(10) + ` Reas:` + RESP2(10) — natural 81 bytes, **stored in X(75)** | `cbl:744-757`, `:117` |
| E-10 | `Account:` + 11 + ` not found in` + ` Acct Master file.Resp:` + RESP(10) + ` Reas:` + RESP2(10) — natural 81, X(75) | `cbl:794-806`, `:117` |
| E-11 | `CustId:` + 9 + ` not found` + ` in customer master.Resp: ` + RESP(10) + ` REAS:` + RESP2(10) — natural 78, X(75) | `cbl:843-856`, `:117` |
| E-12 | `File Error: ` + OPNAME(8) + ` on ` + FILE(9) + ` returned RESP ` + RESP(10) + `,RESP2 ` + RESP2(10) + 5 spaces — 80 bytes, X(75) | `cbl:86-105`, `:760-766`, `:810-816`, `:859-865` |
| CSMSG01Y | `Thank you for using CardDemo application...`, `Invalid key pressed. Please see below...` — **never referenced by COACTVWC** (PF keys other than ENTER/PF3 are folded to ENTER, `cbl:306-314`) | `CSMSG01Y.cpy:19,21` |

Q-09 (FR §6.5/§10): literal parts asserted verbatim; RESP/RESP2 digits are NOT asserted.

## Flag #1 ruling input: does `WS-RETURN-MSG PIC X(75)` truncate?

`STRING ... DELIMITED BY SIZE INTO WS-RETURN-MSG` (no `ON OVERFLOW`, no `POINTER`) stops when the 75-byte receiver is full
(ANSI/IBM STRING semantics). `MOVE WS-RETURN-MSG TO ERRMSGO` (`cbl:532`, `CPY:464` `X(78)`) then pads with 3 blanks —
the map field is longer than the message store, so 75 bytes IS the observable maximum on `ERRMSG` (`bms` `LENGTH=78`, `POS=(23,1)`).
`ERROR-RESP`/`ERROR-RESP2` are `X(10)` filled by `MOVE S9(9) COMP` -> `X(10)` (`cbl:748-749`): 9 digit characters left-justified + 1 blank.

| Msg | prefix bytes before RESP2 | RESP2 bytes visible | Truncated? |
|---|---|---|---|
| E-09 | 8+11+13+23+10+6 = 71 | 4 of 10 | YES (mid-RESP2: 4 digit chars visible) |
| E-10 | 8+11+13+23+10+6 = 71 | 4 of 10 | YES (mid-RESP2: 4 digit chars visible) |
| E-11 | 7+9+10+26+10+6 = 68 | 7 of 10 | YES (mid-RESP2: 7 digit chars visible) — **not in the child's flag** |
| E-12 | 12+8+4+9+15+10+7 = 65 | 10 of 10 (65+10 = 75) | **NO** — only the trailing 5-blank FILLER is cut; RESP2 fully visible — **child's flag on E-12 is wrong** |

## Case table

Legend: status codes per FR §6.1 mapping (400 E-04/E-05, 404 E-09/E-10/E-11, 500 E-12, 401 no session).
Normalization rule (both sides): trailing blanks stripped; money compared as `sign + digits` after removing blanks/commas; dates as `YYYY-MM-DD` text.

| Case | Req | Input / seed | Expected observable (COBOL/FR) | Source | Conf |
|---|---|---|---|---|---|
| A-01 | ACV-01 / AC-01 | GET view state with session, no search | prompt screen: INFO text, empty field, no data, no error (`cbl:353-360`, `:456-458`) | FR | high |
| A-02a | ACV-02 / AC-02 | account `` (empty) | 400, message `No input received`; field `*` red (`cbl:628-633`, `:640-642`, `:561-565`) | COBOL | high |
| A-02b | ACV-02 | account `*` | same as A-02a (`cbl:628-629`) | COBOL | high |
| A-02c | ACV-02 | account `           ` (11 spaces) | same (`cbl:629`, `:653`) | COBOL | high |
| A-03a | ACV-03 / AC-03 | `00000000000` | 400 E-05 (`cbl:666-667`) | COBOL | high |
| A-03b | ACV-03 | `1234567890A` | 400 E-05 (`NOT NUMERIC`) | COBOL | high |
| A-03c | ACV-03 | `12 34567890` | 400 E-05 | COBOL | high |
| A-03d | ACV-03 / Q-02 | `1234567890` (10 digits) | 400 E-05 (INFERRED Q-02, `bms:84-89`) | FR | med |
| A-03e | ACV-03 | `123456789012` (12 chars) | 400 E-05 (FR §7 "account >11 chars") | FR | med |
| A-03f | ACV-03 | `-0000000001` | 400 E-05 (`NOT NUMERIC` for X(11) with sign char) | COBOL | high |
| A-04 | ACV-04 / AC-04 | `00000000099` — no xref row (fixture has acct 1..50 only) | 404, message starts `Account:00000000099 not found in Cross ref file.  Resp:` and contains ` Reas:`; no data (`cbl:741-758`) | mock | high |
| A-05 | ACV-05 / AC-05 / DV-01 | seed xref card `9990000000000001` cust 000000001 acct `00000000901`, no accounts row | 404, message starts `Account:00000000901 not found in Acct Master file.Resp:` contains ` Reas:`; **no account, no customer block** (DV-01; legacy would show customer block, `cbl:792`, `:704-706`) | mock | high |
| A-06 | ACV-06 / AC-06 | seed xref card `9990000000000002` cust `999999902` acct `00000000902` + accounts row 902 copied from acct 1 values, no customer 999999902 | 404, message starts `CustId:999999902 not found in customer master.Resp: ` contains ` REAS:`; **account block present** (`cbl:493`, `:471-491`), customer block absent | mock | high |
| A-07 | ACV-07 / AC-07 | `00000000027` (fixture) | 200; account + customer blocks = `expected_fixture.json` (`cbl:471-523`) | COBOL+raw | high |
| A-08 | ACV-07 / AC-08 field parity | 5 fixture accounts `00000000001`, `010`, `027`, `033`, `050` | every displayed field == raw-byte derivation; money via `+ZZZ,ZZZ,ZZZ.99`; SSN `999-99-9999` (`cbl:496-504`); FICO 3 digits; flags 1 char | raw | high |
| A-09 | DV-05 / AC-09 | `00000000027` (ZIP `07923-8822` = 10 chars, phone `(935)027-1145  ` = 15 chars) | full ZIP and full 15-char phone (trailing blanks normalised) shown, not 5/13 (`bms:290`, `:318`) | FR | high |
| A-10 | DV-06 / D-0038 | any fixture account | `Account Group` shows `A000000000` (legacy: blank, `cbl:490` with `ACCT-GROUP-ID` bytes 113-122 = spaces) — approved deviation, PASS-WITH-RISK not FAIL | FR/D-0038 | high |
| A-11 | Q-04 / AC-10 | seed xref rows for acct `00000000903`: cards `9993000000000002` (cust 000000002) and `9993000000000001` (cust 000000003), inserted higher-card-first; accounts row 903 | 200 with customer `000000003` (lowest card number wins, `cbl:727-735`, Q-04) | mock | high |
| A-12 | ACV-08 / AC-11 (E-12 xref) | PostgreSQL stopped, then `GET` a valid id | 500, message starts `File Error: READ` and contains `CXACAIX` and `returned RESP` and `,RESP2 ` (`cbl:760-766`); D-0040 class | mock | high |
| A-13 | ACV-08 (E-12 acct) | not reachable from outside without fault injection: xref read fails first | UNTESTED at HTTP level unless the suite's mocked test exists (report from backend suite) | mock | — |
| A-14 | ACV-08 (E-12 cust) | as A-13 | UNTESTED at HTTP level (same reason) | mock | — |
| A-15 | ACV-09 / AC-12 / B-0011 | PF3 / Exit / Esc | navigate to `/menu`; no API call (`cbl:324-352` XCTL to `COMEN01C`); DV-02 session unchanged | COBOL/FR | high |
| A-16 | DV-03 / AC-15 | press F5 on the form | nothing happens, no `Invalid key` message (CSMSG01Y not referenced by this program) | FR | high |
| A-17 | AC-16 401 | `GET /api/accounts/{id}` without cookie | 401 (FR §7 `CAVW` without COMMAREA -> 401) | FR | high |
| A-18 | Q-14 / AC-17 | payload/path validation: path with URL-encoded space, 30-char id | 400 E-05 class, never 500 | FR | med |
| A-19 | B-0027/B-0028 / DV-02 / AC-14 | session before/after view and Exit | `GET /api/auth/session` identical userId/userType | FR | high |
| A-20 | AC-18 generic 500 | out-of-read-path failure | UNTESTED (no injection point without modifying code) | — | — |
| A-21 | UI map | `/accounts/view` header `CAVW`/`COACTVWC`/`View Account`; `ACCTSID` `maxlength=11`; all 30 output fields; INFOMSG; ERRMSG red | `bms` field list, `CPY:248-464` | BMS | high |
| A-22 | Route guard | `/accounts/view` without session -> `/signon` | FR §7 session expiry | FR | high |
| A-23 | E-11 UI | A-06 id in the browser | account fields populated, customer fields blank, red E-11 | `cbl:471-493` | COBOL | high |
| A-24 | Truncation (flag #1) | messages E-09/E-10/E-11/E-12 observed vs the 75-byte rule above | see ruling in results §3 | COBOL | high |
