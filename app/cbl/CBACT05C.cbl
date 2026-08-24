      ******************************************************************
      * Program     : CBACT05C.CBL
      * Application : CardDemo
      * Type        : BATCH COBOL Program
      * Function    : Extract interest and fees analytics.
      ******************************************************************
      * Copyright Amazon.com, Inc. or its affiliates.
      * All Rights Reserved.
      *
      * Licensed under the Apache License, Version 2.0 (the "License").
      * You may not use this file except in compliance with the License.
      * You may obtain a copy of the License at
      *
      *    http://www.apache.org/licenses/LICENSE-2.0
      *
      * Unless required by applicable law or agreed to in writing,
      * software distributed under the License is distributed on an
      * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
      * either express or implied. See the License for the specific
      * language governing permissions and limitations under the License
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    CBACT05C.
       AUTHOR.        AWS.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT TCATBAL-FILE ASSIGN TO TCATBALF
                  ORGANIZATION IS INDEXED
                  ACCESS MODE IS SEQUENTIAL
                  RECORD KEY IS FD-TRAN-CAT-KEY
                  FILE STATUS IS TCATBALF-STATUS.

           SELECT ACCOUNT-FILE ASSIGN TO ACCTFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE IS RANDOM
                  RECORD KEY IS FD-ACCT-ID
                  FILE STATUS IS ACCTFILE-STATUS.

           SELECT DISCGRP-FILE ASSIGN TO DISCGRP
                  ORGANIZATION IS INDEXED
                  ACCESS MODE IS RANDOM
                  RECORD KEY IS FD-DISCGRP-KEY
                  FILE STATUS IS DISCGRP-STATUS.

           SELECT INTRPT-FILE ASSIGN TO INTRPT
                  ORGANIZATION IS SEQUENTIAL
                  ACCESS MODE IS SEQUENTIAL
                  FILE STATUS IS INTRPT-STATUS.

       DATA DIVISION.
       FILE SECTION.
       FD  TCATBAL-FILE.
       01  FD-TRAN-CAT-BAL-RECORD.
           05 FD-TRAN-CAT-KEY.
              10 FD-TRANCAT-ACCT-ID             PIC 9(11).
              10 FD-TRANCAT-TYPE-CD             PIC X(02).
              10 FD-TRANCAT-CD                  PIC 9(04).
           05 FD-FD-TRAN-CAT-DATA               PIC X(33).

       FD  ACCOUNT-FILE.
       01  FD-ACCTFILE-REC.
           05 FD-ACCT-ID                        PIC 9(11).
           05 FD-ACCT-DATA                      PIC X(289).

       FD  DISCGRP-FILE.
       01  FD-DISCGRP-REC.
           05 FD-DISCGRP-KEY.
              10 FD-DIS-ACCT-GROUP-ID           PIC X(10).
              10 FD-DIS-TRAN-TYPE-CD            PIC X(02).
              10 FD-DIS-TRAN-CAT-CD             PIC 9(04).
           05 FD-DISCGRP-DATA                   PIC X(34).

       FD  INTRPT-FILE.
       01  FD-INTRPT-REC                         PIC X(120).

       WORKING-STORAGE SECTION.

      *****************************************************************
       COPY CVTRA01Y.
       01  TCATBALF-STATUS.
           05  TCATBALF-STAT1      PIC X.
           05  TCATBALF-STAT2      PIC X.

       COPY CVACT01Y.
       01  ACCTFILE-STATUS.
           05  ACCTFILE-STAT1      PIC X.
           05  ACCTFILE-STAT2      PIC X.

       COPY CVTRA02Y.
       01  DISCGRP-STATUS.
           05  DISCGRP-STAT1       PIC X.
           05  DISCGRP-STAT2       PIC X.

       01  INTRPT-STATUS.
           05  INTRPT-STAT1        PIC X.
           05  INTRPT-STAT2        PIC X.

       01  IO-STATUS.
           05  IO-STAT1            PIC X.
           05  IO-STAT2            PIC X.
       01  TWO-BYTES-BINARY        PIC 9(4) BINARY.
       01  TWO-BYTES-ALPHA         REDEFINES TWO-BYTES-BINARY.
           05  TWO-BYTES-LEFT      PIC X.
           05  TWO-BYTES-RIGHT     PIC X.
       01  IO-STATUS-04.
           05  IO-STATUS-0401      PIC 9 VALUE 0.
           05  IO-STATUS-0403      PIC 999 VALUE 0.

       01  APPL-RESULT             PIC S9(9) COMP.
           88  APPL-AOK            VALUE 0.
           88  APPL-EOF            VALUE 16.
       01  END-OF-FILE             PIC X VALUE 'N'.
       01  HAVE-ACCOUNT             PIC X VALUE 'N'.
       01  ABCODE                  PIC S9(9) BINARY.
       01  TIMING                  PIC S9(9) BINARY.

       01  WS-CURRENT-ACCT-ID      PIC 9(11) VALUE 0.
       01  WS-CATEGORY-COUNT       PIC 9(5) VALUE 0.
       01  WS-TOTAL-BALANCE        PIC S9(11)V99 VALUE 0.
       01  WS-MONTHLY-INT          PIC S9(09)V99 VALUE 0.
       01  WS-TOTAL-INT            PIC S9(11)V99 VALUE 0.
       01  WS-TOTAL-FEES           PIC S9(11)V99 VALUE 0.
       01  WS-WEIGHTED-RATE        PIC S9(15)V9(4) VALUE 0.
       01  WS-AVG-RATE             PIC S9(04)V99 VALUE 0.

       01  WS-EDIT-COUNT           PIC ZZZZ9.
       01  WS-EDIT-BALANCE         PIC -ZZZZZZZZZZ9.99.
       01  WS-EDIT-INTEREST        PIC -ZZZZZZZZZZ9.99.
       01  WS-EDIT-FEES            PIC -ZZZZZZZZZZ9.99.
       01  WS-EDIT-RATE            PIC -ZZZ9.99.

       01  WS-ACCT-ID-TEXT         PIC X(11).
       01  WS-GROUP-TEXT           PIC X(20).
       01  WS-COUNT-TEXT           PIC X(20).
       01  WS-BALANCE-TEXT         PIC X(20).
       01  WS-INTEREST-TEXT        PIC X(20).
       01  WS-FEES-TEXT            PIC X(20).
       01  WS-RATE-TEXT            PIC X(20).
       01  WS-GROUP-LENGTH         PIC 99 VALUE 0.
       01  WS-COUNT-LENGTH         PIC 99 VALUE 0.
       01  WS-BALANCE-LENGTH       PIC 99 VALUE 0.
       01  WS-INTEREST-LENGTH      PIC 99 VALUE 0.
       01  WS-FEES-LENGTH          PIC 99 VALUE 0.
       01  WS-RATE-LENGTH          PIC 99 VALUE 0.

       01  WS-FMT-SOURCE           PIC X(20).
       01  WS-FMT-TARGET           PIC X(20).
       01  WS-FMT-LENGTH           PIC 99 VALUE 0.
       01  WS-FMT-FIRST            PIC 99 VALUE 0.
       01  WS-FMT-SOURCE-INDEX     PIC 99 VALUE 0.
       01  WS-FMT-TARGET-INDEX     PIC 99 VALUE 0.
       01  WS-FMT-DONE             PIC X VALUE 'N'.

       PROCEDURE DIVISION.
           PERFORM 0000-INITIALIZE
           PERFORM 1000-PROCESS-FILE
           PERFORM 9000-CLOSE-FILES
           GOBACK.

      *---------------------------------------------------------------*
       0000-INITIALIZE.
           DISPLAY 'CBACT05C INTEREST ANALYTICS EXTRACT STARTING'
           PERFORM 0100-TCATBALF-OPEN
           PERFORM 0200-ACCTFILE-OPEN
           PERFORM 0300-DISCGRP-OPEN
           PERFORM 0400-INTRPT-OPEN
           MOVE 'N' TO END-OF-FILE
           MOVE 'N' TO HAVE-ACCOUNT
           PERFORM 1050-READ-TCATBAL
           EXIT.

      *---------------------------------------------------------------*
       0100-TCATBALF-OPEN.
           MOVE 8 TO APPL-RESULT
           OPEN INPUT TCATBAL-FILE
           IF TCATBALF-STATUS = '00'
               MOVE 0 TO APPL-RESULT
           END-IF
           IF NOT APPL-AOK
               DISPLAY 'ERROR OPENING TRANSACTION CATEGORY BALANCE'
               MOVE TCATBALF-STATUS TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           EXIT.

      *---------------------------------------------------------------*
       0200-ACCTFILE-OPEN.
           MOVE 8 TO APPL-RESULT
           OPEN INPUT ACCOUNT-FILE
           IF ACCTFILE-STATUS = '00'
               MOVE 0 TO APPL-RESULT
           END-IF
           IF NOT APPL-AOK
               DISPLAY 'ERROR OPENING ACCOUNT MASTER FILE'
               MOVE ACCTFILE-STATUS TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           EXIT.

      *---------------------------------------------------------------*
       0300-DISCGRP-OPEN.
           MOVE 8 TO APPL-RESULT
           OPEN INPUT DISCGRP-FILE
           IF DISCGRP-STATUS = '00'
               MOVE 0 TO APPL-RESULT
           END-IF
           IF NOT APPL-AOK
               DISPLAY 'ERROR OPENING DISCLOSURE GROUP FILE'
               MOVE DISCGRP-STATUS TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           EXIT.

      *---------------------------------------------------------------*
       0400-INTRPT-OPEN.
           MOVE 8 TO APPL-RESULT
           OPEN OUTPUT INTRPT-FILE
           IF INTRPT-STATUS = '00'
               MOVE 0 TO APPL-RESULT
           END-IF
           IF NOT APPL-AOK
               DISPLAY 'ERROR OPENING INTEREST REPORT FILE'
               MOVE INTRPT-STATUS TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           MOVE SPACES TO FD-INTRPT-REC
           MOVE SPACES TO FD-INTRPT-REC
           STRING
             'acct_id,acct_group_id,category_count,total_balance,'
               DELIMITED BY SIZE
             'total_interest,total_fees,avg_interest_rate'
               DELIMITED BY SIZE
             INTO FD-INTRPT-REC
           END-STRING
           WRITE FD-INTRPT-REC
           IF INTRPT-STATUS = '00'
               MOVE 0 TO APPL-RESULT
           ELSE
               MOVE 12 TO APPL-RESULT
           END-IF
           IF NOT APPL-AOK
               DISPLAY 'ERROR WRITING INTEREST REPORT HEADER'
               MOVE INTRPT-STATUS TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           EXIT.

      *---------------------------------------------------------------*
       1000-PROCESS-FILE.
           PERFORM UNTIL END-OF-FILE = 'Y'
               IF HAVE-ACCOUNT = 'N'
                   MOVE TRANCAT-ACCT-ID TO WS-CURRENT-ACCT-ID
                   PERFORM 1100-GET-ACCT-DATA
                   PERFORM 1150-RESET-ACCOUNT
                   MOVE 'Y' TO HAVE-ACCOUNT
               ELSE
                   IF TRANCAT-ACCT-ID NOT = WS-CURRENT-ACCT-ID
                       PERFORM 1500-WRITE-ACCOUNT
                       MOVE TRANCAT-ACCT-ID TO WS-CURRENT-ACCT-ID
                       PERFORM 1100-GET-ACCT-DATA
                       PERFORM 1150-RESET-ACCOUNT
                   END-IF
               END-IF
               ADD 1 TO WS-CATEGORY-COUNT
               ADD TRAN-CAT-BAL TO WS-TOTAL-BALANCE
               COMPUTE WS-WEIGHTED-RATE = WS-WEIGHTED-RATE
                    + (TRAN-CAT-BAL * DIS-INT-RATE)
               IF DIS-INT-RATE NOT = 0
                   PERFORM 1300-COMPUTE-INTEREST
               ELSE
                   MOVE 0 TO WS-MONTHLY-INT
               END-IF
               PERFORM 1050-READ-TCATBAL
           END-PERFORM
           IF HAVE-ACCOUNT = 'Y'
               PERFORM 1500-WRITE-ACCOUNT
           END-IF
           EXIT.

      *---------------------------------------------------------------*
       1050-READ-TCATBAL.
           READ TCATBAL-FILE INTO TRAN-CAT-BAL-RECORD
               AT END
                   MOVE 'Y' TO END-OF-FILE
           END-READ
           IF TCATBALF-STATUS = '00'
               MOVE 0 TO APPL-RESULT
           ELSE
               IF TCATBALF-STATUS = '10'
                   MOVE 16 TO APPL-RESULT
                   MOVE 'Y' TO END-OF-FILE
               ELSE
                   MOVE 12 TO APPL-RESULT
               END-IF
           END-IF
           IF NOT APPL-AOK AND NOT APPL-EOF
               DISPLAY 'ERROR READING TRANSACTION CATEGORY FILE'
               MOVE TCATBALF-STATUS TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           EXIT.

      *---------------------------------------------------------------*
       1100-GET-ACCT-DATA.
           MOVE SPACES TO ACCT-GROUP-ID
           MOVE WS-CURRENT-ACCT-ID TO FD-ACCT-ID
           READ ACCOUNT-FILE INTO ACCOUNT-RECORD
               KEY IS FD-ACCT-ID
               INVALID KEY
                   DISPLAY 'ACCOUNT NOT FOUND: ' FD-ACCT-ID
           END-READ
           IF ACCTFILE-STATUS = '00'
               MOVE 0 TO APPL-RESULT
           ELSE
               IF ACCTFILE-STATUS = '23'
                   DISPLAY 'ACCOUNT NOT FOUND: ' FD-ACCT-ID
                   MOVE SPACES TO ACCT-GROUP-ID
                   MOVE 0 TO APPL-RESULT
               ELSE
                   MOVE 12 TO APPL-RESULT
               END-IF
           END-IF
           IF NOT APPL-AOK
               DISPLAY 'ERROR READING ACCOUNT FILE'
               MOVE ACCTFILE-STATUS TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           EXIT.

      *---------------------------------------------------------------*
       1150-RESET-ACCOUNT.
           MOVE 0 TO WS-CATEGORY-COUNT
           MOVE 0 TO WS-TOTAL-BALANCE
           MOVE 0 TO WS-TOTAL-INT
           MOVE 0 TO WS-TOTAL-FEES
           MOVE 0 TO WS-WEIGHTED-RATE
           MOVE 0 TO WS-AVG-RATE
           EXIT.

      *---------------------------------------------------------------*
       1200-GET-INTEREST-RATE.
           MOVE ACCT-GROUP-ID TO FD-DIS-ACCT-GROUP-ID
           MOVE TRANCAT-TYPE-CD TO FD-DIS-TRAN-TYPE-CD
           MOVE TRANCAT-CD TO FD-DIS-TRAN-CAT-CD
           READ DISCGRP-FILE INTO DIS-GROUP-RECORD
               KEY IS FD-DISCGRP-KEY
               INVALID KEY
                   DISPLAY 'DISCLOSURE GROUP RECORD MISSING'
                   DISPLAY 'TRY WITH DEFAULT GROUP CODE'
           END-READ
           IF DISCGRP-STATUS = '00'
               MOVE 0 TO APPL-RESULT
           ELSE
               IF DISCGRP-STATUS = '23'
                   MOVE 0 TO APPL-RESULT
               ELSE
                   MOVE 12 TO APPL-RESULT
               END-IF
           END-IF
           IF NOT APPL-AOK
               DISPLAY 'ERROR READING DISCLOSURE GROUP FILE'
               MOVE DISCGRP-STATUS TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           IF DISCGRP-STATUS = '23'
               MOVE 'DEFAULT' TO FD-DIS-ACCT-GROUP-ID
               PERFORM 1200-A-GET-DEFAULT-INT-RATE
           END-IF
           EXIT.

      *---------------------------------------------------------------*
       1200-A-GET-DEFAULT-INT-RATE.
           READ DISCGRP-FILE INTO DIS-GROUP-RECORD
               KEY IS FD-DISCGRP-KEY
           END-READ
           IF DISCGRP-STATUS = '00'
               MOVE 0 TO APPL-RESULT
           ELSE
               MOVE 12 TO APPL-RESULT
           END-IF
           IF NOT APPL-AOK
               DISPLAY 'ERROR READING DEFAULT DISCLOSURE GROUP'
               MOVE DISCGRP-STATUS TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           EXIT.

      *---------------------------------------------------------------*
       1300-COMPUTE-INTEREST.
           COMPUTE WS-MONTHLY-INT
             = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200
           ADD WS-MONTHLY-INT TO WS-TOTAL-INT
           EXIT.

      *---------------------------------------------------------------*
       1400-COMPUTE-FEES.
      * CardDemo has no explicit fee amount for this extract.
           MOVE 0 TO WS-TOTAL-FEES
           EXIT.

      *---------------------------------------------------------------*
       1500-WRITE-ACCOUNT.
           PERFORM 1400-COMPUTE-FEES
           IF WS-TOTAL-BALANCE NOT = 0
               COMPUTE WS-AVG-RATE ROUNDED =
                   WS-WEIGHTED-RATE / WS-TOTAL-BALANCE
           ELSE
               MOVE 0 TO WS-AVG-RATE
           END-IF
           MOVE WS-CURRENT-ACCT-ID TO WS-ACCT-ID-TEXT

           MOVE ACCT-GROUP-ID TO WS-FMT-SOURCE
           PERFORM 6100-LEFT-JUSTIFY
           MOVE WS-FMT-TARGET TO WS-GROUP-TEXT
           MOVE WS-FMT-LENGTH TO WS-GROUP-LENGTH

           MOVE WS-CATEGORY-COUNT TO WS-EDIT-COUNT
           MOVE WS-EDIT-COUNT TO WS-FMT-SOURCE
           PERFORM 6100-LEFT-JUSTIFY
           MOVE WS-FMT-TARGET TO WS-COUNT-TEXT
           MOVE WS-FMT-LENGTH TO WS-COUNT-LENGTH

           MOVE WS-TOTAL-BALANCE TO WS-EDIT-BALANCE
           MOVE WS-EDIT-BALANCE TO WS-FMT-SOURCE
           PERFORM 6100-LEFT-JUSTIFY
           MOVE WS-FMT-TARGET TO WS-BALANCE-TEXT
           MOVE WS-FMT-LENGTH TO WS-BALANCE-LENGTH

           MOVE WS-TOTAL-INT TO WS-EDIT-INTEREST
           MOVE WS-EDIT-INTEREST TO WS-FMT-SOURCE
           PERFORM 6100-LEFT-JUSTIFY
           MOVE WS-FMT-TARGET TO WS-INTEREST-TEXT
           MOVE WS-FMT-LENGTH TO WS-INTEREST-LENGTH

           MOVE WS-TOTAL-FEES TO WS-EDIT-FEES
           MOVE WS-EDIT-FEES TO WS-FMT-SOURCE
           PERFORM 6100-LEFT-JUSTIFY
           MOVE WS-FMT-TARGET TO WS-FEES-TEXT
           MOVE WS-FMT-LENGTH TO WS-FEES-LENGTH

           MOVE WS-AVG-RATE TO WS-EDIT-RATE
           MOVE WS-EDIT-RATE TO WS-FMT-SOURCE
           PERFORM 6100-LEFT-JUSTIFY
           MOVE WS-FMT-TARGET TO WS-RATE-TEXT
           MOVE WS-FMT-LENGTH TO WS-RATE-LENGTH

           MOVE SPACES TO FD-INTRPT-REC
           STRING WS-ACCT-ID-TEXT DELIMITED BY SIZE
                  ',' DELIMITED BY SIZE
                  WS-GROUP-TEXT(1:WS-GROUP-LENGTH) DELIMITED BY SIZE
                  ',' DELIMITED BY SIZE
                  WS-COUNT-TEXT(1:WS-COUNT-LENGTH) DELIMITED BY SIZE
                  ',' DELIMITED BY SIZE
                  WS-BALANCE-TEXT(1:WS-BALANCE-LENGTH) DELIMITED BY SIZE
                  ',' DELIMITED BY SIZE
                  WS-INTEREST-TEXT(1:WS-INTEREST-LENGTH)
                     DELIMITED BY SIZE
                  ',' DELIMITED BY SIZE
                  WS-FEES-TEXT(1:WS-FEES-LENGTH) DELIMITED BY SIZE
                  ',' DELIMITED BY SIZE
                  WS-RATE-TEXT(1:WS-RATE-LENGTH) DELIMITED BY SIZE
             INTO FD-INTRPT-REC
           END-STRING
           WRITE FD-INTRPT-REC
           IF INTRPT-STATUS = '00'
               MOVE 0 TO APPL-RESULT
           ELSE
               MOVE 12 TO APPL-RESULT
           END-IF
           IF NOT APPL-AOK
               DISPLAY 'ERROR WRITING INTEREST REPORT RECORD'
               MOVE INTRPT-STATUS TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           DISPLAY 'ACCOUNT PROCESSED: ' WS-CURRENT-ACCT-ID
           EXIT.

      *---------------------------------------------------------------*
       6100-LEFT-JUSTIFY.
           MOVE SPACES TO WS-FMT-TARGET
           MOVE 0 TO WS-FMT-LENGTH
           MOVE 0 TO WS-FMT-FIRST
           MOVE 'N' TO WS-FMT-DONE
           PERFORM VARYING WS-FMT-SOURCE-INDEX FROM 1 BY 1
               UNTIL WS-FMT-SOURCE-INDEX > 20
                  OR WS-FMT-DONE = 'Y'
               IF WS-FMT-SOURCE(WS-FMT-SOURCE-INDEX:1) NOT = SPACE
                   MOVE WS-FMT-SOURCE-INDEX TO WS-FMT-FIRST
                   MOVE 'Y' TO WS-FMT-DONE
               END-IF
           END-PERFORM
           IF WS-FMT-FIRST NOT = 0
               MOVE 1 TO WS-FMT-TARGET-INDEX
               PERFORM VARYING WS-FMT-SOURCE-INDEX
                   FROM WS-FMT-FIRST BY 1
                   UNTIL WS-FMT-SOURCE-INDEX > 20
                   MOVE WS-FMT-SOURCE(WS-FMT-SOURCE-INDEX:1)
                     TO WS-FMT-TARGET(WS-FMT-TARGET-INDEX:1)
                   ADD 1 TO WS-FMT-TARGET-INDEX
               END-PERFORM
               MOVE 20 TO WS-FMT-SOURCE-INDEX
               PERFORM UNTIL WS-FMT-SOURCE-INDEX = 0
                   IF WS-FMT-TARGET(WS-FMT-SOURCE-INDEX:1)
                         NOT = SPACE
                       MOVE WS-FMT-SOURCE-INDEX TO WS-FMT-LENGTH
                       MOVE 0 TO WS-FMT-SOURCE-INDEX
                   ELSE
                       SUBTRACT 1 FROM WS-FMT-SOURCE-INDEX
                   END-IF
               END-PERFORM
           END-IF
           EXIT.

      *---------------------------------------------------------------*
       9000-CLOSE-FILES.
           MOVE 8 TO APPL-RESULT
           CLOSE TCATBAL-FILE
           IF TCATBALF-STATUS = '00'
               MOVE 0 TO APPL-RESULT
           ELSE
               MOVE 12 TO APPL-RESULT
           END-IF
           IF NOT APPL-AOK
               DISPLAY 'ERROR CLOSING TRANSACTION CATEGORY BALANCE FILE'
               MOVE TCATBALF-STATUS TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           MOVE 8 TO APPL-RESULT
           CLOSE DISCGRP-FILE
           IF DISCGRP-STATUS = '00'
               MOVE 0 TO APPL-RESULT
           ELSE
               MOVE 12 TO APPL-RESULT
           END-IF
           IF NOT APPL-AOK
               DISPLAY 'ERROR CLOSING DISCLOSURE GROUP FILE'
               MOVE DISCGRP-STATUS TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           MOVE 8 TO APPL-RESULT
           CLOSE ACCOUNT-FILE
           IF ACCTFILE-STATUS = '00'
               MOVE 0 TO APPL-RESULT
           ELSE
               MOVE 12 TO APPL-RESULT
           END-IF
           IF NOT APPL-AOK
               DISPLAY 'ERROR CLOSING ACCOUNT FILE'
               MOVE ACCTFILE-STATUS TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           MOVE 8 TO APPL-RESULT
           CLOSE INTRPT-FILE
           IF INTRPT-STATUS = '00'
               MOVE 0 TO APPL-RESULT
           ELSE
               MOVE 12 TO APPL-RESULT
           END-IF
           IF NOT APPL-AOK
               DISPLAY 'ERROR CLOSING INTEREST REPORT FILE'
               MOVE INTRPT-STATUS TO IO-STATUS
               PERFORM 9910-DISPLAY-IO-STATUS
               PERFORM 9999-ABEND-PROGRAM
           END-IF
           DISPLAY 'CBACT05C INTEREST ANALYTICS EXTRACT COMPLETE'
           EXIT.

      *****************************************************************
       9910-DISPLAY-IO-STATUS.
           IF IO-STATUS NOT NUMERIC
           OR IO-STAT1 = '9'
               MOVE IO-STAT1 TO IO-STATUS-04(1:1)
               MOVE 0 TO TWO-BYTES-BINARY
               MOVE IO-STAT2 TO TWO-BYTES-RIGHT
               MOVE TWO-BYTES-BINARY TO IO-STATUS-0403
               DISPLAY 'FILE STATUS IS: NNNN' IO-STATUS-04
           ELSE
               MOVE '0000' TO IO-STATUS-04
               MOVE IO-STATUS TO IO-STATUS-04(3:2)
               DISPLAY 'FILE STATUS IS: NNNN' IO-STATUS-04
           END-IF
           EXIT.

      *---------------------------------------------------------------*
       9999-ABEND-PROGRAM.
           DISPLAY 'ABENDING PROGRAM'
           MOVE 0 TO TIMING
           MOVE 999 TO ABCODE
           CALL 'CEE3ABD' USING ABCODE, TIMING
           EXIT.

      *
      * Ver: DJ-106 Interest and Fees Analytics
      *
