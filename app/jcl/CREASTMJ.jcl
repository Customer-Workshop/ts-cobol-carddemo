//CREASTMJ JOB 'Create Statement JSON',CLASS=A,MSGCLASS=H,
//         MSGLEVEL=(1,1),NOTIFY=&SYSUID,TIME=1440
//******************************************************************
//* Create one JSON statement extract for every account in CARDXREF.
//* The input dataset wiring mirrors CREASTMT.JCL.
//******************************************************************
//DELDEF01 EXEC PGM=IDCAMS
//SYSPRINT DD  SYSOUT=*
//SYSIN    DD  *
  DELETE    AWS.M2.CARDDEMO.TRXFL.SEQ
  DELETE    AWS.M2.CARDDEMO.TRXFL.VSAM.KSDS                     -
            CLUSTER
  SET       MAXCC = 0
  DEFINE    CLUSTER  (NAME(AWS.M2.CARDDEMO.TRXFL.VSAM.KSDS)     -
                      KEYS(32 0)                                -
                      VOLUMES(TSU023)                           -
                      RECORDSIZE(350 350)                       -
                      SHAREOPTIONS(2 3)                         -
                      ERASE                                     -
                      INDEXED                                   -
                      CYL(1 5))                                 -
            DATA      (NAME(AWS.M2.CARDDEMO.TRXFL.DATA)         -
                      CISZ(4096))                               -
            INDEX     (NAME(AWS.M2.CARDDEMO.TRXFL.INDEX))
/*
//STEP010  EXEC PGM=SORT
//SORTIN   DD  DISP=SHR,DSN=AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS
//SYSPRINT DD  SYSOUT=*
//SYSOUT   DD  SYSOUT=*
//SORTOUT  DD  DSN=AWS.M2.CARDDEMO.TRXFL.SEQ,
//             DISP=(NEW,CATLG,DELETE),UNIT=SYSDA,
//             DCB=(LRECL=350,BLKSIZE=3500,RECFM=FB),
//             SPACE=(CYL,(1,1),RLSE)
//SYSIN    DD *
  SORT FIELDS=(263,16,CH,A,1,16,CH,A)
  OUTREC FIELDS=(1:263,16,17:1,262,279:279,50)
/*
//STEP020  EXEC PGM=IDCAMS,COND=(0,NE)
//SYSPRINT DD  SYSOUT=*
//INFILE   DD  DISP=SHR,DSN=AWS.M2.CARDDEMO.TRXFL.SEQ
//OUTFILE  DD  DISP=SHR,DSN=AWS.M2.CARDDEMO.TRXFL.VSAM.KSDS
//SYSIN    DD  *
  REPRO INFILE(INFILE) OUTFILE(OUTFILE)
/*
//STEP030  EXEC PGM=IEFBR14,COND=(0,NE)
//JSONFILE DD DISP=(MOD,DELETE,DELETE),
//         UNIT=SYSDA,
//         DCB=(LRECL=400,BLKSIZE=4000,RECFM=FB),
//         SPACE=(CYL,(1,1),RLSE),
//         DSN=AWS.M2.CARDDEMO.STATEMNT.JSON
//STEP040  EXEC PGM=CBSTM04C,COND=(0,NE)
//STEPLIB  DD  DISP=SHR,DSN=AWS.M2.CARDDEMO.LOADLIB
//SYSPRINT DD  SYSOUT=*
//SYSOUT   DD  SYSOUT=*
//TRNXFILE DD  DISP=SHR,DSN=AWS.M2.CARDDEMO.TRXFL.VSAM.KSDS
//XREFFILE DD  DISP=SHR,DSN=AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS
//ACCTFILE DD  DISP=SHR,DSN=AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS
//CUSTFILE DD  DISP=SHR,DSN=AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS
//JSONFILE DD DISP=(NEW,CATLG,DELETE),
//         UNIT=SYSDA,
//         DCB=(LRECL=400,BLKSIZE=4000,RECFM=FB),
//         SPACE=(CYL,(1,1),RLSE),
//         DSN=AWS.M2.CARDDEMO.STATEMNT.JSON
//*
