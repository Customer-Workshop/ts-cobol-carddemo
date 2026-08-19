      ******************************************************************
      * RDUNLD2.cbl - Keyed-order unload utility for the CBTRN03C
      * lookup files (parity harness, wave 2; companion to RDUNLOAD).
      * Reads an indexed file sequentially in primary-key order and
      * writes the raw records to a line-sequential unload. The unload
      * (never the physical ISAM file) is the parity surface.
      * Select which file via environment variable UNLOAD_TARGET:
      *   CXRF -> CARDXREF (50-byte card xref records)
      *   TTYP -> TRANTYPE (60-byte transaction type records)
      *   TCTG -> TRANCATG (60-byte transaction category records)
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    RDUNLD2.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT CXRF-FILE ASSIGN TO CARDXREF
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS FD-CXRF-CARD-NUM
                  FILE STATUS  IS WS-CXRF-STAT.
           SELECT TTYP-FILE ASSIGN TO TRANTYPE
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS FD-TTYP-CD
                  FILE STATUS  IS WS-TTYP-STAT.
           SELECT TCTG-FILE ASSIGN TO TRANCATG
                  ORGANIZATION IS INDEXED
                  ACCESS MODE  IS SEQUENTIAL
                  RECORD KEY   IS FD-TCTG-KEY
                  FILE STATUS  IS WS-TCTG-STAT.
           SELECT OUT50-FILE ASSIGN TO UNLOADOUT
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-OUT-STAT.
           SELECT OUT60-FILE ASSIGN TO UNLOADOUT
                  ORGANIZATION IS LINE SEQUENTIAL
                  FILE STATUS  IS WS-OUT-STAT.
       DATA DIVISION.
       FILE SECTION.
       FD  CXRF-FILE.
       01  FD-CXRF-REC.
           05 FD-CXRF-CARD-NUM                  PIC X(16).
           05 FD-CXRF-DATA                      PIC X(34).
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
       FD  OUT50-FILE.
       01  FD-OUT50-REC                         PIC X(50).
       FD  OUT60-FILE.
       01  FD-OUT60-REC                         PIC X(60).
       WORKING-STORAGE SECTION.
       01  WS-CXRF-STAT                         PIC XX.
       01  WS-TTYP-STAT                         PIC XX.
       01  WS-TCTG-STAT                         PIC XX.
       01  WS-OUT-STAT                          PIC XX.
       01  WS-TARGET                            PIC X(04).
       01  WS-EOF                               PIC X VALUE 'N'.
       PROCEDURE DIVISION.
       MAIN-PARA.
           ACCEPT WS-TARGET FROM ENVIRONMENT 'UNLOAD_TARGET'
           EVALUATE WS-TARGET
             WHEN 'CXRF'
               OPEN OUTPUT OUT50-FILE
               PERFORM UNLOAD-CXRF
               CLOSE OUT50-FILE
             WHEN 'TTYP'
               OPEN OUTPUT OUT60-FILE
               PERFORM UNLOAD-TTYP
               CLOSE OUT60-FILE
             WHEN 'TCTG'
               OPEN OUTPUT OUT60-FILE
               PERFORM UNLOAD-TCTG
               CLOSE OUT60-FILE
             WHEN OTHER
               DISPLAY 'RDUNLD2: BAD UNLOAD_TARGET ' WS-TARGET
               MOVE 12 TO RETURN-CODE
           END-EVALUATE
           GOBACK.

       UNLOAD-CXRF.
           OPEN INPUT CXRF-FILE
           IF WS-CXRF-STAT NOT = '00'
               DISPLAY 'RDUNLD2 CXRF OPEN FAIL ' WS-CXRF-STAT
               MOVE 12 TO RETURN-CODE
               STOP RUN
           END-IF
           PERFORM UNTIL WS-EOF = 'Y'
               READ CXRF-FILE
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       WRITE FD-OUT50-REC FROM FD-CXRF-REC
               END-READ
           END-PERFORM
           CLOSE CXRF-FILE.

       UNLOAD-TTYP.
           OPEN INPUT TTYP-FILE
           IF WS-TTYP-STAT NOT = '00'
               DISPLAY 'RDUNLD2 TTYP OPEN FAIL ' WS-TTYP-STAT
               MOVE 12 TO RETURN-CODE
               STOP RUN
           END-IF
           PERFORM UNTIL WS-EOF = 'Y'
               READ TTYP-FILE
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       WRITE FD-OUT60-REC FROM FD-TTYP-REC
               END-READ
           END-PERFORM
           CLOSE TTYP-FILE.

       UNLOAD-TCTG.
           OPEN INPUT TCTG-FILE
           IF WS-TCTG-STAT NOT = '00'
               DISPLAY 'RDUNLD2 TCTG OPEN FAIL ' WS-TCTG-STAT
               MOVE 12 TO RETURN-CODE
               STOP RUN
           END-IF
           PERFORM UNTIL WS-EOF = 'Y'
               READ TCTG-FILE
                   AT END MOVE 'Y' TO WS-EOF
                   NOT AT END
                       WRITE FD-OUT60-REC FROM FD-TCTG-REC
               END-READ
           END-PERFORM
           CLOSE TCTG-FILE.
       END PROGRAM RDUNLD2.
