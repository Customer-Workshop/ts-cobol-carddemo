# Legacy asset inventory

Generated from the files under `app/`; names and relationships below are source observations, not assumptions. `—` means the source contains no corresponding declaration.

## COBOL programs (`app/cbl/`, 31 files)

| Program | Type | Purpose (source comment/structure) | Files/tables read and written | CALL/XCTL/LINK targets |
|---|---|---|---|---|
| `CBACT01C` | batch | Type : BATCH COBOL Program | `ACCTFILE`, `ARRYFILE`, `OUTFILE`, `VBRCFILE` | `CEE3ABD`, `COBDATFT` |
| `CBACT02C` | batch | Type : BATCH COBOL Program | `CARDFILE` | `CEE3ABD` |
| `CBACT03C` | batch | Type : BATCH COBOL Program | `XREFFILE` | `CEE3ABD` |
| `CBACT04C` | batch | Type : BATCH COBOL Program | `ACCTFILE`, `DISCGRP`, `TCATBALF`, `TRANSACT`, `XREFFILE` | `CEE3ABD` |
| `CBCUS01C` | batch | Type : BATCH COBOL Program | `CUSTFILE` | `CEE3ABD` |
| `CBEXPORT` | batch | Type : BATCH COBOL Program | `ACCTFILE`, `CARDFILE`, `CUSTFILE`, `EXPFILE`, `TRANSACT`, `XREFFILE` | `CEE3ABD` |
| `CBIMPORT` | batch | Type : BATCH COBOL Program | `ACCTOUT`, `CARDOUT`, `CUSTOUT`, `ERROUT`, `EXPFILE`, `TRNXOUT`, `XREFOUT` | `CEE3ABD` |
| `CBSTM03A` | batch | Type : BATCH COBOL Program | `HTMLFILE`, `STMTFILE` | `CBSTM03B`, `CEE3ABD`, `to` |
| `CBSTM03B` | batch | Type : BATCH COBOL Subroutine | `ACCTFILE`, `CUSTFILE`, `TRNXFILE`, `XREFFILE` | — |
| `CBTRN01C` | batch | Type : BATCH COBOL Program | `ACCTFILE`, `CARDFILE`, `CUSTFILE`, `DALYTRAN`, `TRANFILE`, `XREFFILE` | `CEE3ABD` |
| `CBTRN02C` | batch | Type : BATCH COBOL Program | `ACCTFILE`, `DALYREJS`, `DALYTRAN`, `TCATBALF`, `TRANFILE`, `XREFFILE` | `CEE3ABD` |
| `CBTRN03C` | batch | Type : BATCH COBOL Program | `CARDXREF`, `DATEPARM`, `TRANCATG`, `TRANFILE`, `TRANREPT`, `TRANTYPE` | `CEE3ABD` |
| `COACTUPC` | online CICS | Function: Accept and process ACCOUNT UPDATE * | `LIT-ACCTFILENAME`, `LIT-CARDXREFNAME-ACCT-PATH`, `LIT-CUSTFILENAME` | — |
| `COACTVWC` | online CICS | Function: Accept and process Account View request * | `LIT-ACCTFILENAME`, `LIT-CARDXREFNAME-ACCT-PATH`, `LIT-CUSTFILENAME` | — |
| `COADM01C` | online CICS | Type : CICS COBOL Program | — | — |
| `COBIL00C` | online CICS | Type : CICS COBOL Program | `WS-ACCTDAT-FILE`, `WS-CXACAIX-FILE`, `WS-TRANSACT-FILE` | — |
| `COBSWAIT` | utility | Type : BATCH COBOL Program | — | `MVSWAIT` |
| `COCRDLIC` | online CICS | Function: List Credit Cards | `LIT-CARD-FILE` | `CARD`, `MENU` |
| `COCRDSLC` | online CICS | Function: Accept and process credit card detail request * | — | — |
| `COCRDUPC` | online CICS | Function: Accept and process credit card detail request * | — | — |
| `COMEN01C` | online CICS | Type : CICS COBOL Program | — | — |
| `CORPT00C` | online CICS | Type : CICS COBOL Program | — | `CSUTLDTC` |
| `COSGN00C` | online CICS | Type : CICS COBOL Program | `WS-USRSEC-FILE` | — |
| `COTRN00C` | online CICS | Type : CICS COBOL Program | `WS-TRANSACT-FILE` | — |
| `COTRN01C` | online CICS | Type : CICS COBOL Program | `WS-TRANSACT-FILE` | — |
| `COTRN02C` | online CICS | Type : CICS COBOL Program | `WS-CCXREF-FILE`, `WS-CXACAIX-FILE`, `WS-TRANSACT-FILE` | `CSUTLDTC` |
| `COUSR00C` | online CICS | Type : CICS COBOL Program | `WS-USRSEC-FILE` | — |
| `COUSR01C` | online CICS | Type : CICS COBOL Program | `WS-USRSEC-FILE` | — |
| `COUSR02C` | online CICS | Type : CICS COBOL Program | `WS-USRSEC-FILE` | — |
| `COUSR03C` | online CICS | Type : CICS COBOL Program | `WS-USRSEC-FILE` | — |
| `CSUTLDTC` | utility | Purpose is represented by procedure names; see source. | — | `CEEDAYS` |

## BMS maps (`app/bms/`, 17 files)

Field attributes summarize DFHMDF ATTRB/COLOR/HILIGHT/LENGTH; unnamed literals are omitted. Program use is source COPY inclusion.

