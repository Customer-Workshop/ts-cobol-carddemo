# BMS screen specifications

Field metadata is transcribed from the named `DFHMDF` definitions in `app/bms/`. `PROT` means protected output, `UNPROT` means input-capable, and `ASKIP` means the cursor skips the field. A blank initial value means no `INITIAL` clause; `—` means the source omits that attribute.

## COACTUP / CACTUPA (`COACTUP.bms`)

**Map geometry:** `24,80`. **Layout:** Header rows 1–2 carry transaction/date/program/title labels; body fields occupy the remaining rows; footer fields carry messages and PF-key text. PF-key line fields: FKEYS, FKEY05, FKEY12.

| Field | Row/column | Length | Protection/skip | Type | IC | FSET | Initial/literal | Colour | Highlight |
|---|---|---:|---|---|---|---|---|---|---|
| `TRNNAME` | `1,7` | 4 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE01` | `1,21` | 40 | `ASKIP,NORM` | alphanumeric | no | no | `—` | YELLOW | — |
| `CURDATE` | `1,71` | 8 | `ASKIP,NORM` | alphanumeric | no | no | `mm/dd/yy` | BLUE | — |
| `PGMNAME` | `2,7` | 8 | `ASKIP,NORM` | alphanumeric | no | no | `—` | BLUE | — |
| `TITLE02` | `2,21` | 40 | `ASKIP,NORM` | alphanumeric | no | no | `—` | YELLOW | — |
| `CURTIME` | `2,71` | 8 | `ASKIP,NORM` | alphanumeric | no | no | `hh:mm:ss` | BLUE | — |
| `ACCTSID` | `5,38` | 11 | `IC,UNPROT` | alphanumeric | yes | no | `—` | — | UNDERLINE |
| `ACSTTUS` | `5,70` | 1 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `OPNYEAR` | `6,17` | 4 | `FSET,UNPROT` | alphanumeric | no | yes | `—` | — | UNDERLINE |
| `OPNMON` | `6,24` | 2 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `OPNDAY` | `6,29` | 2 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACRDLIM` | `6,61` | 15 | `FSET,UNPROT` | alphanumeric | no | yes | `—` | — | UNDERLINE |
| `EXPYEAR` | `7,17` | 4 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `EXPMON` | `7,24` | 2 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `EXPDAY` | `7,29` | 2 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSHLIM` | `7,61` | 15 | `FSET,UNPROT` | alphanumeric | no | yes | `—` | — | UNDERLINE |
| `RISYEAR` | `8,17` | 4 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `RISMON` | `8,24` | 2 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `RISDAY` | `8,29` | 2 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACURBAL` | `8,61` | 15 | `FSET,UNPROT` | alphanumeric | no | yes | `—` | — | UNDERLINE |
| `ACRCYCR` | `9,61` | 15 | `FSET,UNPROT` | alphanumeric | no | yes | `—` | — | UNDERLINE |
| `AADDGRP` | `10,23` | 10 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACRCYDB` | `10,61` | 15 | `FSET,UNPROT` | alphanumeric | no | yes | `—` | — | UNDERLINE |
| `ACSTNUM` | `12,23` | 9 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACTSSN1` | `12,55` | 3 | `UNPROT` | alphanumeric | no | no | `999` | — | UNDERLINE |
| `ACTSSN2` | `12,61` | 2 | `UNPROT` | alphanumeric | no | no | `99` | — | UNDERLINE |
| `ACTSSN3` | `12,66` | 4 | `UNPROT` | alphanumeric | no | no | `9999` | — | UNDERLINE |
| `DOBYEAR` | `13,23` | 4 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `DOBMON` | `13,30` | 2 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `DOBDAY` | `13,35` | 2 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSTFCO` | `13,62` | 3 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSFNAM` | `15,1` | 25 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSMNAM` | `15,28` | 25 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSLNAM` | `15,55` | 25 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSADL1` | `16,10` | 50 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSSTTE` | `16,73` | 2 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSADL2` | `17,10` | 50 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSZIPC` | `17,73` | 5 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSCITY` | `18,10` | 50 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSCTRY` | `18,73` | 3 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSPH1A` | `19,10` | 3 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSPH1B` | `19,14` | 3 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSPH1C` | `19,18` | 4 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSGOVT` | `19,58` | 20 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSPH2A` | `20,10` | 3 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSPH2B` | `20,14` | 3 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSPH2C` | `20,18` | 4 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSEFTC` | `20,41` | 10 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSPFLG` | `20,78` | 1 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `INFOMSG` | `22,23` | 45 | `ASKIP` | alphanumeric | no | no | `—` | NEUTRAL | OFF |
| `ERRMSG` | `23,1` | 78 | `ASKIP,BRT,FSET` | alphanumeric | no | yes | `—` | RED | — |
| `FKEYS` | `24,1` | 21 | `ASKIP,NORM` | alphanumeric | no | no | `ENTER=Process F3=Exit` | YELLOW | — |
| `FKEY05` | `24,23` | 7 | `ASKIP,DRK` | alphanumeric | no | no | `F5=Save` | YELLOW | — |
| `FKEY12` | `24,31` | 10 | `ASKIP,DRK` | alphanumeric | no | no | `F12=Cancel` | YELLOW | — |

## COACTVW / CACTVWA (`COACTVW.bms`)

**Map geometry:** `24,80`. **Layout:** Header rows 1–2 carry transaction/date/program/title labels; body fields occupy the remaining rows; footer fields carry messages and PF-key text.

| Field | Row/column | Length | Protection/skip | Type | IC | FSET | Initial/literal | Colour | Highlight |
|---|---|---:|---|---|---|---|---|---|---|
| `TRNNAME` | `1,7` | 4 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE01` | `1,21` | 40 | `ASKIP,NORM` | alphanumeric | no | no | `—` | YELLOW | — |
| `CURDATE` | `1,71` | 8 | `ASKIP,NORM` | alphanumeric | no | no | `mm/dd/yy` | BLUE | — |
| `PGMNAME` | `2,7` | 8 | `ASKIP,NORM` | alphanumeric | no | no | `—` | BLUE | — |
| `TITLE02` | `2,21` | 40 | `ASKIP,NORM` | alphanumeric | no | no | `—` | YELLOW | — |
| `CURTIME` | `2,71` | 8 | `ASKIP,NORM` | alphanumeric | no | no | `hh:mm:ss` | BLUE | — |
| `ACCTSID` | `5,38` | 11 | `FSET,IC,NORM,UNPROT` | alphanumeric | yes | yes | `—` | GREEN | UNDERLINE |
| `ACSTTUS` | `5,70` | 1 | `ASKIP` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ADTOPEN` | `6,17` | 10 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACRDLIM` | `6,61` | 15 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `AEXPDT` | `7,17` | 10 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSHLIM` | `7,61` | 15 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `AREISDT` | `8,17` | 10 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACURBAL` | `8,61` | 15 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACRCYCR` | `9,61` | 15 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `AADDGRP` | `10,23` | 10 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACRCYDB` | `10,61` | 15 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSTNUM` | `12,23` | 9 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSTSSN` | `12,54` | 12 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSTDOB` | `13,23` | 10 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSTFCO` | `13,61` | 3 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSFNAM` | `15,1` | 25 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSMNAM` | `15,28` | 25 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSLNAM` | `15,55` | 25 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSADL1` | `16,10` | 50 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSSTTE` | `16,73` | 2 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSADL2` | `17,10` | 50 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSZIPC` | `17,73` | 5 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSCITY` | `18,10` | 50 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSCTRY` | `18,73` | 3 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSPHN1` | `19,10` | 13 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSGOVT` | `19,58` | 20 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSPHN2` | `20,10` | 13 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSEFTC` | `20,41` | 10 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `ACSPFLG` | `20,78` | 1 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `INFOMSG` | `22,23` | 45 | `PROT` | alphanumeric | no | no | `—` | NEUTRAL | OFF |
| `ERRMSG` | `23,1` | 78 | `ASKIP,BRT,FSET` | alphanumeric | no | yes | `—` | RED | — |

## COADM01 / COADM1A (`COADM01.bms`)

**Map geometry:** `24,80`. **Layout:** Header rows 1–2 carry transaction/date/program/title labels; body fields occupy the remaining rows; footer fields carry messages and PF-key text.

| Field | Row/column | Length | Protection/skip | Type | IC | FSET | Initial/literal | Colour | Highlight |
|---|---|---:|---|---|---|---|---|---|---|
| `TRNNAME` | `1,7` | 4 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE01` | `1,21` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | YELLOW | — |
| `CURDATE` | `1,71` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `mm/dd/yy` | BLUE | — |
| `PGMNAME` | `2,7` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE02` | `2,21` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | YELLOW | — |
| `CURTIME` | `2,71` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `hh:mm:ss` | BLUE | — |
| `OPTN001` | `6,20` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `OPTN002` | `7,20` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `OPTN003` | `8,20` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `OPTN004` | `9,20` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `OPTN005` | `10,20` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `OPTN006` | `11,20` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `OPTN007` | `12,20` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `OPTN008` | `13,20` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `OPTN009` | `14,20` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `OPTN010` | `15,20` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `OPTN011` | `16,20` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `OPTN012` | `17,20` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `OPTION` | `20,41` | 2 | `FSET,IC,NORM,NUM,UNPROT` | numeric | yes | yes | `—` | — | UNDERLINE |
| `ERRMSG` | `23,1` | 78 | `ASKIP,BRT,FSET` | alphanumeric | no | yes | `—` | RED | — |

## COBIL00 / COBIL0A (`COBIL00.bms`)

**Map geometry:** `24,80`. **Layout:** Header rows 1–2 carry transaction/date/program/title labels; body fields occupy the remaining rows; footer fields carry messages and PF-key text.

| Field | Row/column | Length | Protection/skip | Type | IC | FSET | Initial/literal | Colour | Highlight |
|---|---|---:|---|---|---|---|---|---|---|
| `TRNNAME` | `1,7` | 4 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE01` | `1,21` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | YELLOW | — |
| `CURDATE` | `1,71` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `mm/dd/yy` | BLUE | — |
| `PGMNAME` | `2,7` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE02` | `2,21` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | YELLOW | — |
| `CURTIME` | `2,71` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `hh:mm:ss` | BLUE | — |
| `ACTIDIN` | `6,21` | 11 | `FSET,IC,NORM,UNPROT` | alphanumeric | yes | yes | `—` | GREEN | UNDERLINE |
| `CURBAL` | `11,32` | 14 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `CONFIRM` | `15,60` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | `—` | GREEN | UNDERLINE |
| `ERRMSG` | `23,1` | 78 | `ASKIP,BRT,FSET` | alphanumeric | no | yes | `—` | RED | — |

## COCRDLI / CCRDLIA (`COCRDLI.bms`)

**Map geometry:** `24,80`. **Layout:** Header rows 1–2 carry transaction/date/program/title labels; body fields occupy the remaining rows; footer fields carry messages and PF-key text.

| Field | Row/column | Length | Protection/skip | Type | IC | FSET | Initial/literal | Colour | Highlight |
|---|---|---:|---|---|---|---|---|---|---|
| `TRNNAME` | `1,7` | 4 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE01` | `1,21` | 40 | `ASKIP,NORM` | alphanumeric | no | no | `—` | YELLOW | — |
| `CURDATE` | `1,71` | 8 | `ASKIP,NORM` | alphanumeric | no | no | `mm/dd/yy` | BLUE | — |
| `PGMNAME` | `2,7` | 8 | `ASKIP,NORM` | alphanumeric | no | no | `—` | BLUE | — |
| `TITLE02` | `2,21` | 40 | `ASKIP,NORM` | alphanumeric | no | no | `—` | YELLOW | — |
| `CURTIME` | `2,71` | 8 | `ASKIP,NORM` | alphanumeric | no | no | `hh:mm:ss` | BLUE | — |
| `PAGENO` | `4,76` | 3 | `—` | alphanumeric | no | no | `—` | — | — |
| `ACCTSID` | `6,44` | 11 | `FSET,IC,NORM,UNPROT` | alphanumeric | yes | yes | `—` | GREEN | UNDERLINE |
| `CARDSID` | `7,44` | 16 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | `—` | GREEN | UNDERLINE |
| `CRDSEL1` | `11,12` | 1 | `FSET,NORM,PROT` | alphanumeric | no | yes | `—` | DEFAULT | UNDERLINE |
| `ACCTNO1` | `11,22` | 11 | `NORM,PROT` | alphanumeric | no | no | `—` | DEFAULT | OFF |
| `CRDNUM1` | `11,43` | 16 | `NORM,PROT` | alphanumeric | no | no | `—` | DEFAULT | OFF |
| `CRDSTS1` | `11,67` | 1 | `NORM,PROT` | alphanumeric | no | no | `—` | DEFAULT | OFF |
| `CRDSEL2` | `12,12` | 1 | `FSET,NORM,PROT` | alphanumeric | no | yes | `—` | DEFAULT | UNDERLINE |
| `CRDSTP2` | `12,14` | 1 | `ASKIP,DRK,FSET` | alphanumeric | no | yes | `—` | DEFAULT | OFF |
| `ACCTNO2` | `12,22` | 11 | `NORM,PROT` | alphanumeric | no | no | `—` | DEFAULT | OFF |
| `CRDNUM2` | `12,43` | 16 | `NORM,PROT` | alphanumeric | no | no | `—` | DEFAULT | OFF |
| `CRDSTS2` | `12,67` | 1 | `NORM,PROT` | alphanumeric | no | no | `—` | DEFAULT | OFF |
| `CRDSEL3` | `13,12` | 1 | `FSET,NORM,PROT` | alphanumeric | no | yes | `—` | DEFAULT | UNDERLINE |
| `CRDSTP3` | `13,14` | 1 | `ASKIP,DRK,FSET` | alphanumeric | no | yes | `—` | DEFAULT | OFF |
| `ACCTNO3` | `13,22` | 11 | `NORM,PROT` | alphanumeric | no | no | `—` | DEFAULT | OFF |
| `CRDNUM3` | `13,43` | 16 | `NORM,PROT` | alphanumeric | no | no | `—` | DEFAULT | OFF |
| `CRDSTS3` | `13,67` | 1 | `NORM,PROT` | alphanumeric | no | no | `—` | DEFAULT | OFF |
| `CRDSEL4` | `14,12` | 1 | `FSET,NORM,PROT` | alphanumeric | no | yes | `—` | DEFAULT | UNDERLINE |
| `CRDSTP4` | `14,14` | 1 | `ASKIP,DRK,FSET` | alphanumeric | no | yes | `—` | DEFAULT | OFF |
| `ACCTNO4` | `14,22` | 11 | `NORM,PROT` | alphanumeric | no | no | `—` | DEFAULT | OFF |
| `CRDNUM4` | `14,43` | 16 | `NORM,PROT` | alphanumeric | no | no | `—` | DEFAULT | OFF |
| `CRDSTS4` | `14,67` | 1 | `NORM,PROT` | alphanumeric | no | no | `—` | DEFAULT | OFF |
| `CRDSEL5` | `15,12` | 1 | `FSET,NORM,PROT` | alphanumeric | no | yes | `—` | DEFAULT | UNDERLINE |
| `CRDSTP5` | `15,14` | 1 | `ASKIP,DRK,FSET` | alphanumeric | no | yes | `—` | DEFAULT | OFF |
| `ACCTNO5` | `15,22` | 11 | `NORM,PROT` | alphanumeric | no | no | `—` | DEFAULT | OFF |
| `CRDNUM5` | `15,43` | 16 | `NORM,PROT` | alphanumeric | no | no | `—` | DEFAULT | OFF |
| `CRDSTS5` | `15,67` | 1 | `NORM,PROT` | alphanumeric | no | no | `—` | DEFAULT | OFF |
| `CRDSEL6` | `16,12` | 1 | `FSET,NORM,PROT` | alphanumeric | no | yes | `—` | DEFAULT | UNDERLINE |
| `CRDSTP6` | `16,14` | 1 | `ASKIP,DRK,FSET` | alphanumeric | no | yes | `—` | DEFAULT | OFF |
| `ACCTNO6` | `16,22` | 11 | `NORM,PROT` | alphanumeric | no | no | `—` | DEFAULT | OFF |
| `CRDNUM6` | `16,43` | 16 | `NORM,PROT` | alphanumeric | no | no | `—` | DEFAULT | OFF |
| `CRDSTS6` | `16,67` | 1 | `NORM,PROT` | alphanumeric | no | no | `—` | DEFAULT | OFF |
| `CRDSEL7` | `17,12` | 1 | `FSET,NORM,PROT` | alphanumeric | no | yes | `—` | DEFAULT | UNDERLINE |
| `CRDSTP7` | `17,14` | 1 | `ASKIP,DRK,FSET` | alphanumeric | no | yes | `—` | DEFAULT | OFF |
| `ACCTNO7` | `17,22` | 11 | `NORM,PROT` | alphanumeric | no | no | `—` | DEFAULT | OFF |
| `CRDNUM7` | `17,43` | 16 | `NORM,PROT` | alphanumeric | no | no | `—` | DEFAULT | OFF |
| `CRDSTS7` | `17,67` | 1 | `NORM,PROT` | alphanumeric | no | no | `—` | DEFAULT | OFF |
| `INFOMSG` | `20,19` | 45 | `PROT` | alphanumeric | no | no | `—` | NEUTRAL | OFF |
| `ERRMSG` | `23,1` | 78 | `ASKIP,BRT,FSET` | alphanumeric | no | yes | `—` | RED | — |

