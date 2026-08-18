//TRANSUMM JOB 'TRANSACTION SUMMARY',                                            
// CLASS=A,MSGCLASS=0,NOTIFY=&SYSUID                                            
//******************************************************************
//* Copyright Amazon.com, Inc. or its affiliates.                   
//* All Rights Reserved.                                            
//*                                                                 
//* Licensed under the Apache License, Version 2.0 (the "License"). 
//* You may not use this file except in compliance with the License.
//* You may obtain a copy of the License at                         
//*                                                                 
//*    http://www.apache.org/licenses/LICENSE-2.0                   
//*                                                                 
//* Unless required by applicable law or agreed to in writing,      
//* software distributed under the License is distributed on an     
//* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,    
//* either express or implied. See the License for the specific     
//* language governing permissions and limitations under the License
//******************************************************************
//JOBLIB JCLLIB ORDER=('AWS.M2.CARDDEMO.PROC')                                  
//* *******************************************************************         
//* Unload the transaction KSDS to a fixed-length sequential data set            
//* *******************************************************************         
//STEP05T EXEC PROC=REPROC,                                                     
// CNTLLIB=AWS.M2.CARDDEMO.CNTL                                                 
//PRC001.FILEIN  DD DISP=SHR,                                                   
//        DSN=AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS                                
//PRC001.FILEOUT DD DISP=(NEW,CATLG,DELETE),                                    
//        UNIT=SYSDA,                                                           
//        DCB=(LRECL=350,RECFM=FB,BLKSIZE=0),                                   
//        SPACE=(CYL,(1,1),RLSE),                                               
//        DSN=AWS.M2.CARDDEMO.TRANSACT.BKUP(+1)                                
//* *******************************************************************         
//* Unload the transaction type KSDS to a fixed-length sequential data set      
//* *******************************************************************         
//STEP05Y EXEC PROC=REPROC,                                                     
// CNTLLIB=AWS.M2.CARDDEMO.CNTL                                                 
//PRC001.FILEIN  DD DISP=SHR,                                                   
//        DSN=AWS.M2.CARDDEMO.TRANTYPE.VSAM.KSDS                                
//PRC001.FILEOUT DD DISP=(NEW,CATLG,DELETE),                                    
//        UNIT=SYSDA,                                                           
//        DCB=(LRECL=60,RECFM=FB,BLKSIZE=0),                                    
//        SPACE=(CYL,(1,1),RLSE),                                               
//        DSN=AWS.M2.CARDDEMO.TRANTYPE.BKUP(+1)                                 
//* *******************************************************************         
//* Unload the transaction category KSDS to a fixed-length sequential data set  
//* *******************************************************************         
//STEP05C EXEC PROC=REPROC,                                                     
// CNTLLIB=AWS.M2.CARDDEMO.CNTL                                                 
//PRC001.FILEIN  DD DISP=SHR,                                                   
//        DSN=AWS.M2.CARDDEMO.TRANCATG.VSAM.KSDS                                
//PRC001.FILEOUT DD DISP=(NEW,CATLG,DELETE),                                    
//        UNIT=SYSDA,                                                           
//        DCB=(LRECL=60,RECFM=FB,BLKSIZE=0),                                    
//        SPACE=(CYL,(1,1),RLSE),                                               
//        DSN=AWS.M2.CARDDEMO.TRANCATG.BKUP(+1)                                 
//* *******************************************************************         
//* Produce the frozen transaction summary CSV                                   
//* *******************************************************************         
//STEP10R EXEC PGM=CBTRN04C                                                     
//STEPLIB  DD DISP=SHR,                                                         
//         DSN=AWS.M2.CARDDEMO.LOADLIB                                          
//SYSOUT   DD SYSOUT=*                                                          
//SYSPRINT DD SYSOUT=*                                                          
//* Input files                                                                 
//TRANFILE DD DISP=SHR,                                                         
//         DSN=AWS.M2.CARDDEMO.TRANSACT.BKUP(+1)                                
//TRANTYPE DD DISP=SHR,                                                         
//         DSN=AWS.M2.CARDDEMO.TRANTYPE.BKUP(+1)                                
//TRANCATG DD DISP=SHR,                                                         
//         DSN=AWS.M2.CARDDEMO.TRANCATG.BKUP(+1)                                
//* Output file                                                                 
//TRANSUMM DD DISP=(NEW,CATLG,DELETE),                                          
//         UNIT=SYSDA,                                                          
//         DCB=(LRECL=200,RECFM=VB,BLKSIZE=0),                                  
//         SPACE=(CYL,(1,1),RLSE),                                              
//         DSN=AWS.M2.CARDDEMO.TRANSUMM(+1)                                     
//                                                                              
//*
