       IDENTIFICATION DIVISION.
       PROGRAM-ID.    CBINLOAD.
      ******************************************************************
      * Local GnuCOBOL stand-in for the IDCAMS REPRO steps in INTRPT.
      * It loads ASCII flat files into indexed files consumed by CBACT05C.
      ******************************************************************
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT IN-TCATBAL-FILE ASSIGN TO LDTCATB
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS IS WS-IN-STATUS.
           SELECT IN-ACCT-FILE ASSIGN TO LDACCT
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS IS WS-IN-STATUS.
           SELECT IN-DISCGRP-FILE ASSIGN TO LDDISC
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS IS WS-IN-STATUS.
           SELECT OUT-TCATBAL-FILE ASSIGN TO TCATBALF
                  ORGANIZATION IS INDEXED
                  ACCESS MODE IS SEQUENTIAL
                  RECORD KEY IS OUT-TCATBAL-KEY
                  FILE STATUS IS WS-OUT-STATUS.
           SELECT OUT-ACCT-FILE ASSIGN TO ACCTFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE IS SEQUENTIAL
                  RECORD KEY IS OUT-ACCT-KEY
                  FILE STATUS IS WS-OUT-STATUS.
           SELECT OUT-DISCGRP-FILE ASSIGN TO DISCGRP
                  ORGANIZATION IS INDEXED
                  ACCESS MODE IS SEQUENTIAL
                  RECORD KEY IS OUT-DISCGRP-KEY
                  FILE STATUS IS WS-OUT-STATUS.

       DATA DIVISION.
       FILE SECTION.
       FD  IN-TCATBAL-FILE.
       01  IN-TCATBAL-REC                         PIC X(50).
       FD  IN-ACCT-FILE.
       01  IN-ACCT-REC                            PIC X(300).
       FD  IN-DISCGRP-FILE.
       01  IN-DISCGRP-REC                         PIC X(50).

       FD  OUT-TCATBAL-FILE.
       01  OUT-TCATBAL-REC.
           05 OUT-TCATBAL-KEY                     PIC X(17).
           05 OUT-TCATBAL-DATA                    PIC X(33).
       FD  OUT-ACCT-FILE.
       01  OUT-ACCT-REC.
           05 OUT-ACCT-KEY                        PIC 9(11).
           05 OUT-ACCT-DATA                       PIC X(289).
       FD  OUT-DISCGRP-FILE.
       01  OUT-DISCGRP-REC.
           05 OUT-DISCGRP-KEY                     PIC X(16).
           05 OUT-DISCGRP-DATA                    PIC X(34).

       WORKING-STORAGE SECTION.
       01  WS-IN-STATUS                           PIC X(02).
       01  WS-OUT-STATUS                          PIC X(02).
       01  WS-EOF                                 PIC X VALUE 'N'.
       01  WS-COUNT                               PIC 9(07) VALUE 0.

       PROCEDURE DIVISION.
           PERFORM 1000-LOAD-TCATBAL
           PERFORM 2000-LOAD-ACCT
           PERFORM 3000-LOAD-DISCGRP
           GOBACK.

       1000-LOAD-TCATBAL.
           OPEN INPUT IN-TCATBAL-FILE
           PERFORM 9000-CHECK-IN
           OPEN OUTPUT OUT-TCATBAL-FILE
           PERFORM 9100-CHECK-OUT
           MOVE 'N' TO WS-EOF
           MOVE 0 TO WS-COUNT
           PERFORM UNTIL WS-EOF = 'Y'
               READ IN-TCATBAL-FILE
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       MOVE IN-TCATBAL-REC TO OUT-TCATBAL-REC
                       WRITE OUT-TCATBAL-REC
                       PERFORM 9100-CHECK-OUT
                       ADD 1 TO WS-COUNT
               END-READ
           END-PERFORM
           CLOSE IN-TCATBAL-FILE OUT-TCATBAL-FILE
           DISPLAY 'CBINLOAD TCATBALF RECORDS: ' WS-COUNT.

       2000-LOAD-ACCT.
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
      *                The ASCII fixture carries the group code in the
      *                legacy ZIP slot; place it in the current layout.
                       MOVE IN-ACCT-REC(103:10)
                         TO OUT-ACCT-DATA(102:10)
                       WRITE OUT-ACCT-REC
                       PERFORM 9100-CHECK-OUT
                       ADD 1 TO WS-COUNT
               END-READ
           END-PERFORM
           CLOSE IN-ACCT-FILE OUT-ACCT-FILE
           DISPLAY 'CBINLOAD ACCTFILE RECORDS: ' WS-COUNT.

       3000-LOAD-DISCGRP.
           OPEN INPUT IN-DISCGRP-FILE
           PERFORM 9000-CHECK-IN
           OPEN OUTPUT OUT-DISCGRP-FILE
           PERFORM 9100-CHECK-OUT
           MOVE 'N' TO WS-EOF
           MOVE 0 TO WS-COUNT
           PERFORM UNTIL WS-EOF = 'Y'
               READ IN-DISCGRP-FILE
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       MOVE IN-DISCGRP-REC TO OUT-DISCGRP-REC
                       WRITE OUT-DISCGRP-REC
                       PERFORM 9100-CHECK-OUT
                       ADD 1 TO WS-COUNT
               END-READ
           END-PERFORM
           CLOSE IN-DISCGRP-FILE OUT-DISCGRP-FILE
           DISPLAY 'CBINLOAD DISCGRP RECORDS: ' WS-COUNT.

       9000-CHECK-IN.
           IF WS-IN-STATUS NOT = '00' AND WS-IN-STATUS NOT = '04'
               DISPLAY 'CBINLOAD INPUT ERROR: ' WS-IN-STATUS
               MOVE 12 TO RETURN-CODE
               GOBACK
           END-IF.

       9100-CHECK-OUT.
           IF WS-OUT-STATUS NOT = '00' AND WS-OUT-STATUS NOT = '04'
               DISPLAY 'CBINLOAD OUTPUT ERROR: ' WS-OUT-STATUS
               MOVE 12 TO RETURN-CODE
               GOBACK
           END-IF.