## COCRDSL / CCRDSLA (`COCRDSL.bms`)

**Map geometry:** `24,80`. **Layout:** Header rows 1–2 carry transaction/date/program/title labels; body fields occupy the remaining rows; footer fields carry messages and PF-key text. PF-key line fields: FKEYS.

| Field | Row/column | Length | Protection/skip | Type | IC | FSET | Initial/literal | Colour | Highlight |
|---|---|---:|---|---|---|---|---|---|---|
| `TRNNAME` | `1,7` | 4 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE01` | `1,21` | 40 | `ASKIP,NORM` | alphanumeric | no | no | `—` | YELLOW | — |
| `CURDATE` | `1,71` | 8 | `ASKIP,NORM` | alphanumeric | no | no | `mm/dd/yy` | BLUE | — |
| `PGMNAME` | `2,7` | 8 | `ASKIP,NORM` | alphanumeric | no | no | `—` | BLUE | — |
| `TITLE02` | `2,21` | 40 | `ASKIP,NORM` | alphanumeric | no | no | `—` | YELLOW | — |
| `CURTIME` | `2,71` | 8 | `ASKIP,NORM` | alphanumeric | no | no | `hh:mm:ss` | BLUE | — |
| `ACCTSID` | `7,45` | 11 | `FSET,IC,NORM,UNPROT` | alphanumeric | yes | yes | `—` | DEFAULT | UNDERLINE |
| `CARDSID` | `8,45` | 16 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | `—` | DEFAULT | UNDERLINE |
| `CRDNAME` | `11,25` | 50 | `—` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `CRDSTCD` | `13,25` | 1 | `ASKIP` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `EXPMON` | `15,25` | 2 | `ASKIP` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `EXPYEAR` | `15,30` | 4 | `ASKIP` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `INFOMSG` | `20,25` | 40 | `PROT` | alphanumeric | no | no | `—` | NEUTRAL | OFF |
| `ERRMSG` | `23,1` | 80 | `ASKIP,BRT,FSET` | alphanumeric | no | yes | `—` | RED | — |
| `FKEYS` | `24,1` | 75 | `ASKIP,NORM` | alphanumeric | no | no | `ENTER=Search Cards  F3=Exit` | YELLOW | — |

## COCRDUP / CCRDUPA (`COCRDUP.bms`)

**Map geometry:** `24,80`. **Layout:** Header rows 1–2 carry transaction/date/program/title labels; body fields occupy the remaining rows; footer fields carry messages and PF-key text. PF-key line fields: FKEYS, FKEYSC.

| Field | Row/column | Length | Protection/skip | Type | IC | FSET | Initial/literal | Colour | Highlight |
|---|---|---:|---|---|---|---|---|---|---|
| `TRNNAME` | `1,7` | 4 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE01` | `1,21` | 40 | `ASKIP,NORM` | alphanumeric | no | no | `—` | YELLOW | — |
| `CURDATE` | `1,71` | 8 | `ASKIP,NORM` | alphanumeric | no | no | `mm/dd/yy` | BLUE | — |
| `PGMNAME` | `2,7` | 8 | `ASKIP,NORM` | alphanumeric | no | no | `—` | BLUE | — |
| `TITLE02` | `2,21` | 40 | `ASKIP,NORM` | alphanumeric | no | no | `—` | YELLOW | — |
| `CURTIME` | `2,71` | 8 | `ASKIP,NORM` | alphanumeric | no | no | `hh:mm:ss` | BLUE | — |
| `ACCTSID` | `7,45` | 11 | `FSET,IC,NORM,PROT` | alphanumeric | yes | yes | `—` | DEFAULT | UNDERLINE |
| `CARDSID` | `8,45` | 16 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | `—` | DEFAULT | UNDERLINE |
| `CRDNAME` | `11,25` | 50 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `CRDSTCD` | `13,25` | 1 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `EXPMON` | `15,25` | 2 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `EXPYEAR` | `15,30` | 4 | `UNPROT` | alphanumeric | no | no | `—` | — | UNDERLINE |
| `EXPDAY` | `15,36` | 2 | `DRK,FSET,PROT` | alphanumeric | no | yes | `—` | — | OFF |
| `INFOMSG` | `20,25` | 40 | `PROT` | alphanumeric | no | no | `—` | NEUTRAL | OFF |
| `ERRMSG` | `23,1` | 80 | `ASKIP,BRT,FSET` | alphanumeric | no | yes | `—` | RED | — |
| `FKEYS` | `24,1` | 21 | `ASKIP,NORM` | alphanumeric | no | no | `ENTER=Process F3=Exit` | YELLOW | — |
| `FKEYSC` | `24,23` | 18 | `ASKIP,DRK` | alphanumeric | no | no | `F5=Save F12=Cancel` | YELLOW | — |

