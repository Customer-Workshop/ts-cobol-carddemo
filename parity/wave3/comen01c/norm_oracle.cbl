       IDENTIFICATION DIVISION.
       PROGRAM-ID. NORMORCL.
      * Replays COMEN01C.cbl:117-134 verbatim (same PICs) under GnuCOBOL
      * for a list of OPTIONI values read from stdin (2 chars per line).
      * Prints: input|echo|class   class = E08 or OK
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01 WS-OPTION-X                PIC X(02) JUST RIGHT.
       01 WS-OPTION                  PIC 9(02) VALUE 0.
       01 WS-IDX                     PIC S9(04) COMP VALUE ZEROS.
       01 CDEMO-MENU-OPT-COUNT       PIC 9(02) VALUE 11.
       01 OPTIONI                    PIC X(2).
       01 OPTIONO                    PIC X(2).
       01 WS-LINE                    PIC X(10).
       01 WS-EOF                     PIC X VALUE 'N'.
       01 WS-CLASS                   PIC X(3).
       PROCEDURE DIVISION.
       MAIN.
           PERFORM UNTIL WS-EOF = 'Y'
               ACCEPT WS-LINE
               IF WS-LINE = 'END'
                   MOVE 'Y' TO WS-EOF
               ELSE
                   MOVE WS-LINE(1:2) TO OPTIONI
                   INSPECT OPTIONI REPLACING ALL '_' BY ' '
                   PERFORM PROCESS-ENTER-KEY
               END-IF
           END-PERFORM
           STOP RUN.
       PROCESS-ENTER-KEY.
           PERFORM VARYING WS-IDX
                   FROM LENGTH OF OPTIONI BY -1 UNTIL
                   OPTIONI(WS-IDX:1) NOT = SPACES OR
                   WS-IDX = 1
           END-PERFORM
           MOVE OPTIONI(1:WS-IDX) TO WS-OPTION-X
           INSPECT WS-OPTION-X REPLACING ALL ' ' BY '0'
           MOVE WS-OPTION-X              TO WS-OPTION
           MOVE WS-OPTION                TO OPTIONO
           IF WS-OPTION IS NOT NUMERIC OR
              WS-OPTION > CDEMO-MENU-OPT-COUNT OR
              WS-OPTION = ZEROS
               MOVE 'E08' TO WS-CLASS
           ELSE
               MOVE 'OK ' TO WS-CLASS
           END-IF
           DISPLAY '[' OPTIONI '] echo=[' OPTIONO '] ' WS-CLASS.
