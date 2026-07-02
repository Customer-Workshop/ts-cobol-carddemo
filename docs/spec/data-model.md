# Data Model

*21 data entities across 8 capabilities.*

| Data Store | Type | Accessed By |
|-----------|------|-------------|
| AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS | VSAM KSDS | CustomerandAccountDataManagement, FileAccessControlandDataCoordination, InteractiveNavigationandMenuControl, StatementandReportGeneration, TransactionProcessingandValidation |
| AWS.M2.CARDDEMO.CARDDATA.VSAM.AIX.PATH | VSAM PATH | InteractiveNavigationandMenuControl |
| AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS | VSAM KSDS | CustomerandAccountDataManagement, InteractiveNavigationandMenuControl |
| AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX.PATH | VSAM PATH | InteractiveNavigationandMenuControl |
| AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS | VSAM KSDS | CustomerandAccountDataManagement, FileAccessControlandDataCoordination, InteractiveNavigationandMenuControl, StatementandReportGeneration, TransactionProcessingandValidation |
| AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS | VSAM KSDS | CustomerandAccountDataManagement, FileAccessControlandDataCoordination, InteractiveNavigationandMenuControl |
| AWS.M2.CARDDEMO.DALYREJS | QSAM | TransactionProcessingandValidation |
| AWS.M2.CARDDEMO.DALYTRAN.PS | Non VSAM | TransactionProcessingandValidation |
| AWS.M2.CARDDEMO.DATEPARM | QSAM | StatementandReportGeneration |
| AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS | VSAM KSDS | StatementandReportGeneration |
| AWS.M2.CARDDEMO.STATEMNT.HTML | QSAM | StatementandReportGeneration |
| AWS.M2.CARDDEMO.STATEMNT.PS | QSAM | StatementandReportGeneration |
| AWS.M2.CARDDEMO.SYSTRAN | GDG Base | StatementandReportGeneration |
| AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS | VSAM KSDS | StatementandReportGeneration, TransactionProcessingandValidation |
| AWS.M2.CARDDEMO.TRANCATG.VSAM.KSDS | VSAM KSDS | StatementandReportGeneration |
| AWS.M2.CARDDEMO.TRANREPT | GDG Base | StatementandReportGeneration |
| AWS.M2.CARDDEMO.TRANSACT.DALY | GDG Base | StatementandReportGeneration |
| AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS | VSAM KSDS | InteractiveNavigationandMenuControl, TransactionProcessingandValidation |
| AWS.M2.CARDDEMO.TRANTYPE.VSAM.KSDS | VSAM KSDS | StatementandReportGeneration |
| AWS.M2.CARDDEMO.TRXFL.VSAM.KSDS | VSAM-KSDS | FileAccessControlandDataCoordination |
| AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS | VSAM KSDS | InteractiveNavigationandMenuControl |
