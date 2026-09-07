### GnuCOBOL differential (real CSUTLDTC.cbl, stub CEEDAYS) vs Java formatted80

| id | cobol len | bytes 1-45 & 56-80 identical | COBOL bytes 46-55 (hex, DV-04 corrupted VSTRING) | Java bytes 46-55 | COBOL RETURN-CODE | RC == Java severity | verdict |
|---|---|---|---|---|---|---|---|
| DTC-C01 | 80 | yes | `000a3230323430323239` | `20240229  ` | RC=+000000000 | yes | MATCH |
| DTC-C02 | 80 | yes | `000a323032342d30322d` | `2024-02-29` | RC=+000000000 | yes | MATCH |
| DTC-C03 | 80 | yes | `000a3230323430313031` | `20240101  ` | RC=+000000000 | yes | MATCH |
| DTC-C04 | 80 | yes | `000a3230323331323331` | `20231231  ` | RC=+000000000 | yes | MATCH |
| DTC-C05 | 80 | yes | `000a3032323932303234` | `02292024  ` | RC=+000000000 | yes | MATCH |
| DTC-C06 | 80 | yes | `000a3230323430323330` | `20240230  ` | RC=+000000003 | yes | MATCH |
| DTC-C07 | 80 | yes | `000a3230323330323239` | `20230229  ` | RC=+000000003 | yes | MATCH |
| DTC-C08 | 80 | yes | `000a3135303030313031` | `15000101  ` | RC=+000000003 | yes | MATCH |
| DTC-C09 | 80 | yes | `000a3135383231303134` | `15821014  ` | RC=+000000003 | yes | MATCH |
| DTC-C10 | 80 | yes | `000a3135383231303135` | `15821015  ` | RC=+000000000 | yes | MATCH |
| DTC-C11 | 80 | yes | `000a3939393931323331` | `99991231  ` | RC=+000000000 | yes | MATCH |
| DTC-C12 | 80 | yes | `000a3230323431333031` | `20241301  ` | RC=+000000003 | yes | MATCH |
| DTC-C13 | 80 | yes | `000a3230323430303031` | `20240001  ` | RC=+000000003 | yes | MATCH |
| DTC-C14 | 80 | yes | `000a3230323441423031` | `2024AB01  ` | RC=+000000003 | yes | MATCH |
| DTC-C15 | 80 | yes | `000a2020202020202020` | `          ` | RC=+000000003 | yes | MATCH |
| DTC-C16 | 80 | yes | `000a3230323420202020` | `2024      ` | RC=+000000003 | yes | MATCH |
| DTC-C17 | 80 | yes | `000a3230323430323239` | `20240229  ` | RC=+000000003 | yes | MATCH |
| DTC-C18 | 80 | yes | `000a3230323430313030` | `20240100  ` | RC=+000000003 | yes | MATCH |
| DTC-C19 | 80 | yes | `000a3230323430343332` | `20240432  ` | RC=+000000003 | yes | MATCH |

Result: ALL MATCH (19/19). DV-04 is confirmed as FACT by the compiled COBOL: bytes 46-47 are the VSTRING length (X'000A' for a 10-byte LS-DATE) and 48-55 the first 8 date chars; the target intentionally writes the 10-char date (CSUTLDTC.cbl:108, :122; FR §7 DV-04).
The stub returns the code named in cases.csv, so this run proves the COBOL formatting path only; the CEEDAYS mapping remains INFERRED (D-0028).
