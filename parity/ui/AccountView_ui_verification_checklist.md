# S-01 AccountView — UI verification checklist

Stream: S-01 AccountView (CardDemo core). Journey under test: `/signon` (COSGN00C) -> `/menu` (COMEN01C) ->
option 1 -> `/accounts/view` (COACTVWC) -> Exit -> `/menu` -> Exit -> `/signon`. Hard stop: nothing beyond
Account View; menu options 2-11 only show `Option not available in this release`.

Requirement source: `functional/CardDemo/AccountView_functional_requirement.md` (FR-01..FR-25). One case per
requirement. Locale: English only (the legacy BMS maps carry English literals; no other locale exists), so every
case has one variant.

## How to run

```bash
# 1. PostgreSQL 16 (real database; first boot is slow)
docker run -d --name carddemo-pg -e POSTGRES_USER=carddemo -e POSTGRES_PASSWORD=carddemo \
  -e POSTGRES_DB=carddemo -p 5433:5432 postgres:16
docker exec carddemo-pg pg_isready -U carddemo

# 2. One-shot legacy import (50 accounts / 50 customers / 50 card xrefs / 10 users)
cd backend && CARDDEMO_DB_USER=carddemo CARDDEMO_DB_PASSWORD=carddemo \
  JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B spring-boot:run -Dspring-boot.run.profiles=import \
  -Dspring-boot.run.arguments="--spring.main.web-application-type=none"

# 3. Synthetic rows for FR-17 / FR-18 (accounts 901, 902, 903)
PGPASSWORD=carddemo psql -h localhost -p 5433 -U carddemo -d carddemo -f parity/wave4/coactvwc/seed_synthetic.sql

# 4. Backend (port 8080) and Angular dev server (port 4200, proxies /api to 8080)
cd backend  && CARDDEMO_DB_USER=carddemo CARDDEMO_DB_PASSWORD=carddemo \
  JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B spring-boot:run
cd frontend && npm ci && npm start          # Node 22
```

Open `http://localhost:4200/` in a maximised browser. Test users (fixture): `USER0001` / `PASSWORD` (type U),
`ADMIN001` / `PASSWORD` (type A).

## How to capture

Maximise the browser window. For each case: start the recording, show the empty screen, type the inputs, submit,
hold the result long enough to read, stop. Annotate setup, test start and assertion. Save a screenshot per case
under `parity/ui/evidence/<case>.png`. Pass criteria are business-visible (field values, message text, which
screen is shown), never internal codes.

## Case table