| Map | Mapset | Screen title/literals | Fields (name; length; attributes) | Program(s) |
|---|---|---|---|---|
| `CACTUPA` | `COACTUP` | Tran:; Date:; mm/dd/yy | `TRNNAME` (4; ASKIP,FSET,NORM); `TITLE01` (40; ASKIP,NORM); `CURDATE` (8; ASKIP,NORM); `PGMNAME` (8; ASKIP,NORM); `TITLE02` (40; ASKIP,NORM); `CURTIME` (8; ASKIP,NORM); `ACCTSID` (11; IC,UNPROT); `ACSTTUS` (1; UNPROT); `OPNYEAR` (4; FSET,UNPROT); `OPNMON` (2; UNPROT); `OPNDAY` (2; UNPROT); `ACRDLIM` (15; FSET,UNPROT); `EXPYEAR` (4; UNPROT); `EXPMON` (2; UNPROT); `EXPDAY` (2; UNPROT); `ACSHLIM` (15; FSET,UNPROT); `RISYEAR` (4; UNPROT); `RISMON` (2; UNPROT); `RISDAY` (2; UNPROT); `ACURBAL` (15; FSET,UNPROT); `ACRCYCR` (15; FSET,UNPROT); `AADDGRP` (10; UNPROT); `ACRCYDB` (15; FSET,UNPROT); `ACSTNUM` (9; UNPROT); `ACTSSN1` (3; UNPROT); `ACTSSN2` (2; UNPROT); `ACTSSN3` (4; UNPROT); `DOBYEAR` (4; UNPROT); `DOBMON` (2; UNPROT); `DOBDAY` (2; UNPROT); `ACSTFCO` (3; UNPROT); `ACSFNAM` (25; UNPROT); `ACSMNAM` (25; UNPROT); `ACSLNAM` (25; UNPROT); `ACSADL1` (50; UNPROT); `ACSSTTE` (2; UNPROT); `ACSADL2` (50; UNPROT); `ACSZIPC` (5; UNPROT); `ACSCITY` (50; UNPROT); `ACSCTRY` (3; UNPROT); `ACSPH1A` (3; UNPROT); `ACSPH1B` (3; UNPROT); `ACSPH1C` (4; UNPROT); `ACSGOVT` (20; UNPROT); `ACSPH2A` (3; UNPROT); `ACSPH2B` (3; UNPROT); `ACSPH2C` (4; UNPROT); `ACSEFTC` (10; UNPROT); `ACSPFLG` (1; UNPROT); `INFOMSG` (45; ASKIP); `ERRMSG` (78; ASKIP,BRT,FSET); `FKEYS` (21; ASKIP,NORM); `FKEY05` (7; ASKIP,DRK); `FKEY12` (10; ASKIP,DRK) | `COACTUPC` |
| `CACTVWA` | `COACTVW` | Tran:; Date:; mm/dd/yy | `TRNNAME` (4; ASKIP,FSET,NORM); `TITLE01` (40; ASKIP,NORM); `CURDATE` (8; ASKIP,NORM); `PGMNAME` (8; ASKIP,NORM); `TITLE02` (40; ASKIP,NORM); `CURTIME` (8; ASKIP,NORM); `ACCTSID` (11; FSET,IC,NORM,UNPROT); `ACSTTUS` (1; ASKIP); `ADTOPEN` (10; default); `ACRDLIM` (15; default); `AEXPDT` (10; default); `ACSHLIM` (15; default); `AREISDT` (10; default); `ACURBAL` (15; default); `ACRCYCR` (15; default); `AADDGRP` (10; default); `ACRCYDB` (15; default); `ACSTNUM` (9; default); `ACSTSSN` (12; default); `ACSTDOB` (10; default); `ACSTFCO` (3; default); `ACSFNAM` (25; default); `ACSMNAM` (25; default); `ACSLNAM` (25; default); `ACSADL1` (50; default); `ACSSTTE` (2; default); `ACSADL2` (50; default); `ACSZIPC` (5; default); `ACSCITY` (50; default); `ACSCTRY` (3; default); `ACSPHN1` (13; default); `ACSGOVT` (20; default); `ACSPHN2` (13; default); `ACSEFTC` (10; default); `ACSPFLG` (1; default); `INFOMSG` (45; PROT); `ERRMSG` (78; ASKIP,BRT,FSET) | `COACTVWC` |
| `COADM1A` | `COADM01` | Tran:; Date:; mm/dd/yy | `TRNNAME` (4; ASKIP,FSET,NORM); `TITLE01` (40; ASKIP,FSET,NORM); `CURDATE` (8; ASKIP,FSET,NORM); `PGMNAME` (8; ASKIP,FSET,NORM); `TITLE02` (40; ASKIP,FSET,NORM); `CURTIME` (8; ASKIP,FSET,NORM); `OPTN001` (40; ASKIP,FSET,NORM); `OPTN002` (40; ASKIP,FSET,NORM); `OPTN003` (40; ASKIP,FSET,NORM); `OPTN004` (40; ASKIP,FSET,NORM); `OPTN005` (40; ASKIP,FSET,NORM); `OPTN006` (40; ASKIP,FSET,NORM); `OPTN007` (40; ASKIP,FSET,NORM); `OPTN008` (40; ASKIP,FSET,NORM); `OPTN009` (40; ASKIP,FSET,NORM); `OPTN010` (40; ASKIP,FSET,NORM); `OPTN011` (40; ASKIP,FSET,NORM); `OPTN012` (40; ASKIP,FSET,NORM); `OPTION` (2; FSET,IC,NORM,NUM,UNPROT); `ERRMSG` (78; ASKIP,BRT,FSET) | `COADM01C` |
| `COBIL0A` | `COBIL00` | Tran:; Date:; mm/dd/yy | `TRNNAME` (4; ASKIP,FSET,NORM); `TITLE01` (40; ASKIP,FSET,NORM); `CURDATE` (8; ASKIP,FSET,NORM); `PGMNAME` (8; ASKIP,FSET,NORM); `TITLE02` (40; ASKIP,FSET,NORM); `CURTIME` (8; ASKIP,FSET,NORM); `ACTIDIN` (11; FSET,IC,NORM,UNPROT); `CURBAL` (14; ASKIP,FSET,NORM); `CONFIRM` (1; FSET,NORM,UNPROT); `ERRMSG` (78; ASKIP,BRT,FSET) | `COBIL00C` |
| `CCRDLIA` | `COCRDLI` | Tran:; Date:; mm/dd/yy | `TRNNAME` (4; ASKIP,FSET,NORM); `TITLE01` (40; ASKIP,NORM); `CURDATE` (8; ASKIP,NORM); `PGMNAME` (8; ASKIP,NORM); `TITLE02` (40; ASKIP,NORM); `CURTIME` (8; ASKIP,NORM); `PAGENO` (3; default); `ACCTSID` (11; FSET,IC,NORM,UNPROT); `CARDSID` (16; FSET,NORM,UNPROT); `CRDSEL1` (1; FSET,NORM,PROT); `ACCTNO1` (11; NORM,PROT); `CRDNUM1` (16; NORM,PROT); `CRDSTS1` (1; NORM,PROT); `CRDSEL2` (1; FSET,NORM,PROT); `CRDSTP2` (1; ASKIP,DRK,FSET); `ACCTNO2` (11; NORM,PROT); `CRDNUM2` (16; NORM,PROT); `CRDSTS2` (1; NORM,PROT); `CRDSEL3` (1; FSET,NORM,PROT); `CRDSTP3` (1; ASKIP,DRK,FSET); `ACCTNO3` (11; NORM,PROT); `CRDNUM3` (16; NORM,PROT); `CRDSTS3` (1; NORM,PROT); `CRDSEL4` (1; FSET,NORM,PROT); `CRDSTP4` (1; ASKIP,DRK,FSET); `ACCTNO4` (11; NORM,PROT); `CRDNUM4` (16; NORM,PROT); `CRDSTS4` (1; NORM,PROT); `CRDSEL5` (1; FSET,NORM,PROT); `CRDSTP5` (1; ASKIP,DRK,FSET); `ACCTNO5` (11; NORM,PROT); `CRDNUM5` (16; NORM,PROT); `CRDSTS5` (1; NORM,PROT); `CRDSEL6` (1; FSET,NORM,PROT); `CRDSTP6` (1; ASKIP,DRK,FSET); `ACCTNO6` (11; NORM,PROT); `CRDNUM6` (16; NORM,PROT); `CRDSTS6` (1; NORM,PROT); `CRDSEL7` (1; FSET,NORM,PROT); `CRDSTP7` (1; ASKIP,DRK,FSET); `ACCTNO7` (11; NORM,PROT); `CRDNUM7` (16; NORM,PROT); `CRDSTS7` (1; NORM,PROT); `INFOMSG` (45; PROT); `ERRMSG` (78; ASKIP,BRT,FSET) | `COCRDLIC` |
| `CCRDSLA` | `COCRDSL` | Tran:; Date:; mm/dd/yy | `TRNNAME` (4; ASKIP,FSET,NORM); `TITLE01` (40; ASKIP,NORM); `CURDATE` (8; ASKIP,NORM); `PGMNAME` (8; ASKIP,NORM); `TITLE02` (40; ASKIP,NORM); `CURTIME` (8; ASKIP,NORM); `ACCTSID` (11; FSET,IC,NORM,UNPROT); `CARDSID` (16; FSET,NORM,UNPROT); `CRDNAME` (50; default); `CRDSTCD` (1; ASKIP); `EXPMON` (2; ASKIP); `EXPYEAR` (4; ASKIP); `INFOMSG` (40; PROT); `ERRMSG` (80; ASKIP,BRT,FSET); `FKEYS` (75; ASKIP,NORM) | `COCRDLIC`, `COCRDSLC` |
| `CCRDUPA` | `COCRDUP` | Tran:; Date:; mm/dd/yy | `TRNNAME` (4; ASKIP,FSET,NORM); `TITLE01` (40; ASKIP,NORM); `CURDATE` (8; ASKIP,NORM); `PGMNAME` (8; ASKIP,NORM); `TITLE02` (40; ASKIP,NORM); `CURTIME` (8; ASKIP,NORM); `ACCTSID` (11; FSET,IC,NORM,PROT); `CARDSID` (16; FSET,NORM,UNPROT); `CRDNAME` (50; UNPROT); `CRDSTCD` (1; UNPROT); `EXPMON` (2; UNPROT); `EXPYEAR` (4; UNPROT); `EXPDAY` (2; DRK,FSET,PROT); `INFOMSG` (40; PROT); `ERRMSG` (80; ASKIP,BRT,FSET); `FKEYS` (21; ASKIP,NORM); `FKEYSC` (18; ASKIP,DRK) | `COCRDUPC` |
| `COMEN1A` | `COMEN01` | Tran:; Date:; mm/dd/yy | `TRNNAME` (4; ASKIP,FSET,NORM); `TITLE01` (40; ASKIP,FSET,NORM); `CURDATE` (8; ASKIP,FSET,NORM); `PGMNAME` (8; ASKIP,FSET,NORM); `TITLE02` (40; ASKIP,FSET,NORM); `CURTIME` (8; ASKIP,FSET,NORM); `OPTN001` (40; ASKIP,FSET,NORM); `OPTN002` (40; ASKIP,FSET,NORM); `OPTN003` (40; ASKIP,FSET,NORM); `OPTN004` (40; ASKIP,FSET,NORM); `OPTN005` (40; ASKIP,FSET,NORM); `OPTN006` (40; ASKIP,FSET,NORM); `OPTN007` (40; ASKIP,FSET,NORM); `OPTN008` (40; ASKIP,FSET,NORM); `OPTN009` (40; ASKIP,FSET,NORM); `OPTN010` (40; ASKIP,FSET,NORM); `OPTN011` (40; ASKIP,FSET,NORM); `OPTN012` (40; ASKIP,FSET,NORM); `OPTION` (2; FSET,IC,NORM,NUM,UNPROT); `ERRMSG` (78; ASKIP,BRT,FSET) | `COMEN01C` |
| `CORPT0A` | `CORPT00` | Tran:; Date:; mm/dd/yy | `TRNNAME` (4; ASKIP,FSET,NORM); `TITLE01` (40; ASKIP,FSET,NORM); `CURDATE` (8; ASKIP,FSET,NORM); `PGMNAME` (8; ASKIP,FSET,NORM); `TITLE02` (40; ASKIP,FSET,NORM); `CURTIME` (8; ASKIP,FSET,NORM); `MONTHLY` (1; FSET,IC,NORM,UNPROT); `YEARLY` (1; FSET,NORM,UNPROT); `CUSTOM` (1; FSET,NORM,UNPROT); `SDTMM` (2; FSET,NORM,NUM,UNPROT); `SDTDD` (2; FSET,NORM,NUM,UNPROT); `SDTYYYY` (4; FSET,NORM,NUM,UNPROT); `EDTMM` (2; FSET,NORM,NUM,UNPROT); `EDTDD` (2; FSET,NORM,NUM,UNPROT); `EDTYYYY` (4; FSET,NORM,NUM,UNPROT); `CONFIRM` (1; FSET,NORM,UNPROT); `ERRMSG` (78; ASKIP,BRT,FSET) | `CORPT00C` |
| `COSGN0A` | `COSGN00` | Tran :; Date :; mm/dd/yy | `TRNNAME` (4; ASKIP,FSET,NORM); `TITLE01` (40; ASKIP,FSET,NORM); `CURDATE` (8; ASKIP,FSET,NORM); `PGMNAME` (8; FSET,NORM,PROT); `TITLE02` (40; ASKIP,FSET,NORM); `CURTIME` (9; FSET,NORM,PROT); `APPLID` (8; FSET,NORM,PROT); `SYSID` (8; FSET,NORM,PROT); `USERID` (8; FSET,IC,NORM,UNPROT); `PASSWD` (8; DRK,FSET,UNPROT); `ERRMSG` (78; ASKIP,BRT,FSET) | `COSGN00C` |
| `COTRN0A` | `COTRN00` | Tran:; Date:; mm/dd/yy | `TRNNAME` (4; ASKIP,FSET,NORM); `TITLE01` (40; ASKIP,FSET,NORM); `CURDATE` (8; ASKIP,FSET,NORM); `PGMNAME` (8; ASKIP,FSET,NORM); `TITLE02` (40; ASKIP,FSET,NORM); `CURTIME` (8; ASKIP,FSET,NORM); `PAGENUM` (8; ASKIP,FSET,NORM); `TRNIDIN` (16; FSET,NORM,UNPROT); `SEL0001` (1; FSET,NORM,UNPROT); `TRNID01` (16; ASKIP,FSET,NORM); `TDATE01` (8; ASKIP,FSET,NORM); `TDESC01` (26; ASKIP,FSET,NORM); `TAMT001` (12; ASKIP,FSET,NORM); `SEL0002` (1; FSET,NORM,UNPROT); `TRNID02` (16; ASKIP,FSET,NORM); `TDATE02` (8; ASKIP,FSET,NORM); `TDESC02` (26; ASKIP,FSET,NORM); `TAMT002` (12; ASKIP,FSET,NORM); `SEL0003` (1; FSET,NORM,UNPROT); `TRNID03` (16; ASKIP,FSET,NORM); `TDATE03` (8; ASKIP,FSET,NORM); `TDESC03` (26; ASKIP,FSET,NORM); `TAMT003` (12; ASKIP,FSET,NORM); `SEL0004` (1; FSET,NORM,UNPROT); `TRNID04` (16; ASKIP,FSET,NORM); `TDATE04` (8; ASKIP,FSET,NORM); `TDESC04` (26; ASKIP,FSET,NORM); `TAMT004` (12; ASKIP,FSET,NORM); `SEL0005` (1; FSET,NORM,UNPROT); `TRNID05` (16; ASKIP,FSET,NORM); `TDATE05` (8; ASKIP,FSET,NORM); `TDESC05` (26; ASKIP,FSET,NORM); `TAMT005` (12; ASKIP,FSET,NORM); `SEL0006` (1; FSET,NORM,UNPROT); `TRNID06` (16; ASKIP,FSET,NORM); `TDATE06` (8; ASKIP,FSET,NORM); `TDESC06` (26; ASKIP,FSET,NORM); `TAMT006` (12; ASKIP,FSET,NORM); `SEL0007` (1; FSET,NORM,UNPROT); `TRNID07` (16; ASKIP,FSET,NORM); `TDATE07` (8; ASKIP,FSET,NORM); `TDESC07` (26; ASKIP,FSET,NORM); `TAMT007` (12; ASKIP,FSET,NORM); `SEL0008` (1; FSET,NORM,UNPROT); `TRNID08` (16; ASKIP,FSET,NORM); `TDATE08` (8; ASKIP,FSET,NORM); `TDESC08` (26; ASKIP,FSET,NORM); `TAMT008` (12; ASKIP,FSET,NORM); `SEL0009` (1; FSET,NORM,UNPROT); `TRNID09` (16; ASKIP,FSET,NORM); `TDATE09` (8; ASKIP,FSET,NORM); `TDESC09` (26; ASKIP,FSET,NORM); `TAMT009` (12; ASKIP,FSET,NORM); `SEL0010` (1; FSET,NORM,UNPROT); `TRNID10` (16; ASKIP,FSET,NORM); `TDATE10` (8; ASKIP,FSET,NORM); `TDESC10` (26; ASKIP,FSET,NORM); `TAMT010` (12; ASKIP,FSET,NORM); `ERRMSG` (78; ASKIP,BRT,FSET) | `COTRN00C` |
| `COTRN1A` | `COTRN01` | Tran:; Date:; mm/dd/yy | `TRNNAME` (4; ASKIP,FSET,NORM); `TITLE01` (40; ASKIP,FSET,NORM); `CURDATE` (8; ASKIP,FSET,NORM); `PGMNAME` (8; ASKIP,FSET,NORM); `TITLE02` (40; ASKIP,FSET,NORM); `CURTIME` (8; ASKIP,FSET,NORM); `TRNIDIN` (16; FSET,IC,NORM,UNPROT); `TRNID` (16; ASKIP,NORM); `CARDNUM` (16; ASKIP,NORM); `TTYPCD` (2; ASKIP,NORM); `TCATCD` (4; ASKIP,NORM); `TRNSRC` (10; ASKIP,NORM); `TDESC` (60; ASKIP,NORM); `TRNAMT` (12; ASKIP,NORM); `TORIGDT` (10; ASKIP,NORM); `TPROCDT` (10; ASKIP,NORM); `MID` (9; ASKIP,NORM); `MNAME` (30; ASKIP,NORM); `MCITY` (25; ASKIP,NORM); `MZIP` (10; ASKIP,NORM); `ERRMSG` (78; ASKIP,BRT,FSET) | `COTRN01C` |
| `COTRN2A` | `COTRN02` | Tran:; Date:; mm/dd/yy | `TRNNAME` (4; ASKIP,FSET,NORM); `TITLE01` (40; ASKIP,FSET,NORM); `CURDATE` (8; ASKIP,FSET,NORM); `PGMNAME` (8; ASKIP,FSET,NORM); `TITLE02` (40; ASKIP,FSET,NORM); `CURTIME` (8; ASKIP,FSET,NORM); `ACTIDIN` (11; FSET,IC,NORM,UNPROT); `CARDNIN` (16; FSET,NORM,UNPROT); `TTYPCD` (2; FSET,NORM,UNPROT); `TCATCD` (4; FSET,NORM,UNPROT); `TRNSRC` (10; FSET,NORM,UNPROT); `TDESC` (60; FSET,NORM,UNPROT); `TRNAMT` (12; FSET,NORM,UNPROT); `TORIGDT` (10; FSET,NORM,UNPROT); `TPROCDT` (10; FSET,NORM,UNPROT); `MID` (9; FSET,NORM,UNPROT); `MNAME` (30; FSET,NORM,UNPROT); `MCITY` (25; FSET,NORM,UNPROT); `MZIP` (10; FSET,NORM,UNPROT); `CONFIRM` (1; FSET,NORM,UNPROT); `ERRMSG` (78; ASKIP,BRT,FSET) | `COTRN02C` |
| `COUSR0A` | `COUSR00` | Tran:; Date:; mm/dd/yy | `TRNNAME` (4; ASKIP,FSET,NORM); `TITLE01` (40; ASKIP,FSET,NORM); `CURDATE` (8; ASKIP,FSET,NORM); `PGMNAME` (8; ASKIP,FSET,NORM); `TITLE02` (40; ASKIP,FSET,NORM); `CURTIME` (8; ASKIP,FSET,NORM); `PAGENUM` (8; ASKIP,FSET,NORM); `USRIDIN` (8; FSET,NORM,UNPROT); `SEL0001` (1; FSET,NORM,UNPROT); `USRID01` (8; ASKIP,FSET,NORM); `FNAME01` (20; ASKIP,FSET,NORM); `LNAME01` (20; ASKIP,FSET,NORM); `UTYPE01` (1; ASKIP,FSET,NORM); `SEL0002` (1; FSET,NORM,UNPROT); `USRID02` (8; ASKIP,FSET,NORM); `FNAME02` (20; ASKIP,FSET,NORM); `LNAME02` (20; ASKIP,FSET,NORM); `UTYPE02` (1; ASKIP,FSET,NORM); `SEL0003` (1; FSET,NORM,UNPROT); `USRID03` (8; ASKIP,FSET,NORM); `FNAME03` (20; ASKIP,FSET,NORM); `LNAME03` (20; ASKIP,FSET,NORM); `UTYPE03` (1; ASKIP,FSET,NORM); `SEL0004` (1; FSET,NORM,UNPROT); `USRID04` (8; ASKIP,FSET,NORM); `FNAME04` (20; ASKIP,FSET,NORM); `LNAME04` (20; ASKIP,FSET,NORM); `UTYPE04` (1; ASKIP,FSET,NORM); `SEL0005` (1; FSET,NORM,UNPROT); `USRID05` (8; ASKIP,FSET,NORM); `FNAME05` (20; ASKIP,FSET,NORM); `LNAME05` (20; ASKIP,FSET,NORM); `UTYPE05` (1; ASKIP,FSET,NORM); `SEL0006` (1; FSET,NORM,UNPROT); `USRID06` (8; ASKIP,FSET,NORM); `FNAME06` (20; ASKIP,FSET,NORM); `LNAME06` (20; ASKIP,FSET,NORM); `UTYPE06` (1; ASKIP,FSET,NORM); `SEL0007` (1; FSET,NORM,UNPROT); `USRID07` (8; ASKIP,FSET,NORM); `FNAME07` (20; ASKIP,FSET,NORM); `LNAME07` (20; ASKIP,FSET,NORM); `UTYPE07` (1; ASKIP,FSET,NORM); `SEL0008` (1; FSET,NORM,UNPROT); `USRID08` (8; ASKIP,FSET,NORM); `FNAME08` (20; ASKIP,FSET,NORM); `LNAME08` (20; ASKIP,FSET,NORM); `UTYPE08` (1; ASKIP,FSET,NORM); `SEL0009` (1; FSET,NORM,UNPROT); `USRID09` (8; ASKIP,FSET,NORM); `FNAME09` (20; ASKIP,FSET,NORM); `LNAME09` (20; ASKIP,FSET,NORM); `UTYPE09` (1; ASKIP,FSET,NORM); `SEL0010` (1; FSET,NORM,UNPROT); `USRID10` (8; ASKIP,FSET,NORM); `FNAME10` (20; ASKIP,FSET,NORM); `LNAME10` (20; ASKIP,FSET,NORM); `UTYPE10` (1; ASKIP,FSET,NORM); `ERRMSG` (78; ASKIP,BRT,FSET) | `COUSR00C` |
| `COUSR1A` | `COUSR01` | Tran:; Date:; mm/dd/yy | `TRNNAME` (4; ASKIP,FSET,NORM); `TITLE01` (40; ASKIP,FSET,NORM); `CURDATE` (8; ASKIP,FSET,NORM); `PGMNAME` (8; ASKIP,FSET,NORM); `TITLE02` (40; ASKIP,FSET,NORM); `CURTIME` (8; ASKIP,FSET,NORM); `FNAME` (20; FSET,IC,NORM,UNPROT); `LNAME` (20; FSET,NORM,UNPROT); `USERID` (8; FSET,NORM,UNPROT); `PASSWD` (8; DRK,FSET,UNPROT); `USRTYPE` (1; FSET,NORM,UNPROT); `ERRMSG` (78; ASKIP,BRT,FSET) | `COUSR01C` |
| `COUSR2A` | `COUSR02` | Tran:; Date:; mm/dd/yy | `TRNNAME` (4; ASKIP,FSET,NORM); `TITLE01` (40; ASKIP,FSET,NORM); `CURDATE` (8; ASKIP,FSET,NORM); `PGMNAME` (8; ASKIP,FSET,NORM); `TITLE02` (40; ASKIP,FSET,NORM); `CURTIME` (8; ASKIP,FSET,NORM); `USRIDIN` (8; FSET,IC,NORM,UNPROT); `FNAME` (20; FSET,NORM,UNPROT); `LNAME` (20; FSET,NORM,UNPROT); `PASSWD` (8; DRK,FSET,UNPROT); `USRTYPE` (1; FSET,NORM,UNPROT); `ERRMSG` (78; ASKIP,BRT,FSET) | `COUSR02C` |
| `COUSR3A` | `COUSR03` | Tran:; Date:; mm/dd/yy | `TRNNAME` (4; ASKIP,FSET,NORM); `TITLE01` (40; ASKIP,FSET,NORM); `CURDATE` (8; ASKIP,FSET,NORM); `PGMNAME` (8; ASKIP,FSET,NORM); `TITLE02` (40; ASKIP,FSET,NORM); `CURTIME` (8; ASKIP,FSET,NORM); `USRIDIN` (8; FSET,IC,NORM,UNPROT); `FNAME` (20; ASKIP,FSET,NORM); `LNAME` (20; ASKIP,FSET,NORM); `USRTYPE` (1; ASKIP,FSET,NORM); `ERRMSG` (78; ASKIP,BRT,FSET) | `COUSR03C` |

