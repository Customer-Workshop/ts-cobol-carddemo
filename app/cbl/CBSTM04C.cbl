       IDENTIFICATION DIVISION.
       PROGRAM-ID.    CBSTM04C.
       AUTHOR.        AWS.
      ******************************************************************
      * Program     : CBSTM04C.cbl
      * Application : CardDemo
      * Type        : BATCH COBOL Program
      * Function    : Extract account statements as one JSON document.
      *
      * The input files/read loop follow CBSTM03A/CBSTM03B.  JSONFILE
      * is a fixed-record sequential file so this program also runs on a
      * mainframe without a JSON library.
      ******************************************************************
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT JSON-FILE ASSIGN TO JSONFILE.

       DATA DIVISION.
       FILE SECTION.
       FD  JSON-FILE.
       01  FD-JSONFILE-REC                 PIC X(400).

       WORKING-STORAGE SECTION.
       COPY COSTM01.
       COPY CVTRA05Y.
       COPY CVACT03Y.
       COPY CVCUS01Y.
       COPY CVACT01Y.

       01  WS-M03B-AREA.
           05  WS-M03B-DD                  PIC X(08).
           05  WS-M03B-OPER                PIC X(01).
             88  M03B-OPEN                VALUE 'O'.
             88  M03B-CLOSE               VALUE 'C'.
             88  M03B-READ                VALUE 'R'.
             88  M03B-READ-K              VALUE 'K'.
           05  WS-M03B-RC                  PIC X(02).
           05  WS-M03B-KEY                 PIC X(25).
           05  WS-M03B-KEY-LN              PIC S9(4).
           05  WS-M03B-FLDT                PIC X(1000).

       01  WS-COUNTERS                     COMP.
           05  WS-CARD-CNT                 PIC S9(4) VALUE 0.
           05  WS-CARD-IDX                 PIC S9(4) VALUE 0.
           05  WS-TRAN-IDX                 PIC S9(4) VALUE 0.
           05  WS-STMT-CNT                 PIC S9(9) VALUE 0.
           05  WS-TRAN-CNT                 PIC S9(9) VALUE 0.
           05  WS-TRAN-CARD-CNT            PIC S9(4) VALUE 0.

       01  WS-STATUS.
           05  WS-EOF                      PIC X VALUE 'N'.
           05  WS-FIRST-TRAN               PIC X VALUE 'Y'.
           05  WS-FIRST-STMT               PIC X VALUE 'Y'.

       01  WS-TRANSACTION-TABLE.
           05  WS-CARD-ENTRY OCCURS 51 TIMES.
               10  WS-CARD-NUM             PIC X(16).
               10  WS-TRAN-ENTRY OCCURS 10 TIMES.
                   15  WS-TRAN-ID          PIC X(16).
                   15  WS-TRAN-REST        PIC X(318).
               10  WS-TRAN-COUNT            PIC S9(4) COMP VALUE 0.

       01  WS-AMOUNTS                      COMP-3.
           05  WS-TOTAL-DEBITS             PIC S9(11)V99 VALUE 0.
           05  WS-TOTAL-CREDITS            PIC S9(11)V99 VALUE 0.
           05  WS-CREDITS-ABS              PIC S9(11)V99 VALUE 0.

       01  WS-DATE-TIME.
           05  WS-DATE                      PIC 9(8).
           05  WS-TIME                      PIC 9(8).

       01  WS-NUMERIC-EDIT.
           05  WS-AMOUNT-EDIT              PIC -(11)9.99.

       01  WS-TEXT-WORK.
           05  WS-TRIM-SRC                 PIC X(400).
           05  WS-TRIM-LEN                 PIC S9(4) COMP VALUE 0.
           05  WS-ESC-VAL                  PIC X(400).
           05  WS-ESC-LEN                  PIC S9(4) COMP VALUE 0.
           05  WS-CHAR                     PIC X.
           05  WS-IDX                      PIC S9(4) COMP VALUE 0.
           05  WS-IDX2                     PIC S9(4) COMP VALUE 0.

       01  WS-JSON-WORK.
           05  WS-JSON-REC                 PIC X(400).
           05  WS-JSON-PTR                 PIC S9(4) COMP VALUE 1.
           05  WS-APP-VALUE                PIC X(400).
           05  WS-APP-LENGTH                PIC S9(4) COMP VALUE 0.

       PROCEDURE DIVISION.
           OPEN OUTPUT JSON-FILE
           PERFORM 0100-WRITE-HEADER
           INITIALIZE WS-TRANSACTION-TABLE
           PERFORM 1000-OPEN-TRNX
           PERFORM 1100-READ-ALL-TRNX
           PERFORM 1200-CLOSE-TRNX
           PERFORM 2000-OPEN-XREF
           PERFORM 2100-OPEN-CUST
           PERFORM 2200-OPEN-ACCT
           PERFORM 3000-WRITE-STATEMENTS
           PERFORM 2300-CLOSE-ACCT
           PERFORM 2400-CLOSE-CUST
           PERFORM 2500-CLOSE-XREF
           PERFORM 0200-WRITE-FOOTER
           CLOSE JSON-FILE
           DISPLAY 'CBSTM04C STATEMENTS WRITTEN  : ' WS-STMT-CNT
           DISPLAY 'CBSTM04C TRANSACTIONS WRITTEN: ' WS-TRAN-CNT
           GOBACK.

       0100-WRITE-HEADER.
           ACCEPT WS-DATE FROM DATE YYYYMMDD
           ACCEPT WS-TIME FROM TIME
           MOVE SPACES TO WS-JSON-REC
           STRING '{' DELIMITED BY SIZE
                  INTO WS-JSON-REC
           END-STRING
           PERFORM 7900-WRITE-JSON
           MOVE SPACES TO WS-JSON-REC
           STRING '  "generatedAt": "' DELIMITED BY SIZE
                  WS-DATE (1:4) DELIMITED BY SIZE '-' DELIMITED BY SIZE
                  WS-DATE (5:2) DELIMITED BY SIZE '-' DELIMITED BY SIZE
                  WS-DATE (7:2) DELIMITED BY SIZE 'T' DELIMITED BY SIZE
                  WS-TIME (1:2) DELIMITED BY SIZE ':' DELIMITED BY SIZE
                  WS-TIME (3:2) DELIMITED BY SIZE ':' DELIMITED BY SIZE
                  WS-TIME (5:2) DELIMITED BY SIZE
                  'Z",' DELIMITED BY SIZE
                  INTO WS-JSON-REC
           END-STRING
           PERFORM 7900-WRITE-JSON
           MOVE '  "statements": [' TO WS-JSON-REC
           PERFORM 7900-WRITE-JSON.

       0200-WRITE-FOOTER.
           MOVE '  ]' TO WS-JSON-REC
           PERFORM 7900-WRITE-JSON
           MOVE '}' TO WS-JSON-REC
           PERFORM 7900-WRITE-JSON.

       1000-OPEN-TRNX.
           MOVE 'TRNXFILE' TO WS-M03B-DD
           SET M03B-OPEN TO TRUE
           CALL 'CBSTM03B' USING WS-M03B-AREA
           PERFORM 9000-CHECK-STATUS
           .

       1100-READ-ALL-TRNX.
           MOVE 'N' TO WS-EOF
           PERFORM 1110-READ-ONE-TRNX
           IF WS-EOF = 'N'
               MOVE 1 TO WS-CARD-CNT
               MOVE TRNX-CARD-NUM TO WS-CARD-NUM (1)
               PERFORM UNTIL WS-EOF = 'Y'
                   IF TRNX-CARD-NUM NOT = WS-CARD-NUM (WS-CARD-CNT)
                       ADD 1 TO WS-CARD-CNT
                       MOVE TRNX-CARD-NUM
                         TO WS-CARD-NUM (WS-CARD-CNT)
                   END-IF
                   ADD 1 TO WS-TRAN-COUNT (WS-CARD-CNT)
                   MOVE TRNX-ID
                     TO WS-TRAN-ID (WS-CARD-CNT,
                                    WS-TRAN-COUNT (WS-CARD-CNT))
                   MOVE TRNX-REST
                     TO WS-TRAN-REST (WS-CARD-CNT,
                                      WS-TRAN-COUNT (WS-CARD-CNT))
                   ADD 1 TO WS-TRAN-CNT
                   PERFORM 1110-READ-ONE-TRNX
               END-PERFORM
           END-IF.

       1110-READ-ONE-TRNX.
           MOVE 'TRNXFILE' TO WS-M03B-DD
           SET M03B-READ TO TRUE
           MOVE SPACES TO WS-M03B-FLDT
           CALL 'CBSTM03B' USING WS-M03B-AREA
           EVALUATE WS-M03B-RC
             WHEN '00'
               MOVE WS-M03B-FLDT TO TRNX-RECORD
             WHEN '10'
               MOVE 'Y' TO WS-EOF
             WHEN OTHER
               DISPLAY 'ERROR READING TRNXFILE: ' WS-M03B-RC
               MOVE 12 TO RETURN-CODE
               GOBACK
           END-EVALUATE.

       1200-CLOSE-TRNX.
           MOVE 'TRNXFILE' TO WS-M03B-DD
           SET M03B-CLOSE TO TRUE
           CALL 'CBSTM03B' USING WS-M03B-AREA
           PERFORM 9000-CHECK-STATUS.

       2000-OPEN-XREF.
           MOVE 'XREFFILE' TO WS-M03B-DD
           SET M03B-OPEN TO TRUE
           CALL 'CBSTM03B' USING WS-M03B-AREA
           PERFORM 9000-CHECK-STATUS.

       2100-OPEN-CUST.
           MOVE 'CUSTFILE' TO WS-M03B-DD
           SET M03B-OPEN TO TRUE
           CALL 'CBSTM03B' USING WS-M03B-AREA
           PERFORM 9000-CHECK-STATUS.

       2200-OPEN-ACCT.
           MOVE 'ACCTFILE' TO WS-M03B-DD
           SET M03B-OPEN TO TRUE
           CALL 'CBSTM03B' USING WS-M03B-AREA
           PERFORM 9000-CHECK-STATUS.

       3000-WRITE-STATEMENTS.
           MOVE 'N' TO WS-EOF
           PERFORM 3010-READ-XREF
           PERFORM UNTIL WS-EOF = 'Y'
               PERFORM 3100-READ-CUST
               PERFORM 3200-READ-ACCT
               PERFORM 4000-WRITE-STATEMENT
               PERFORM 3010-READ-XREF
           END-PERFORM.

       3010-READ-XREF.
           MOVE 'XREFFILE' TO WS-M03B-DD
           SET M03B-READ TO TRUE
           MOVE SPACES TO WS-M03B-FLDT
           CALL 'CBSTM03B' USING WS-M03B-AREA
           EVALUATE WS-M03B-RC
             WHEN '00'
               MOVE WS-M03B-FLDT TO CARD-XREF-RECORD
             WHEN '10'
               MOVE 'Y' TO WS-EOF
             WHEN OTHER
               DISPLAY 'ERROR READING XREFFILE: ' WS-M03B-RC
               MOVE 12 TO RETURN-CODE
               GOBACK
           END-EVALUATE.

       3100-READ-CUST.
           MOVE 'CUSTFILE' TO WS-M03B-DD
           SET M03B-READ-K TO TRUE
           MOVE XREF-CUST-ID TO WS-M03B-KEY
           MOVE LENGTH OF XREF-CUST-ID TO WS-M03B-KEY-LN
           MOVE SPACES TO WS-M03B-FLDT
           CALL 'CBSTM03B' USING WS-M03B-AREA
           IF WS-M03B-RC NOT = '00'
               DISPLAY 'ERROR READING CUSTFILE: ' WS-M03B-RC
               MOVE 12 TO RETURN-CODE
               GOBACK
           END-IF
           MOVE WS-M03B-FLDT TO CUSTOMER-RECORD.

       3200-READ-ACCT.
           MOVE 'ACCTFILE' TO WS-M03B-DD
           SET M03B-READ-K TO TRUE
           MOVE XREF-ACCT-ID TO WS-M03B-KEY
           MOVE LENGTH OF XREF-ACCT-ID TO WS-M03B-KEY-LN
           MOVE SPACES TO WS-M03B-FLDT
           CALL 'CBSTM03B' USING WS-M03B-AREA
           IF WS-M03B-RC NOT = '00'
               DISPLAY 'ERROR READING ACCTFILE: ' WS-M03B-RC
               MOVE 12 TO RETURN-CODE
               GOBACK
           END-IF
           MOVE WS-M03B-FLDT TO ACCOUNT-RECORD.

       4000-WRITE-STATEMENT.
           IF WS-FIRST-STMT = 'Y'
               MOVE 'N' TO WS-FIRST-STMT
           ELSE
               MOVE '    ,' TO WS-JSON-REC
               PERFORM 7900-WRITE-JSON
           END-IF
           ADD 1 TO WS-STMT-CNT
           MOVE '    {' TO WS-JSON-REC
           PERFORM 7900-WRITE-JSON

           MOVE '      "accountId": "' TO WS-APP-VALUE
           PERFORM 7410-APPEND-LITERAL
           MOVE ACCT-ID TO WS-TRIM-SRC
           PERFORM 7500-APPEND-TEXT
           MOVE '",' TO WS-APP-VALUE
           PERFORM 7410-APPEND-LITERAL
           PERFORM 7900-WRITE-JSON

           MOVE '      "customer": {' TO WS-JSON-REC
           PERFORM 7900-WRITE-JSON
           MOVE '        "name": "' TO WS-APP-VALUE
           PERFORM 7410-APPEND-LITERAL
           MOVE CUST-FIRST-NAME TO WS-TRIM-SRC
           PERFORM 7500-APPEND-TEXT
           MOVE CUST-MIDDLE-NAME TO WS-TRIM-SRC
           PERFORM 7550-APPEND-TEXT-SP
           MOVE CUST-LAST-NAME TO WS-TRIM-SRC
           PERFORM 7550-APPEND-TEXT-SP
           MOVE '",' TO WS-APP-VALUE
           PERFORM 7410-APPEND-LITERAL
           PERFORM 7900-WRITE-JSON
           MOVE '        "address": [' TO WS-JSON-REC
           PERFORM 7900-WRITE-JSON
           PERFORM 4010-WRITE-ADDRESS-LINE-1
           PERFORM 4020-WRITE-ADDRESS-LINE-2
           PERFORM 4030-WRITE-ADDRESS-LINE-3
           MOVE '          "' TO WS-APP-VALUE
           PERFORM 7410-APPEND-LITERAL
           MOVE CUST-ADDR-STATE-CD TO WS-TRIM-SRC
           PERFORM 7500-APPEND-TEXT
           MOVE CUST-ADDR-COUNTRY-CD TO WS-TRIM-SRC
           PERFORM 7550-APPEND-TEXT-SP
           MOVE CUST-ADDR-ZIP TO WS-TRIM-SRC
           PERFORM 7550-APPEND-TEXT-SP
           MOVE '"' TO WS-APP-VALUE
           PERFORM 7410-APPEND-LITERAL
           PERFORM 7900-WRITE-JSON
           MOVE '        ]' TO WS-JSON-REC
           PERFORM 7900-WRITE-JSON
           MOVE '      },' TO WS-JSON-REC
           PERFORM 7900-WRITE-JSON

           MOVE '      "currentBalance":' TO WS-APP-VALUE
           PERFORM 7420-APPEND-LITERAL-SPACE
           MOVE ACCT-CURR-BAL TO WS-AMOUNT-EDIT
           MOVE WS-AMOUNT-EDIT TO WS-TRIM-SRC
           PERFORM 7600-APPEND-NUMBER
           MOVE ',' TO WS-APP-VALUE
           PERFORM 7410-APPEND-LITERAL
           PERFORM 7900-WRITE-JSON
           MOVE '      "creditLimit":' TO WS-APP-VALUE
           PERFORM 7420-APPEND-LITERAL-SPACE
           MOVE ACCT-CREDIT-LIMIT TO WS-AMOUNT-EDIT
           MOVE WS-AMOUNT-EDIT TO WS-TRIM-SRC
           PERFORM 7600-APPEND-NUMBER
           MOVE ',' TO WS-APP-VALUE
           PERFORM 7410-APPEND-LITERAL
           PERFORM 7900-WRITE-JSON

           MOVE '      "transactions": [' TO WS-JSON-REC
           PERFORM 7900-WRITE-JSON
           MOVE 'Y' TO WS-FIRST-TRAN
           MOVE 0 TO WS-TOTAL-DEBITS WS-TOTAL-CREDITS
           MOVE 0 TO WS-CARD-IDX
           PERFORM VARYING WS-CARD-IDX FROM 1 BY 1
             UNTIL WS-CARD-IDX > WS-CARD-CNT
             IF WS-CARD-NUM (WS-CARD-IDX) = XREF-CARD-NUM
                 PERFORM VARYING WS-TRAN-IDX FROM 1 BY 1
                   UNTIL WS-TRAN-IDX >
                         WS-TRAN-COUNT (WS-CARD-IDX)
                   MOVE WS-TRAN-ID (WS-CARD-IDX, WS-TRAN-IDX)
                     TO TRNX-ID
                   MOVE WS-TRAN-REST (WS-CARD-IDX, WS-TRAN-IDX)
                     TO TRNX-REST
                   PERFORM 6000-WRITE-TRANSACTION
                 END-PERFORM
             END-IF
           END-PERFORM
           MOVE '      ],' TO WS-JSON-REC
           PERFORM 7900-WRITE-JSON
           MOVE '      "totalDebits":' TO WS-APP-VALUE
           PERFORM 7420-APPEND-LITERAL-SPACE
           MOVE WS-TOTAL-DEBITS TO WS-AMOUNT-EDIT
           MOVE WS-AMOUNT-EDIT TO WS-TRIM-SRC
           PERFORM 7600-APPEND-NUMBER
           MOVE ',' TO WS-APP-VALUE
           PERFORM 7410-APPEND-LITERAL
           PERFORM 7900-WRITE-JSON
           COMPUTE WS-CREDITS-ABS = WS-TOTAL-CREDITS * -1
           MOVE '      "totalCredits":' TO WS-APP-VALUE
           PERFORM 7420-APPEND-LITERAL-SPACE
           MOVE WS-CREDITS-ABS TO WS-AMOUNT-EDIT
           MOVE WS-AMOUNT-EDIT TO WS-TRIM-SRC
           PERFORM 7600-APPEND-NUMBER
           PERFORM 7900-WRITE-JSON
           MOVE '    }' TO WS-JSON-REC
           PERFORM 7900-WRITE-JSON.

       4010-WRITE-ADDRESS-LINE-1.
           MOVE CUST-ADDR-LINE-1 TO WS-TRIM-SRC
           PERFORM 7100-TRIM-SOURCE
           IF WS-TRIM-LEN > 0
               MOVE '          "' TO WS-APP-VALUE
               PERFORM 7410-APPEND-LITERAL
               PERFORM 7500-APPEND-TEXT
               MOVE '",' TO WS-APP-VALUE
               PERFORM 7410-APPEND-LITERAL
               PERFORM 7900-WRITE-JSON
           END-IF.

       4020-WRITE-ADDRESS-LINE-2.
           MOVE CUST-ADDR-LINE-2 TO WS-TRIM-SRC
           PERFORM 7100-TRIM-SOURCE
           IF WS-TRIM-LEN > 0
               MOVE '          "' TO WS-APP-VALUE
               PERFORM 7410-APPEND-LITERAL
               PERFORM 7500-APPEND-TEXT
               MOVE '",' TO WS-APP-VALUE
               PERFORM 7410-APPEND-LITERAL
               PERFORM 7900-WRITE-JSON
           END-IF.

       4030-WRITE-ADDRESS-LINE-3.
           MOVE CUST-ADDR-LINE-3 TO WS-TRIM-SRC
           PERFORM 7100-TRIM-SOURCE
           IF WS-TRIM-LEN > 0
               MOVE '          "' TO WS-APP-VALUE
               PERFORM 7410-APPEND-LITERAL
               PERFORM 7500-APPEND-TEXT
               MOVE '",' TO WS-APP-VALUE
               PERFORM 7410-APPEND-LITERAL
               PERFORM 7900-WRITE-JSON
           END-IF.

       6000-WRITE-TRANSACTION.
           IF WS-FIRST-TRAN = 'Y'
               MOVE 'N' TO WS-FIRST-TRAN
               MOVE '        { "tranId": "' TO WS-APP-VALUE
           ELSE
               MOVE '        ,{ "tranId": "' TO WS-APP-VALUE
           END-IF
           PERFORM 7410-APPEND-LITERAL
           MOVE TRNX-ID TO WS-TRIM-SRC
           PERFORM 7500-APPEND-TEXT
           MOVE '", "typeCode": "' TO WS-APP-VALUE
           PERFORM 7410-APPEND-LITERAL
           MOVE TRNX-TYPE-CD TO WS-TRIM-SRC
           PERFORM 7500-APPEND-TEXT
           MOVE '", "categoryCode": "' TO WS-APP-VALUE
           PERFORM 7410-APPEND-LITERAL
           MOVE TRNX-CAT-CD TO WS-TRIM-SRC
           PERFORM 7500-APPEND-TEXT
           MOVE '", "description": "' TO WS-APP-VALUE
           PERFORM 7410-APPEND-LITERAL
           MOVE TRNX-DESC TO WS-TRIM-SRC
           PERFORM 7500-APPEND-TEXT
           MOVE '", "amount":' TO WS-APP-VALUE
           PERFORM 7420-APPEND-LITERAL-SPACE
           MOVE TRNX-AMT TO WS-AMOUNT-EDIT
           MOVE WS-AMOUNT-EDIT TO WS-TRIM-SRC
           PERFORM 7600-APPEND-NUMBER
           MOVE ' }' TO WS-APP-VALUE
           PERFORM 7410-APPEND-LITERAL
           PERFORM 7900-WRITE-JSON
           IF TRNX-AMT >= 0
               ADD TRNX-AMT TO WS-TOTAL-DEBITS
           ELSE
               ADD TRNX-AMT TO WS-TOTAL-CREDITS
           END-IF.

       7100-TRIM-SOURCE.
           MOVE 0 TO WS-TRIM-LEN
           PERFORM VARYING WS-IDX2 FROM 400 BY -1
             UNTIL WS-IDX2 < 1 OR WS-TRIM-LEN > 0
             IF WS-TRIM-SRC (WS-IDX2:1) NOT = SPACE
                 MOVE WS-IDX2 TO WS-TRIM-LEN
             END-IF
           END-PERFORM.

       7200-ESCAPE-TEXT.
           MOVE SPACES TO WS-ESC-VAL
           MOVE 0 TO WS-ESC-LEN
           PERFORM VARYING WS-IDX FROM 1 BY 1
             UNTIL WS-IDX > WS-TRIM-LEN
             MOVE WS-TRIM-SRC (WS-IDX:1) TO WS-CHAR
             IF WS-CHAR = '"' OR WS-CHAR = '\'
                 ADD 1 TO WS-ESC-LEN
                 MOVE '\' TO WS-ESC-VAL (WS-ESC-LEN:1)
             END-IF
             ADD 1 TO WS-ESC-LEN
             MOVE WS-CHAR TO WS-ESC-VAL (WS-ESC-LEN:1)
           END-PERFORM.

       7400-APPEND.
           IF WS-APP-LENGTH > 0
               STRING WS-APP-VALUE (1:WS-APP-LENGTH)
                 DELIMITED BY SIZE INTO WS-JSON-REC
                 WITH POINTER WS-JSON-PTR
               END-STRING
           END-IF.

       7410-APPEND-LITERAL.
           MOVE 0 TO WS-APP-LENGTH
           PERFORM VARYING WS-IDX2 FROM 400 BY -1
             UNTIL WS-IDX2 < 1 OR WS-APP-LENGTH > 0
             IF WS-APP-VALUE (WS-IDX2:1) NOT = SPACE
                 MOVE WS-IDX2 TO WS-APP-LENGTH
             END-IF
           END-PERFORM
           PERFORM 7400-APPEND.

       7420-APPEND-LITERAL-SPACE.
           PERFORM 7410-APPEND-LITERAL
           MOVE SPACE TO WS-APP-VALUE
           MOVE 1 TO WS-APP-LENGTH
           PERFORM 7400-APPEND.

       7500-APPEND-TEXT.
           PERFORM 7100-TRIM-SOURCE
           PERFORM 7200-ESCAPE-TEXT
           MOVE WS-ESC-VAL TO WS-APP-VALUE
           MOVE WS-ESC-LEN TO WS-APP-LENGTH
           PERFORM 7400-APPEND.

       7550-APPEND-TEXT-SP.
           PERFORM 7100-TRIM-SOURCE
           IF WS-TRIM-LEN > 0
               PERFORM 7200-ESCAPE-TEXT
               MOVE SPACE TO WS-APP-VALUE
               MOVE 1 TO WS-APP-LENGTH
               PERFORM 7400-APPEND
               MOVE WS-ESC-VAL TO WS-APP-VALUE
               MOVE WS-ESC-LEN TO WS-APP-LENGTH
               PERFORM 7400-APPEND
           END-IF.

       7600-APPEND-NUMBER.
           PERFORM 7100-TRIM-SOURCE
           IF WS-TRIM-LEN > 0
               MOVE WS-TRIM-SRC (1:WS-TRIM-LEN)
                 TO WS-APP-VALUE
               MOVE WS-TRIM-LEN TO WS-APP-LENGTH
               PERFORM 7400-APPEND
           END-IF.

       7900-WRITE-JSON.
           WRITE FD-JSONFILE-REC FROM WS-JSON-REC
           MOVE SPACES TO WS-JSON-REC
           MOVE 1 TO WS-JSON-PTR.

       9000-CHECK-STATUS.
           IF WS-M03B-RC NOT = '00' AND WS-M03B-RC NOT = '04'
               DISPLAY 'ERROR FILE OPERATION ' WS-M03B-DD
               DISPLAY 'RETURN CODE: ' WS-M03B-RC
               MOVE 12 TO RETURN-CODE
               GOBACK
           END-IF.

       2300-CLOSE-ACCT.
           MOVE 'ACCTFILE' TO WS-M03B-DD
           SET M03B-CLOSE TO TRUE
           CALL 'CBSTM03B' USING WS-M03B-AREA
           PERFORM 9000-CHECK-STATUS.

       2400-CLOSE-CUST.
           MOVE 'CUSTFILE' TO WS-M03B-DD
           SET M03B-CLOSE TO TRUE
           CALL 'CBSTM03B' USING WS-M03B-AREA
           PERFORM 9000-CHECK-STATUS.

       2500-CLOSE-XREF.
           MOVE 'XREFFILE' TO WS-M03B-DD
           SET M03B-CLOSE TO TRUE
           CALL 'CBSTM03B' USING WS-M03B-AREA
           PERFORM 9000-CHECK-STATUS.