| # | Case | Input | Expected result | Pass criteria |
|---|---|---|---|---|
| FR-01 | Sign-on screen | open `http://localhost:4200/` | Sign-on screen, User ID empty with focus, Password masked, header `CC00` / `COSGN00C`, date `mm/dd/yy`, time `hh:mm:ss`, AppID/SysID, footer `ENTER=Sign-on  F3=Exit`, no message | all header items present, focus in User ID, no error |
| FR-02 | Blank User ID | User ID blank, Password blank, Sign on | `Please enter User ID ...`, focus User ID | message text + focus |
| FR-03 | Blank Password | `USER0001`, Password blank, Sign on | `Please enter Password ...`, focus Password | message text + focus |
| FR-04 | Upper-casing | `user0001` / `password` | Signs on, menu shown | menu appears exactly as with upper case |
| FR-05 | Sign-on success (U) | `USER0001` / `PASSWORD` | Main menu shown; user id `USER0001` carried | menu screen visible |
| FR-05b | Admin lands on menu (Q-01) | `ADMIN001` / `PASSWORD` | Main menu shown (no admin menu in this release); a notice that admin functions are unavailable | menu visible for type A |
| FR-06 | Wrong password | `USER0001` / `WRONGPW` | `Wrong Password. Try again ...`, focus Password, still on sign-on | message + focus + no navigation |
| FR-07 | Unknown user | `NOBODY01` / `PASSWORD` | `User not found. Try again ...`, focus User ID | message + focus |
| FR-08 | Sign-on exit | Exit (button / F3 / Esc) on sign-on | `Thank you for using CardDemo application...`; nothing else | thank-you text shown |
| FR-09 | Menu display | after sign-on | header `CM00` / `COMEN01C`, 11 options `01. Account View` .. `11. Pending Authorization View`, footer `ENTER=Continue  F3=Exit`, option field empty | 11 options listed in order, header + footer present |
| FR-10 | Invalid option | Continue with option blank, `0`, `12`, `A` | `Please enter a valid option number...`; menu stays; option echoes `00`, `00`, `12`, `0A` | message + echoed value |
| FR-10b | Facade option (hard stop) | option `5` (also spot-check another 2-11) | `Option not available in this release`; menu stays | message, no navigation |
| FR-11 | Option 1 | option `1` (also ` 1`, `01`, `1 `) | Account View entry screen | `/accounts/view` shown |
| FR-12 | Menu exit | Exit / F3 / Esc on menu | fresh sign-on screen; identity dropped (Back button does not return to menu) | sign-on shown; deep-link to `/menu` redirects to `/signon` |
| FR-13 | Account View entry | arrive via option 1 | header `CAVW` / `COACTVWC` / title, Account Number empty with focus, info line `Enter or update id of account to display`, all data fields blank, no error | prompt state |
| FR-14 | Blank account | Search with empty field; also with `*` | red `No input received`; field shows red `*`; no data | message + red `*` |
| FR-15 | Bad account filter | `00000000000`, `1234567890A`, `1234567890` (10 digits) | red `Account Filter must  be a non-zero 11 digit number`; value echoed; no data | message for each |
| FR-16 | No card xref | `00000000099` | `Account:00000000099 not found in Cross ref file.  Resp:... Reas:...`; no data | message prefix, empty blocks |
| FR-17 | Account master missing (DV-01) | `00000000901` | `Account:00000000901 not found in Acct Master file.Resp:... Reas:...`; **no** account or customer data | message prefix, both blocks blank |
| FR-18 | Customer missing | `00000000902` | `CustId:999999902 not found in customer master.Resp: ... REAS:...`; account block filled, customer block blank | message + account block visible + customer blank |
| FR-19 | Success | `00000000027` | status `Y`, opened `2012-09-30`, expiry `2025-07-13`, reissue `2025-07-13`, credit limit `+      5,572.00`, cash limit `+      2,075.00`, balance `+        284.00`, cycle credit/debit `+           .00`, group `A000000000` (DV-06); customer `000000027`, SSN `980-16-1210`, DOB `1986-11-08`, FICO `078`, `Ward Henri Jones`, `210 Amaya Turnpike` / `Suite 180` / `Port Dwight`, `GU`, ZIP `07923-8822` (DV-05), `USA`, phones `(935)027-1145` / `(103)537-5007` (DV-05), govt id `00000000000881558757`, EFT `0050024139`, primary `Y`; info line unchanged; no error | every field equals `parity/wave4/coactvwc/expected_fixture.json` (DV-05/06 variants) |
| FR-20 | Data-store failure | `docker stop carddemo-pg`, then search `00000000027`; restart afterwards | red `File Error: READ     on CXACAIX   returned RESP ...,RESP2 ...`; no data | message prefix, no data |
| FR-21 | Account View exit (hard stop) | Exit / F3 / Esc after a successful search | Main menu shown; returning to Account View shows the empty entry state | menu visible, search state gone |
| FR-22 | Sign-on store failure | `docker stop carddemo-pg`, then sign on `USER0001` / `PASSWORD` | `Unable to verify the User ...`, focus User ID | message + focus |
| FR-23 | Date validation valid | internal utility (CSUTLDTC), no screen | — | NOT UI-VERIFIABLE: covered by `DateValidationServiceTest` (wave 1 parity) |
| FR-24 | Date validation invalid | internal utility, no screen | — | NOT UI-VERIFIABLE: as FR-23 |
| FR-25 | Invalid key | key other than ENTER/F3 (e.g. F5) | dropped by approved deviation DV-03 (no browser trigger; browser-native keys out of scope) | pressing F1/F2 on sign-on and menu shows no `Invalid key` text and does not break the screen |
| X-01 | Deep links unauthenticated | open `/menu` and `/accounts/view` in a fresh session | redirected to `/signon` | URL ends `/signon` |
| X-02 | Session survives navigation | sign on, go menu -> account view -> reload page | still signed on (account view still reachable) | no redirect to sign-on |
| X-03 | Sign-off drops session | menu Exit, then browser Back | redirected to `/signon` | no menu shown |
| X-04 | Keyboard-only + accessibility | Tab through each screen | Tab order follows BMS field order (sign-on: User ID -> Password -> Sign on -> Exit; menu: option -> Continue -> Exit; account view: account -> Search -> Exit); every input has a bound label; focus ring visible | order + labels + visible focus |

## Notes

- FR-20 and FR-22 use a real outage (stopping the PostgreSQL container); restart it (`docker start carddemo-pg`)
  before continuing. RESP/RESP2 digits are target values, not CICS bytes (Q-09) — verify the literal prefix only.
- FR-17 shows no customer block by design (approved deviation DV-01); the legacy quirk would show one.
- Account Group `A000000000` is approved deviation DV-06 (legacy showed blank). Full ZIP/phone is DV-05.
- Stopping at the Account View exit is the stream hard stop; options 2-11 are facades and nothing behind them is tested.
