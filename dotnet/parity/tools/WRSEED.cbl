      ******************************************************************
      * WRSEED.cbl - Parity harness fixture seeder (NOT estate code)
      * Generates deterministic, non-degenerate seed data for the
      * recompile-to-run oracle. Writes:
      *   ACCTFILE  indexed  (key ACCT-ID)            - 12 accounts
      *   TCATBALF  indexed  (key acct+type+cat)      - accts 1-8 only
      *   XREFFILE  indexed  (+ AIX on acct id)       - CBACT04C shape
      *   CARDXREF  indexed  (primary key only)       - CBTRN03C shape
      *   DISCGRP   indexed  (incl. DEFAULT + 0-rate) - rate table
      *   TRANTYPE  indexed, TRANCATG indexed         - report lookups
      *   TRANFILE  sequential 350-byte               - 130 txns
      *   DATEPARM  line sequential                   - report window
      * Accounts 9-12 get NO tcatbal rows: they must appear byte-for-
      * byte untouched in the post-run keyed unload (missed-REWRITE
      * detector). Accounts 5-6 hit a zero interest rate, 7-8 hit the
      * DISCGRP status-23 DEFAULT fallback.
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    WRSEED.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT ACCT-FILE ASSIGN TO ACCTFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS RANDOM
                  RECORD KEY   IS FD-ACCT-ID
                  FILE STATUS  IS WS-ACCT-STAT.
           SELECT TCAT-FILE ASSIGN TO TCATBALF
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS RANDOM
                  RECORD KEY   IS FD-TCAT-KEY
                  FILE STATUS  IS WS-TCAT-STAT.
           SELECT XREF-FILE ASSIGN TO XREFFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS RANDOM
                  RECORD KEY   IS FD-XREF-CARD-NUM
                  ALTERNATE RECORD KEY IS FD-XREF-ACCT-ID
                  FILE STATUS  IS WS-XREF-STAT.
           SELECT CXRF-FILE ASSIGN TO CARDXREF
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS RANDOM
                  RECORD KEY   IS FD-CXRF-CARD-NUM
                  FILE STATUS  IS WS-CXRF-STAT.
           SELECT DISC-FILE ASSIGN TO DISCGRP
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS RANDOM
                  RECORD KEY   IS FD-DISC-KEY
                  FILE STATUS  IS WS-DISC-STAT.
           SELECT TTYP-FILE ASSIGN TO TRANTYPE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS RANDOM
                  RECORD KEY   IS FD-TTYP-CD
                  FILE STATUS  IS WS-TTYP-STAT.
           SELECT TCTG-FILE ASSIGN TO TRANCATG
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS RANDOM
                  RECORD KEY   IS FD-TCTG-KEY
                  FILE STATUS  IS WS-TCTG-STAT.
           SELECT TRAN-FILE ASSIGN TO TRANFILE
                  ORGANIZATION IS SEQUENTIAL
                  ACCESS MODE  IS SEQUENTIAL
                  FILE STATUS  IS WS-TRAN-STAT.
           SELECT TRN2-FILE ASSIGN TO TRANFIL2
                  ORGANIZATION IS SEQUENTIAL
                  ACCESS MODE  IS SEQUENTIAL
                  FILE STATUS  IS WS-TRN2-STAT.
           SELECT DPRM-FILE ASSIGN TO DATEPARM
                  ORGANIZATION IS SEQUENTIAL
                  FILE STATUS  IS WS-DPRM-STAT.
       DATA DIVISION.
       FILE SECTION.
       FD  ACCT-FILE.
       01  FD-ACCTFILE-REC.
           05 FD-ACCT-ID                        PIC 9(11).
           05 FD-ACCT-DATA                      PIC X(289).
       FD  TCAT-FILE.
       01  FD-TCAT-REC.
           05 FD-TCAT-KEY.
              10 FD-TCAT-ACCT-ID                PIC 9(11).
              10 FD-TCAT-TYPE-CD                PIC X(02).
              10 FD-TCAT-CD                     PIC 9(04).
           05 FD-TCAT-DATA                      PIC X(33).
       FD  XREF-FILE.
       01  FD-XREFFILE-REC.
           05 FD-XREF-CARD-NUM                  PIC X(16).
           05 FD-XREF-CUST-NUM                  PIC 9(09).
           05 FD-XREF-ACCT-ID                   PIC 9(11).
           05 FD-XREF-FILLER                    PIC X(14).
       FD  CXRF-FILE.
       01  FD-CXRF-REC.
           05 FD-CXRF-CARD-NUM                  PIC X(16).
           05 FD-CXRF-DATA                      PIC X(34).
       FD  DISC-FILE.
       01  FD-DISC-REC.
           05 FD-DISC-KEY.
              10 FD-DISC-GROUP-ID               PIC X(10).
              10 FD-DISC-TYPE-CD                PIC X(02).
              10 FD-DISC-CAT-CD                 PIC 9(04).
           05 FD-DISC-DATA                      PIC X(34).
       FD  TTYP-FILE.
       01  FD-TTYP-REC.
           05 FD-TTYP-CD                        PIC X(02).
           05 FD-TTYP-DATA                      PIC X(58).
       FD  TCTG-FILE.
       01  FD-TCTG-REC.
           05 FD-TCTG-KEY.
              10 FD-TCTG-TYPE-CD                PIC X(02).
              10 FD-TCTG-CAT-CD                 PIC 9(04).
           05 FD-TCTG-DATA                      PIC X(54).
       FD  TRAN-FILE.
       01  FD-TRAN-REC                          PIC X(350).
       FD  TRN2-FILE.
       01  FD-TRN2-REC                          PIC X(350).
       FD  DPRM-FILE.
       01  FD-DPRM-REC                          PIC X(80).
       WORKING-STORAGE SECTION.
       01  WS-ACCT-STAT                         PIC XX.
       01  WS-TCAT-STAT                         PIC XX.
       01  WS-XREF-STAT                         PIC XX.
       01  WS-CXRF-STAT                         PIC XX.
       01  WS-DISC-STAT                         PIC XX.
       01  WS-TTYP-STAT                         PIC XX.
       01  WS-TCTG-STAT                         PIC XX.
       01  WS-TRAN-STAT                         PIC XX.
       01  WS-TRN2-STAT                         PIC XX.
       01  WS-DPRM-STAT                         PIC XX.
       01  I                                    PIC 9(04) COMP.
       01  J                                    PIC 9(04) COMP.
       01  WS-DAY                               PIC 9(02).
       01  WS-I-DISP                            PIC 9(02).
       01  WS-CARD-IX                           PIC 9(02).
       01  ACCOUNT-RECORD.
           05  ACCT-ID                          PIC 9(11).
           05  ACCT-ACTIVE-STATUS               PIC X(01).
           05  ACCT-CURR-BAL                    PIC S9(10)V99.
           05  ACCT-CREDIT-LIMIT                PIC S9(10)V99.
           05  ACCT-CASH-CREDIT-LIMIT           PIC S9(10)V99.
           05  ACCT-OPEN-DATE                   PIC X(10).
           05  ACCT-EXPIRAION-DATE              PIC X(10).
           05  ACCT-REISSUE-DATE                PIC X(10).
           05  ACCT-CURR-CYC-CREDIT             PIC S9(10)V99.
           05  ACCT-CURR-CYC-DEBIT              PIC S9(10)V99.
           05  ACCT-ADDR-ZIP                    PIC X(10).
           05  ACCT-GROUP-ID                    PIC X(10).
           05  FILLER                           PIC X(178).
       01  TCAT-RECORD.
           05  TC-ACCT-ID                       PIC 9(11).
           05  TC-TYPE-CD                       PIC X(02).
           05  TC-CAT-CD                        PIC 9(04).
           05  TC-BAL                           PIC S9(09)V99.
           05  FILLER                           PIC X(22).
       01  DISC-RECORD.
           05  DG-GROUP-ID                      PIC X(10).
           05  DG-TYPE-CD                       PIC X(02).
           05  DG-CAT-CD                        PIC 9(04).
           05  DG-INT-RATE                      PIC S9(04)V99.
           05  FILLER                           PIC X(28).
       01  TRAN-RECORD.
           05  TRAN-ID                          PIC X(16).
           05  TRAN-TYPE-CD                     PIC X(02).
           05  TRAN-CAT-CD                      PIC 9(04).
           05  TRAN-SOURCE                      PIC X(10).
           05  TRAN-DESC                        PIC X(100).
           05  TRAN-AMT                         PIC S9(09)V99.
           05  TRAN-MERCHANT-ID                 PIC 9(09).
           05  TRAN-MERCHANT-NAME               PIC X(50).
           05  TRAN-MERCHANT-CITY               PIC X(50).
           05  TRAN-MERCHANT-ZIP                PIC X(10).
           05  TRAN-CARD-NUM                    PIC X(16).
           05  TRAN-ORIG-TS                     PIC X(26).
           05  TRAN-PROC-TS                     PIC X(26).
           05  FILLER                           PIC X(20).
       01  WS-TRAN-SEQ                          PIC 9(06) VALUE 0.
       01  WS-AMT-WORK                          PIC S9(09)V99.
       PROCEDURE DIVISION.
       MAIN-PARA.
           PERFORM SEED-ACCOUNTS
           PERFORM SEED-TCATBAL
           PERFORM SEED-XREFS
           PERFORM SEED-DISCGRP
           PERFORM SEED-LOOKUPS
           PERFORM SEED-TRANS
           PERFORM SEED-DATEPARM
           DISPLAY 'WRSEED: ALL FIXTURES WRITTEN'
           GOBACK.

       SEED-ACCOUNTS.
           OPEN OUTPUT ACCT-FILE
           PERFORM VARYING I FROM 1 BY 1 UNTIL I > 12
               INITIALIZE ACCOUNT-RECORD
               MOVE I                        TO ACCT-ID
               IF FUNCTION MOD(I, 2) = 1
                   MOVE 'Y' TO ACCT-ACTIVE-STATUS
               ELSE
                   MOVE 'N' TO ACCT-ACTIVE-STATUS
               END-IF
               COMPUTE ACCT-CURR-BAL = I * 1111.11 - 5000
               COMPUTE ACCT-CREDIT-LIMIT = 10000 + I * 500
               COMPUTE ACCT-CASH-CREDIT-LIMIT = 5000 + I * 250
               MOVE '2019-01-15'             TO ACCT-OPEN-DATE
               MOVE '2027-12-31'             TO ACCT-EXPIRAION-DATE
               MOVE '2024-06-30'             TO ACCT-REISSUE-DATE
               COMPUTE ACCT-CURR-CYC-CREDIT = I * 77.25
               COMPUTE ACCT-CURR-CYC-DEBIT  = I * 33.10
               MOVE '75001'                  TO ACCT-ADDR-ZIP
               EVALUATE TRUE
                 WHEN I <= 4  MOVE 'GRPA000001' TO ACCT-GROUP-ID
                 WHEN I <= 6  MOVE 'GRPZERO001' TO ACCT-GROUP-ID
                 WHEN I <= 8  MOVE 'GRPMISSING' TO ACCT-GROUP-ID
                 WHEN OTHER   MOVE 'GRPA000001' TO ACCT-GROUP-ID
               END-EVALUATE
               MOVE ACCOUNT-RECORD TO FD-ACCTFILE-REC
               WRITE FD-ACCTFILE-REC
               IF WS-ACCT-STAT NOT = '00'
                   DISPLAY 'WRSEED ACCT WRITE FAIL ' WS-ACCT-STAT
                   MOVE 12 TO RETURN-CODE
                   STOP RUN
               END-IF
           END-PERFORM
           CLOSE ACCT-FILE.

       SEED-TCATBAL.
           OPEN OUTPUT TCAT-FILE
           PERFORM VARYING I FROM 1 BY 1 UNTIL I > 8
               INITIALIZE TCAT-RECORD
               MOVE I     TO TC-ACCT-ID
               MOVE '01'  TO TC-TYPE-CD
               MOVE 1     TO TC-CAT-CD
               COMPUTE TC-BAL = I * 250.33 + 100
               PERFORM WRITE-TCAT
               IF FUNCTION MOD(I, 2) = 0
                   MOVE 2 TO TC-CAT-CD
                   COMPUTE TC-BAL = I * 175.50 - 400
                   PERFORM WRITE-TCAT
               END-IF
               IF FUNCTION MOD(I, 3) = 0
                   MOVE '02' TO TC-TYPE-CD
                   MOVE 3    TO TC-CAT-CD
                   COMPUTE TC-BAL = I * 990.75
                   PERFORM WRITE-TCAT
               END-IF
           END-PERFORM
           CLOSE TCAT-FILE.

       WRITE-TCAT.
           MOVE TCAT-RECORD TO FD-TCAT-REC
           WRITE FD-TCAT-REC
           IF WS-TCAT-STAT NOT = '00'
               DISPLAY 'WRSEED TCAT WRITE FAIL ' WS-TCAT-STAT
               MOVE 12 TO RETURN-CODE
               STOP RUN
           END-IF.

       SEED-XREFS.
           OPEN OUTPUT XREF-FILE
           OPEN OUTPUT CXRF-FILE
           PERFORM VARYING I FROM 1 BY 1 UNTIL I > 12
               MOVE SPACES TO FD-XREFFILE-REC
               MOVE I TO WS-I-DISP
               STRING '40000000000000' WS-I-DISP DELIMITED BY SIZE
                   INTO FD-XREF-CARD-NUM
               END-STRING
               COMPUTE FD-XREF-CUST-NUM = 100000000 + I
               MOVE I TO FD-XREF-ACCT-ID
               MOVE SPACES TO FD-XREF-FILLER
               WRITE FD-XREFFILE-REC
               IF WS-XREF-STAT NOT = '00'
                   DISPLAY 'WRSEED XREF WRITE FAIL ' WS-XREF-STAT
                   MOVE 12 TO RETURN-CODE
                   STOP RUN
               END-IF
               MOVE FD-XREFFILE-REC TO FD-CXRF-REC
               WRITE FD-CXRF-REC
               IF WS-CXRF-STAT NOT = '00'
                   DISPLAY 'WRSEED CXRF WRITE FAIL ' WS-CXRF-STAT
                   MOVE 12 TO RETURN-CODE
                   STOP RUN
               END-IF
           END-PERFORM
           CLOSE XREF-FILE
           CLOSE CXRF-FILE.

       SEED-DISCGRP.
           OPEN OUTPUT DISC-FILE
           MOVE 'GRPA000001' TO DG-GROUP-ID
           MOVE '01' TO DG-TYPE-CD
           MOVE 1    TO DG-CAT-CD
           MOVE 14.99 TO DG-INT-RATE
           PERFORM WRITE-DISC
           MOVE 2    TO DG-CAT-CD
           MOVE 19.99 TO DG-INT-RATE
           PERFORM WRITE-DISC
           MOVE '02' TO DG-TYPE-CD
           MOVE 3    TO DG-CAT-CD
           MOVE 21.50 TO DG-INT-RATE
           PERFORM WRITE-DISC
           MOVE 'GRPZERO001' TO DG-GROUP-ID
           MOVE '01' TO DG-TYPE-CD
           MOVE 1    TO DG-CAT-CD
           MOVE 0    TO DG-INT-RATE
           PERFORM WRITE-DISC
           MOVE 2    TO DG-CAT-CD
           PERFORM WRITE-DISC
           MOVE '02' TO DG-TYPE-CD
           MOVE 3    TO DG-CAT-CD
           PERFORM WRITE-DISC
           MOVE 'DEFAULT'    TO DG-GROUP-ID
           MOVE '01' TO DG-TYPE-CD
           MOVE 1    TO DG-CAT-CD
           MOVE 25.00 TO DG-INT-RATE
           PERFORM WRITE-DISC
           MOVE 2    TO DG-CAT-CD
           PERFORM WRITE-DISC
           MOVE '02' TO DG-TYPE-CD
           MOVE 3    TO DG-CAT-CD
           PERFORM WRITE-DISC
           CLOSE DISC-FILE.

       WRITE-DISC.
           MOVE SPACES TO FD-DISC-DATA
           MOVE DISC-RECORD TO FD-DISC-REC
           WRITE FD-DISC-REC
           IF WS-DISC-STAT NOT = '00'
               DISPLAY 'WRSEED DISC WRITE FAIL ' WS-DISC-STAT
               MOVE 12 TO RETURN-CODE
               STOP RUN
           END-IF.

       SEED-LOOKUPS.
           OPEN OUTPUT TTYP-FILE
           MOVE SPACES TO FD-TTYP-REC
           MOVE '01' TO FD-TTYP-CD
           MOVE 'PURCHASE' TO FD-TTYP-DATA
           WRITE FD-TTYP-REC
           MOVE SPACES TO FD-TTYP-REC
           MOVE '02' TO FD-TTYP-CD
           MOVE 'PAYMENT' TO FD-TTYP-DATA
           WRITE FD-TTYP-REC
           CLOSE TTYP-FILE
           OPEN OUTPUT TCTG-FILE
           MOVE SPACES TO FD-TCTG-REC
           MOVE '01' TO FD-TCTG-TYPE-CD
           MOVE 1 TO FD-TCTG-CAT-CD
           MOVE 'RETAIL PURCHASE' TO FD-TCTG-DATA
           WRITE FD-TCTG-REC
           MOVE SPACES TO FD-TCTG-REC
           MOVE '01' TO FD-TCTG-TYPE-CD
           MOVE 2 TO FD-TCTG-CAT-CD
           MOVE 'CASH ADVANCE' TO FD-TCTG-DATA
           WRITE FD-TCTG-REC
           MOVE SPACES TO FD-TCTG-REC
           MOVE '02' TO FD-TCTG-TYPE-CD
           MOVE 3 TO FD-TCTG-CAT-CD
           MOVE 'BILL PAYMENT' TO FD-TCTG-DATA
           WRITE FD-TCTG-REC
           CLOSE TCTG-FILE.

      * Two transaction files from one deterministic stream:
      *   TRANFILE - txns 125+ carry out-of-window timestamps, which
      *     (per estate behavior) terminate CBTRN03C's report loop via
      *     NEXT SENTENCE; goldens preserve this bug-for-bug.
      *   TRANFIL2 - all in-window, so the EOF grand-total path runs.
       SEED-TRANS.
           OPEN OUTPUT TRAN-FILE
           OPEN OUTPUT TRN2-FILE
           PERFORM VARYING WS-CARD-IX FROM 1 BY 1 UNTIL WS-CARD-IX > 4
             PERFORM VARYING J FROM 1 BY 1 UNTIL J > 33
               INITIALIZE TRAN-RECORD
               ADD 1 TO WS-TRAN-SEQ
               STRING 'TR' WS-TRAN-SEQ '00000000'
                   DELIMITED BY SIZE INTO TRAN-ID
               END-STRING
               IF FUNCTION MOD(J, 5) = 0
                   MOVE '02' TO TRAN-TYPE-CD
                   MOVE 3    TO TRAN-CAT-CD
                   COMPUTE WS-AMT-WORK = -1 * (J * 45.05 + WS-CARD-IX)
               ELSE
                   MOVE '01' TO TRAN-TYPE-CD
                   IF FUNCTION MOD(J, 2) = 0
                       MOVE 2 TO TRAN-CAT-CD
                   ELSE
                       MOVE 1 TO TRAN-CAT-CD
                   END-IF
                   COMPUTE WS-AMT-WORK
                       = J * 13.37 + WS-CARD-IX * 100
               END-IF
               MOVE WS-AMT-WORK TO TRAN-AMT
               MOVE 'POS TERM'  TO TRAN-SOURCE
               MOVE 'SEEDED PARITY TRANSACTION' TO TRAN-DESC
               COMPUTE TRAN-MERCHANT-ID = 900000000 + J
               MOVE 'MERCHANT OF RECORD' TO TRAN-MERCHANT-NAME
               MOVE 'DALLAS'    TO TRAN-MERCHANT-CITY
               MOVE '75001'     TO TRAN-MERCHANT-ZIP
               MOVE 0 TO WS-DAY
               COMPUTE WS-DAY = FUNCTION MOD(J - 1, 28) + 1
               STRING '40000000000000' WS-CARD-IX
                   DELIMITED BY SIZE INTO TRAN-CARD-NUM
               END-STRING
               STRING '2025-07-' WS-DAY ' 10.00.00.000000'
                   DELIMITED BY SIZE INTO TRAN-PROC-TS
               END-STRING
               MOVE TRAN-PROC-TS TO TRAN-ORIG-TS
               MOVE TRAN-RECORD TO FD-TRN2-REC
               WRITE FD-TRN2-REC
               IF WS-TRN2-STAT NOT = '00'
                   DISPLAY 'WRSEED TRN2 WRITE FAIL ' WS-TRN2-STAT
                   MOVE 12 TO RETURN-CODE
                   STOP RUN
               END-IF
               IF WS-TRAN-SEQ >= 125
                   STRING '2025-09-' WS-DAY ' 10.00.00.000000'
                       DELIMITED BY SIZE INTO TRAN-PROC-TS
                   END-STRING
                   MOVE TRAN-PROC-TS TO TRAN-ORIG-TS
               END-IF
               MOVE TRAN-RECORD TO FD-TRAN-REC
               WRITE FD-TRAN-REC
               IF WS-TRAN-STAT NOT = '00'
                   DISPLAY 'WRSEED TRAN WRITE FAIL ' WS-TRAN-STAT
                   MOVE 12 TO RETURN-CODE
                   STOP RUN
               END-IF
             END-PERFORM
           END-PERFORM
           CLOSE TRAN-FILE
           CLOSE TRN2-FILE.

       SEED-DATEPARM.
           OPEN OUTPUT DPRM-FILE
           MOVE '2025-07-01 2025-08-31' TO FD-DPRM-REC
           WRITE FD-DPRM-REC
           CLOSE DPRM-FILE.
       END PROGRAM WRSEED.
