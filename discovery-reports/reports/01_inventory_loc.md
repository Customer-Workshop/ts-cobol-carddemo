# 1. Inventory Report with Lines of Code

_Application: AWS CardDemo (mainframe credit-card management)_  
_Generated: 2026-07-22T22:23:08Z_

## Summary by artifact type

| Artifact Type | Files | Total LOC | Code | Comment | Blank |
| --- | --- | --- | --- | --- | --- |
| COBOL Program | 44 | 30175 | 23636 | 3906 | 2633 |
| Copybook | 62 | 10651 | 9589 | 989 | 73 |
| Data/Control | 25 | 5909 | 5702 | 61 | 146 |
| BMS Map | 21 | 5756 | 5322 | 432 | 2 |
| JCL | 46 | 2932 | 1608 | 1304 | 20 |
| CICS CSD | 4 | 685 | 685 | 0 | 0 |
| Control Card | 8 | 253 | 232 | 0 | 21 |
| DB2 DCLGEN | 3 | 192 | 85 | 106 | 1 |
| IMS DBD | 4 | 135 | 41 | 94 | 0 |
| Assembler | 2 | 114 | 75 | 39 | 0 |
| PROC | 2 | 114 | 60 | 54 | 0 |
| IMS PSB | 4 | 88 | 24 | 64 | 0 |
| DB2 DDL | 6 | 53 | 53 | 0 | 0 |
| Assembler Macro | 2 | 43 | 11 | 32 | 0 |
| **TOTAL** | 233 | 57100 | 47123 | 7081 | 2896 |

## COBOL program inventory (LOC)

| Program | Type | Module | Total | Code | Comment | Function |
| --- | --- | --- | --- | --- | --- | --- |
| CBACT01C | Batch | base | 430 | 358 | 51 | READ THE ACCOUNT FILE AND WRITE INTO FILES. |
| CBACT02C | Batch | base | 178 | 129 | 34 | Read and print card data file. |
| CBACT03C | Batch | base | 178 | 130 | 33 | Read and print account cross reference data file. |
| CBACT04C | Batch | base | 652 | 552 | 53 | This is a interest calculator program. |
| CBCUS01C | Batch | base | 178 | 130 | 33 | Read and print customer data file. |
| CBEXPORT | Batch | base | 582 | 396 | 91 | Export Customer Data for Branch Migration |
| CBIMPORT | Batch | base | 487 | 337 | 74 | Import Customer Data from Branch Migration Export |
| CBPAUP0C | Batch | app-authorization-ims-db2-mq | 386 | 266 | 90 | Delete Expired Pending Authoriation Messages |
| CBSTM03A | Batch | base | 924 | 784 | 50 | Print Account Statements from Transaction data |
| CBSTM03B | Batch | base | 230 | 162 | 26 | Does file processing related to Transact Report |
| CBTRN01C | Batch | base | 494 | 415 | 45 | Post the records from daily transaction file. |
| CBTRN02C | Batch | base | 731 | 619 | 59 | Post the records from daily transaction file. |
| CBTRN03C | Batch | base | 649 | 545 | 53 | Print the transaction detail report. |
| COACCT01 | CICS Online | app-vsam-mq | 620 | 601 | 19 |  |
| COACTUPC | CICS Online | base | 4236 | 3368 | 492 | Accept and process ACCOUNT UPDATE |
| COACTVWC | CICS Online | base | 941 | 703 | 130 | Accept and process Account View request |
| COADM01C | CICS Online | base | 288 | 189 | 58 | Admin Menu for Admin users |
| COBIL00C | CICS Online | base | 572 | 420 | 79 | Bill Payment - Pay account balance in full and a |
| COBSWAIT | Batch | base | 41 | 13 | 22 | UTILITY PROGRAM TO WAIT (PARM IN CENTISECONDS) |
| COBTUPDT | Batch | app-transaction-type-db2 | 237 | 205 | 32 | Update Transaction type based on user input       *00040032 |
| COCRDLIC | CICS Online | base | 1459 | 1093 | 203 | List Credit Cards |
| COCRDSLC | CICS Online | base | 887 | 642 | 130 | Accept and process credit card detail request |
| COCRDUPC | CICS Online | base | 1560 | 1195 | 195 | Accept and process credit card detail request |
| CODATE01 | CICS Online | app-vsam-mq | 524 | 508 | 16 |  |
| COMEN01C | CICS Online | base | 308 | 213 | 53 | Main Menu for the Regular users |
| COPAUA0C | CICS Online | app-authorization-ims-db2-mq | 1026 | 771 | 152 | Card Authorization Decision Program |
| COPAUS0C | CICS Online | app-authorization-ims-db2-mq | 1032 | 792 | 82 | Summary View of Authoriation Messages |
| COPAUS1C | CICS Online | app-authorization-ims-db2-mq | 604 | 461 | 39 | Detail View of Authorization Message |
| COPAUS2C | CICS Online | app-authorization-ims-db2-mq | 244 | 201 | 25 | Mark Authorization Message Fraud |
| CORPT00C | CICS Online | base | 649 | 498 | 63 | Print Transaction reports by submitting batch |
| COSGN00C | CICS Online | base | 260 | 172 | 49 | Signon Screen for the CardDemo Application |
| COTRN00C | CICS Online | base | 699 | 529 | 82 | List Transactions from TRANSACT file |
| COTRN01C | CICS Online | base | 330 | 231 | 57 | View a Transaction from TRANSACT file |
| COTRN02C | CICS Online | base | 783 | 614 | 85 | Add a new Transaction to TRANSACT file |
| COTRTLIC | CICS Online | app-transaction-type-db2 | 2098 | 1861 | 237 | List Transaction Type for updates and deletes |
| COTRTUPC | CICS Online | app-transaction-type-db2 | 1702 | 1429 | 273 | Accept and process TRANSACTION TYPE UPDATE        *00040000 |
| COUSR00C | CICS Online | base | 695 | 531 | 80 | List all users from USRSEC file |
| COUSR01C | CICS Online | base | 299 | 198 | 61 | Add a new Regular/Admin user to USRSEC file |
| COUSR02C | CICS Online | base | 414 | 303 | 63 | Update a user in USRSEC file |
| COUSR03C | CICS Online | base | 359 | 251 | 63 | Delete a user from USRSEC file |
| CSUTLDTC | Batch | base | 157 | 114 | 29 |  |
| DBUNLDGS | Batch | app-authorization-ims-db2-mq | 366 | 211 | 155 |  |
| PAUDBLOD | Batch | app-authorization-ims-db2-mq | 369 | 274 | 95 |  |
| PAUDBUNL | Batch | app-authorization-ims-db2-mq | 317 | 222 | 95 |  |