## COMEN01 / COMEN1A (`COMEN01.bms`)

**Map geometry:** `24,80`. **Layout:** Header rows 1–2 carry transaction/date/program/title labels; body fields occupy the remaining rows; footer fields carry messages and PF-key text.

| Field | Row/column | Length | Protection/skip | Type | IC | FSET | Initial/literal | Colour | Highlight |
|---|---|---:|---|---|---|---|---|---|---|
| `TRNNAME` | `1,7` | 4 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE01` | `1,21` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | YELLOW | — |
| `CURDATE` | `1,71` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `mm/dd/yy` | BLUE | — |
| `PGMNAME` | `2,7` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE02` | `2,21` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | YELLOW | — |
| `CURTIME` | `2,71` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `hh:mm:ss` | BLUE | — |
| `OPTN001` | `6,20` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `OPTN002` | `7,20` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `OPTN003` | `8,20` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `OPTN004` | `9,20` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `OPTN005` | `10,20` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `OPTN006` | `11,20` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `OPTN007` | `12,20` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `OPTN008` | `13,20` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `OPTN009` | `14,20` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `OPTN010` | `15,20` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `OPTN011` | `16,20` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `OPTN012` | `17,20` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `OPTION` | `20,41` | 2 | `FSET,IC,NORM,NUM,UNPROT` | numeric | yes | yes | `—` | — | UNDERLINE |
| `ERRMSG` | `23,1` | 78 | `ASKIP,BRT,FSET` | alphanumeric | no | yes | `—` | RED | — |

