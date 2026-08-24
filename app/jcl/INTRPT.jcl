//INTRPT JOB 'INTEREST ANALYTICS',CLASS=A,MSGCLASS=0,
//   NOTIFY=&SYSUID
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
//* *******************************************************************
//* Produce the interest and fees analytics CSV extract.
//* *******************************************************************
//STEP15 EXEC PGM=CBACT05C
//STEPLIB  DD DISP=SHR,
//            DSN=AWS.M2.CARDDEMO.LOADLIB
//SYSPRINT DD SYSOUT=*
//SYSOUT   DD SYSOUT=*
//TCATBALF DD DISP=SHR,
//         DSN=AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS
//ACCTFILE DD DISP=SHR,
//         DSN=AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS
//DISCGRP  DD DISP=SHR,
//         DSN=AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS
//INTRPT   DD DISP=(NEW,CATLG,DELETE),
//         UNIT=SYSDA,
//         DCB=(RECFM=FB,LRECL=120,BLKSIZE=0),
//         SPACE=(CYL,(1,1),RLSE),
//         DSN=AWS.M2.CARDDEMO.INTRPT.PS
//*
