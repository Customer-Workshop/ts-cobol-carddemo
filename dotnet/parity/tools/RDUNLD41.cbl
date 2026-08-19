      ******************************************************************
      * RDUNLD41.cbl - Wave-1 keyed-order unload utility (parity
      * harness, NOT estate code). Companion to RDUNLOAD.cbl: unloads
      * the two CBACT04C input files that wave 0 did not surface, so
      * the .NET port consumes oracle-produced input bytes only.
      * Select which file via environment variable UNLOAD_TARGET:
      *   DISC -> DISCGRP  (50-byte disclosure-group rate records)
      *   XREF -> XREFFILE (50-byte card cross-reference records)
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    RDUNLD41.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT DISC-FILE ASSIGN TO DISCGRP
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS FD-DISC-KEY
                  FILE STATUS  IS WS-DISC-STAT.
           SELECT XREF-FILE ASSIGN TO XREFFILE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS FD-XREF-CARD-NUM
                  ALTERNATE RECORD KEY IS FD-XREF-ACCT-ID
                  FILE STATUS  IS WS-XREF-STAT.
           SELECT OUT50-FILE ASSIGN TO UNLOADOUT
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-OUT-STAT.
       DATA DIVISION.
       FILE SECTION.
       FD  DISC-FILE.
       01  FD-DISC-REC.
           05 FD-DISC-KEY.
              10 FD-DISC-GROUP-ID               PIC X(10).
              10 FD-DISC-TYPE-CD                PIC X(02).
              10 FD-DISC-CAT-CD                 PIC 9(04).
           05 FD-DISC-DATA                      PIC X(34).
       FD  XREF-FILE.
       01  FD-XREFFILE-REC.
           05 FD-XREF-CARD-NUM                  PIC X(16).
           05 FD-XREF-CUST-NUM                  PIC 9(09).
           05 FD-XREF-ACCT-ID                   PIC 9(11).
           05 FD-XREF-FILLER                    PIC X(14).
       FD  OUT50-FILE.
       01  FD-OUT50-REC                         PIC X(50).
       WORKING-STORAGE SECTION.
       01  WS-DISC-STAT                         PIC XX.
       01  WS-XREF-STAT                         PIC XX.
       01  WS-OUT-STAT                          PIC XX.
       01  WS-TARGET                            PIC X(04).
       01  WS-EOF                               PIC X VALUE 'N'.
       PROCEDURE DIVISION.
       MAIN-PARA.
           ACCEPT WS-TARGET FROM ENVIRONMENT 'UNLOAD_TARGET'
           EVALUATE WS-TARGET
             WHEN 'DISC'
               OPEN OUTPUT OUT50-FILE
               PERFORM UNLOAD-DISC
               CLOSE OUT50-FILE
             WHEN 'XREF'
               OPEN OUTPUT OUT50-FILE
               PERFORM UNLOAD-XREF
               CLOSE OUT50-FILE
             WHEN OTHER
               DISPLAY 'RDUNLD41: BAD UNLOAD_TARGET ' WS-TARGET
               MOVE 12 TO RETURN-CODE
           END-EVALUATE
           GOBACK.

       UNLOAD-DISC.
           OPEN INPUT DISC-FILE
           IF WS-DISC-STAT NOT = '00'
               DISPLAY 'RDUNLD41 DISC OPEN FAIL ' WS-DISC-STAT
               MOVE 12 TO RETURN-CODE
               STOP RUN
           END-IF
           PERFORM UNTIL WS-EOF = 'Y'
               READ DISC-FILE
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       WRITE FD-OUT50-REC FROM FD-DISC-REC
               END-READ
           END-PERFORM
           CLOSE DISC-FILE.

       UNLOAD-XREF.
           OPEN INPUT XREF-FILE
           IF WS-XREF-STAT NOT = '00'
               DISPLAY 'RDUNLD41 XREF OPEN FAIL ' WS-XREF-STAT
               MOVE 12 TO RETURN-CODE
               STOP RUN
           END-IF
           PERFORM UNTIL WS-EOF = 'Y'
               READ XREF-FILE
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       WRITE FD-OUT50-REC FROM FD-XREFFILE-REC
               END-READ
           END-PERFORM
           CLOSE XREF-FILE.
       END PROGRAM RDUNLD41.
