# COSGN00C parity case table (derived BEFORE opening the Java/Angular)

Sources: `app/cbl/COSGN00C.cbl` (cbl), `app/bms/COSGN00.bms` (bms), `app/cpy-bms/COSGN00.CPY`, `app/cpy/CSUSR01Y.cpy`,
`app/cpy/CSMSG01Y.cpy`, `app/cpy/COCOM01Y.cpy`, `app/cpy/COTTL01Y.cpy`, `app/cpy/CSDAT01Y.cpy`,
`functional/CardDemo/programs/COSGN00C_functional_requirement.md` (FR). Fixture: `app/data/EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS`
(10 rows ADMIN001..005 type A, USER0001..0005 type U, password `PASSWORD` — confirmed in wave 1 parity §3).
No customer-supplied cases were provided. Surface per FR §1: `POST /api/auth/signon`, `GET /api/auth/session`, `POST /api/auth/signoff`, Angular `/signon`.

Normalization rule (both sides): compare message text after stripping trailing spaces only (COBOL `WS-MESSAGE` is X(80) space padded,
`CSMSG01Y` literals are X(50) space padded); everything else exact.

| Id | Req (FR / stream) | Input | Seed | Expected observable result | Source / cite | Conf. |
|---|---|---|---|---|---|---|
| P-01 | SGN-01 / FR-01 | fresh `GET /signon` (EIBCALEN=0) | fixture | screen: User ID empty with focus (`USERIDL=-1`, bms `IC`), password masked (bms `DRK`), `Tran :` `CC00`, `Prog :` `COSGN00C`, `Date :` `mm/dd/yy`, `Time :` `hh:mm:ss`, `AppID:`, `SysID:`, titles `      AWS Mainframe Modernization       ` / `              CardDemo                  `, footer `ENTER=Sign-on  F3=Exit`, ERRMSG blank | cbl:80-84,145-157,177-204; bms:29-205; COTTL01Y:18-22 | HIGH |
| P-02 | SGN-02 / FR-02 | userId blank, password `X` | none | E-01 `Please enter User ID ...`, focus User ID, no USRSEC read | cbl:118-122,138-140 | HIGH |
| P-03 | SGN-03 / FR-03 | userId `USER0001`, password blank | fixture | E-02 `Please enter Password ...`, focus Password, no USRSEC read | cbl:123-127,138-140 | HIGH |
| P-04 | V-1/V-2 order | both blank | none | E-01 only (first `WHEN` of `EVALUATE TRUE`) | cbl:117-130 | HIGH |
| P-05 | SGN-04 / FR-04 | `user0001` / `password` | fixture | identical to upper-case: success, userId `USER0001`, type `U` | cbl:132-136 | HIGH |
| P-06 | SGN-05 / FR-05(SGN) | `USER0001` / `PASSWORD` | fixture | success; COMMAREA `CDEMO-USER-ID='USER0001'`, `CDEMO-USER-TYPE='U'`; control to COMEN01C == `landingTarget='/menu'`; session shows userId/userType | cbl:221-229,235-239; COCOM01Y:27-28; FR §3.4 | HIGH |
| P-07 | SGN-06 / FR-06 | `USER0001` / `WRONGPW` | fixture | E-06 `Wrong Password. Try again ...`, focus Password, no session | cbl:241-246 | HIGH |
| P-08 | SGN-07 / FR-07 | `NOBODY01` / any | fixture (id absent) | E-07 `User not found. Try again ...`, focus User ID (RESP 13) | cbl:247-251 | HIGH |
| P-09 | SGN-08 / FR-08 | PF3 / Exit | any | plain text `Thank you for using CardDemo application...` (X(50), trailing spaces), conversation ends; target: signoff invalidates session | cbl:88-90,162-172; CSMSG01Y:18-19 | HIGH |
| P-10 | SGN-09 / FR-22 | USRSEC read RESP other than 0/13 (DB outage) | DB stopped | E-13 `Unable to verify the User ...`, focus User ID | cbl:252-256 | HIGH (text) / MED (trigger is simulated) |
| P-11 | SGN-10 / FR-25(SGN), DV-03 | unbound key (F5) on sign-on | none | legacy E-03 `Invalid key pressed. Please see below...`; target DV-03: nothing happens, no message; `Invalid key` text absent from the sign-on slice | cbl:91-94; CSMSG01Y:20-21; FR §7 DV-03 | HIGH |
| P-12 | Q-01 / B-0009 | `ADMIN001` / `PASSWORD` | fixture | authenticates (`SEC-USR-PWD` match), `CDEMO-USER-TYPE='A'`; legacy XCTL COADM01C (excluded) -> target `landingTarget='/menu'`, session userType `A`; no `/admin` route | cbl:223-234; FR §5 Q-01 | HIGH |
| P-13 | Q-13 / B-0026 / D-0029 | legacy row (`sec_usr_pwd_legacy='PASSWORD'`, hash NULL) sign on twice | fixture import | 1st call success and row now `sec_usr_pwd_hash` LIKE `$2%`, `sec_usr_pwd_legacy IS NULL`; 2nd call success against hash; hash unchanged between calls (written once) | FR §3.4, §5 target rules; V-5 | HIGH |
| P-13b | Q-13 (negative) | wrong password on a legacy row | fixture import | E-06 and row **unchanged** (hash still NULL, legacy still set) | V-5 + FR §5 "on successful match" | HIGH |
| P-13c | Q-13 (post-upgrade) | wrong password after upgrade | upgraded row | E-06 text identical to P-07 | cbl:241-246 | HIGH |
| P-14 | V-7 / B-0026 | 10 wrong then right | fixture | 11th succeeds, no lockout | cbl:207-257 (absence) | HIGH |
| P-15 | B-0027 / B-0031 | unauthenticated `GET` protected URL; signoff then `GET /api/auth/session` | none | JSON 401 (no HTML/redirect); after signoff session gone -> 401 | FR §6 B-0027, B-0026 | HIGH |
| P-16 | D-0016 | inspect routes/endpoints | none | `CC00` only in header text, never in URL | FR AC-SGN-16 | HIGH |
| P-17 | V-8 / A-SGN-1 | userId 9 chars | none | legacy: impossible (map length 8); target: 400 with a NEW TEXT (not a legacy literal) | COSGN00.CPY:72,78; FR §7 | MED |
| P-18 | V-5 (padding) | `USER0001` / `PASSWORD ` (trailing space) | fixture | legacy 8-char field cannot carry a 9th byte; target result documented (informational, PASS-WITH-RISK if 400 or success) | cbl:223 | LOW |
| P-19 | SGN-01 field map | Angular `/signon` DOM | — | fields: userId maxlength 8, password maxlength 8 `type=password`; labels `User ID     :`, `Password    :`, hints `(8 Char)`; display-only Tran/Prog/Date/Time/AppID/SysID; ERRMSG line; footer text verbatim; prompt `Type your User ID and Password, then press ENTER:` | bms:29-205 | HIGH |
| P-20 | FR §3.4 session content | after P-06, `GET /api/auth/session` | fixture | only `userId`, `userType` (COMMAREA fields at cbl:226-227); no password/legacy value in any response | cbl:224-228; D-0030 | HIGH |

FR-doc vs COBOL notes made while deriving:
- FR §2 says COMEN01C PF3 `XCTL COSGN00C` "without COMMAREA" -> EIBCALEN=0. Not in scope for this program; not verified here.
- `COTTL01Y.cpy` also carries `CCDA-THANK-YOU 'Thank you for using CCDA application... '` (X(40)) — **unused** by COSGN00C, which uses `CCDA-MSG-THANK-YOU` from `CSMSG01Y` (cbl:89). Expected exit text is the CSMSG01Y one.
- `PASSWD` BMS field has `INITIAL='________'` (bms:180) — a placeholder of 8 underscores in the DRK field; invisible on 3270 (DRK). Informational only.
- CURTIME BMS length is 9 with INITIAL `Ahh:mm:ss` (bms:72-74) but the program moves an 8-char `WS-CURTIME-HH-MM-SS` (cbl:196); observable is `hh:mm:ss`.