## CORPT00 / CORPT0A (`CORPT00.bms`)

**Map geometry:** `24,80`. **Layout:** Header rows 1–2 carry transaction/date/program/title labels; body fields occupy the remaining rows; footer fields carry messages and PF-key text.

| Field | Row/column | Length | Protection/skip | Type | IC | FSET | Initial/literal | Colour | Highlight |
|---|---|---:|---|---|---|---|---|---|---|
| `TRNNAME` | `1,7` | 4 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE01` | `1,21` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | YELLOW | — |
| `CURDATE` | `1,71` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `mm/dd/yy` | BLUE | — |
| `PGMNAME` | `2,7` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE02` | `2,21` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | YELLOW | — |
| `CURTIME` | `2,71` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `hh:mm:ss` | BLUE | — |
| `MONTHLY` | `7,10` | 1 | `FSET,IC,NORM,UNPROT` | alphanumeric | yes | yes | ` ` | GREEN | UNDERLINE |
| `YEARLY` | `9,10` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `CUSTOM` | `11,10` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `SDTMM` | `13,29` | 2 | `FSET,NORM,NUM,UNPROT` | numeric | no | yes | `  ` | GREEN | UNDERLINE |
| `SDTDD` | `13,34` | 2 | `FSET,NORM,NUM,UNPROT` | numeric | no | yes | `  ` | GREEN | UNDERLINE |
| `SDTYYYY` | `13,39` | 4 | `FSET,NORM,NUM,UNPROT` | numeric | no | yes | `    ` | GREEN | UNDERLINE |
| `EDTMM` | `14,29` | 2 | `FSET,NORM,NUM,UNPROT` | numeric | no | yes | `  ` | GREEN | UNDERLINE |
| `EDTDD` | `14,34` | 2 | `FSET,NORM,NUM,UNPROT` | numeric | no | yes | `  ` | GREEN | UNDERLINE |
| `EDTYYYY` | `14,39` | 4 | `FSET,NORM,NUM,UNPROT` | numeric | no | yes | `    ` | GREEN | UNDERLINE |
| `CONFIRM` | `19,66` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | `—` | GREEN | UNDERLINE |
| `ERRMSG` | `23,1` | 78 | `ASKIP,BRT,FSET` | alphanumeric | no | yes | `—` | RED | — |

