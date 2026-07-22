# 5. File (PF) to Field Mapping

_Application: AWS CardDemo (mainframe credit-card management)_  
_Generated: 2026-07-22T22:23:08Z_

Record layouts (fields, PICTURE, USAGE, byte length and offset) for each physical file, parsed from the associated COBOL copybook. Offsets assume the primary (non-REDEFINES) path.


## Account Master  
_PF: `AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS`  |  Copybook: `CVACT01Y`_

| Lvl | Field | Usage | PIC | Start | Len | Redefines |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | ACCOUNT-RECORD | GROUP |  |  |  |  |
| 5 | ACCT-ID | DISPLAY | 9(11) | 1 | 11 |  |
| 5 | ACCT-ACTIVE-STATUS | DISPLAY | X(01) | 12 | 1 |  |
| 5 | ACCT-CURR-BAL | DISPLAY | S9(10)V99 | 13 | 12 |  |
| 5 | ACCT-CREDIT-LIMIT | DISPLAY | S9(10)V99 | 25 | 12 |  |
| 5 | ACCT-CASH-CREDIT-LIMIT | DISPLAY | S9(10)V99 | 37 | 12 |  |
| 5 | ACCT-OPEN-DATE | DISPLAY | X(10) | 49 | 10 |  |
| 5 | ACCT-EXPIRAION-DATE | DISPLAY | X(10) | 59 | 10 |  |
| 5 | ACCT-REISSUE-DATE | DISPLAY | X(10) | 69 | 10 |  |
| 5 | ACCT-CURR-CYC-CREDIT | DISPLAY | S9(10)V99 | 79 | 12 |  |
| 5 | ACCT-CURR-CYC-DEBIT | DISPLAY | S9(10)V99 | 91 | 12 |  |
| 5 | ACCT-ADDR-ZIP | DISPLAY | X(10) | 103 | 10 |  |
| 5 | ACCT-GROUP-ID | DISPLAY | X(10) | 113 | 10 |  |
| 5 | FILLER | DISPLAY | X(178) | 123 | 178 |  |

## Card Master  
_PF: `AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS`  |  Copybook: `CVACT02Y`_

| Lvl | Field | Usage | PIC | Start | Len | Redefines |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | CARD-RECORD | GROUP |  |  |  |  |
| 5 | CARD-NUM | DISPLAY | X(16) | 1 | 16 |  |
| 5 | CARD-ACCT-ID | DISPLAY | 9(11) | 17 | 11 |  |
| 5 | CARD-CVV-CD | DISPLAY | 9(03) | 28 | 3 |  |
| 5 | CARD-EMBOSSED-NAME | DISPLAY | X(50) | 31 | 50 |  |
| 5 | CARD-EXPIRAION-DATE | DISPLAY | X(10) | 81 | 10 |  |
| 5 | CARD-ACTIVE-STATUS | DISPLAY | X(01) | 91 | 1 |  |
| 5 | FILLER | DISPLAY | X(59) | 92 | 59 |  |

## Card/Account/Customer Cross-Reference  
_PF: `AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS`  |  Copybook: `CVACT03Y`_

| Lvl | Field | Usage | PIC | Start | Len | Redefines |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | CARD-XREF-RECORD | GROUP |  |  |  |  |
| 5 | XREF-CARD-NUM | DISPLAY | X(16) | 1 | 16 |  |
| 5 | XREF-CUST-ID | DISPLAY | 9(09) | 17 | 9 |  |
| 5 | XREF-ACCT-ID | DISPLAY | 9(11) | 26 | 11 |  |
| 5 | FILLER | DISPLAY | X(14) | 37 | 14 |  |

## Customer Master  
_PF: `AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS`  |  Copybook: `CUSTREC`_

| Lvl | Field | Usage | PIC | Start | Len | Redefines |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | CUSTOMER-RECORD | GROUP |  |  |  |  |
| 5 | CUST-ID | DISPLAY | 9(09) | 1 | 9 |  |
| 5 | CUST-FIRST-NAME | DISPLAY | X(25) | 10 | 25 |  |
| 5 | CUST-MIDDLE-NAME | DISPLAY | X(25) | 35 | 25 |  |
| 5 | CUST-LAST-NAME | DISPLAY | X(25) | 60 | 25 |  |
| 5 | CUST-ADDR-LINE-1 | DISPLAY | X(50) | 85 | 50 |  |
| 5 | CUST-ADDR-LINE-2 | DISPLAY | X(50) | 135 | 50 |  |
| 5 | CUST-ADDR-LINE-3 | DISPLAY | X(50) | 185 | 50 |  |
| 5 | CUST-ADDR-STATE-CD | DISPLAY | X(02) | 235 | 2 |  |
| 5 | CUST-ADDR-COUNTRY-CD | DISPLAY | X(03) | 237 | 3 |  |
| 5 | CUST-ADDR-ZIP | DISPLAY | X(10) | 240 | 10 |  |
| 5 | CUST-PHONE-NUM-1 | DISPLAY | X(15) | 250 | 15 |  |
| 5 | CUST-PHONE-NUM-2 | DISPLAY | X(15) | 265 | 15 |  |
| 5 | CUST-SSN | DISPLAY | 9(09) | 280 | 9 |  |
| 5 | CUST-GOVT-ISSUED-ID | DISPLAY | X(20) | 289 | 20 |  |
| 5 | CUST-DOB-YYYYMMDD | DISPLAY | X(10) | 309 | 10 |  |
| 5 | CUST-EFT-ACCOUNT-ID | DISPLAY | X(10) | 319 | 10 |  |
| 5 | CUST-PRI-CARD-HOLDER-IND | DISPLAY | X(01) | 329 | 1 |  |
| 5 | CUST-FICO-CREDIT-SCORE | DISPLAY | 9(03) | 330 | 3 |  |
| 5 | FILLER | DISPLAY | X(168) | 333 | 168 |  |

