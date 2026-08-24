       IDENTIFICATION DIVISION.
       PROGRAM-ID.    CBSTLOAD.
      ******************************************************************
      * Local GnuCOBOL stand-in for the IDCAMS REPRO steps in CREASTMJ.
      * It loads the ASCII flat files into indexed files consumed by
      * CBSTM03B.  This utility is only used by run_statement_json.sh.
      ******************************************************************
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT IN-TRNX-FILE ASSIGN TO LDTRNX
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS IS WS-IN-STATUS.
           SELECT IN-XREF-FILE ASSIGN TO LDXREF
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS IS WS-IN-STATUS.
           SELECT IN-CUST-FILE ASSIGN TO LDCUST
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS IS WS-IN-STATUS.
           SELECT IN-ACCT-FILE ASSIGN TO LDACCT
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS IS WS-IN-STATUS.
           SELECT OUT-TRNX-FILE ASSIGN TO TRNXFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE IS SEQUENTIAL
                  RECORD KEY IS OUT-TRNX-KEY
                  FILE STATUS IS WS-OUT-STATUS.
           SELECT OUT-XREF-FILE ASSIGN TO XREFFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE IS SEQUENTIAL
                  RECORD KEY IS OUT-XREF-KEY
                  FILE STATUS IS WS-OUT-STATUS.
           SELECT OUT-CUST-FILE ASSIGN TO CUSTFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE IS SEQUENTIAL
                  RECORD KEY IS OUT-CUST-KEY
                  FILE STATUS IS WS-OUT-STATUS.
           SELECT OUT-ACCT-FILE ASSIGN TO ACCTFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE IS SEQUENTIAL
                  RECORD KEY IS OUT-ACCT-KEY
                  FILE STATUS IS WS-OUT-STATUS.

       DATA DIVISION.
       FILE SECTION.
       FD IN-TRNX-FILE.
       01 IN-TRNX-REC PIC X(350).
       FD IN-XREF-FILE.
       01 IN-XREF-REC PIC X(50).
       FD IN-CUST-FILE.
       01 IN-CUST-REC PIC X(500).
       FD IN-ACCT-FILE.
       01 IN-ACCT-REC PIC X(300).
       FD OUT-TRNX-FILE.
       01 OUT-TRNX-REC.
          05 OUT-TRNX-KEY PIC X(32).
          05 OUT-TRNX-DATA PIC X(318).
       FD OUT-XREF-FILE.
       01 OUT-XREF-REC.
          05 OUT-XREF-KEY PIC X(16).
          05 OUT-XREF-DATA PIC X(34).
       FD OUT-CUST-FILE.
       01 OUT-CUST-REC.
          05 OUT-CUST-KEY PIC X(09).
          05 OUT-CUST-DATA PIC X(491).
       FD OUT-ACCT-FILE.
       01 OUT-ACCT-REC.
          05 OUT-ACCT-KEY PIC 9(11).
          05 OUT-ACCT-DATA PIC X(289).

       WORKING-STORAGE SECTION.
       01 WS-IN-STATUS PIC X(02).
       01 WS-OUT-STATUS PIC X(02).
       01 WS-EOF PIC X VALUE 'N'.
       01 WS-COUNT PIC 9(07) VALUE 0.

       PROCEDURE DIVISION.
           PERFORM 1000-LOAD-TRNX
           PERFORM 2000-LOAD-XREF
           PERFORM 3000-LOAD-CUST
           PERFORM 4000-LOAD-ACCT
           GOBACK.

       1000-LOAD-TRNX.
           OPEN INPUT IN-TRNX-FILE
           PERFORM 9000-CHECK-IN
           OPEN OUTPUT OUT-TRNX-FILE
           PERFORM 9100-CHECK-OUT
           MOVE 'N' TO WS-EOF
           MOVE 0 TO WS-COUNT
           PERFORM UNTIL WS-EOF = 'Y'
             READ IN-TRNX-FILE
               AT END MOVE 'Y' TO WS-EOF
               NOT AT END
                 MOVE IN-TRNX-REC TO OUT-TRNX-REC
                 WRITE OUT-TRNX-REC
                 PERFORM 9100-CHECK-OUT
                 ADD 1 TO WS-COUNT
             END-READ
           END-PERFORM
           CLOSE IN-TRNX-FILE OUT-TRNX-FILE
           DISPLAY 'CBSTLOAD TRNXFILE RECORDS: ' WS-COUNT.

       2000-LOAD-XREF.
           OPEN INPUT IN-XREF-FILE
           PERFORM 9000-CHECK-IN
           OPEN OUTPUT OUT-XREF-FILE
           PERFORM 9100-CHECK-OUT
           MOVE 'N' TO WS-EOF
           MOVE 0 TO WS-COUNT
           PERFORM UNTIL WS-EOF = 'Y'
             READ IN-XREF-FILE
               AT END MOVE 'Y' TO WS-EOF
               NOT AT END
                 MOVE IN-XREF-REC TO OUT-XREF-REC
                 WRITE OUT-XREF-REC
                 PERFORM 9100-CHECK-OUT
                 ADD 1 TO WS-COUNT
             END-READ
           END-PERFORM
           CLOSE IN-XREF-FILE OUT-XREF-FILE
           DISPLAY 'CBSTLOAD XREFFILE RECORDS: ' WS-COUNT.

       3000-LOAD-CUST.
           OPEN INPUT IN-CUST-FILE
           PERFORM 9000-CHECK-IN
           OPEN OUTPUT OUT-CUST-FILE
           PERFORM 9100-CHECK-OUT
           MOVE 'N' TO WS-EOF
           MOVE 0 TO WS-COUNT
           PERFORM UNTIL WS-EOF = 'Y'
             READ IN-CUST-FILE
               AT END MOVE 'Y' TO WS-EOF
               NOT AT END
                 MOVE IN-CUST-REC TO OUT-CUST-REC
                 WRITE OUT-CUST-REC
                 PERFORM 9100-CHECK-OUT
                 ADD 1 TO WS-COUNT
             END-READ
           END-PERFORM
           CLOSE IN-CUST-FILE OUT-CUST-FILE
           DISPLAY 'CBSTLOAD CUSTFILE RECORDS: ' WS-COUNT.

       4000-LOAD-ACCT.
           OPEN INPUT IN-ACCT-FILE
           PERFORM 9000-CHECK-IN
           OPEN OUTPUT OUT-ACCT-FILE
           PERFORM 9100-CHECK-OUT
           MOVE 'N' TO WS-EOF
           MOVE 0 TO WS-COUNT
           PERFORM UNTIL WS-EOF = 'Y'
             READ IN-ACCT-FILE
               AT END MOVE 'Y' TO WS-EOF
               NOT AT END
                 MOVE IN-ACCT-REC TO OUT-ACCT-REC
                 WRITE OUT-ACCT-REC
                 PERFORM 9100-CHECK-OUT
                 ADD 1 TO WS-COUNT
             END-READ
           END-PERFORM
           CLOSE IN-ACCT-FILE OUT-ACCT-FILE
           DISPLAY 'CBSTLOAD ACCTFILE RECORDS: ' WS-COUNT.

       9000-CHECK-IN.
           IF WS-IN-STATUS NOT = '00' AND WS-IN-STATUS NOT = '04'
             DISPLAY 'CBSTLOAD INPUT ERROR: ' WS-IN-STATUS
             MOVE 12 TO RETURN-CODE
             GOBACK
           END-IF.

       9100-CHECK-OUT.
           IF WS-OUT-STATUS NOT = '00' AND WS-OUT-STATUS NOT = '04'
             DISPLAY 'CBSTLOAD OUTPUT ERROR: ' WS-OUT-STATUS
             MOVE 12 TO RETURN-CODE
             GOBACK
           END-IF.