## Copybooks (`app/cpy/`, 30 files)

| Copybook | Defined record/structure (top-level and fields) | Including programs | Classification |
|---|---|---|---|
| `COADM02Y.cpy` | `unnamed`; no PIC fields | COADM01C | data record layout |
| `COCOM01Y.cpy` | `CARDDEMO-COMMAREA`; `CDEMO-FROM-TRANID` `X(04)`, `CDEMO-FROM-PROGRAM` `X(08)`, `CDEMO-TO-TRANID` `X(04)`, `CDEMO-TO-PROGRAM` `X(08)`, `CDEMO-USER-ID` `X(08)`, `CDEMO-USER-TYPE` `X(01)`, `CDEMO-PGM-CONTEXT` `9(01)`, `CDEMO-CUST-ID` `9(09)` | COACTUPC, COACTVWC, COADM01C, COBIL00C, COCRDLIC, COCRDSLC, COCRDUPC, COMEN01C, CORPT00C, COSGN00C, COTRN00C, COTRN01C, COTRN02C, COUSR00C, COUSR01C, COUSR02C, COUSR03C | screen/COMMAREA structure or constants/lookup |
| `CODATECN.cpy` | `CODATECN-REC`; `CODATECN-TYPE` `X`, `CODATECN-INP-DATE` `X(20)`, `CODATECN-1YYYY` `XXXX`, `CODATECN-1MM` `XX`, `CODATECN-1DD` `XX`, `CODATECN-1FIL` `X(12)`, `CODATECN-1O-YYYY` `XXXX`, `CODATECN-1I-S1` `X` | CBACT01C | utility structure |
| `COMEN02Y.cpy` | `CARDDEMO-MAIN-MENU-OPTIONS`; `CDEMO-MENU-OPT-COUNT` `9(02)`, `FILLER` `9(02)`, `FILLER` `X(35)`, `FILLER` `X(08)`, `FILLER` `X(01)`, `FILLER` `9(02)`, `FILLER` `X(35)`, `FILLER` `X(08)` | COMEN01C | data record layout |
| `COSTM01.CPY` | `TRNX-RECORD`; `TRNX-CARD-NUM` `X(16)`, `TRNX-ID` `X(16)`, `TRNX-TYPE-CD` `X(02)`, `TRNX-CAT-CD` `9(04)`, `TRNX-SOURCE` `X(10)`, `TRNX-DESC` `X(100)`, `TRNX-AMT` `S9(09)V99`, `TRNX-MERCHANT-ID` `9(09)` | CBSTM03A | utility structure |
| `COTTL01Y.cpy` | `CCDA-SCREEN-TITLE`; `CCDA-TITLE01` `X(40)`, `CCDA-TITLE02` `X(40)`, `CCDA-THANK-YOU` `X(40)` | COACTUPC, COACTVWC, COADM01C, COBIL00C, COCRDLIC, COCRDSLC, COCRDUPC, COMEN01C, CORPT00C, COSGN00C, COTRN00C, COTRN01C, COTRN02C, COUSR00C, COUSR01C, COUSR02C, COUSR03C | screen/COMMAREA structure or constants/lookup |
| `CSDAT01Y.cpy` | `WS-DATE-TIME`; `WS-CURDATE-YEAR` `9(04)`, `WS-CURDATE-MONTH` `9(02)`, `WS-CURDATE-DAY` `9(02)`, `WS-CURDATE-N` `9(08)`, `WS-CURTIME-HOURS` `9(02)`, `WS-CURTIME-MINUTE` `9(02)`, `WS-CURTIME-SECOND` `9(02)`, `WS-CURTIME-MILSEC` `9(02)` | COACTUPC, COACTVWC, COADM01C, COBIL00C, COCRDLIC, COCRDSLC, COCRDUPC, COMEN01C, CORPT00C, COSGN00C, COTRN00C, COTRN01C, COTRN02C, COUSR00C, COUSR01C, COUSR02C, COUSR03C | screen/COMMAREA structure or constants/lookup |
| `CSLKPCDY.cpy` | `WS-US-PHONE-AREA-CODE-TO-EDIT, US-STATE-CODE-TO-EDIT, US-STATE-ZIPCODE-TO-EDIT`; `WS-US-PHONE-AREA-CODE-TO-EDIT` `XXX`, `US-STATE-CODE-TO-EDIT` `X(2)`, `US-STATE-AND-FIRST-ZIP2` `X(4)`, `LAST-3-OF-ZIP` `X(3)` | COACTUPC | screen/COMMAREA structure or constants/lookup |
| `CSMSG01Y.cpy` | `CCDA-COMMON-MESSAGES`; `CCDA-MSG-THANK-YOU` `X(50)`, `CCDA-MSG-INVALID-KEY` `X(50)` | COACTUPC, COACTVWC, COADM01C, COBIL00C, COCRDLIC, COCRDSLC, COCRDUPC, COMEN01C, CORPT00C, COSGN00C, COTRN00C, COTRN01C, COTRN02C, COUSR00C, COUSR01C, COUSR02C, COUSR03C | screen/COMMAREA structure or constants/lookup |
| `CSMSG02Y.cpy` | `unnamed`; no PIC fields | COACTUPC, COACTVWC, COCRDLIC, COCRDSLC, COCRDUPC | screen/COMMAREA structure or constants/lookup |
| `CSSETATY.cpy` | `unnamed`; no PIC fields | COACTUPC | screen/COMMAREA structure or constants/lookup |
| `CSSTRPFY.cpy` | `unnamed`; no PIC fields | — | screen/COMMAREA structure or constants/lookup |
| `CSUSR01Y.cpy` | `SEC-USER-DATA`; `SEC-USR-ID` `X(08)`, `SEC-USR-FNAME` `X(20)`, `SEC-USR-LNAME` `X(20)`, `SEC-USR-PWD` `X(08)`, `SEC-USR-TYPE` `X(01)`, `SEC-USR-FILLER` `X(23)` | COACTUPC, COACTVWC, COADM01C, COCRDLIC, COCRDSLC, COCRDUPC, COMEN01C, COSGN00C, COUSR00C, COUSR01C, COUSR02C, COUSR03C | screen/COMMAREA structure or constants/lookup |
| `CSUTLDPY.cpy` | `unnamed`; no PIC fields | COACTUPC | screen/COMMAREA structure or constants/lookup |
| `CSUTLDWY.cpy` | `unnamed`; `WS-EDIT-DATE-CC` `X(2)`, `WS-EDIT-DATE-YY` `X(2)`, `WS-EDIT-DATE-MM` `X(2)`, `WS-EDIT-DATE-DD` `X(2)`, `WS-EDIT-DATE-BINARY` `S9(9) BINARY`, `WS-CURRENT-DATE-YYYYMMDD` `X(8)`, `WS-CURRENT-DATE-BINARY` `S9(9) BINARY`, `WS-EDIT-YEAR-FLG` `X(01)` | — | screen/COMMAREA structure or constants/lookup |
| `CUSTREC.cpy` | `CUSTOMER-RECORD`; `CUST-ID` `9(09)`, `CUST-FIRST-NAME` `X(25)`, `CUST-MIDDLE-NAME` `X(25)`, `CUST-LAST-NAME` `X(25)`, `CUST-ADDR-LINE-1` `X(50)`, `CUST-ADDR-LINE-2` `X(50)`, `CUST-ADDR-LINE-3` `X(50)`, `CUST-ADDR-STATE-CD` `X(02)` | CBSTM03A | data record layout |
| `CVACT01Y.cpy` | `ACCOUNT-RECORD`; `ACCT-ID` `9(11)`, `ACCT-ACTIVE-STATUS` `X(01)`, `ACCT-CURR-BAL` `S9(10)V99`, `ACCT-CREDIT-LIMIT` `S9(10)V99`, `ACCT-CASH-CREDIT-LIMIT` `S9(10)V99`, `ACCT-OPEN-DATE` `X(10)`, `ACCT-EXPIRAION-DATE` `X(10)`, `ACCT-REISSUE-DATE` `X(10)` | CBACT01C, CBACT04C, CBEXPORT, CBIMPORT, CBSTM03A, CBTRN01C, CBTRN02C, COACTUPC, COACTVWC, COBIL00C, COCRDSLC, COCRDUPC, COTRN02C | data record layout |
| `CVACT02Y.cpy` | `CARD-RECORD`; `CARD-NUM` `X(16)`, `CARD-ACCT-ID` `9(11)`, `CARD-CVV-CD` `9(03)`, `CARD-EMBOSSED-NAME` `X(50)`, `CARD-EXPIRAION-DATE` `X(10)`, `CARD-ACTIVE-STATUS` `X(01)`, `FILLER` `X(59)` | CBACT02C, CBEXPORT, CBIMPORT, CBTRN01C, COACTVWC, COCRDLIC, COCRDSLC, COCRDUPC | data record layout |
| `CVACT03Y.cpy` | `CARD-XREF-RECORD`; `XREF-CARD-NUM` `X(16)`, `XREF-CUST-ID` `9(09)`, `XREF-ACCT-ID` `9(11)`, `FILLER` `X(14)` | CBACT03C, CBACT04C, CBEXPORT, CBIMPORT, CBSTM03A, CBTRN01C, CBTRN02C, CBTRN03C, COACTUPC, COACTVWC, COBIL00C, COCRDSLC, COCRDUPC, COTRN02C | data record layout |
| `CVCRD01Y.cpy` | `unnamed`; `CC-ACCT-ID-N` `9(11)`, `CC-CARD-NUM-N` `9(16)` | COACTUPC, COACTVWC, COCRDLIC, COCRDSLC, COCRDUPC | data record layout |
| `CVCUS01Y.cpy` | `CUSTOMER-RECORD`; `CUST-ID` `9(09)`, `CUST-FIRST-NAME` `X(25)`, `CUST-MIDDLE-NAME` `X(25)`, `CUST-LAST-NAME` `X(25)`, `CUST-ADDR-LINE-1` `X(50)`, `CUST-ADDR-LINE-2` `X(50)`, `CUST-ADDR-LINE-3` `X(50)`, `CUST-ADDR-STATE-CD` `X(02)` | CBCUS01C, CBEXPORT, CBIMPORT, CBTRN01C, COACTUPC, COACTVWC, COCRDSLC, COCRDUPC | data record layout |
| `CVEXPORT.cpy` | `EXPORT-RECORD`; `EXPORT-REC-TYPE` `X(1)`, `EXPORT-TIMESTAMP` `X(26)`, `EXPORT-DATE` `X(10)`, `EXPORT-DATE-TIME-SEP` `X(1)`, `EXPORT-TIME` `X(15)`, `EXPORT-SEQUENCE-NUM` `9(9) COMP`, `EXPORT-BRANCH-ID` `X(4)`, `EXPORT-REGION-CODE` `X(5)` | CBEXPORT, CBIMPORT | data record layout |
| `CVTRA01Y.cpy` | `TRAN-CAT-BAL-RECORD`; `TRANCAT-ACCT-ID` `9(11)`, `TRANCAT-TYPE-CD` `X(02)`, `TRANCAT-CD` `9(04)`, `TRAN-CAT-BAL` `S9(09)V99`, `FILLER` `X(22)` | CBACT04C, CBTRN02C | data record layout |
| `CVTRA02Y.cpy` | `DIS-GROUP-RECORD`; `DIS-ACCT-GROUP-ID` `X(10)`, `DIS-TRAN-TYPE-CD` `X(02)`, `DIS-TRAN-CAT-CD` `9(04)`, `DIS-INT-RATE` `S9(04)V99`, `FILLER` `X(28)` | CBACT04C | data record layout |
| `CVTRA03Y.cpy` | `TRAN-TYPE-RECORD`; `TRAN-TYPE` `X(02)`, `TRAN-TYPE-DESC` `X(50)`, `FILLER` `X(08)` | CBTRN03C | data record layout |
| `CVTRA04Y.cpy` | `TRAN-CAT-RECORD`; `TRAN-TYPE-CD` `X(02)`, `TRAN-CAT-CD` `9(04)`, `TRAN-CAT-TYPE-DESC` `X(50)`, `FILLER` `X(04)` | CBTRN03C | data record layout |
| `CVTRA05Y.cpy` | `TRAN-RECORD`; `TRAN-ID` `X(16)`, `TRAN-TYPE-CD` `X(02)`, `TRAN-CAT-CD` `9(04)`, `TRAN-SOURCE` `X(10)`, `TRAN-DESC` `X(100)`, `TRAN-AMT` `S9(09)V99`, `TRAN-MERCHANT-ID` `9(09)`, `TRAN-MERCHANT-NAME` `X(50)` | CBACT04C, CBEXPORT, CBIMPORT, CBTRN01C, CBTRN02C, CBTRN03C, COBIL00C, CORPT00C, COTRN00C, COTRN01C, COTRN02C | data record layout |
| `CVTRA06Y.cpy` | `DALYTRAN-RECORD`; `DALYTRAN-ID` `X(16)`, `DALYTRAN-TYPE-CD` `X(02)`, `DALYTRAN-CAT-CD` `9(04)`, `DALYTRAN-SOURCE` `X(10)`, `DALYTRAN-DESC` `X(100)`, `DALYTRAN-AMT` `S9(09)V99`, `DALYTRAN-MERCHANT-ID` `9(09)`, `DALYTRAN-MERCHANT-NAME` `X(50)` | CBTRN01C, CBTRN02C | data record layout |
| `CVTRA07Y.cpy` | `REPORT-NAME-HEADER, TRANSACTION-DETAIL-REPORT, TRANSACTION-HEADER-1, TRANSACTION-HEADER-2, REPORT-PAGE-TOTALS, REPORT-ACCOUNT-TOTALS, REPORT-GRAND-TOTALS`; `REPT-SHORT-NAME` `X(38)`, `REPT-LONG-NAME` `X(41)`, `REPT-DATE-HEADER` `X(12)`, `REPT-START-DATE` `X(10)`, `FILLER` `X(04)`, `REPT-END-DATE` `X(10)`, `TRAN-REPORT-TRANS-ID` `X(16)`, `FILLER` `X(01)` | CBTRN03C | data record layout |
| `UNUSED1Y.cpy` | `UNUSED-DATA`; `UNUSED-ID` `X(08)`, `UNUSED-FNAME` `X(20)`, `UNUSED-LNAME` `X(20)`, `UNUSED-PWD` `X(08)`, `UNUSED-TYPE` `X(01)`, `UNUSED-FILLER` `X(23)` | — | data record layout |