## COSGN00 / COSGN0A (`COSGN00.bms`)

**Map geometry:** `24,80`. **Layout:** Header rows 1–2 carry transaction/date/program/title labels; body fields occupy the remaining rows; footer fields carry messages and PF-key text.

| Field | Row/column | Length | Protection/skip | Type | IC | FSET | Initial/literal | Colour | Highlight |
|---|---|---:|---|---|---|---|---|---|---|
| `TRNNAME` | `1,8` | 4 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE01` | `1,21` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | YELLOW | — |
| `CURDATE` | `1,71` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `mm/dd/yy` | BLUE | — |
| `PGMNAME` | `2,8` | 8 | `FSET,NORM,PROT` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE02` | `2,21` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | YELLOW | — |
| `CURTIME` | `2,71` | 9 | `FSET,NORM,PROT` | alphanumeric | no | yes | `Ahh:mm:ss` | BLUE | — |
| `APPLID` | `3,8` | 8 | `FSET,NORM,PROT` | alphanumeric | no | yes | `—` | BLUE | — |
| `SYSID` | `3,71` | 8 | `FSET,NORM,PROT` | alphanumeric | no | yes | `        ` | BLUE | — |
| `USERID` | `19,43` | 8 | `FSET,IC,NORM,UNPROT` | alphanumeric | yes | yes | `—` | GREEN | OFF |
| `PASSWD` | `20,43` | 8 | `DRK,FSET,UNPROT` | alphanumeric | no | yes | `________` | GREEN | OFF |
| `ERRMSG` | `23,1` | 78 | `ASKIP,BRT,FSET` | alphanumeric | no | yes | `—` | RED | — |

