      ******************************************************************
      * Program     : CBTRN04C.CBL                                      
      * Application : CardDemo                                          
      * Type        : BATCH COBOL Program                                
      * Function    : Aggregate the transaction summary extract.
      ******************************************************************
      * Copyright Amazon.com, Inc. or its affiliates.                   
      * All Rights Reserved.                                            
      *                                                                 
      * Licensed under the Apache License, Version 2.0 (the "License"). 
      * You may not use this file except in compliance with the License.
      * You may obtain a copy of the License at                         
      *                                                                 
      *    http://www.apache.org/licenses/LICENSE-2.0                   
      *                                                                 
      * Unless required by applicable law or agreed to in writing,      
      * software distributed under the License is distributed on an     
      * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,    
      * either express or implied. See the License for the specific     
      * language governing permissions and limitations under the License
      ******************************************************************
       IDENTIFICATION DIVISION.                                                 
       PROGRAM-ID.    CBTRN04C.                                                 
       AUTHOR.        AWS.                                                      
                                                                                
       ENVIRONMENT DIVISION.                                                    
       INPUT-OUTPUT SECTION.                                                    
       FILE-CONTROL.                                                            
           SELECT TRANSACT-FILE ASSIGN TO TRANFILE                              
                  ORGANIZATION IS SEQUENTIAL                                    
                  FILE STATUS  IS TRANFILE-STATUS.                              
                                                                                
           SELECT TRANTYPE-FILE ASSIGN TO TRANTYPE                              
                  ORGANIZATION IS SEQUENTIAL                                    
                  FILE STATUS  IS TRANTYPE-STATUS.                              
                                                                                
           SELECT TRANCATG-FILE ASSIGN TO TRANCATG                              
                  ORGANIZATION IS SEQUENTIAL                                    
                  FILE STATUS  IS TRANCATG-STATUS.                              
                                                                                
           SELECT SUMMARY-FILE ASSIGN TO TRANSUMM                               
                  ORGANIZATION IS LINE SEQUENTIAL                               
                  FILE STATUS  IS SUMMARY-STATUS.                              
                                                                                
       DATA DIVISION.                                                           
       FILE SECTION.                                                            
       FD  TRANSACT-FILE.                                                       
       01 FD-TRANFILE-REC.                                                      
          05 FD-TRANS-DATA      PIC X(304).                                     
          05 FD-TRAN-PROC-TS    PIC X(26).                                      
          05 FD-FILLER          PIC X(20).                                      
                                                                                
       FD  TRANTYPE-FILE.                                                       
       01 FD-TRANTYPE-REC.                                                      
          05 FD-TRAN-TYPE       PIC X(02).                                      
          05 FD-TRAN-DATA       PIC X(58).                                      
                                                                                
       FD  TRANCATG-FILE.                                                       
       01 FD-TRAN-CAT-RECORD.                                                   
           05  FD-TRAN-CAT-KEY.                                                 
              10 FD-TRAN-TYPE-CD PIC X(02).                                    
              10 FD-TRAN-CAT-CD  PIC 9(04).                                    
           05 FD-TRAN-CAT-DATA   PIC X(54).                                    
                                                                                
       FD  SUMMARY-FILE.                                                        
       01 FD-SUMMARY-REC       PIC X(200).                                     
                                                                                
       WORKING-STORAGE SECTION.                                                 
                                                                                
      *****************************************************************         
       COPY CVTRA05Y.                                                           
       01 TRANFILE-STATUS.                                                      
          05 TRANFILE-STAT1     PIC X.                                          
          05 TRANFILE-STAT2     PIC X.                                          
                                                                                
       COPY CVTRA03Y.                                                           
       01 TRANTYPE-STATUS.                                                      
          05 TRANTYPE-STAT1     PIC X.                                          
          05 TRANTYPE-STAT2     PIC X.                                          
                                                                                
       COPY CVTRA04Y.                                                           
       01 TRANCATG-STATUS.                                                      
          05 TRANCATG-STAT1     PIC X.                                          
          05 TRANCATG-STAT2     PIC X.                                          
                                                                                
       01 SUMMARY-STATUS.                                                       
          05 SUMMARY-STAT1      PIC X.                                          
          05 SUMMARY-STAT2      PIC X.                                          
                                                                                
       01 WS-TYPE-TABLE.                                                         
          05 WS-TYPE-COUNT      PIC 9(02) VALUE 0.                              
          05 WS-TYPE-ITEM OCCURS 50 TIMES.                                      
             10 WS-TYPE-CODE    PIC X(02).                                      
             10 WS-TYPE-DESC    PIC X(50).                                      
                                                                                
       01 WS-SUMMARY-TABLE.                                                      
          05 WS-SUMMARY-COUNT   PIC 9(03) VALUE 0.                              
          05 WS-SUMMARY-ITEM OCCURS 500 TIMES.                                  
             10 WS-SUM-TYPE-CODE PIC X(02).                                    
             10 WS-SUM-CAT-CODE  PIC 9(04).                                    
             10 WS-SUM-TYPE-DESC PIC X(50).                                    
             10 WS-SUM-CAT-DESC  PIC X(50).                                    
             10 WS-SUM-TRAN-COUNT PIC 9(09) VALUE 0.                            
             10 WS-SUM-TOTAL     PIC S9(13)V99 VALUE 0.                         
                                                                                
       01 WS-PROCESS-VARS.                                                       
          05 WS-TYPE-EOF        PIC X VALUE 'N'.                                
          05 WS-CAT-EOF         PIC X VALUE 'N'.                                
          05 WS-TRAN-COUNT      PIC 9(09) VALUE 0.                              
          05 WS-OUTPUT-COUNT    PIC 9(09) VALUE 0.                              
          05 WS-SUM-INDEX       PIC 9(03) VALUE 0.                              
          05 WS-TYPE-INDEX      PIC 9(02) VALUE 0.                              
          05 WS-FOUND           PIC X VALUE 'N'.                                
                                                                                
       01 WS-DESC-VARS.                                                         
          05 WS-DESC-SOURCE     PIC X(50).                                      
          05 WS-DESC-TRIM-LEN   PIC 9(02) VALUE 0.                              
          05 WS-DESC-INDEX      PIC 9(02) VALUE 0.                              
          05 WS-DESC-OUT-POS    PIC 9(03) VALUE 0.                              
          05 WS-DESC-OUT-LEN    PIC 9(03) VALUE 0.                              
          05 WS-DESC-COMMA      PIC X VALUE 'N'.                                
          05 WS-DESC-OUT        PIC X(110).                                     
          05 WS-TYPE-FIELD      PIC X(110).                                     
          05 WS-TYPE-FIELD-LEN  PIC 9(03) VALUE 0.                              
          05 WS-CAT-FIELD       PIC X(110).                                     
          05 WS-CAT-FIELD-LEN   PIC 9(03) VALUE 0.                              
                                                                                
       01 WS-NUMERIC-VARS.                                                      
          05 WS-COUNT-EDIT      PIC Z(8)9.                                      
          05 WS-COUNT-START     PIC 9(02) VALUE 1.                              
          05 WS-COUNT-LEN       PIC 9(02) VALUE 0.                              
          05 WS-AMOUNT-EDIT     PIC -------------9.99.                         
          05 WS-AMOUNT-START    PIC 9(02) VALUE 1.                              
          05 WS-AMOUNT-LEN      PIC 9(02) VALUE 0.                              
          05 WS-LEADING         PIC 9(02) VALUE 0.                              
                                                                                
       01 WS-CSV-LINE           PIC X(200).                                     
       01 WS-HDR-1              PIC X(35).                                      
       01 WS-HDR-2              PIC X(32).                                      
       01 IO-STATUS.                                                            
          05 IO-STAT1           PIC X.                                          
          05 IO-STAT2           PIC X.                                          
       01 TWO-BYTES-BINARY      PIC 9(4) BINARY.                                
       01 TWO-BYTES-ALPHA REDEFINES TWO-BYTES-BINARY.                           
          05 TWO-BYTES-LEFT     PIC X.                                          
          05 TWO-BYTES-RIGHT    PIC X.                                          
       01 IO-STATUS-04.                                                         
          05 IO-STATUS-0401     PIC 9      VALUE 0.                             
          05 IO-STATUS-0403     PIC 999    VALUE 0.                             
                                                                                
       01 APPL-RESULT           PIC S9(9) COMP.                                 
          88 APPL-AOK                      VALUE 0.                             
          88 APPL-EOF                      VALUE 16.                            
                                                                                
       01 END-OF-FILE           PIC X(01)  VALUE 'N'.                           
       01 ABCODE                PIC S9(9) BINARY.                               
       01 TIMING                PIC S9(9) BINARY.                               
                                                                                
      *****************************************************************         
       PROCEDURE DIVISION.                                                      
           DISPLAY 'START OF EXECUTION OF PROGRAM CBTRN04C'.                    
           PERFORM 0000-TRANFILE-OPEN.                                          
           PERFORM 0100-TRANTYPE-OPEN.                                          
           PERFORM 0200-TRANCATG-OPEN.                                          
           PERFORM 0300-SUMMARY-OPEN.                                           
                                                                                
           PERFORM 1000-LOAD-TRANTYPE.                                          
           PERFORM 2000-LOAD-TRANCATG.                                          
           PERFORM 3000-AGGREGATE-TRANSACTIONS.                                 
           PERFORM 4000-WRITE-SUMMARY.                                         
                                                                                
           PERFORM 9000-TRANFILE-CLOSE.                                         
           PERFORM 9100-TRANTYPE-CLOSE.                                         
           PERFORM 9200-TRANCATG-CLOSE.                                         
           PERFORM 9300-SUMMARY-CLOSE.                                         
                                                                                
           DISPLAY 'TRANSACTION RECORDS READ : ' WS-TRAN-COUNT.                 
           DISPLAY 'SUMMARY ROWS WRITTEN      : ' WS-OUTPUT-COUNT.               
           DISPLAY 'END OF EXECUTION OF PROGRAM CBTRN04C'.                      
                                                                                
           GOBACK.                                                              
                                                                                
      * Load transaction types into the local reference table.                   
       1000-LOAD-TRANTYPE.                                                      
           PERFORM UNTIL WS-TYPE-EOF = 'Y'                                      
              PERFORM 1100-TRANTYPE-GET-NEXT                                    
              IF WS-TYPE-EOF = 'N'                                              
                 IF WS-TYPE-COUNT >= 50                                         
                    DISPLAY 'TRANSACTION TYPE TABLE OVERFLOW'                   
                    MOVE 23 TO IO-STATUS                                        
                    PERFORM 9910-DISPLAY-IO-STATUS                             
                    PERFORM 9999-ABEND-PROGRAM                                  
                 END-IF                                                         
                 ADD 1 TO WS-TYPE-COUNT                                         
                 MOVE TRAN-TYPE TO WS-TYPE-CODE (WS-TYPE-COUNT)                 
                 MOVE TRAN-TYPE-DESC TO WS-TYPE-DESC (WS-TYPE-COUNT)            
              END-IF                                                             
           END-PERFORM                                                           
           DISPLAY 'TRANSACTION TYPES LOADED : ' WS-TYPE-COUNT                  
           EXIT.                                                                
                                                                                
       1100-TRANTYPE-GET-NEXT.                                                  
           READ TRANTYPE-FILE INTO TRAN-TYPE-RECORD                              
           EVALUATE TRANTYPE-STATUS                                             
             WHEN '00'                                                          
                 MOVE 0 TO APPL-RESULT                                          
             WHEN '10'                                                          
                 MOVE 16 TO APPL-RESULT                                         
             WHEN OTHER                                                         
                 MOVE 12 TO APPL-RESULT                                         
           END-EVALUATE                                                          
           IF APPL-AOK                                                          
              CONTINUE                                                          
           ELSE                                                                 
              IF APPL-EOF                                                       
                 MOVE 'Y' TO WS-TYPE-EOF                                        
              ELSE                                                              
                 DISPLAY 'ERROR READING TRANSACTION TYPE FILE'                  
                 MOVE TRANTYPE-STATUS TO IO-STATUS                              
                 PERFORM 9910-DISPLAY-IO-STATUS                                 
                 PERFORM 9999-ABEND-PROGRAM                                     
              END-IF                                                            
           END-IF                                                               
           EXIT.                                                                
                                                                                
      * Load categories in reference-file order for deterministic output.        
       2000-LOAD-TRANCATG.                                                      
           PERFORM UNTIL WS-CAT-EOF = 'Y'                                       
              PERFORM 2100-TRANCATG-GET-NEXT                                    
              IF WS-CAT-EOF = 'N'                                               
                 IF WS-SUMMARY-COUNT >= 500                                     
                    DISPLAY 'TRANSACTION CATEGORY TABLE OVERFLOW'               
                    MOVE 23 TO IO-STATUS                                        
                    PERFORM 9910-DISPLAY-IO-STATUS                             
                    PERFORM 9999-ABEND-PROGRAM                                  
                 END-IF                                                         
                 ADD 1 TO WS-SUMMARY-COUNT                                      
                 MOVE TRAN-TYPE-CD OF TRAN-CAT-KEY                              
                   TO WS-SUM-TYPE-CODE (WS-SUMMARY-COUNT)                       
                 MOVE TRAN-CAT-CD OF TRAN-CAT-KEY                               
                   TO WS-SUM-CAT-CODE (WS-SUMMARY-COUNT)                        
                 MOVE TRAN-CAT-TYPE-DESC TO WS-SUM-CAT-DESC                     
                 (WS-SUMMARY-COUNT)                                             
                 PERFORM 2200-LOOKUP-TYPE                                       
                 MOVE WS-DESC-SOURCE TO WS-SUM-TYPE-DESC                        
                 (WS-SUMMARY-COUNT)                                             
              END-IF                                                             
           END-PERFORM                                                           
           DISPLAY 'TRANSACTION CATEGORIES LOADED : ' WS-SUMMARY-COUNT          
           EXIT.                                                                
                                                                                
       2100-TRANCATG-GET-NEXT.                                                  
           READ TRANCATG-FILE INTO TRAN-CAT-RECORD                              
           EVALUATE TRANCATG-STATUS                                             
             WHEN '00'                                                          
                 MOVE 0 TO APPL-RESULT                                          
             WHEN '10'                                                          
                 MOVE 16 TO APPL-RESULT                                         
             WHEN OTHER                                                         
                 MOVE 12 TO APPL-RESULT                                         
           END-EVALUATE                                                          
           IF APPL-AOK                                                          
              CONTINUE                                                          
           ELSE                                                                 
              IF APPL-EOF                                                       
                 MOVE 'Y' TO WS-CAT-EOF                                         
              ELSE                                                              
                 DISPLAY 'ERROR READING TRANSACTION CATEGORY FILE'              
                 MOVE TRANCATG-STATUS TO IO-STATUS                              
                 PERFORM 9910-DISPLAY-IO-STATUS                                 
                 PERFORM 9999-ABEND-PROGRAM                                     
              END-IF                                                            
           END-IF                                                               
           EXIT.                                                                
                                                                                
       2200-LOOKUP-TYPE.                                                        
           MOVE SPACES TO WS-DESC-SOURCE                                        
           MOVE 'N' TO WS-FOUND                                                 
           PERFORM VARYING WS-TYPE-INDEX FROM 1 BY 1                            
             UNTIL WS-TYPE-INDEX > WS-TYPE-COUNT OR WS-FOUND = 'Y'              
              IF WS-TYPE-CODE (WS-TYPE-INDEX) =                                 
                 TRAN-TYPE-CD OF TRAN-CAT-KEY                                   
                 MOVE WS-TYPE-DESC (WS-TYPE-INDEX) TO WS-DESC-SOURCE            
                 MOVE 'Y' TO WS-FOUND                                           
              END-IF                                                            
           END-PERFORM                                                           
           IF WS-FOUND = 'N'                                                     
              DISPLAY 'WARNING: TRANSACTION TYPE NOT FOUND : '                  
                 TRAN-TYPE-CD OF TRAN-CAT-KEY                                   
           END-IF                                                               
           EXIT.                                                                
                                                                                
      * Aggregate every transaction against the loaded category table.           
       3000-AGGREGATE-TRANSACTIONS.                                             
           MOVE 'N' TO END-OF-FILE                                              
           PERFORM UNTIL END-OF-FILE = 'Y'                                      
              PERFORM 3100-TRANFILE-GET-NEXT                                    
              IF END-OF-FILE = 'N'                                              
                 ADD 1 TO WS-TRAN-COUNT                                         
                 PERFORM 3200-FIND-SUMMARY                                      
                 IF WS-FOUND = 'N'                                              
                    MOVE TRAN-TYPE-CD OF TRAN-RECORD TO FD-TRAN-TYPE-CD         
                    MOVE TRAN-CAT-CD OF TRAN-RECORD TO FD-TRAN-CAT-CD           
                    DISPLAY 'INVALID TRAN CATG KEY : ' FD-TRAN-CAT-KEY          
                    MOVE 23 TO IO-STATUS                                        
                    PERFORM 9910-DISPLAY-IO-STATUS                             
                    PERFORM 9999-ABEND-PROGRAM                                  
                 ELSE                                                            
                    ADD 1 TO WS-SUM-TRAN-COUNT (WS-SUM-INDEX)                   
                    ADD TRAN-AMT TO WS-SUM-TOTAL (WS-SUM-INDEX)                 
                 END-IF                                                         
              END-IF                                                             
           END-PERFORM                                                           
           EXIT.                                                                
                                                                                
       3100-TRANFILE-GET-NEXT.                                                  
           READ TRANSACT-FILE INTO TRAN-RECORD                                  
           EVALUATE TRANFILE-STATUS                                             
             WHEN '00'                                                          
                 MOVE 0 TO APPL-RESULT                                          
             WHEN '10'                                                          
                 MOVE 16 TO APPL-RESULT                                         
             WHEN OTHER                                                         
                 MOVE 12 TO APPL-RESULT                                         
           END-EVALUATE                                                          
           IF APPL-AOK                                                          
              CONTINUE                                                          
           ELSE                                                                 
              IF APPL-EOF                                                       
                 MOVE 'Y' TO END-OF-FILE                                        
              ELSE                                                              
                 DISPLAY 'ERROR READING TRANSACTION FILE'                       
                 MOVE TRANFILE-STATUS TO IO-STATUS                              
                 PERFORM 9910-DISPLAY-IO-STATUS                                 
                 PERFORM 9999-ABEND-PROGRAM                                     
              END-IF                                                            
           END-IF                                                               
           EXIT.                                                                
                                                                                
       3200-FIND-SUMMARY.                                                       
           MOVE 'N' TO WS-FOUND                                                 
           MOVE 0 TO WS-SUM-INDEX                                               
           PERFORM VARYING WS-SUM-INDEX FROM 1 BY 1                             
             UNTIL WS-SUM-INDEX > WS-SUMMARY-COUNT OR WS-FOUND = 'Y'             
              IF WS-SUM-TYPE-CODE (WS-SUM-INDEX) =                               
                 TRAN-TYPE-CD OF TRAN-RECORD                                    
                 AND WS-SUM-CAT-CODE (WS-SUM-INDEX) =                           
                 TRAN-CAT-CD OF TRAN-RECORD                                     
                 MOVE 'Y' TO WS-FOUND                                           
              END-IF                                                            
           END-PERFORM                                                           
           IF WS-FOUND = 'Y'                                                     
              SUBTRACT 1 FROM WS-SUM-INDEX                                      
           END-IF                                                               
           EXIT.                                                                
                                                                                
      * Write the frozen CSV contract in reference-file order.                  
       4000-WRITE-SUMMARY.                                                      
           MOVE SPACES TO FD-SUMMARY-REC                                         
           MOVE 'TRAN_TYPE_CD,TRAN_CAT_CD,TYPE_DESC,' TO WS-HDR-1                
           MOVE 'CAT_DESC,TRAN_COUNT,TOTAL_AMOUNT' TO WS-HDR-2                   
           MOVE WS-HDR-1 TO FD-SUMMARY-REC (1:35)                               
           MOVE WS-HDR-2 TO FD-SUMMARY-REC (36:32)                              
           PERFORM 4100-WRITE-SUMMARY-REC                                       
           PERFORM VARYING WS-SUM-INDEX FROM 1 BY 1                             
             UNTIL WS-SUM-INDEX > WS-SUMMARY-COUNT                              
              IF WS-SUM-TRAN-COUNT (WS-SUM-INDEX) > 0                           
                 PERFORM 4200-FORMAT-SUMMARY-ROW                                
                 PERFORM 4100-WRITE-SUMMARY-REC                                 
                 ADD 1 TO WS-OUTPUT-COUNT                                       
              END-IF                                                             
           END-PERFORM                                                           
           EXIT.                                                                
                                                                                
       4100-WRITE-SUMMARY-REC.                                                  
           WRITE FD-SUMMARY-REC                                                  
           IF SUMMARY-STATUS = '00'                                             
              MOVE 0 TO APPL-RESULT                                             
           ELSE                                                                 
              MOVE 12 TO APPL-RESULT                                            
           END-IF                                                               
           IF APPL-AOK                                                          
              CONTINUE                                                          
           ELSE                                                                 
              DISPLAY 'ERROR WRITING TRANSACTION SUMMARY FILE'                  
              MOVE SUMMARY-STATUS TO IO-STATUS                                   
              PERFORM 9910-DISPLAY-IO-STATUS                                    
              PERFORM 9999-ABEND-PROGRAM                                        
           END-IF                                                               
           EXIT.                                                                
                                                                                
       4200-FORMAT-SUMMARY-ROW.                                                  
           MOVE WS-SUM-TYPE-DESC (WS-SUM-INDEX) TO WS-DESC-SOURCE                
           PERFORM 4300-FORMAT-DESCRIPTION                                      
           MOVE WS-DESC-OUT TO WS-TYPE-FIELD                                    
           MOVE WS-DESC-OUT-LEN TO WS-TYPE-FIELD-LEN                            
           IF WS-TYPE-FIELD-LEN = 0                                             
              MOVE 1 TO WS-TYPE-FIELD-LEN                                       
           END-IF                                                               
           MOVE WS-SUM-CAT-DESC (WS-SUM-INDEX) TO WS-DESC-SOURCE                
           PERFORM 4300-FORMAT-DESCRIPTION                                      
           MOVE WS-DESC-OUT TO WS-CAT-FIELD                                     
           MOVE WS-DESC-OUT-LEN TO WS-CAT-FIELD-LEN                             
           IF WS-CAT-FIELD-LEN = 0                                              
              MOVE 1 TO WS-CAT-FIELD-LEN                                        
           END-IF                                                               
           MOVE WS-SUM-TRAN-COUNT (WS-SUM-INDEX) TO WS-COUNT-EDIT               
           MOVE 0 TO WS-LEADING                                                 
           INSPECT WS-COUNT-EDIT TALLYING WS-LEADING FOR LEADING SPACES          
           COMPUTE WS-COUNT-START = WS-LEADING + 1                              
           COMPUTE WS-COUNT-LEN = 9 - WS-LEADING                                
           MOVE WS-SUM-TOTAL (WS-SUM-INDEX) TO WS-AMOUNT-EDIT                   
           MOVE 0 TO WS-LEADING                                                 
           INSPECT WS-AMOUNT-EDIT TALLYING WS-LEADING FOR LEADING SPACES         
           COMPUTE WS-AMOUNT-START = WS-LEADING + 1                             
           COMPUTE WS-AMOUNT-LEN = 17 - WS-LEADING                              
           MOVE SPACES TO WS-CSV-LINE                                           
           STRING                                                                
             WS-SUM-TYPE-CODE (WS-SUM-INDEX) DELIMITED BY SIZE                  
             ',' DELIMITED BY SIZE                                              
             WS-SUM-CAT-CODE (WS-SUM-INDEX) DELIMITED BY SIZE                   
             ',' DELIMITED BY SIZE                                              
             WS-TYPE-FIELD (1:WS-TYPE-FIELD-LEN) DELIMITED BY SIZE              
             ',' DELIMITED BY SIZE                                              
             WS-CAT-FIELD (1:WS-CAT-FIELD-LEN) DELIMITED BY SIZE               
             ',' DELIMITED BY SIZE                                              
             WS-COUNT-EDIT (WS-COUNT-START:WS-COUNT-LEN)                        
                DELIMITED BY SIZE                                               
             ',' DELIMITED BY SIZE                                              
             WS-AMOUNT-EDIT (WS-AMOUNT-START:WS-AMOUNT-LEN)                     
                DELIMITED BY SIZE                                               
             INTO WS-CSV-LINE                                                   
           END-STRING                                                           
           MOVE WS-CSV-LINE TO FD-SUMMARY-REC                                   
           EXIT.                                                                
                                                                                
       4300-FORMAT-DESCRIPTION.                                                  
           MOVE SPACES TO WS-DESC-OUT                                           
           MOVE 0 TO WS-DESC-TRIM-LEN                                           
           PERFORM VARYING WS-DESC-INDEX FROM 1 BY 1                            
             UNTIL WS-DESC-INDEX > 50                                           
              IF WS-DESC-SOURCE (WS-DESC-INDEX:1) NOT = SPACE                    
                 MOVE WS-DESC-INDEX TO WS-DESC-TRIM-LEN                         
              END-IF                                                            
           END-PERFORM                                                           
           MOVE 'N' TO WS-DESC-COMMA                                             
           PERFORM VARYING WS-DESC-INDEX FROM 1 BY 1                            
             UNTIL WS-DESC-INDEX > WS-DESC-TRIM-LEN                             
              IF WS-DESC-SOURCE (WS-DESC-INDEX:1) = ','                          
                 MOVE 'Y' TO WS-DESC-COMMA                                      
              END-IF                                                             
           END-PERFORM                                                           
           MOVE 1 TO WS-DESC-OUT-POS                                            
           IF WS-DESC-COMMA = 'Y'                                               
              MOVE '"' TO WS-DESC-OUT (WS-DESC-OUT-POS:1)                       
              ADD 1 TO WS-DESC-OUT-POS                                           
           END-IF                                                               
           PERFORM VARYING WS-DESC-INDEX FROM 1 BY 1                            
             UNTIL WS-DESC-INDEX > WS-DESC-TRIM-LEN                             
              IF WS-DESC-SOURCE (WS-DESC-INDEX:1) = '"'                          
                 AND WS-DESC-COMMA = 'Y'                                         
                 MOVE '"' TO WS-DESC-OUT (WS-DESC-OUT-POS:1)                    
                 ADD 1 TO WS-DESC-OUT-POS                                        
              END-IF                                                             
              MOVE WS-DESC-SOURCE (WS-DESC-INDEX:1)                             
                TO WS-DESC-OUT (WS-DESC-OUT-POS:1)                              
              ADD 1 TO WS-DESC-OUT-POS                                           
           END-PERFORM                                                           
           IF WS-DESC-COMMA = 'Y'                                               
              MOVE '"' TO WS-DESC-OUT (WS-DESC-OUT-POS:1)                       
              ADD 1 TO WS-DESC-OUT-POS                                           
           END-IF                                                               
           COMPUTE WS-DESC-OUT-LEN = WS-DESC-OUT-POS - 1                        
           EXIT.                                                                
                                                                                
      *---------------------------------------------------------------*         
       0000-TRANFILE-OPEN.                                                      
           MOVE 8 TO APPL-RESULT                                                
           OPEN INPUT TRANSACT-FILE                                             
           IF TRANFILE-STATUS = '00'                                            
              MOVE 0 TO APPL-RESULT                                             
           ELSE                                                                 
              MOVE 12 TO APPL-RESULT                                            
           END-IF                                                               
           IF APPL-AOK                                                          
              CONTINUE                                                          
           ELSE                                                                 
              DISPLAY 'ERROR OPENING TRANFILE'                                  
              MOVE TRANFILE-STATUS TO IO-STATUS                                 
              PERFORM 9910-DISPLAY-IO-STATUS                                    
              PERFORM 9999-ABEND-PROGRAM                                        
           END-IF                                                               
           EXIT.                                                                
                                                                                
       0100-TRANTYPE-OPEN.                                                      
           MOVE 8 TO APPL-RESULT                                                
           OPEN INPUT TRANTYPE-FILE                                             
           IF TRANTYPE-STATUS = '00'                                            
              MOVE 0 TO APPL-RESULT                                             
           ELSE                                                                 
              MOVE 12 TO APPL-RESULT                                            
           END-IF                                                               
           IF APPL-AOK                                                          
              CONTINUE                                                          
           ELSE                                                                 
              DISPLAY 'ERROR OPENING TRANSACTION TYPE FILE'                    
              MOVE TRANTYPE-STATUS TO IO-STATUS                                 
              PERFORM 9910-DISPLAY-IO-STATUS                                    
              PERFORM 9999-ABEND-PROGRAM                                        
           END-IF                                                               
           EXIT.                                                                
                                                                                
       0200-TRANCATG-OPEN.                                                      
           MOVE 8 TO APPL-RESULT                                                
           OPEN INPUT TRANCATG-FILE                                             
           IF TRANCATG-STATUS = '00'                                            
              MOVE 0 TO APPL-RESULT                                             
           ELSE                                                                 
              MOVE 12 TO APPL-RESULT                                             
           END-IF                                                               
           IF APPL-AOK                                                          
              CONTINUE                                                          
           ELSE                                                                 
              DISPLAY 'ERROR OPENING TRANSACTION CATEGORY FILE'                
              MOVE TRANCATG-STATUS TO IO-STATUS                                 
              PERFORM 9910-DISPLAY-IO-STATUS                                    
              PERFORM 9999-ABEND-PROGRAM                                        
           END-IF                                                               
           EXIT.                                                                
                                                                                
       0300-SUMMARY-OPEN.                                                       
           MOVE 8 TO APPL-RESULT                                                
           OPEN OUTPUT SUMMARY-FILE                                             
           IF SUMMARY-STATUS = '00'                                             
              MOVE 0 TO APPL-RESULT                                             
           ELSE                                                                 
              MOVE 12 TO APPL-RESULT                                            
           END-IF                                                               
           IF APPL-AOK                                                          
              CONTINUE                                                          
           ELSE                                                                 
              DISPLAY 'ERROR OPENING TRANSACTION SUMMARY FILE'                  
              MOVE SUMMARY-STATUS TO IO-STATUS                                  
              PERFORM 9910-DISPLAY-IO-STATUS                                    
              PERFORM 9999-ABEND-PROGRAM                                        
           END-IF                                                               
           EXIT.                                                                
                                                                                
       9000-TRANFILE-CLOSE.                                                     
           ADD 8 TO ZERO GIVING APPL-RESULT                                     
           CLOSE TRANSACT-FILE                                                  
           IF TRANFILE-STATUS = '00'                                            
              SUBTRACT APPL-RESULT FROM APPL-RESULT                             
           ELSE                                                                 
              ADD 12 TO ZERO GIVING APPL-RESULT                                 
           END-IF                                                               
           IF APPL-AOK                                                          
              CONTINUE                                                          
           ELSE                                                                 
              DISPLAY 'ERROR CLOSING TRANFILE'                                  
              MOVE TRANFILE-STATUS TO IO-STATUS                                 
              PERFORM 9910-DISPLAY-IO-STATUS                                    
              PERFORM 9999-ABEND-PROGRAM                                        
           END-IF                                                               
           EXIT.                                                                
                                                                                
       9100-TRANTYPE-CLOSE.                                                     
           MOVE 8 TO APPL-RESULT                                                
           CLOSE TRANTYPE-FILE                                                  
           IF TRANTYPE-STATUS = '00'                                            
              MOVE 0 TO APPL-RESULT                                             
           ELSE                                                                 
              MOVE 12 TO APPL-RESULT                                            
           END-IF                                                               
           IF APPL-AOK                                                          
              CONTINUE                                                          
           ELSE                                                                 
              DISPLAY 'ERROR CLOSING TRANSACTION TYPE FILE'                    
              MOVE TRANTYPE-STATUS TO IO-STATUS                                 
              PERFORM 9910-DISPLAY-IO-STATUS                                    
              PERFORM 9999-ABEND-PROGRAM                                        
           END-IF                                                               
           EXIT.                                                                
                                                                                
       9200-TRANCATG-CLOSE.                                                     
           MOVE 8 TO APPL-RESULT                                                
           CLOSE TRANCATG-FILE                                                  
           IF TRANCATG-STATUS = '00'                                            
              MOVE 0 TO APPL-RESULT                                             
           ELSE                                                                 
              MOVE 12 TO APPL-RESULT                                            
           END-IF                                                               
           IF APPL-AOK                                                          
              CONTINUE                                                          
           ELSE                                                                 
              DISPLAY 'ERROR CLOSING TRANSACTION CATEGORY FILE'                
              MOVE TRANCATG-STATUS TO IO-STATUS                                 
              PERFORM 9910-DISPLAY-IO-STATUS                                    
              PERFORM 9999-ABEND-PROGRAM                                        
           END-IF                                                               
           EXIT.                                                                
                                                                                
       9300-SUMMARY-CLOSE.                                                      
           MOVE 8 TO APPL-RESULT                                                
           CLOSE SUMMARY-FILE                                                   
           IF SUMMARY-STATUS = '00'                                             
              MOVE 0 TO APPL-RESULT                                             
           ELSE                                                                 
              MOVE 12 TO APPL-RESULT                                            
           END-IF                                                               
           IF APPL-AOK                                                          
              CONTINUE                                                          
           ELSE                                                                 
              DISPLAY 'ERROR CLOSING TRANSACTION SUMMARY FILE'                 
              MOVE SUMMARY-STATUS TO IO-STATUS                                  
              PERFORM 9910-DISPLAY-IO-STATUS                                    
              PERFORM 9999-ABEND-PROGRAM                                        
           END-IF                                                               
           EXIT.                                                                
                                                                                
      *****************************************************************         
       9910-DISPLAY-IO-STATUS.                                                  
           IF IO-STATUS NOT NUMERIC                                             
              OR IO-STAT1 = '9'                                                 
              MOVE IO-STAT1 TO IO-STATUS-04(1:1)                                
              MOVE 0 TO TWO-BYTES-BINARY                                        
              MOVE IO-STAT2 TO TWO-BYTES-RIGHT                                  
              MOVE TWO-BYTES-BINARY TO IO-STATUS-0403                           
              DISPLAY 'FILE STATUS IS: NNNN' IO-STATUS-04                       
           ELSE                                                                 
              MOVE '0000' TO IO-STATUS-04                                       
              MOVE IO-STATUS TO IO-STATUS-04(3:2)                               
              DISPLAY 'FILE STATUS IS: NNNN' IO-STATUS-04                       
           END-IF                                                               
           EXIT.                                                                
                                                                                
       9999-ABEND-PROGRAM.                                                      
           DISPLAY 'ABENDING PROGRAM'                                           
           MOVE 0 TO TIMING                                                     
           MOVE 999 TO ABCODE                                                   
           CALL 'CEE3ABD' USING ABCODE, TIMING                                  
           EXIT.                                                                
