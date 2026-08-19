      ******************************************************************
      * DRVACT04.cbl - JCL PARM shim for CBACT04C (parity harness).
      * z/OS passes a halfword-length-prefixed PARM area; GnuCOBOL has
      * no JCL, so this driver builds the same LINKAGE shape and CALLs
      * CBACT04C. The run date comes from environment PARM_DATE
      * (YYYY-MM-DD), matching INTCALC.jcl's PARM.
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    DRVACT04.
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01  EXTERNAL-PARMS.
           05  PARM-LENGTH         PIC S9(04) COMP VALUE 10.
           05  PARM-DATE           PIC X(10).
       PROCEDURE DIVISION.
       MAIN-PARA.
           ACCEPT PARM-DATE FROM ENVIRONMENT 'PARM_DATE'
           IF PARM-DATE = SPACES
               DISPLAY 'DRVACT04: PARM_DATE env var not set'
               MOVE 12 TO RETURN-CODE
               STOP RUN
           END-IF
           CALL 'CBACT04C' USING EXTERNAL-PARMS
           GOBACK.
       END PROGRAM DRVACT04.