## COTRN00 / COTRN0A (`COTRN00.bms`)

**Map geometry:** `24,80`. **Layout:** Header rows 1–2 carry transaction/date/program/title labels; body fields occupy the remaining rows; footer fields carry messages and PF-key text.

| Field | Row/column | Length | Protection/skip | Type | IC | FSET | Initial/literal | Colour | Highlight |
|---|---|---:|---|---|---|---|---|---|---|
| `TRNNAME` | `1,7` | 4 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE01` | `1,21` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | YELLOW | — |
| `CURDATE` | `1,71` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `mm/dd/yy` | BLUE | — |
| `PGMNAME` | `2,7` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE02` | `2,21` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | YELLOW | — |
| `CURTIME` | `2,71` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `hh:mm:ss` | BLUE | — |
| `PAGENUM` | `4,71` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TRNIDIN` | `6,21` | 16 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | `—` | GREEN | UNDERLINE |
| `SEL0001` | `10,3` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `TRNID01` | `10,8` | 16 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TDATE01` | `10,27` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TDESC01` | `10,38` | 26 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TAMT001` | `10,67` | 12 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `SEL0002` | `11,3` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `TRNID02` | `11,8` | 16 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TDATE02` | `11,27` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TDESC02` | `11,38` | 26 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TAMT002` | `11,67` | 12 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `SEL0003` | `12,3` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `TRNID03` | `12,8` | 16 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TDATE03` | `12,27` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TDESC03` | `12,38` | 26 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TAMT003` | `12,67` | 12 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `SEL0004` | `13,3` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `TRNID04` | `13,8` | 16 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TDATE04` | `13,27` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TDESC04` | `13,38` | 26 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TAMT004` | `13,67` | 12 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `SEL0005` | `14,3` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `TRNID05` | `14,8` | 16 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TDATE05` | `14,27` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TDESC05` | `14,38` | 26 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TAMT005` | `14,67` | 12 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `SEL0006` | `15,3` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `TRNID06` | `15,8` | 16 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TDATE06` | `15,27` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TDESC06` | `15,38` | 26 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TAMT006` | `15,67` | 12 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `SEL0007` | `16,3` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `TRNID07` | `16,8` | 16 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TDATE07` | `16,27` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TDESC07` | `16,38` | 26 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TAMT007` | `16,67` | 12 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `SEL0008` | `17,3` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `TRNID08` | `17,8` | 16 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TDATE08` | `17,27` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TDESC08` | `17,38` | 26 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TAMT008` | `17,67` | 12 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `SEL0009` | `18,3` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `TRNID09` | `18,8` | 16 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TDATE09` | `18,27` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TDESC09` | `18,38` | 26 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TAMT009` | `18,67` | 12 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `SEL0010` | `19,3` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `TRNID10` | `19,8` | 16 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TDATE10` | `19,27` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TDESC10` | `19,38` | 26 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `TAMT010` | `19,67` | 12 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `ERRMSG` | `23,1` | 78 | `ASKIP,BRT,FSET` | alphanumeric | no | yes | `—` | RED | — |

## COTRN01 / COTRN1A (`COTRN01.bms`)

**Map geometry:** `24,80`. **Layout:** Header rows 1–2 carry transaction/date/program/title labels; body fields occupy the remaining rows; footer fields carry messages and PF-key text.

