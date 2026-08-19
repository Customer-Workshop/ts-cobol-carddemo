      ******************************************************************
      * RDUNLOAD.cbl - Keyed-order unload utility (parity harness).
      * Reads an indexed file sequentially in primary-key order and
      * writes the raw records to a line-sequential unload. The unload
      * (never the physical ISAM file) is the parity surface.
      * Select which file via environment variable UNLOAD_TARGET:
      *   ACCT -> ACCTFILE (300-byte account records)
      *   TCAT -> TCATBALF (50-byte tran-cat-balance records)
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    RDUNLOAD.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT ACCT-FILE ASSIGN TO ACCTFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS FD-ACCT-ID
                  FILE STATUS  IS WS-ACCT-STAT.
           SELECT TCAT-FILE ASSIGN TO TCATBALF
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS FD-TCAT-KEY
                  FILE STATUS  IS WS-TCAT-STAT.
           SELECT OUT-FILE ASSIGN TO UNLOADOUT
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-OUT-STAT.
           SELECT OUT50-FILE ASSIGN TO UNLOADOUT
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-OUT-STAT.
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
       FD  OUT-FILE.
       01  FD-OUT-REC                           PIC X(300).
       FD  OUT50-FILE.
       01  FD-OUT50-REC                         PIC X(50).
       WORKING-STORAGE SECTION.
       01  WS-ACCT-STAT                         PIC XX.
       01  WS-TCAT-STAT                         PIC XX.
       01  WS-OUT-STAT                          PIC XX.
       01  WS-TARGET                            PIC X(04).
       01  WS-EOF                               PIC X VALUE 'N'.
       PROCEDURE DIVISION.
       MAIN-PARA.
           ACCEPT WS-TARGET FROM ENVIRONMENT 'UNLOAD_TARGET'
           EVALUATE WS-TARGET
             WHEN 'ACCT'
               OPEN OUTPUT OUT-FILE
               PERFORM UNLOAD-ACCT
               CLOSE OUT-FILE
             WHEN 'TCAT'
               OPEN OUTPUT OUT50-FILE
               PERFORM UNLOAD-TCAT
               CLOSE OUT50-FILE
             WHEN OTHER
               DISPLAY 'RDUNLOAD: BAD UNLOAD_TARGET ' WS-TARGET
               MOVE 12 TO RETURN-CODE
           END-EVALUATE
           GOBACK.

       UNLOAD-ACCT.
           OPEN INPUT ACCT-FILE
           IF WS-ACCT-STAT NOT = '00'
               DISPLAY 'RDUNLOAD ACCT OPEN FAIL ' WS-ACCT-STAT
               MOVE 12 TO RETURN-CODE
               STOP RUN
           END-IF
           PERFORM UNTIL WS-EOF = 'Y'
               READ ACCT-FILE
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       MOVE SPACES TO FD-OUT-REC
                       WRITE FD-OUT-REC FROM FD-ACCTFILE-REC
               END-READ
           END-PERFORM
           CLOSE ACCT-FILE.

       UNLOAD-TCAT.
           OPEN INPUT TCAT-FILE
           IF WS-TCAT-STAT NOT = '00'
               DISPLAY 'RDUNLOAD TCAT OPEN FAIL ' WS-TCAT-STAT
               MOVE 12 TO RETURN-CODE
               STOP RUN
           END-IF
           PERFORM UNTIL WS-EOF = 'Y'
               READ TCAT-FILE
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       WRITE FD-OUT50-REC FROM FD-TCAT-REC
               END-READ
           END-PERFORM
           CLOSE TCAT-FILE.
       END PROGRAM RDUNLOAD.