## Transaction Master  
_PF: `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS`  |  Copybook: `CVTRA05Y`_

| Lvl | Field | Usage | PIC | Start | Len | Redefines |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | TRAN-RECORD | GROUP |  |  |  |  |
| 5 | TRAN-ID | DISPLAY | X(16) | 1 | 16 |  |
| 5 | TRAN-TYPE-CD | DISPLAY | X(02) | 17 | 2 |  |
| 5 | TRAN-CAT-CD | DISPLAY | 9(04) | 19 | 4 |  |
| 5 | TRAN-SOURCE | DISPLAY | X(10) | 23 | 10 |  |
| 5 | TRAN-DESC | DISPLAY | X(100) | 33 | 100 |  |
| 5 | TRAN-AMT | DISPLAY | S9(09)V99 | 133 | 11 |  |
| 5 | TRAN-MERCHANT-ID | DISPLAY | 9(09) | 144 | 9 |  |
| 5 | TRAN-MERCHANT-NAME | DISPLAY | X(50) | 153 | 50 |  |
| 5 | TRAN-MERCHANT-CITY | DISPLAY | X(50) | 203 | 50 |  |
| 5 | TRAN-MERCHANT-ZIP | DISPLAY | X(10) | 253 | 10 |  |
| 5 | TRAN-CARD-NUM | DISPLAY | X(16) | 263 | 16 |  |
| 5 | TRAN-ORIG-TS | DISPLAY | X(26) | 279 | 26 |  |
| 5 | TRAN-PROC-TS | DISPLAY | X(26) | 305 | 26 |  |
| 5 | FILLER | DISPLAY | X(20) | 331 | 20 |  |

## Transaction Category Balance  
_PF: `AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS`  |  Copybook: `CVTRA01Y`_

| Lvl | Field | Usage | PIC | Start | Len | Redefines |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | TRAN-CAT-BAL-RECORD | GROUP |  |  |  |  |
| 5 | TRAN-CAT-KEY | GROUP |  |  |  |  |
| 10 | TRANCAT-ACCT-ID | DISPLAY | 9(11) | 1 | 11 |  |
| 10 | TRANCAT-TYPE-CD | DISPLAY | X(02) | 12 | 2 |  |
| 10 | TRANCAT-CD | DISPLAY | 9(04) | 14 | 4 |  |
| 5 | TRAN-CAT-BAL | DISPLAY | S9(09)V99 | 18 | 11 |  |
| 5 | FILLER | DISPLAY | X(22) | 29 | 22 |  |

## Disclosure Group  
_PF: `AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS`  |  Copybook: `CVTRA02Y`_

| Lvl | Field | Usage | PIC | Start | Len | Redefines |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | DIS-GROUP-RECORD | GROUP |  |  |  |  |
| 5 | DIS-GROUP-KEY | GROUP |  |  |  |  |
| 10 | DIS-ACCT-GROUP-ID | DISPLAY | X(10) | 1 | 10 |  |
| 10 | DIS-TRAN-TYPE-CD | DISPLAY | X(02) | 11 | 2 |  |
| 10 | DIS-TRAN-CAT-CD | DISPLAY | 9(04) | 13 | 4 |  |
| 5 | DIS-INT-RATE | DISPLAY | S9(04)V99 | 17 | 6 |  |
| 5 | FILLER | DISPLAY | X(28) | 23 | 28 |  |

## Transaction Type  
_PF: `AWS.M2.CARDDEMO.TRANTYPE.VSAM.KSDS`  |  Copybook: `CVTRA03Y`_

| Lvl | Field | Usage | PIC | Start | Len | Redefines |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | TRAN-TYPE-RECORD | GROUP |  |  |  |  |
| 5 | TRAN-TYPE | DISPLAY | X(02) | 1 | 2 |  |
| 5 | TRAN-TYPE-DESC | DISPLAY | X(50) | 3 | 50 |  |
| 5 | FILLER | DISPLAY | X(08) | 53 | 8 |  |

## Transaction Category  
_PF: `AWS.M2.CARDDEMO.TRANCATG.VSAM.KSDS`  |  Copybook: `CVTRA04Y`_

| Lvl | Field | Usage | PIC | Start | Len | Redefines |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | TRAN-CAT-RECORD | GROUP |  |  |  |  |
| 5 | TRAN-CAT-KEY | GROUP |  |  |  |  |
| 10 | TRAN-TYPE-CD | DISPLAY | X(02) | 1 | 2 |  |
| 10 | TRAN-CAT-CD | DISPLAY | 9(04) | 3 | 4 |  |
| 5 | TRAN-CAT-TYPE-DESC | DISPLAY | X(50) | 7 | 50 |  |
| 5 | FILLER | DISPLAY | X(04) | 57 | 4 |  |

## User Security  
_PF: `AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS`  |  Copybook: `CSUSR01Y`_

| Lvl | Field | Usage | PIC | Start | Len | Redefines |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | SEC-USER-DATA | GROUP |  |  |  |  |
| 5 | SEC-USR-ID | DISPLAY | X(08) | 1 | 8 |  |
| 5 | SEC-USR-FNAME | DISPLAY | X(20) | 9 | 20 |  |
| 5 | SEC-USR-LNAME | DISPLAY | X(20) | 29 | 20 |  |
| 5 | SEC-USR-PWD | DISPLAY | X(08) | 49 | 8 |  |
| 5 | SEC-USR-TYPE | DISPLAY | X(01) | 57 | 1 |  |
| 5 | SEC-USR-FILLER | DISPLAY | X(23) | 58 | 23 |  |