| Field | Row/column | Length | Protection/skip | Type | IC | FSET | Initial/literal | Colour | Highlight |
|---|---|---:|---|---|---|---|---|---|---|
| `TRNNAME` | `1,7` | 4 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE01` | `1,21` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | YELLOW | — |
| `CURDATE` | `1,71` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `mm/dd/yy` | BLUE | — |
| `PGMNAME` | `2,7` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE02` | `2,21` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | YELLOW | — |
| `CURTIME` | `2,71` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `hh:mm:ss` | BLUE | — |
| `TRNIDIN` | `6,21` | 16 | `FSET,IC,NORM,UNPROT` | alphanumeric | yes | yes | ` ` | GREEN | UNDERLINE |
| `TRNID` | `10,22` | 16 | `ASKIP,NORM` | alphanumeric | no | no | ` ` | BLUE | — |
| `CARDNUM` | `10,58` | 16 | `ASKIP,NORM` | alphanumeric | no | no | ` ` | BLUE | — |
| `TTYPCD` | `12,15` | 2 | `ASKIP,NORM` | alphanumeric | no | no | ` ` | BLUE | — |
| `TCATCD` | `12,36` | 4 | `ASKIP,NORM` | alphanumeric | no | no | ` ` | BLUE | — |
| `TRNSRC` | `12,54` | 10 | `ASKIP,NORM` | alphanumeric | no | no | ` ` | BLUE | — |
| `TDESC` | `14,19` | 60 | `ASKIP,NORM` | alphanumeric | no | no | ` ` | BLUE | — |
| `TRNAMT` | `16,14` | 12 | `ASKIP,NORM` | alphanumeric | no | no | ` ` | BLUE | — |
| `TORIGDT` | `16,42` | 10 | `ASKIP,NORM` | alphanumeric | no | no | ` ` | BLUE | — |
| `TPROCDT` | `16,68` | 10 | `ASKIP,NORM` | alphanumeric | no | no | ` ` | BLUE | — |
| `MID` | `18,19` | 9 | `ASKIP,NORM` | alphanumeric | no | no | ` ` | BLUE | — |
| `MNAME` | `18,48` | 30 | `ASKIP,NORM` | alphanumeric | no | no | ` ` | BLUE | — |
| `MCITY` | `20,21` | 25 | `ASKIP,NORM` | alphanumeric | no | no | ` ` | BLUE | — |
| `MZIP` | `20,67` | 10 | `ASKIP,NORM` | alphanumeric | no | no | ` ` | BLUE | — |
| `ERRMSG` | `23,1` | 78 | `ASKIP,BRT,FSET` | alphanumeric | no | yes | `—` | RED | — |

## COTRN02 / COTRN2A (`COTRN02.bms`)

**Map geometry:** `24,80`. **Layout:** Header rows 1–2 carry transaction/date/program/title labels; body fields occupy the remaining rows; footer fields carry messages and PF-key text.

| Field | Row/column | Length | Protection/skip | Type | IC | FSET | Initial/literal | Colour | Highlight |
|---|---|---:|---|---|---|---|---|---|---|
| `TRNNAME` | `1,7` | 4 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE01` | `1,21` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | YELLOW | — |
| `CURDATE` | `1,71` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `mm/dd/yy` | BLUE | — |
| `PGMNAME` | `2,7` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE02` | `2,21` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | YELLOW | — |
| `CURTIME` | `2,71` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `hh:mm:ss` | BLUE | — |
| `ACTIDIN` | `6,21` | 11 | `FSET,IC,NORM,UNPROT` | alphanumeric | yes | yes | ` ` | GREEN | UNDERLINE |
| `CARDNIN` | `6,55` | 16 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | `—` | GREEN | UNDERLINE |
| `TTYPCD` | `10,15` | 2 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `TCATCD` | `10,36` | 4 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `TRNSRC` | `10,54` | 10 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `TDESC` | `12,19` | 60 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `TRNAMT` | `14,14` | 12 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `TORIGDT` | `14,42` | 10 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `TPROCDT` | `14,68` | 10 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `MID` | `16,19` | 9 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `MNAME` | `16,48` | 30 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `MCITY` | `18,21` | 25 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `MZIP` | `18,67` | 10 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `CONFIRM` | `21,63` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | `—` | GREEN | UNDERLINE |
| `ERRMSG` | `23,1` | 78 | `ASKIP,BRT,FSET` | alphanumeric | no | yes | `—` | RED | — |

## COUSR00 / COUSR0A (`COUSR00.bms`)

**Map geometry:** `24,80`. **Layout:** Header rows 1–2 carry transaction/date/program/title labels; body fields occupy the remaining rows; footer fields carry messages and PF-key text.

| Field | Row/column | Length | Protection/skip | Type | IC | FSET | Initial/literal | Colour | Highlight |
|---|---|---:|---|---|---|---|---|---|---|
| `TRNNAME` | `1,7` | 4 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE01` | `1,21` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | YELLOW | — |
| `CURDATE` | `1,71` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `mm/dd/yy` | BLUE | — |
| `PGMNAME` | `2,7` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE02` | `2,21` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | YELLOW | — |
| `CURTIME` | `2,71` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `hh:mm:ss` | BLUE | — |
| `PAGENUM` | `4,71` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `USRIDIN` | `6,21` | 8 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | `—` | GREEN | UNDERLINE |
| `SEL0001` | `10,6` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `USRID01` | `10,12` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `FNAME01` | `10,24` | 20 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `LNAME01` | `10,48` | 20 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `UTYPE01` | `10,73` | 1 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `SEL0002` | `11,6` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `USRID02` | `11,12` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `FNAME02` | `11,24` | 20 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `LNAME02` | `11,48` | 20 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `UTYPE02` | `11,73` | 1 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `SEL0003` | `12,6` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `USRID03` | `12,12` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `FNAME03` | `12,24` | 20 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `LNAME03` | `12,48` | 20 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `UTYPE03` | `12,73` | 1 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `SEL0004` | `13,6` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `USRID04` | `13,12` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `FNAME04` | `13,24` | 20 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `LNAME04` | `13,48` | 20 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `UTYPE04` | `13,73` | 1 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `SEL0005` | `14,6` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `USRID05` | `14,12` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `FNAME05` | `14,24` | 20 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `LNAME05` | `14,48` | 20 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `UTYPE05` | `14,73` | 1 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `SEL0006` | `15,6` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `USRID06` | `15,12` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `FNAME06` | `15,24` | 20 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `LNAME06` | `15,48` | 20 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `UTYPE06` | `15,73` | 1 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `SEL0007` | `16,6` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `USRID07` | `16,12` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `FNAME07` | `16,24` | 20 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `LNAME07` | `16,48` | 20 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `UTYPE07` | `16,73` | 1 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `SEL0008` | `17,6` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `USRID08` | `17,12` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `FNAME08` | `17,24` | 20 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `LNAME08` | `17,48` | 20 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `UTYPE08` | `17,73` | 1 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `SEL0009` | `18,6` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `USRID09` | `18,12` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `FNAME09` | `18,24` | 20 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `LNAME09` | `18,48` | 20 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `UTYPE09` | `18,73` | 1 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `SEL0010` | `19,6` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | ` ` | GREEN | UNDERLINE |
| `USRID10` | `19,12` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `FNAME10` | `19,24` | 20 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `LNAME10` | `19,48` | 20 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `UTYPE10` | `19,73` | 1 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | ` ` | BLUE | — |
| `ERRMSG` | `23,1` | 78 | `ASKIP,BRT,FSET` | alphanumeric | no | yes | `—` | RED | — |

