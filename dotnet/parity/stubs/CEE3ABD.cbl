      ******************************************************************
      * CEE3ABD.cbl - stub for the z/OS Language Environment abend
      * service (parity harness). On z/OS this abends the job with the
      * given code; here it prints the code and terminates nonzero so
      * a parity run that hits an abend path fails loudly.
      ******************************************************************
       IDENTIFICATION DIVISION.
       PROGRAM-ID.    CEE3ABD.
       DATA DIVISION.
       LINKAGE SECTION.
       01  ABCODE              PIC S9(9) BINARY.
       01  TIMING              PIC S9(9) BINARY.
       PROCEDURE DIVISION USING ABCODE TIMING.
       MAIN-PARA.
           DISPLAY 'CEE3ABD STUB: ABEND CODE ' ABCODE
           MOVE 99 TO RETURN-CODE
           STOP RUN.
       END PROGRAM CEE3ABD.