## JCL, procedures, control, catalog, CSD, and scheduler assets

The job table lists all 38 files in `app/jcl/`; utility/program and DD names are extracted from EXEC and DD statements. Scheduler ordering is documented after the table.

| Job/file | Utility/program invoked | Inputs and outputs (DD/DSN observations) | Stream/order evidence |
|---|---|---|---|
| `ACCTFILE.jcl` | `IDCAMS` | `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `ACCTDATA=inline/DD`, `ACCTVSAM=inline/DD`, `SYSIN=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `CARDFILE.jcl` | `IDCAMS`, `SDSF` | `ISFOUT=inline/DD`, `CMDOUT=inline/DD`, `ISFIN=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `CARDDATA=inline/DD`, `CARDVSAM=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `CBADMCDJ.jcl` | `DFHCSDUP` | `STEPLIB=inline/DD`, `DFHCSD=inline/DD`, `OUTDD=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `CBEXPORT.jcl` | `CBEXPORT`, `IDCAMS` | `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `STEPLIB=inline/DD`, `CUSTFILE=inline/DD`, `ACCTFILE=inline/DD`, `XREFFILE=inline/DD`, `TRANSACT=inline/DD`, `CARDFILE=inline/DD`, `EXPFILE=inline/DD`, `SYSOUT=inline/DD`, `SYSPRINT=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `CBIMPORT.jcl` | `CBIMPORT` | `STEPLIB=inline/DD`, `EXPFILE=inline/DD`, `CUSTOUT=inline/DD`, `ACCTOUT=inline/DD`, `XREFOUT=inline/DD`, `TRNXOUT=inline/DD`, `ERROUT=inline/DD`, `SYSOUT=inline/DD`, `SYSPRINT=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `CLOSEFIL.jcl` | `SDSF` | `ISFOUT=inline/DD`, `CMDOUT=inline/DD`, `ISFIN=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `COMBTRAN.jcl` | `IDCAMS`, `SORT` | `SORTIN=inline/DD`, `SYMNAMES=inline/DD`, `SYSIN=inline/DD`, `SYSOUT=inline/DD`, `SORTOUT=inline/DD`, `SYSPRINT=inline/DD`, `TRANSACT=inline/DD`, `TRANVSAM=inline/DD`, `SYSIN=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `CREASTMT.JCL` | `CBSTM03A`, `IDCAMS`, `IEFBR14`, `SORT` | `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SORTIN=inline/DD`, `SYSPRINT=inline/DD`, `SYSOUT=inline/DD`, `SORTOUT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `INFILE=inline/DD`, `OUTFILE=inline/DD`, `SYSIN=inline/DD`, `HTMLFILE=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `CUSTFILE.jcl` | `IDCAMS`, `SDSF` | `ISFOUT=inline/DD`, `CMDOUT=inline/DD`, `ISFIN=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `CUSTDATA=inline/DD`, `CUSTVSAM=inline/DD`, `SYSIN=inline/DD`, `ISFOUT=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `DALYREJS.jcl` | `IDCAMS` | `SYSPRINT=inline/DD`, `SYSIN=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `DEFCUST.jcl` | `IDCAMS` | `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `DEFGDGB.jcl` | `IDCAMS` | `SYSPRINT=inline/DD`, `SYSIN=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `DEFGDGD.jcl` | `IDCAMS`, `IEBGENER` | `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSUT1=inline/DD`, `SYSUT2=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSUT1=inline/DD`, `SYSUT2=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `DISCGRP.jcl` | `IDCAMS` | `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `DISCGRP=inline/DD`, `DISCVSAM=inline/DD`, `SYSIN=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `DUSRSECJ.jcl` | `IDCAMS`, `IEBGENER`, `IEFBR14` | `DD01=inline/DD`, `SYSUT1=inline/DD`, `SYSUT2=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `IN=inline/DD`, `OUT=inline/DD`, `SYSOUT=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `ESDSRRDS.jcl` | `IDCAMS`, `IEBGENER`, `IEFBR14` | `DD01=inline/DD`, `SYSUT1=inline/DD`, `SYSUT2=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `IN=inline/DD`, `OUT=inline/DD`, `SYSOUT=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `FTPJCL.JCL` | `FTP` | `SYSIN=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `INTCALC.jcl` | `CBACT04C` | `STEPLIB=inline/DD`, `SYSPRINT=inline/DD`, `SYSOUT=inline/DD`, `TCATBALF=inline/DD`, `XREFFILE=inline/DD`, `XREFFIL1=inline/DD`, `ACCTFILE=inline/DD`, `DISCGRP=inline/DD`, `TRANSACT=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `INTRDRJ1.JCL` | `IDCAMS`, `IEBGENER` | `SYSPRINT=inline/DD`, `IN=inline/DD`, `OUT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSUT1=inline/DD`, `SYSUT2=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `INTRDRJ2.JCL` | `IDCAMS` | `SYSPRINT=inline/DD`, `IN=inline/DD`, `OUT=inline/DD`, `SYSIN=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `OPENFIL.jcl` | `SDSF` | `ISFOUT=inline/DD`, `CMDOUT=inline/DD`, `ISFIN=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `POSTTRAN.jcl` | `CBTRN02C` | `STEPLIB=inline/DD`, `SYSPRINT=inline/DD`, `SYSOUT=inline/DD`, `TRANFILE=inline/DD`, `DALYTRAN=inline/DD`, `XREFFILE=inline/DD`, `DALYREJS=inline/DD`, `ACCTFILE=inline/DD`, `TCATBALF=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `PRTCATBL.jcl` | `IEFBR14`, `SORT` | `THEFILE=inline/DD`, `SORTIN=inline/DD`, `SYMNAMES=inline/DD`, `SYSIN=inline/DD`, `SYSOUT=inline/DD`, `SORTOUT=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `READACCT.jcl` | `CBACT01C`, `IEFBR14` | `DD01=inline/DD`, `DD02=inline/DD`, `DD03=inline/DD`, `STEPLIB=inline/DD`, `ACCTFILE=inline/DD`, `OUTFILE=inline/DD`, `ARRYFILE=inline/DD`, `VBRCFILE=inline/DD`, `SYSOUT=inline/DD`, `SYSPRINT=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `READCARD.jcl` | `CBACT02C` | `STEPLIB=inline/DD`, `CARDFILE=inline/DD`, `SYSOUT=inline/DD`, `SYSPRINT=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `READCUST.jcl` | `CBCUS01C` | `STEPLIB=inline/DD`, `CUSTFILE=inline/DD`, `SYSOUT=inline/DD`, `SYSPRINT=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `READXREF.jcl` | `CBACT03C` | `STEPLIB=inline/DD`, `XREFFILE=inline/DD`, `SYSOUT=inline/DD`, `SYSPRINT=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `REPTFILE.jcl` | `IDCAMS` | `SYSPRINT=inline/DD`, `SYSIN=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `TCATBALF.jcl` | `IDCAMS` | `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `TCATBAL=inline/DD`, `TCATBALV=inline/DD`, `SYSIN=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `TRANBKP.jcl` | `IDCAMS` | `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `TRANCATG.jcl` | `IDCAMS` | `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `TRANCATG=inline/DD`, `TCATVSAM=inline/DD`, `SYSIN=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `TRANFILE.jcl` | `IDCAMS`, `SDSF` | `ISFOUT=inline/DD`, `CMDOUT=inline/DD`, `ISFIN=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `TRANSACT=inline/DD`, `TRANVSAM=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `TRANIDX.jcl` | `IDCAMS` | `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `TRANREPT.jcl` | `CBTRN03C`, `SORT` | `SORTIN=inline/DD`, `SYMNAMES=inline/DD`, `SYSIN=inline/DD`, `SYSOUT=inline/DD`, `SORTOUT=inline/DD`, `STEPLIB=inline/DD`, `SYSOUT=inline/DD`, `SYSPRINT=inline/DD`, `TRANFILE=inline/DD`, `CARDXREF=inline/DD`, `TRANTYPE=inline/DD`, `TRANCATG=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `TRANTYPE.jcl` | `IDCAMS` | `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `TRANTYPE=inline/DD`, `TTYPVSAM=inline/DD`, `SYSIN=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `TXT2PDF1.JCL` | `IKJEFT1B` | `STEPLIB=inline/DD`, `SYSEXEC=inline/DD`, `INDD=inline/DD`, `SYSPRINT=inline/DD`, `SYSTSPRT=inline/DD`, `SYSTSIN=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `WAITSTEP.jcl` | `COBSWAIT` | `STEPLIB=inline/DD`, `SYSOUT=inline/DD`, `SYSIN=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |
| `XREFFILE.jcl` | `IDCAMS` | `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `XREFDATA=inline/DD`, `XREFVSAM=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD`, `SYSPRINT=inline/DD`, `SYSIN=inline/DD` | See Control-M stream below / not scheduled in supplied definitions |

