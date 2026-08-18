       IDENTIFICATION DIVISION.
       PROGRAM-ID.    CBSTLOAD.
      ******************************************************************
      * Program     : CBSTLOAD.cbl
      * Application : CardDemo - local (GnuCOBOL) test harness
      * Type        : BATCH COBOL Utility
      * Function    : Load the ASCII flat files shipped in
      *               app/data/ASCII into GnuCOBOL indexed files whose
      *               record layouts match the FDs of CBSTM03B, so that
      *               CBSTM04C can be run without a mainframe.
      *
      *               Input  DD names : LDTRNX LDXREF LDCUST LDACCT
      *               Output DD names : TRNXFILE XREFFILE CUSTFILE
      *                                 ACCTFILE
      *
      * NOTE        : This program is part of the local run harness
      *               (scripts/run_statement_json.sh) only. It is not
      *               part of the mainframe batch stream, where IDCAMS
      *               REPRO loads the VSAM KSDS clusters.
      ******************************************************************
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT IN-TRNX-FILE ASSIGN TO LDTRNX
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-IN-STATUS.

           SELECT IN-XREF-FILE ASSIGN TO LDXREF
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-IN-STATUS.

           SELECT IN-CUST-FILE ASSIGN TO LDCUST
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-IN-STATUS.

           SELECT IN-ACCT-FILE ASSIGN TO LDACCT
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-IN-STATUS.

           SELECT TRNX-FILE ASSIGN TO TRNXFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS FD-TRNXS-ID
                  FILE STATUS  IS WS-OUT-STATUS.

           SELECT XREF-FILE ASSIGN TO XREFFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS FD-XREF-CARD-NUM
                  FILE STATUS  IS WS-OUT-STATUS.

           SELECT CUST-FILE ASSIGN TO CUSTFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS FD-CUST-ID
                  FILE STATUS  IS WS-OUT-STATUS.

           SELECT ACCT-FILE ASSIGN TO ACCTFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS FD-ACCT-ID
                  FILE STATUS  IS WS-OUT-STATUS.

       DATA DIVISION.
       FILE SECTION.
       FD  IN-TRNX-FILE.
       01  IN-TRNX-REC                 PIC X(350).
       FD  IN-XREF-FILE.
       01  IN-XREF-REC                 PIC X(50).
       FD  IN-CUST-FILE.
       01  IN-CUST-REC                 PIC X(500).
       FD  IN-ACCT-FILE.
       01  IN-ACCT-REC                 PIC X(300).

       FD  TRNX-FILE.
       01  FD-TRNXFILE-REC.
           05 FD-TRNXS-ID.
              10  FD-TRNX-CARD                  PIC X(16).
              10  FD-TRNX-ID                    PIC X(16).
           05 FD-TRNX-DATA                      PIC X(318).

       FD  XREF-FILE.
       01  FD-XREFFILE-REC.
           05 FD-XREF-CARD-NUM                  PIC X(16).
           05 FD-XREF-DATA                      PIC X(34).

       FD  CUST-FILE.
       01  FD-CUSTFILE-REC.
           05 FD-CUST-ID                        PIC X(09).
           05 FD-CUST-DATA                      PIC X(491).

       FD  ACCT-FILE.
       01  FD-ACCTFILE-REC.
           05 FD-ACCT-ID                        PIC 9(11).
           05 FD-ACCT-DATA                      PIC X(289).

       WORKING-STORAGE SECTION.
       01  WS-IN-STATUS                PIC X(02).
       01  WS-OUT-STATUS               PIC X(02).
       01  WS-EOF                      PIC X(01) VALUE 'N'.
       01  WS-COUNT                    PIC 9(07) VALUE 0.

       PROCEDURE DIVISION.
           PERFORM 1000-LOAD-TRNX.
           PERFORM 2000-LOAD-XREF.
           PERFORM 3000-LOAD-CUST.
           PERFORM 4000-LOAD-ACCT.
           GOBACK.

       1000-LOAD-TRNX.
           OPEN INPUT IN-TRNX-FILE.
           PERFORM 9000-CHECK-IN.
           OPEN OUTPUT TRNX-FILE.
           PERFORM 9100-CHECK-OUT.
           MOVE 'N' TO WS-EOF.
           MOVE 0 TO WS-COUNT.
           PERFORM UNTIL WS-EOF = 'Y'
               READ IN-TRNX-FILE
                 AT END
                   MOVE 'Y' TO WS-EOF
                 NOT AT END
                   MOVE IN-TRNX-REC TO FD-TRNXFILE-REC
                   WRITE FD-TRNXFILE-REC
                   PERFORM 9100-CHECK-OUT
                   ADD 1 TO WS-COUNT
               END-READ
           END-PERFORM.
           CLOSE IN-TRNX-FILE TRNX-FILE.
           DISPLAY 'CBSTLOAD TRNXFILE RECORDS: ' WS-COUNT.
           EXIT.

       2000-LOAD-XREF.
           OPEN INPUT IN-XREF-FILE.
           PERFORM 9000-CHECK-IN.
           OPEN OUTPUT XREF-FILE.
           PERFORM 9100-CHECK-OUT.
           MOVE 'N' TO WS-EOF.
           MOVE 0 TO WS-COUNT.
           PERFORM UNTIL WS-EOF = 'Y'
               READ IN-XREF-FILE
                 AT END
                   MOVE 'Y' TO WS-EOF
                 NOT AT END
                   MOVE IN-XREF-REC TO FD-XREFFILE-REC
                   WRITE FD-XREFFILE-REC
                   PERFORM 9100-CHECK-OUT
                   ADD 1 TO WS-COUNT
               END-READ
           END-PERFORM.
           CLOSE IN-XREF-FILE XREF-FILE.
           DISPLAY 'CBSTLOAD XREFFILE RECORDS: ' WS-COUNT.
           EXIT.

       3000-LOAD-CUST.
           OPEN INPUT IN-CUST-FILE.
           PERFORM 9000-CHECK-IN.
           OPEN OUTPUT CUST-FILE.
           PERFORM 9100-CHECK-OUT.
           MOVE 'N' TO WS-EOF.
           MOVE 0 TO WS-COUNT.
           PERFORM UNTIL WS-EOF = 'Y'
               READ IN-CUST-FILE
                 AT END
                   MOVE 'Y' TO WS-EOF
                 NOT AT END
                   MOVE IN-CUST-REC TO FD-CUSTFILE-REC
                   WRITE FD-CUSTFILE-REC
                   PERFORM 9100-CHECK-OUT
                   ADD 1 TO WS-COUNT
               END-READ
           END-PERFORM.
           CLOSE IN-CUST-FILE CUST-FILE.
           DISPLAY 'CBSTLOAD CUSTFILE RECORDS: ' WS-COUNT.
           EXIT.

       4000-LOAD-ACCT.
           OPEN INPUT IN-ACCT-FILE.
           PERFORM 9000-CHECK-IN.
           OPEN OUTPUT ACCT-FILE.
           PERFORM 9100-CHECK-OUT.
           MOVE 'N' TO WS-EOF.
           MOVE 0 TO WS-COUNT.
           PERFORM UNTIL WS-EOF = 'Y'
               READ IN-ACCT-FILE
                 AT END
                   MOVE 'Y' TO WS-EOF
                 NOT AT END
                   MOVE IN-ACCT-REC TO FD-ACCTFILE-REC
                   WRITE FD-ACCTFILE-REC
                   PERFORM 9100-CHECK-OUT
                   ADD 1 TO WS-COUNT
               END-READ
           END-PERFORM.
           CLOSE IN-ACCT-FILE ACCT-FILE.
           DISPLAY 'CBSTLOAD ACCTFILE RECORDS: ' WS-COUNT.
           EXIT.

       9000-CHECK-IN.
           IF WS-IN-STATUS NOT = '00' AND WS-IN-STATUS NOT = '04'
               DISPLAY 'CBSTLOAD INPUT ERROR, STATUS: ' WS-IN-STATUS
               MOVE 12 TO RETURN-CODE
               GOBACK
           END-IF.
           EXIT.

       9100-CHECK-OUT.
           IF WS-OUT-STATUS NOT = '00' AND WS-OUT-STATUS NOT = '04'
               DISPLAY 'CBSTLOAD OUTPUT ERROR, STATUS: ' WS-OUT-STATUS
               MOVE 12 TO RETURN-CODE
               GOBACK
           END-IF.
           EXIT.
