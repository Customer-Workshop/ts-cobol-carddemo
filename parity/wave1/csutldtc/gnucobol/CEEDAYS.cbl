       IDENTIFICATION DIVISION.
       PROGRAM-ID. CEEDAYS.
      * Stub LE service for the differential run only. It does NOT judge
      * the date: it writes the 8-byte feedback token selected by the
      * environment variable STUB_CODE (0000 -> all zeros, else
      * X'0003' + code + X'59C3C5C5'), so only CSUTLDTC's formatting
      * path is exercised (D-0028: the CEEDAYS mapping stays INFERRED).
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-CODE-X   PIC X(4).
       01 WS-CODE-N   REDEFINES WS-CODE-X PIC 9(4).
       LINKAGE SECTION.
       01 LS-DATE.
          02 V-LEN  PIC S9(4) BINARY.
          02 V-TXT  PIC X(256).
       01 LS-FMT.
          02 F-LEN  PIC S9(4) BINARY.
          02 F-TXT  PIC X(256).
       01 LS-LILIAN PIC S9(9) BINARY.
       01 LS-FC.
          02 FC-SEV  PIC S9(4) BINARY.
          02 FC-MSG  PIC S9(4) BINARY.
          02 FC-CTL  PIC X.
          02 FC-FAC  PIC XXX.
          02 FC-ISI  PIC S9(9) BINARY.
       PROCEDURE DIVISION USING LS-DATE, LS-FMT, LS-LILIAN, LS-FC.
           ACCEPT WS-CODE-X FROM ENVIRONMENT "STUB_CODE"
           IF WS-CODE-N = 0
              MOVE 0 TO FC-SEV FC-MSG
              MOVE LOW-VALUES TO FC-CTL FC-FAC
           ELSE
              MOVE 3 TO FC-SEV
              MOVE WS-CODE-N TO FC-MSG
              MOVE X'59' TO FC-CTL
              MOVE X'C3C5C5' TO FC-FAC
           END-IF
           MOVE 0 TO FC-ISI
           GOBACK.
