# COCRDUPC business-rule specification

## Purpose and trigger

CICS transaction `CCUP` fetches and updates a card record through `CARDDAT`. It supports search by account/card and a confirm-before-rewrite state.

## Inputs and outputs

Map `CCRDUPA`/mapset `COCRDUP`; files `CARDDAT` and `CARDAIX` are used. The persisted card record is:
| Offset | Field | PIC / representation | Length |
|---:|---|---|---:|
| 1 | CARD-NUM | `PIC X(16)` display | 16 |
| 17 | CARD-ACCT-ID | `PIC 9(11)` unsigned display numeric | 11 |
| 28 | CARD-CVV-CD | `PIC 9(03)` unsigned display numeric | 3 |
| 31 | CARD-EMBOSSED-NAME | `PIC X(50)` display | 50 |
| 81 | CARD-EXPIRAION-DATE | `PIC X(10)` display `YYYY-MM-DD` | 10 |
| 91 | CARD-ACTIVE-STATUS | `PIC X(01)` display | 1 |
| 92 | FILLER | `PIC X(59)` | 59 |


The update work area has old/new account `PIC X(11)`, card `PIC X(16)`, CVV `PIC X(3)`, name `PIC X(50)`, expiry components year `PIC X(4)`, month/day `PIC X(2)` each, and status `PIC X(1)`. The persisted numeric account/CVV fields are unsigned display `PIC 9(11)`/`PIC 9(03)`.

## Validation and error rules (source order)

1. First entry initializes `CCUP-DETAILS-NOT-FETCHED`; Enter, PF3, PF5-before-confirmation, and PF12-after-fetch are valid attention keys. Other keys are remapped to Enter.
2. Search key validation accepts account or card filters. Account must be a nonzero 11-digit number; exact source message for zero/non-numeric is `Account number must be a non zero 11 digit number`. Card, if supplied, must be 16 digits; exact message is `Card number if supplied must be a 16 digit number`.
3. Read the selected card through `CARDAIX`/`CARDDAT`. Missing account/card combination sets `Did not find cards for this search condition`; read failures set `Error reading Card Data File` or the constructed file-error message. No rewrite occurs.
4. When details are fetched, validate changed values in field order: embossed name must contain only alphabets/spaces (`Card name can only contain alphabets and spaces`); active status must be `Y` or `N` (`Card Active Status must be Y or N`); expiry month must be `1`–`12` (`Card expiry month must be between 1 and 12`); expiry year must satisfy the source current-date comparison (`Invalid card expiry year`).
5. If no field differs from the fetched old record, show `No change detected with respect to values fetched.` and do not rewrite. Valid changes move to confirmation state `CCUP-CHANGES-OK-NOT-CONFIRMED`; PF5 confirms and executes rewrite.
6. A failed lock/read displays `Could not lock record for update`; a changed-before-update condition displays `Record changed by some one else. Please review`; rewrite failure displays `Update of record failed`.

## Calculations

No monetary calculation. Numeric account/CVV text is converted back to unsigned display fields; expiry is stored as `YYYY-MM-DD` in the 10-byte field.

## Control flow and failure handling

PF3 returns via XCTL to caller/menu with common COMMAREA. PF12 reads/fetches details when not fetched. PF5 validates/confirm-updates; on completion the program can return to `COCRDLIC` (`CCLI`) or menu depending on `CDEMO-LAST-MAPSET`. `WS-THIS-PROGCOMMAREA` stores change action and old/new details; state values are `S` show, `E` validation error, `N` changes valid/not confirmed, `C` done, `L` lock error, `F` update failed. CICS `SYNCPOINT` is used around update completion.

## Test cases

| # | Concrete input | Expected output |
|---:|---|---|
| 1 | Search card `0500024453765740`; change name to `Immanuel Kessler`, status `Y`, expiry `2026-03-09`; confirm PF5 | `CARDDAT` rewrite for that card with account `00000000005`, CVV `074`, new name/date; state `C`. |
| 2 | Account filter `00000000005`, card blank | Card details fetched through account AIX; no rewrite until a changed field is confirmed. |
| 3 | Account filter `00000000000` | Exact `Account number must be a non zero 11 digit number`; no read. |
| 4 | Card filter `05000244537657A0` | Exact `Card number if supplied must be a 16 digit number`; no read. |
| 5 | Valid card, embossed name `A9` | Exact `Card name can only contain alphabets and spaces`; no rewrite. |
| 6 | Valid card, active status `X` | Exact `Card Active Status must be Y or N`; no rewrite. |
| 7 | Valid card, expiry month `00` | Exact `Card expiry month must be between 1 and 12`; no rewrite. |
| 8 | Valid card, no changed values, PF5 | Exact `No change detected with respect to values fetched.`; no rewrite. |
