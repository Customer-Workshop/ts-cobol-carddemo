### CSUTLDTC case results (cases derived: 19, run: 19, passed: 19, failed: 0)

| id | req | input date | mask | expected (sev/msg/reason) | actual (sev/msg/reason) | f80 byte-exact | verdict | conf |
|---|---|---|---|---|---|---|---|---|
| DTC-C01 | FR-23/DTC-01 | `20240229` | `YYYYMMDD` | 0000/0000/`Date is valid  ` | 0000/0000/`Date is valid  ` | yes | PASS | HIGH (FACT :88-100 :105-130 :42-57) |
| DTC-C02 | FR-23/DTC-01 | `2024-02-29` | `YYYY-MM-DD` | 0000/0000/`Date is valid  ` | 0000/0000/`Date is valid  ` | yes | PASS | HIGH (layout FACT; separator picture accepted by CEEDAYS = INFERRED) |
| DTC-C03 | FR-23/DTC-01 | `20240101  ` | `YYYYMMDD  ` | 0000/0000/`Date is valid  ` | 0000/0000/`Date is valid  ` | yes | PASS | HIGH (linkage FACT :84-85; blank tolerance INFERRED) |
| DTC-C04 | FR-23/DTC-01 | `20231231` | `YYYYMMDD` | 0000/0000/`Date is valid  ` | 0000/0000/`Date is valid  ` | yes | PASS | HIGH |
| DTC-C05 | FR-23/DTC-01 | `02292024` | `MMDDYYYY` | 0000/0000/`Date is valid  ` | 0000/0000/`Date is valid  ` | yes | PASS | MEDIUM (MMDDYYYY is a documented CEEDAYS picture; INFERRED) |
| DTC-C06 | FR-24/DTC-02 | `20240230` | `YYYYMMDD` | 0003/2508/`Datevalue error` | 0003/2508/`Datevalue error` | yes | PASS | severity HIGH; code INFERRED (D-0028) |
| DTC-C07 | FR-24/DTC-02 | `20230229` | `YYYYMMDD` | 0003/2508/`Datevalue error` | 0003/2508/`Datevalue error` | yes | PASS | severity HIGH; code INFERRED |
| DTC-C08 | FR-24/DTC-02 contractual | `15000101` | `YYYYMMDD` | 0003/2513/`Unsupp. Range  ` | 0003/2513/`Unsupp. Range  ` | yes | PASS | HIGH (contractual; callers branch on 2513 COTRN02C.cbl:398-401) |
| DTC-C09 | FR-24/DTC-02 boundary | `15821014` | `YYYYMMDD` | 0003/2513/`Unsupp. Range  ` | 0003/2513/`Unsupp. Range  ` | yes | PASS | INFERRED (A-DTC-3) |
| DTC-C10 | FR-23 boundary | `15821015` | `YYYYMMDD` | 0000/0000/`Date is valid  ` | 0000/0000/`Date is valid  ` | yes | PASS | INFERRED (A-DTC-3) |
| DTC-C11 | FR-23 boundary | `99991231` | `YYYYMMDD` | 0000/0000/`Date is valid  ` | 0000/0000/`Date is valid  ` | yes | PASS | INFERRED (A-DTC-3) |
| DTC-C12 | FR-24/DTC-02 | `20241301` | `YYYYMMDD` | 0003/2517/`Invalid month  ` | 0003/2517/`Invalid month  ` | yes | PASS | severity HIGH; code INFERRED |
| DTC-C13 | FR-24/DTC-02 | `20240001` | `YYYYMMDD` | 0003/2517/`Invalid month  ` | 0003/2517/`Invalid month  ` | yes | PASS | severity HIGH; code INFERRED |
| DTC-C14 | FR-24/DTC-02 | `2024AB01` | `YYYYMMDD` | 0003/2520/`Nonnumeric data` | 0003/2520/`Nonnumeric data` | yes | PASS | severity HIGH; code INFERRED |
| DTC-C15 | FR-24/DTC-02 | `          ` | `YYYYMMDD` | 0003/2507/`Insufficient   ` | 0003/2507/`Insufficient   ` | yes | PASS | severity HIGH; code INFERRED |
| DTC-C16 | FR-24/DTC-02 | `2024` | `YYYYMMDD` | 0003/2507/`Insufficient   ` | 0003/2507/`Insufficient   ` | yes | PASS | severity HIGH; code INFERRED |
| DTC-C17 | FR-24/DTC-02 | `20240229` | `ZZZZ` | 0003/2518/`Bad Pic String ` | 0003/2518/`Bad Pic String ` | yes | PASS | severity HIGH; code INFERRED |
| DTC-C18 | FR-24/DTC-02 | `20240100` | `YYYYMMDD` | 0003/2508/`Datevalue error` | 0003/2508/`Datevalue error` | yes | PASS | severity HIGH; code INFERRED |
| DTC-C19 | FR-24/DTC-02 | `20240432` | `YYYYMMDD` | 0003/2508/`Datevalue error` | 0003/2508/`Datevalue error` | yes | PASS | severity HIGH; code INFERRED |

### Structural / contract cases

- DTC-S01 B-0014 null date -> IllegalArgumentException -> **PASS**
- DTC-S02 B-0014 null mask -> IllegalArgumentException -> **PASS**
- DTC-S03 A-DTC-4 11-char date -> IllegalArgumentException (INFERRED policy) -> **PASS**
- DTC-S04 R-6 never throws over 1,000 mixed inputs; unmapped -> sev 3 'Date is invalid'; catalogue pairing holds -> **PASS (fallback 'Date is invalid' observed 81 times)**