## COUSR01 / COUSR1A (`COUSR01.bms`)

**Map geometry:** `24,80`. **Layout:** Header rows 1–2 carry transaction/date/program/title labels; body fields occupy the remaining rows; footer fields carry messages and PF-key text.

| Field | Row/column | Length | Protection/skip | Type | IC | FSET | Initial/literal | Colour | Highlight |
|---|---|---:|---|---|---|---|---|---|---|
| `TRNNAME` | `1,7` | 4 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE01` | `1,21` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | YELLOW | — |
| `CURDATE` | `1,71` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `mm/dd/yy` | BLUE | — |
| `PGMNAME` | `2,7` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE02` | `2,21` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | YELLOW | — |
| `CURTIME` | `2,71` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `hh:mm:ss` | BLUE | — |
| `FNAME` | `8,18` | 20 | `FSET,IC,NORM,UNPROT` | alphanumeric | yes | yes | `—` | GREEN | UNDERLINE |
| `LNAME` | `8,56` | 20 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | `—` | GREEN | UNDERLINE |
| `USERID` | `11,15` | 8 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | `—` | GREEN | UNDERLINE |
| `PASSWD` | `11,55` | 8 | `DRK,FSET,UNPROT` | alphanumeric | no | yes | `—` | GREEN | UNDERLINE |
| `USRTYPE` | `14,17` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | `—` | GREEN | UNDERLINE |
| `ERRMSG` | `23,1` | 78 | `ASKIP,BRT,FSET` | alphanumeric | no | yes | `—` | RED | — |

## COUSR02 / COUSR2A (`COUSR02.bms`)

**Map geometry:** `24,80`. **Layout:** Header rows 1–2 carry transaction/date/program/title labels; body fields occupy the remaining rows; footer fields carry messages and PF-key text.

| Field | Row/column | Length | Protection/skip | Type | IC | FSET | Initial/literal | Colour | Highlight |
|---|---|---:|---|---|---|---|---|---|---|
| `TRNNAME` | `1,7` | 4 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE01` | `1,21` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | YELLOW | — |
| `CURDATE` | `1,71` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `mm/dd/yy` | BLUE | — |
| `PGMNAME` | `2,7` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE02` | `2,21` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | YELLOW | — |
| `CURTIME` | `2,71` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `hh:mm:ss` | BLUE | — |
| `USRIDIN` | `6,21` | 8 | `FSET,IC,NORM,UNPROT` | alphanumeric | yes | yes | `—` | GREEN | UNDERLINE |
| `FNAME` | `11,18` | 20 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | `—` | GREEN | UNDERLINE |
| `LNAME` | `11,56` | 20 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | `—` | GREEN | UNDERLINE |
| `PASSWD` | `13,16` | 8 | `DRK,FSET,UNPROT` | alphanumeric | no | yes | `—` | GREEN | UNDERLINE |
| `USRTYPE` | `15,17` | 1 | `FSET,NORM,UNPROT` | alphanumeric | no | yes | `—` | GREEN | UNDERLINE |
| `ERRMSG` | `23,1` | 78 | `ASKIP,BRT,FSET` | alphanumeric | no | yes | `—` | RED | — |

## COUSR03 / COUSR3A (`COUSR03.bms`)

**Map geometry:** `24,80`. **Layout:** Header rows 1–2 carry transaction/date/program/title labels; body fields occupy the remaining rows; footer fields carry messages and PF-key text.

| Field | Row/column | Length | Protection/skip | Type | IC | FSET | Initial/literal | Colour | Highlight |
|---|---|---:|---|---|---|---|---|---|---|
| `TRNNAME` | `1,7` | 4 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE01` | `1,21` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | YELLOW | — |
| `CURDATE` | `1,71` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `mm/dd/yy` | BLUE | — |
| `PGMNAME` | `2,7` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | — |
| `TITLE02` | `2,21` | 40 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | YELLOW | — |
| `CURTIME` | `2,71` | 8 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `hh:mm:ss` | BLUE | — |
| `USRIDIN` | `6,21` | 8 | `FSET,IC,NORM,UNPROT` | alphanumeric | yes | yes | `—` | GREEN | UNDERLINE |
| `FNAME` | `11,18` | 20 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | UNDERLINE |
| `LNAME` | `13,18` | 20 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | UNDERLINE |
| `USRTYPE` | `15,17` | 1 | `ASKIP,FSET,NORM` | alphanumeric | no | yes | `—` | BLUE | UNDERLINE |
| `ERRMSG` | `23,1` | 78 | `ASKIP,BRT,FSET` | alphanumeric | no | yes | `—` | RED | — |

