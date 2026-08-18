       IDENTIFICATION DIVISION.
       PROGRAM-ID.    CBSTM04C.
       AUTHOR.        AWS.
      ******************************************************************
      * Program     : CBSTM04C.cbl
      * Application : CardDemo
      * Type        : BATCH COBOL Program
      * Function    : Extract Account Statements from Transaction data
      *               as a single JSON document
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
      * This program reads the same inputs as CBSTM03A (TRNXFILE,
      * XREFFILE, CUSTFILE, ACCTFILE through the CBSTM03B file handler)
      * and writes one JSON document holding every statement to
      * JSONFILE. The contract is app/data/json/statements.schema.json.
      *
      * Differences from CBSTM03A:
      *  1. Output is JSON on JSONFILE instead of text/HTML statements
      *  2. Address line 3 and the customer name keep their full value
      *     instead of being truncated at the first embedded blank
      *  3. No mainframe control block (PSA/TCB/TIOT) addressing
      ******************************************************************
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT JSON-FILE ASSIGN TO JSONFILE.
      *
       DATA DIVISION.
       FILE SECTION.
       FD  JSON-FILE.
       01  FD-JSONFILE-REC         PIC X(400).

       WORKING-STORAGE SECTION.

       COPY COSTM01.

       COPY CVACT03Y.

       COPY CUSTREC.

       COPY CVACT01Y.

       01  COMP-VARIABLES          COMP.
           05  CR-CNT              PIC S9(4) VALUE 0.
           05  TR-CNT              PIC S9(4) VALUE 0.
           05  CR-JMP              PIC S9(4) VALUE 0.
           05  TR-JMP              PIC S9(4) VALUE 0.
       01  COMP3-VARIABLES         COMP-3.
           05  WS-TOTAL-AMT        PIC S9(9)V99 VALUE 0.
           05  WS-STMT-CNT         PIC S9(9) VALUE 0.
           05  WS-TRAN-CNT         PIC S9(9) VALUE 0.
       01  MISC-VARIABLES.
           05  WS-FL-DD            PIC X(8) VALUE 'TRNXFILE'.
           05  WS-SAVE-CARD VALUE SPACES PIC X(16).
           05  END-OF-FILE         PIC X(01) VALUE 'N'.
           05  WS-FIRST-STMT       PIC X(01) VALUE 'Y'.
           05  WS-FIRST-TRAN       PIC X(01) VALUE 'Y'.
       01  WS-M03B-AREA.
           05  WS-M03B-DD          PIC X(08).
           05  WS-M03B-OPER        PIC X(01).
             88  M03B-OPEN       VALUE 'O'.
             88  M03B-CLOSE      VALUE 'C'.
             88  M03B-READ       VALUE 'R'.
             88  M03B-READ-K     VALUE 'K'.
             88  M03B-WRITE      VALUE 'W'.
             88  M03B-REWRITE    VALUE 'Z'.
           05  WS-M03B-RC          PIC X(02).
           05  WS-M03B-KEY         PIC X(25).
           05  WS-M03B-KEY-LN      PIC S9(4).
           05  WS-M03B-FLDT        PIC X(1000).

      *****************************************************************
      *    JSON record assembly work areas                            *
      *****************************************************************
       01  WS-JSON-BUILD.
           05  WS-JSON-REC         PIC X(400).
           05  WS-JSON-PTR         PIC S9(4) COMP VALUE 1.
           05  WS-APP-VAL          PIC X(400).
           05  WS-APP-LEN          PIC S9(4) COMP VALUE 0.
       01  WS-TEXT-WORK.
           05  WS-TRIM-SRC         PIC X(400).
           05  WS-TRIM-LEN         PIC S9(4) COMP VALUE 0.
           05  WS-ESC-VAL          PIC X(400).
           05  WS-ESC-LEN          PIC S9(4) COMP VALUE 0.
           05  WS-CHR              PIC X(01).
           05  WS-IDX              PIC S9(4) COMP VALUE 0.
           05  WS-IDX2             PIC S9(4) COMP VALUE 0.
           05  WS-NSTART           PIC S9(4) COMP VALUE 0.
           05  WS-NEND             PIC S9(4) COMP VALUE 0.
       01  WS-NUM-WORK.
           05  WS-AMT-ED           PIC -(11)9.99.
           05  WS-FICO-ED          PIC ZZ9.
           05  WS-ACCT-KEY         PIC X(11).
           05  WS-CUST-KEY         PIC X(09).

       01  WS-TRNX-TABLE.
           05  WS-CARD-TBL OCCURS 51 TIMES.
               10  WS-CARD-NUM                          PIC X(16).
               10  WS-TRAN-TBL OCCURS 10 TIMES.
                   15  WS-TRAN-NUM                      PIC X(16).
                   15  WS-TRAN-REST                     PIC X(318).
       01  WS-TRN-TBL-CNTR.
           05  WS-TRN-TBL-CTR OCCURS 51 TIMES.
               10  WS-TRCT               PIC S9(4) COMP.

      *****************************************************************
       PROCEDURE DIVISION.
      *****************************************************************
           OPEN OUTPUT JSON-FILE.
           INITIALIZE WS-TRNX-TABLE WS-TRN-TBL-CNTR.
           PERFORM 0100-WRITE-JSON-HEADER.

       0000-START.

           EVALUATE WS-FL-DD
             WHEN 'TRNXFILE'
               ALTER 8100-FILE-OPEN TO PROCEED TO 8100-TRNXFILE-OPEN
               GO TO 8100-FILE-OPEN
             WHEN 'XREFFILE'
               ALTER 8100-FILE-OPEN TO PROCEED TO 8200-XREFFILE-OPEN
               GO TO 8100-FILE-OPEN
             WHEN 'CUSTFILE'
               ALTER 8100-FILE-OPEN TO PROCEED TO 8300-CUSTFILE-OPEN
               GO TO 8100-FILE-OPEN
             WHEN 'ACCTFILE'
               ALTER 8100-FILE-OPEN TO PROCEED TO 8400-ACCTFILE-OPEN
               GO TO 8100-FILE-OPEN
             WHEN 'READTRNX'
               GO TO 8500-READTRNX-READ
             WHEN OTHER
               GO TO 9999-GOBACK.

       1000-MAINLINE.
           PERFORM UNTIL END-OF-FILE = 'Y'
               IF  END-OF-FILE = 'N'
                   PERFORM 1000-XREFFILE-GET-NEXT
                   IF  END-OF-FILE = 'N'
                       PERFORM 2000-CUSTFILE-GET
                       PERFORM 3000-ACCTFILE-GET
                       PERFORM 5000-CREATE-STATEMENT
                       MOVE 1 TO CR-JMP
                       MOVE ZERO TO WS-TOTAL-AMT
                       PERFORM 4000-TRNXFILE-GET
                   END-IF
               END-IF
           END-PERFORM.

           PERFORM 0200-WRITE-JSON-FOOTER.

           PERFORM 9100-TRNXFILE-CLOSE.

           PERFORM 9200-XREFFILE-CLOSE.

           PERFORM 9300-CUSTFILE-CLOSE.

           PERFORM 9400-ACCTFILE-CLOSE.

           CLOSE JSON-FILE.

           DISPLAY 'CBSTM04C STATEMENTS WRITTEN  : ' WS-STMT-CNT.
           DISPLAY 'CBSTM04C TRANSACTIONS WRITTEN: ' WS-TRAN-CNT.

       9999-GOBACK.
           GOBACK.

      *---------------------------------------------------------------*
       0100-WRITE-JSON-HEADER.
           MOVE '{' TO WS-JSON-REC.
           PERFORM 7900-WRITE-JSON.
           MOVE '  "statements": [' TO WS-JSON-REC.
           PERFORM 7900-WRITE-JSON.
           EXIT.

       0200-WRITE-JSON-FOOTER.
           MOVE '  ]' TO WS-JSON-REC.
           PERFORM 7900-WRITE-JSON.
           MOVE '}' TO WS-JSON-REC.
           PERFORM 7900-WRITE-JSON.
           EXIT.

      *---------------------------------------------------------------*
       1000-XREFFILE-GET-NEXT.

           MOVE 'XREFFILE' TO WS-M03B-DD.
           SET M03B-READ TO TRUE.
           MOVE ZERO TO WS-M03B-RC.
           MOVE SPACES TO WS-M03B-FLDT.
           CALL 'CBSTM03B' USING WS-M03B-AREA.

           EVALUATE WS-M03B-RC
             WHEN '00'
               CONTINUE
             WHEN '10'
               MOVE 'Y' TO END-OF-FILE
             WHEN OTHER
               DISPLAY 'ERROR READING XREFFILE'
               DISPLAY 'RETURN CODE: ' WS-M03B-RC
               PERFORM 9999-ABEND-PROGRAM
           END-EVALUATE.

           MOVE WS-M03B-FLDT TO CARD-XREF-RECORD.

           EXIT.

       2000-CUSTFILE-GET.

           MOVE 'CUSTFILE' TO WS-M03B-DD.
           SET M03B-READ-K TO TRUE.
           MOVE XREF-CUST-ID TO WS-M03B-KEY.
           MOVE ZERO TO WS-M03B-KEY-LN.
           COMPUTE WS-M03B-KEY-LN = LENGTH OF XREF-CUST-ID.
           MOVE ZERO TO WS-M03B-RC.
           MOVE SPACES TO WS-M03B-FLDT.
           CALL 'CBSTM03B' USING WS-M03B-AREA.

           EVALUATE WS-M03B-RC
             WHEN '00'
               CONTINUE
             WHEN OTHER
               DISPLAY 'ERROR READING CUSTFILE'
               DISPLAY 'RETURN CODE: ' WS-M03B-RC
               PERFORM 9999-ABEND-PROGRAM
           END-EVALUATE.

           MOVE WS-M03B-FLDT TO CUSTOMER-RECORD.

           EXIT.

       3000-ACCTFILE-GET.

           MOVE 'ACCTFILE' TO WS-M03B-DD.
           SET M03B-READ-K TO TRUE.
           MOVE XREF-ACCT-ID TO WS-M03B-KEY.
           MOVE ZERO TO WS-M03B-KEY-LN.
           COMPUTE WS-M03B-KEY-LN = LENGTH OF XREF-ACCT-ID.
           MOVE ZERO TO WS-M03B-RC.
           MOVE SPACES TO WS-M03B-FLDT.
           CALL 'CBSTM03B' USING WS-M03B-AREA.

           EVALUATE WS-M03B-RC
             WHEN '00'
               CONTINUE
             WHEN OTHER
               DISPLAY 'ERROR READING ACCTFILE'
               DISPLAY 'RETURN CODE: ' WS-M03B-RC
               PERFORM 9999-ABEND-PROGRAM
           END-EVALUATE.

           MOVE WS-M03B-FLDT TO ACCOUNT-RECORD.

           EXIT.

      *---------------------------------------------------------------*
       4000-TRNXFILE-GET.
           PERFORM VARYING CR-JMP FROM 1 BY 1
             UNTIL CR-JMP > CR-CNT
             OR (WS-CARD-NUM (CR-JMP) > XREF-CARD-NUM)
               IF XREF-CARD-NUM = WS-CARD-NUM (CR-JMP)
                   MOVE WS-CARD-NUM (CR-JMP) TO TRNX-CARD-NUM
                   PERFORM VARYING TR-JMP FROM 1 BY 1
                     UNTIL (TR-JMP > WS-TRCT (CR-JMP))
                       MOVE WS-TRAN-NUM (CR-JMP, TR-JMP)
                         TO TRNX-ID
                       MOVE WS-TRAN-REST (CR-JMP, TR-JMP)
                         TO TRNX-REST
                       PERFORM 6000-WRITE-TRANS
                       ADD TRNX-AMT TO WS-TOTAL-AMT
                   END-PERFORM
               END-IF
           END-PERFORM.

           MOVE '      ],' TO WS-JSON-REC.
           PERFORM 7900-WRITE-JSON.

           MOVE '      "totalAmount":' TO WS-APP-VAL.
           PERFORM 7420-APPEND-LIT-SP.
           MOVE WS-TOTAL-AMT TO WS-AMT-ED.
           MOVE WS-AMT-ED TO WS-TRIM-SRC.
           PERFORM 7600-APPEND-NUM.
           PERFORM 7900-WRITE-JSON.

           MOVE '    }' TO WS-JSON-REC.
           PERFORM 7900-WRITE-JSON.

           EXIT.

      *---------------------------------------------------------------*
       5000-CREATE-STATEMENT.
           IF WS-FIRST-STMT = 'Y'
               MOVE 'N' TO WS-FIRST-STMT
           ELSE
               MOVE '    ,' TO WS-JSON-REC
               PERFORM 7900-WRITE-JSON
           END-IF.

           ADD 1 TO WS-STMT-CNT.

           MOVE '    {' TO WS-JSON-REC.
           PERFORM 7900-WRITE-JSON.

           MOVE ACCT-ID TO WS-ACCT-KEY.
           MOVE '      "accountId": "' TO WS-APP-VAL.
           PERFORM 7410-APPEND-LIT.
           MOVE WS-ACCT-KEY TO WS-TRIM-SRC.
           PERFORM 7500-APPEND-TEXT.
           MOVE '",' TO WS-APP-VAL.
           PERFORM 7410-APPEND-LIT.
           PERFORM 7900-WRITE-JSON.

           MOVE '      "currentBalance":' TO WS-APP-VAL.
           PERFORM 7420-APPEND-LIT-SP.
           MOVE ACCT-CURR-BAL TO WS-AMT-ED.
           MOVE WS-AMT-ED TO WS-TRIM-SRC.
           PERFORM 7600-APPEND-NUM.
           MOVE ',' TO WS-APP-VAL.
           PERFORM 7410-APPEND-LIT.
           PERFORM 7900-WRITE-JSON.

           MOVE '      "ficoScore":' TO WS-APP-VAL.
           PERFORM 7420-APPEND-LIT-SP.
           MOVE CUST-FICO-CREDIT-SCORE TO WS-FICO-ED.
           MOVE WS-FICO-ED TO WS-TRIM-SRC.
           PERFORM 7600-APPEND-NUM.
           MOVE ',' TO WS-APP-VAL.
           PERFORM 7410-APPEND-LIT.
           PERFORM 7900-WRITE-JSON.

           MOVE '      "customer": {' TO WS-JSON-REC.
           PERFORM 7900-WRITE-JSON.

           MOVE CUST-ID TO WS-CUST-KEY.
           MOVE '        "id": "' TO WS-APP-VAL.
           PERFORM 7410-APPEND-LIT.
           MOVE WS-CUST-KEY TO WS-TRIM-SRC.
           PERFORM 7500-APPEND-TEXT.
           MOVE '",' TO WS-APP-VAL.
           PERFORM 7410-APPEND-LIT.
           PERFORM 7900-WRITE-JSON.

           MOVE '        "name": "' TO WS-APP-VAL.
           PERFORM 7410-APPEND-LIT.
           MOVE CUST-FIRST-NAME TO WS-TRIM-SRC.
           PERFORM 7500-APPEND-TEXT.
           MOVE CUST-MIDDLE-NAME TO WS-TRIM-SRC.
           PERFORM 7550-APPEND-TEXT-SP.
           MOVE CUST-LAST-NAME TO WS-TRIM-SRC.
           PERFORM 7550-APPEND-TEXT-SP.
           MOVE '",' TO WS-APP-VAL.
           PERFORM 7410-APPEND-LIT.
           PERFORM 7900-WRITE-JSON.

           MOVE '        "address": [' TO WS-JSON-REC.
           PERFORM 7900-WRITE-JSON.

           MOVE '          "' TO WS-APP-VAL.
           PERFORM 7410-APPEND-LIT.
           MOVE CUST-ADDR-LINE-1 TO WS-TRIM-SRC.
           PERFORM 7500-APPEND-TEXT.
           MOVE '",' TO WS-APP-VAL.
           PERFORM 7410-APPEND-LIT.
           PERFORM 7900-WRITE-JSON.

           MOVE '          "' TO WS-APP-VAL.
           PERFORM 7410-APPEND-LIT.
           MOVE CUST-ADDR-LINE-2 TO WS-TRIM-SRC.
           PERFORM 7500-APPEND-TEXT.
           MOVE '",' TO WS-APP-VAL.
           PERFORM 7410-APPEND-LIT.
           PERFORM 7900-WRITE-JSON.

           MOVE '          "' TO WS-APP-VAL.
           PERFORM 7410-APPEND-LIT.
           MOVE CUST-ADDR-LINE-3 TO WS-TRIM-SRC.
           PERFORM 7500-APPEND-TEXT.
           MOVE CUST-ADDR-STATE-CD TO WS-TRIM-SRC.
           PERFORM 7550-APPEND-TEXT-SP.
           MOVE CUST-ADDR-COUNTRY-CD TO WS-TRIM-SRC.
           PERFORM 7550-APPEND-TEXT-SP.
           MOVE CUST-ADDR-ZIP TO WS-TRIM-SRC.
           PERFORM 7550-APPEND-TEXT-SP.
           MOVE '"' TO WS-APP-VAL.
           PERFORM 7410-APPEND-LIT.
           PERFORM 7900-WRITE-JSON.

           MOVE '        ]' TO WS-JSON-REC.
           PERFORM 7900-WRITE-JSON.
           MOVE '      },' TO WS-JSON-REC.
           PERFORM 7900-WRITE-JSON.

           MOVE '      "transactions": [' TO WS-JSON-REC.
           PERFORM 7900-WRITE-JSON.
           MOVE 'Y' TO WS-FIRST-TRAN.

           EXIT.

      *---------------------------------------------------------------*
       6000-WRITE-TRANS.
           ADD 1 TO WS-TRAN-CNT.

           IF WS-FIRST-TRAN = 'Y'
               MOVE 'N' TO WS-FIRST-TRAN
               MOVE '        { "tranId": "' TO WS-APP-VAL
           ELSE
               MOVE '        ,{ "tranId": "' TO WS-APP-VAL
           END-IF.
           PERFORM 7410-APPEND-LIT.
           MOVE TRNX-ID TO WS-TRIM-SRC.
           PERFORM 7500-APPEND-TEXT.
           MOVE '", "details": "' TO WS-APP-VAL.
           PERFORM 7410-APPEND-LIT.
           MOVE TRNX-DESC TO WS-TRIM-SRC.
           PERFORM 7500-APPEND-TEXT.
           MOVE '", "amount":' TO WS-APP-VAL.
           PERFORM 7420-APPEND-LIT-SP.
           MOVE TRNX-AMT TO WS-AMT-ED.
           MOVE WS-AMT-ED TO WS-TRIM-SRC.
           PERFORM 7600-APPEND-NUM.
           MOVE ' }' TO WS-APP-VAL.
           PERFORM 7410-APPEND-LIT.
           PERFORM 7900-WRITE-JSON.

           EXIT.

      *---------------------------------------------------------------*
      *    JSON text helpers                                          *
      *---------------------------------------------------------------*
       7100-TRIM-SRC.
           MOVE 0 TO WS-TRIM-LEN.
           PERFORM VARYING WS-IDX2 FROM 400 BY -1
             UNTIL WS-IDX2 < 1 OR WS-TRIM-LEN > 0
               IF WS-TRIM-SRC (WS-IDX2:1) NOT = SPACE
                   MOVE WS-IDX2 TO WS-TRIM-LEN
               END-IF
           END-PERFORM.
           EXIT.

       7200-ESCAPE-TEXT.
           MOVE SPACES TO WS-ESC-VAL.
           MOVE 0 TO WS-ESC-LEN.
           PERFORM VARYING WS-IDX FROM 1 BY 1
             UNTIL WS-IDX > WS-TRIM-LEN
               MOVE WS-TRIM-SRC (WS-IDX:1) TO WS-CHR
               IF WS-CHR = '"' OR WS-CHR = '\'
                   ADD 1 TO WS-ESC-LEN
                   MOVE '\' TO WS-ESC-VAL (WS-ESC-LEN:1)
               END-IF
               ADD 1 TO WS-ESC-LEN
               MOVE WS-CHR TO WS-ESC-VAL (WS-ESC-LEN:1)
           END-PERFORM.
           EXIT.

       7400-APPEND.
           IF WS-APP-LEN > 0
               STRING WS-APP-VAL (1:WS-APP-LEN) DELIMITED BY SIZE
                 INTO WS-JSON-REC WITH POINTER WS-JSON-PTR
               END-STRING
           END-IF.
           EXIT.

       7410-APPEND-LIT.
           MOVE 0 TO WS-APP-LEN.
           PERFORM VARYING WS-IDX FROM 400 BY -1
             UNTIL WS-IDX < 1 OR WS-APP-LEN > 0
               IF WS-APP-VAL (WS-IDX:1) NOT = SPACE
                   MOVE WS-IDX TO WS-APP-LEN
               END-IF
           END-PERFORM.
           PERFORM 7400-APPEND.
           EXIT.

       7420-APPEND-LIT-SP.
           PERFORM 7410-APPEND-LIT.
           MOVE SPACE TO WS-APP-VAL.
           MOVE 1 TO WS-APP-LEN.
           PERFORM 7400-APPEND.
           EXIT.

       7500-APPEND-TEXT.
           PERFORM 7100-TRIM-SRC.
           PERFORM 7200-ESCAPE-TEXT.
           MOVE WS-ESC-VAL TO WS-APP-VAL.
           MOVE WS-ESC-LEN TO WS-APP-LEN.
           PERFORM 7400-APPEND.
           EXIT.

       7550-APPEND-TEXT-SP.
           PERFORM 7100-TRIM-SRC.
           IF WS-TRIM-LEN > 0
               PERFORM 7200-ESCAPE-TEXT
               MOVE SPACE TO WS-APP-VAL
               MOVE 1 TO WS-APP-LEN
               PERFORM 7400-APPEND
               MOVE WS-ESC-VAL TO WS-APP-VAL
               MOVE WS-ESC-LEN TO WS-APP-LEN
               PERFORM 7400-APPEND
           END-IF.
           EXIT.

       7600-APPEND-NUM.
           MOVE 0 TO WS-NSTART.
           MOVE 0 TO WS-NEND.
           PERFORM VARYING WS-IDX2 FROM 1 BY 1
             UNTIL WS-IDX2 > 400 OR WS-NSTART > 0
               IF WS-TRIM-SRC (WS-IDX2:1) NOT = SPACE
                   MOVE WS-IDX2 TO WS-NSTART
               END-IF
           END-PERFORM.
           PERFORM VARYING WS-IDX2 FROM 400 BY -1
             UNTIL WS-IDX2 < 1 OR WS-NEND > 0
               IF WS-TRIM-SRC (WS-IDX2:1) NOT = SPACE
                   MOVE WS-IDX2 TO WS-NEND
               END-IF
           END-PERFORM.
           IF WS-NSTART > 0
               COMPUTE WS-APP-LEN = WS-NEND - WS-NSTART + 1
               MOVE WS-TRIM-SRC (WS-NSTART:WS-APP-LEN) TO WS-APP-VAL
               PERFORM 7400-APPEND
           END-IF.
           EXIT.

       7900-WRITE-JSON.
           WRITE FD-JSONFILE-REC FROM WS-JSON-REC.
           MOVE SPACES TO WS-JSON-REC.
           MOVE 1 TO WS-JSON-PTR.
           EXIT.

      *---------------------------------------------------------------*
       8100-FILE-OPEN.
           GO TO 8100-TRNXFILE-OPEN
           .

       8100-TRNXFILE-OPEN.
           MOVE 'TRNXFILE' TO WS-M03B-DD.
           SET M03B-OPEN TO TRUE.
           MOVE ZERO TO WS-M03B-RC.
           CALL 'CBSTM03B' USING WS-M03B-AREA.

           IF WS-M03B-RC = '00' OR '04'
               CONTINUE
           ELSE
               DISPLAY 'ERROR OPENING TRNXFILE'
               DISPLAY 'RETURN CODE: ' WS-M03B-RC
               PERFORM 9999-ABEND-PROGRAM
           END-IF.

           SET M03B-READ TO TRUE.
           MOVE SPACES TO WS-M03B-FLDT.
           CALL 'CBSTM03B' USING WS-M03B-AREA.

           IF WS-M03B-RC = '00' OR '04'
               CONTINUE
           ELSE
               DISPLAY 'ERROR READING TRNXFILE'
               DISPLAY 'RETURN CODE: ' WS-M03B-RC
               PERFORM 9999-ABEND-PROGRAM
           END-IF.

           MOVE WS-M03B-FLDT TO TRNX-RECORD.
           MOVE TRNX-CARD-NUM TO WS-SAVE-CARD.
           MOVE 1 TO CR-CNT.
           MOVE 0 TO TR-CNT.
           MOVE 'READTRNX' TO WS-FL-DD.
           GO TO 0000-START.
           EXIT.

      *---------------------------------------------------------------*
       8200-XREFFILE-OPEN.
           MOVE 'XREFFILE' TO WS-M03B-DD.
           SET M03B-OPEN TO TRUE.
           MOVE ZERO TO WS-M03B-RC.
           CALL 'CBSTM03B' USING WS-M03B-AREA.

           IF WS-M03B-RC = '00' OR '04'
               CONTINUE
           ELSE
               DISPLAY 'ERROR OPENING XREFFILE'
               DISPLAY 'RETURN CODE: ' WS-M03B-RC
               PERFORM 9999-ABEND-PROGRAM
           END-IF.

           MOVE 'CUSTFILE' TO WS-FL-DD.
           GO TO 0000-START.
           EXIT.
      *---------------------------------------------------------------*
       8300-CUSTFILE-OPEN.
           MOVE 'CUSTFILE' TO WS-M03B-DD.
           SET M03B-OPEN TO TRUE.
           MOVE ZERO TO WS-M03B-RC.
           CALL 'CBSTM03B' USING WS-M03B-AREA.

           IF WS-M03B-RC = '00' OR '04'
               CONTINUE
           ELSE
               DISPLAY 'ERROR OPENING CUSTFILE'
               DISPLAY 'RETURN CODE: ' WS-M03B-RC
               PERFORM 9999-ABEND-PROGRAM
           END-IF.

           MOVE 'ACCTFILE' TO WS-FL-DD.
           GO TO 0000-START.
           EXIT.
      *---------------------------------------------------------------*
       8400-ACCTFILE-OPEN.
           MOVE 'ACCTFILE' TO WS-M03B-DD.
           SET M03B-OPEN TO TRUE.
           MOVE ZERO TO WS-M03B-RC.
           CALL 'CBSTM03B' USING WS-M03B-AREA.

           IF WS-M03B-RC = '00' OR '04'
               CONTINUE
           ELSE
               DISPLAY 'ERROR OPENING ACCTFILE'
               DISPLAY 'RETURN CODE: ' WS-M03B-RC
               PERFORM 9999-ABEND-PROGRAM
           END-IF.

           GO TO 1000-MAINLINE.
           EXIT.
      *---------------------------------------------------------------*
       8500-READTRNX-READ.
           IF WS-SAVE-CARD = TRNX-CARD-NUM
               ADD 1 TO TR-CNT
           ELSE
               MOVE TR-CNT TO WS-TRCT (CR-CNT)
               ADD 1 TO CR-CNT
               MOVE 1 TO TR-CNT
           END-IF.

           MOVE TRNX-CARD-NUM TO WS-CARD-NUM (CR-CNT).
           MOVE TRNX-ID TO WS-TRAN-NUM (CR-CNT, TR-CNT).
           MOVE TRNX-REST TO WS-TRAN-REST (CR-CNT, TR-CNT).
           MOVE TRNX-CARD-NUM TO WS-SAVE-CARD.

           MOVE 'TRNXFILE' TO WS-M03B-DD.
           SET M03B-READ TO TRUE.
           MOVE SPACES TO WS-M03B-FLDT.
           CALL 'CBSTM03B' USING WS-M03B-AREA.

           EVALUATE WS-M03B-RC
             WHEN '00'
               MOVE WS-M03B-FLDT TO TRNX-RECORD
               GO TO 8500-READTRNX-READ
             WHEN '10'
               GO TO 8599-EXIT
             WHEN OTHER
               DISPLAY 'ERROR READING TRNXFILE'
               DISPLAY 'RETURN CODE: ' WS-M03B-RC
               PERFORM 9999-ABEND-PROGRAM
           END-EVALUATE.

       8599-EXIT.
           MOVE TR-CNT TO WS-TRCT (CR-CNT).
           MOVE 'XREFFILE' TO WS-FL-DD.
           GO TO 0000-START.
           EXIT.

      *---------------------------------------------------------------*
       9100-TRNXFILE-CLOSE.
           MOVE 'TRNXFILE' TO WS-M03B-DD.
           SET M03B-CLOSE TO TRUE.
           MOVE ZERO TO WS-M03B-RC.
           CALL 'CBSTM03B' USING WS-M03B-AREA.

           IF WS-M03B-RC = '00' OR '04'
               CONTINUE
           ELSE
               DISPLAY 'ERROR CLOSING TRNXFILE'
               DISPLAY 'RETURN CODE: ' WS-M03B-RC
               PERFORM 9999-ABEND-PROGRAM
           END-IF.

           EXIT.

      *---------------------------------------------------------------*
       9200-XREFFILE-CLOSE.
           MOVE 'XREFFILE' TO WS-M03B-DD.
           SET M03B-CLOSE TO TRUE.
           MOVE ZERO TO WS-M03B-RC.
           CALL 'CBSTM03B' USING WS-M03B-AREA.

           IF WS-M03B-RC = '00' OR '04'
               CONTINUE
           ELSE
               DISPLAY 'ERROR CLOSING XREFFILE'
               DISPLAY 'RETURN CODE: ' WS-M03B-RC
               PERFORM 9999-ABEND-PROGRAM
           END-IF.

           EXIT.
      *---------------------------------------------------------------*
       9300-CUSTFILE-CLOSE.
           MOVE 'CUSTFILE' TO WS-M03B-DD.
           SET M03B-CLOSE TO TRUE.
           MOVE ZERO TO WS-M03B-RC.
           CALL 'CBSTM03B' USING WS-M03B-AREA.

           IF WS-M03B-RC = '00' OR '04'
               CONTINUE
           ELSE
               DISPLAY 'ERROR CLOSING CUSTFILE'
               DISPLAY 'RETURN CODE: ' WS-M03B-RC
               PERFORM 9999-ABEND-PROGRAM
           END-IF.

           EXIT.
      *---------------------------------------------------------------*
       9400-ACCTFILE-CLOSE.
           MOVE 'ACCTFILE' TO WS-M03B-DD.
           SET M03B-CLOSE TO TRUE.
           MOVE ZERO TO WS-M03B-RC.
           CALL 'CBSTM03B' USING WS-M03B-AREA.

           IF WS-M03B-RC = '00' OR '04'
               CONTINUE
           ELSE
               DISPLAY 'ERROR CLOSING ACCTFILE'
               DISPLAY 'RETURN CODE: ' WS-M03B-RC
               PERFORM 9999-ABEND-PROGRAM
           END-IF.

           EXIT.

       9999-ABEND-PROGRAM.
           DISPLAY 'ABENDING PROGRAM'
           CALL 'CEE3ABD'.