### `app/proc/`

- `REPROC.prc`: //REPROC PROC //****************************************************************** //* Copyright Amazon.com, Inc. or its affiliates. //* All Rights Reserved. //* //* Licensed under the Apache License, Version 2.0 (the "License"). //* You may not use this file except in compliance with the License. //* You may obtain a copy of the License at //* //* http://www.apache.org/licenses/LICENSE-2.0 //* //* Unless required by applicable law or agreed to in writing, //* software distributed under the Lice
- `TRANREPT.prc`: //REPROC PROC //****************************************************************** //* Copyright Amazon.com, Inc. or its affiliates. //* All Rights Reserved. //* //* Licensed under the Apache License, Version 2.0 (the "License"). //* You may not use this file except in compliance with the License. //* You may obtain a copy of the License at //* //* http://www.apache.org/licenses/LICENSE-2.0 //* //* Unless required by applicable law or agreed to in writing, //* software distributed under the Lice

### `app/ctl/`

- `REPROCT.ctl`: /* Copyright Amazon.com, Inc. or its affiliates. */ /* All Rights Reserved. */ /* */ /* Licensed under the Apache License, Version 2.0 (the "License"). */ /* You may not use this file except in compliance with the License.*/ /* You may obtain a copy of the License at */ /* */ /* http://www.apache.org/licenses/LICENSE-2.0 */ /* */ /* Unless required by applicable law or agreed to in writing, */ /* software distributed under the License is distributed on an */ /* "AS IS" BASIS, WITHOUT WARRANTIES 

### `app/catlg/`

- `LISTCAT.txt`: 1IDCAMS SYSTEM SERVICES TIME: 15:36:44 09/01/22 PAGE 1 0 LISTCAT LEVEL(AWS.M2.CARDDEMO) - ALL 1IDCAMS SYSTEM SERVICES TIME: 15:36:44 09/01/22 PAGE 2 - LISTING FROM CATALOG -- CATALOG.XXXXXXXX.YYYY 0NONVSAM ------- AWS.M2.CARDDEMO.ACCTDATA.PS IN-CAT --- CATALOG.XXXXXXXX.YYYY HISTORY DATASET-OWNER-----(NULL) CREATION--------2022.136 RELEASE----------------2 EXPIRATION------0000.000 ACCOUNT-INFO-----------------------------------(NULL) SMSDATA STORAGECLASS -----SCTECH MANAGEMENTCLASS---(NULL) DATAC

### `app/csd/`

- `.gitkeep`: placeholder; no source content
- `CARDDEMO.CSD`: DEFINE FILE(ACCTDAT) GROUP(CARDDEMO) DSNAME(AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS) RLSACCESS(NO) LSRPOOLNUM(1) READINTEG(UNCOMMITTED) DSNSHARING(ALLREQS) STRINGS(1) STATUS(ENABLED) OPENTIME(FIRSTREF) DISPOSITION(SHARE) DATABUFFERS(2) INDEXBUFFERS(1) TABLE(NO) MAXNUMRECS(NOLIMIT) UPDATEMODEL(LOCKING) LOAD(NO) RECORDFORMAT(V) ADD(YES) BROWSE(YES) DELETE(YES) READ(YES) UPDATE(YES) JOURNAL(NO) JNLREAD(NONE) JNLSYNCREAD(NO) JNLUPDATE(NO) JNLADD(NONE) JNLSYNCWRITE(YES) RECOVERY(NONE) FWDRECOVLOG(NO) BACK

### Actual Control-M ordering (`app/scheduler/CardDemo.controlm`)

The supplied definitions contain four folders and explicit in/out conditions. The observed chains are:
- **Daily TransactionBackup:** `CLOSEFIL → TRANBKP → WAITSTEP`; each successor consumes the predecessor condition.
- **Weekly DisclosureGroupsRefresh:** `CLOSEFIL → DISCGRP → WAITSTEP → OPENFIL`; the folder also emits the condition consumed by the transaction-type refresh.
- **Weekly TransactionTypesDBRefresh:** `MNTTRDB2 → TRANEXTR` (the source XML places this condition relationship across the folder definitions; verify scheduler deployment before cutover).
- **Monthly InterestCalculation:** `CLOSEFIL → INTCALC → COMBTRAN → WAITSTEP → OPENFIL`.

## Assembler (`app/asm/`)

| Routine | Behavior and calling convention |
|---|---|
| `COBDATFT` | Receives address of `CODATECN-REC` as the first (R1) argument; accepts type `1` (YYYYMMDD) or `2` (YYYY-MM-DD), writes the requested output format, and writes `INVALID INPUT` on incompatible type/separator/output-type combinations; returns with R15=0. |
| `MVSWAIT` | Receives address of a fullword delay value through the first argument (R1 points to pointer, then value is loaded); invokes `ASMWAIT` with that value, restores registers, returns R15=0. |

## Optional modules

### `app-authorization-ims-db2-mq`

- `bms/`: 2 files; COPAU01.bms, COPAU00.bms
- `cbl/`: 8 files; PAUDBLOD.CBL, CBPAUP0C.cbl, COPAUA0C.cbl, COPAUS2C.cbl, PAUDBUNL.CBL, COPAUS0C.cbl, DBUNLDGS.CBL, COPAUS1C.cbl
- `cpy/`: 9 files; CIPAUDTY.cpy, CCPAURQY.cpy, PADFLPCB.CPY, IMSFUNCS.cpy, CCPAURLY.cpy, PAUTBPCB.CPY, CIPAUSMY.cpy, CCPAUERY.cpy, PASFLPCB.CPY
- `cpy-bms/`: 2 files; COPAU01.cpy, COPAU00.cpy
- `csd/`: 1 files; CRDDEMO2.csd
- `data/`: 1 files; EBCDIC
- `dcl/`: 1 files; AUTHFRDS.dcl
- `ddl/`: 2 files; XAUTHFRD.ddl, AUTHFRDS.ddl
- `ims/`: 8 files; DBPAUTP0.dbd, PADFLDBD.DBD, PAUTBUNL.PSB, PASFLDBD.DBD, DBPAUTX0.dbd, DLIGSAMP.PSB, PSBPAUTB.psb, PSBPAUTL.psb
- `jcl/`: 5 files; UNLDPADB.JCL, CBPAUP0J.jcl, LOADPADB.JCL, DBPAUTP0.jcl, UNLDGSAM.JCL
- External dependency: **IMS DB + DB2 + IBM MQ**. Source files in this module are optional and are not part of the 31 base `app/cbl` programs.
- Components: `COPAUS0C`/`COPAUS1C`/`COPAUS2C` are summary/detail/process online flows with `COPAU00`/`COPAU01` BMS; `COPAUA0C` is the MQ-triggered authorization processor; `CBPAUP0C` purges expired authorizations; `PAUDBLOD`/`PAUDBUNL`/`DBUNLDGS` are IMS load/unload utilities. IMS DBD/PSB and DCL/DDL files define the pending-authorization segments/tables; IBM MQ request/reply behavior is declared in the COBOL/CSD.
### `app-transaction-type-db2`

- `bms/`: 2 files; COTRTUP.bms, COTRTLI.bms
- `cbl/`: 3 files; COTRTUPC.cbl, COBTUPDT.cbl, COTRTLIC.cbl
- `cpy/`: 2 files; CSDB2RWY.cpy, CSDB2RPY.cpy
- `cpy-bms/`: 2 files; COTRTLI.cpy, COTRTUP.cpy
- `csd/`: 1 files; CRDDEMOD.csd
- `ctl/`: 7 files; DB2LTCAT.ctl, DB2TIAD1.ctl, DB2CREAT.ctl, DB2LTTYP.ctl, REPROCT.ctl, DB2FREE.ctl, DB2TEP41.ctl
- `dcl/`: 2 files; DCLTRTYP.dcl, DCLTRCAT.dcl
- `ddl/`: 4 files; XTRNTYCAT.ddl, TRNTYPE.ddl, XTRNTYPE.ddl, TRNTYCAT.ddl
- `jcl/`: 3 files; CREADB21.jcl, TRANEXTR.jcl, MNTTRDB2.jcl
- External dependency: **DB2**. Source files in this module are optional and are not part of the 31 base `app/cbl` programs.
- Components: `COTRTUPC`/`COTRTLIC` provide transaction-type/category update/list online flows with `COTRTUP`/`COTRTLI` BMS; `COBTUPDT` and `MNTTRDB2` maintain DB2 tables. `CREADB21` creates/loads the DB2 database and `TRANEXTR` extracts transaction-type data. DCL/DDL and DB2 control members are included.
### `app-vsam-mq`

- `cbl/`: 2 files; CODATE01.cbl, COACCT01.cbl
- `csd/`: 1 files; CRDDEMOM.csd
- External dependency: **VSAM + IBM MQ**. Source files in this module are optional and are not part of the 31 base `app/cbl` programs.
- Components: `COACCT01` and `CODATE01` are MQ request/reply examples for account and date inquiries; `CRDDEMOM.csd` declares the CICS-side resources. This module does not include DB2 or IMS definitions.

## Data files (`app/data/`)

ASCII files are readable fixtures; EBCDIC files are mainframe-oriented fixed records. LRECL is derived from the source copybook/file definition where present and checked against fixture line widths. VSAM type is the JCL/IDCAMS target, not a property of the ASCII fixture.

| File | Encoding | LRECL | Record layout copybook | VSAM type |
|---|---|---:|---|---|
| `acctdata.txt` | ASCII | 300 (fixture widths observed: 300) | `CVACT01Y` | KSDS |
| `carddata.txt` | ASCII | 150 (fixture widths observed: 150) | `CVACT02Y` | KSDS |
| `cardxref.txt` | ASCII | 36 (fixture widths observed: 36) | `CVACT03Y` | KSDS |
| `custdata.txt` | ASCII | 500 (fixture widths observed: 500) | `CVCUS01Y` | KSDS |
| `dailytran.txt` | ASCII | 350 (fixture widths observed: 350) | `CVTRA06Y` | sequential |
| `discgrp.txt` | ASCII | 50 (fixture widths observed: 50) | `CVTRA02Y` | KSDS |
| `tcatbal.txt` | ASCII | 50 (fixture widths observed: 50) | `CVTRA01Y` | KSDS |
| `trancatg.txt` | ASCII | 60 (fixture widths observed: 60) | `CVTRA04Y` | KSDS |
| `trantype.txt` | ASCII | 60 (fixture widths observed: 60) | `CVTRA03Y` | KSDS |
| `AWS.M2.CARDDEMO.ACCDATA.PS` | EBCDIC (binary/fixed) | not safely inferable from bytes alone; see copybook/JCL | source dataset name identifies layout; see ASCII companion where available | JCL-defined; mostly PS input to KSDS load |
| `AWS.M2.CARDDEMO.ACCTDATA.PS` | EBCDIC (binary/fixed) | not safely inferable from bytes alone; see copybook/JCL | source dataset name identifies layout; see ASCII companion where available | JCL-defined; mostly PS input to KSDS load |
| `AWS.M2.CARDDEMO.CARDDATA.PS` | EBCDIC (binary/fixed) | not safely inferable from bytes alone; see copybook/JCL | source dataset name identifies layout; see ASCII companion where available | JCL-defined; mostly PS input to KSDS load |
| `AWS.M2.CARDDEMO.CARDXREF.PS` | EBCDIC (binary/fixed) | not safely inferable from bytes alone; see copybook/JCL | source dataset name identifies layout; see ASCII companion where available | JCL-defined; mostly PS input to KSDS load |
| `AWS.M2.CARDDEMO.CUSTDATA.PS` | EBCDIC (binary/fixed) | not safely inferable from bytes alone; see copybook/JCL | source dataset name identifies layout; see ASCII companion where available | JCL-defined; mostly PS input to KSDS load |
| `AWS.M2.CARDDEMO.DALYTRAN.PS` | EBCDIC (binary/fixed) | not safely inferable from bytes alone; see copybook/JCL | source dataset name identifies layout; see ASCII companion where available | JCL-defined; mostly PS input to KSDS load |
| `AWS.M2.CARDDEMO.DALYTRAN.PS.INIT` | EBCDIC (binary/fixed) | not safely inferable from bytes alone; see copybook/JCL | source dataset name identifies layout; see ASCII companion where available | JCL-defined; mostly PS input to KSDS load |
| `AWS.M2.CARDDEMO.DISCGRP.PS` | EBCDIC (binary/fixed) | not safely inferable from bytes alone; see copybook/JCL | source dataset name identifies layout; see ASCII companion where available | JCL-defined; mostly PS input to KSDS load |
| `AWS.M2.CARDDEMO.EXPORT.DATA.PS` | EBCDIC (binary/fixed) | not safely inferable from bytes alone; see copybook/JCL | source dataset name identifies layout; see ASCII companion where available | JCL-defined; mostly PS input to KSDS load |
| `AWS.M2.CARDDEMO.TCATBALF.PS` | EBCDIC (binary/fixed) | not safely inferable from bytes alone; see copybook/JCL | source dataset name identifies layout; see ASCII companion where available | JCL-defined; mostly PS input to KSDS load |
| `AWS.M2.CARDDEMO.TRANCATG.PS` | EBCDIC (binary/fixed) | not safely inferable from bytes alone; see copybook/JCL | source dataset name identifies layout; see ASCII companion where available | JCL-defined; mostly PS input to KSDS load |
| `AWS.M2.CARDDEMO.TRANTYPE.PS` | EBCDIC (binary/fixed) | not safely inferable from bytes alone; see copybook/JCL | source dataset name identifies layout; see ASCII companion where available | JCL-defined; mostly PS input to KSDS load |
| `AWS.M2.CARDDEMO.USRSEC.PS` | EBCDIC (binary/fixed) | not safely inferable from bytes alone; see copybook/JCL | source dataset name identifies layout; see ASCII companion where available | JCL-defined; mostly PS input to KSDS load |
