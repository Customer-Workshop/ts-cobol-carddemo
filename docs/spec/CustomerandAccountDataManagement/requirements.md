# Customer and Account Data Management — Requirements

## Global Preconditions
- All operations require valid input data and appropriate authorization.
- Processing constraints and scheduling dependencies are documented in the Job Dependencies section.

## 1. Account File Sequential Retrieval and Display
As a batch operations team, I want all credit card account records retrieved sequentially and their details reported so that account data can be reviewed in full across the entire account master file.

**Data flow:** Reads the credit card account master file (ACCTFILE-FILE) sequentially from beginning to end; outputs account record details for each successfully retrieved record.

### Requirements

REQ-F-001: [Ubiquitous] The system shall open the Acctfile File data store (legacy: ACCTFILE-FILE) in sequential input mode before retrieving any account records.

REQ-F-002: [State-driven] While the end-of-file condition has not been reached, the system shall retrieve the next account record from the Acctfile File data store and, upon successful retrieval, output the full account record details.

REQ-F-003: [Event-driven] When a sequential read of the Acctfile File data store returns an end-of-file result (application result code 16), the system shall terminate sequential processing.

REQ-F-004: [Event-driven] When a sequential read of the Acctfile File data store returns an error result (application result code 12), the system shall terminate sequential processing.

REQ-F-005: [Event-driven] When an account record is successfully retrieved, the system shall output all account record fields — account identifier, active status, current balance, credit limit, cash credit limit, open date, expiration date, reissue date, current cycle credit, current cycle debit, and group identifier — each accompanied by a descriptive label.

## 2. Card File Sequential Read and Display
As a batch operations team, I want all credit card records read sequentially from the card master file and displayed so that the full card file contents can be reviewed in order.

### Requirements

REQ-F-006: [Ubiquitous] The system shall open the sequential credit card flat file (legacy: AWS.M2.CARDDEMO.CARDDATA.PS) in read-only input mode before retrieving any card records.

REQ-F-007: [State-driven] While file processing is not complete, the system shall repeatedly retrieve the next card record from the credit card flat file and, upon each successful retrieval, display that card record.

REQ-F-008: [Event-driven] When a request to retrieve the next card record is made, the system shall read the next record from the credit card flat file; if the read succeeds, the system shall set the application result to success (code 0).

REQ-F-009: [Event-driven] When the end of the credit card flat file is reached during a read attempt, the system shall set the application result to end-of-file (code 16) and mark file processing as complete.

REQ-F-010: [Unwanted] If an I/O error occurs while reading the credit card flat file, the system shall set the application result to error (code 12) and mark file processing as complete.

REQ-F-011: [Event-driven] When a card record is successfully retrieved from the credit card flat file, the system shall display that card record.

## 3. Card Cross-Reference Sequential Retrieval
As a batch operations team, I want all card-to-account cross-reference records retrieved sequentially and displayed so that the full contents of the cross-reference data store can be reported and verified.

### Requirements

REQ-F-012: [Ubiquitous] The system shall open the card-to-account cross-reference data store (Xreffile File data store, legacy: XREFFILE-FILE) in input mode before any sequential record retrieval begins.

REQ-F-013: [State-driven] While the end-of-file condition has not been reached, the system shall retrieve the next record from the card-to-account cross-reference data store and, upon successful retrieval, display the card cross-reference record containing the card number (16 characters), customer ID (9 digits), and account ID (11 digits).

REQ-F-014: [Event-driven] When a read operation against the card-to-account cross-reference data store completes, the system shall set the application result code to 0 if the read succeeds, to 16 if end-of-file is reached, and to 12 if an I/O error occurs.

REQ-F-015: [Event-driven] When the application result code is 16 (end-of-file), the system shall set the end-of-file flag to 'Y' to terminate sequential processing of the card-to-account cross-reference data store.

REQ-F-016: [Event-driven] When a cross-reference record is successfully retrieved (application result code 0), the system shall display the card cross-reference record.

## 4. Customer Master Sequential Retrieval
As a batch operations team, I want all customer master records retrieved sequentially and made available for downstream processing so that the full customer population can be inspected or consumed in order.

**Restart/Recovery:** This job performs read-only sequential access; no writes occur, so no rollback boundary applies. Restart requires re-reading from the beginning of the customer master data store.

### Requirements

REQ-F-017: [Ubiquitous] The system shall open the customer master data store (legacy: CUST-FILE) for sequential input access before any record retrieval begins.

REQ-F-018: [State-driven] While the end-of-file condition has not been reached, the system shall retrieve the next customer record from the customer master data store and display it, continuing until all records have been processed.

REQ-F-019: [Event-driven] When a customer record is successfully retrieved and the end-of-file condition has not been signalled, the system shall display the customer record — including customer ID, first name, middle name, last name, address lines, state code, country code, ZIP code, phone numbers, SSN, government-issued ID, date of birth, EFT account ID, primary card holder indicator, and FICO credit score.

REQ-F-020: [Event-driven] When the end-of-file condition is detected (application result code 16), the system shall set the end-of-file flag to terminate the retrieval loop.

REQ-F-021: [Event-driven] When a sequential read of the customer master data store results in a file error (application result code 12), the system shall set the application result code to 12 and cease further retrieval attempts.

REQ-F-022: [Event-driven] When a sequential read of the customer master data store completes successfully (application result code 0), the system shall retain the retrieved record for display and continue the retrieval loop.

## 5. Account Master File Read Operation
As a batch operations team, I want all credit card account master records read sequentially and their details reported so that account data can be verified and consumed by downstream processes.

**Data flow:** Reads the credit card account master file (keyed data store); writes account record details to output destinations.

### Requirements

REQ-F-023: [Ubiquitous] The system shall execute the account master read operation with access to the account master data store (legacy: ACCTFILE-FILE) and output destinations.

REQ-F-024: [Ubiquitous] The system shall open the account master data store in sequential input mode before retrieving any records.

REQ-F-025: [State-driven] While the end-of-file condition has not been reached, the system shall retrieve the next account record sequentially and, upon successful retrieval, output all account record fields with descriptive labels, including account identifier, active status, current balance, credit limit, cash credit limit, open date, expiration date, reissue date, current cycle credit, current cycle debit, and group identifier.

REQ-F-026: [Event-driven] When a sequential read returns an end-of-file result (application result code 16), the system shall set the end-of-file flag to 'Y' and terminate sequential processing.

REQ-F-027: [Event-driven] When a sequential read returns a file operation error (application result code 12), the system shall set the end-of-file flag to 'Y' and terminate sequential processing.

## 6. Card Master File Sequential Read
As a batch operations team, I want all credit card master records read sequentially and produced as output so that downstream processes have access to the full card master data set.

### Requirements

REQ-F-028: [Ubiquitous] The system shall open the primary credit card master data store (legacy: `AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS`) in input mode before retrieving any card records.

REQ-F-029: [State-driven] While file processing is not complete, the system shall retrieve the next record sequentially from the credit card master data store and, if the retrieval succeeds, produce the card record as output.

REQ-F-030: [Event-driven] When a sequential read of the credit card master data store succeeds, the system shall set the processing result to success (result code 0) and continue to the next record.

REQ-F-031: [Event-driven] When the end of the credit card master data store is reached, the system shall set the processing result to end-of-file (result code 16) and mark file processing as complete.

REQ-F-032: [Unwanted] If an I/O error occurs while reading the credit card master data store, the system shall set the processing result to error (result code 12) and mark file processing as complete.

REQ-F-033: [Event-driven] When a card record is successfully retrieved from the credit card master data store, the system shall write that card record to the designated output destination.

## 7. Customer Master File Read Operation
As a batch operations team, I want customer master records read sequentially and made available for downstream processing so that all customer data is retrieved and processed in full each time the job executes.

### Requirements

REQ-F-034: [Ubiquitous] The system shall execute the customer master read operation with read access to the primary VSAM KSDS storing customer master records (legacy: AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS) and with output destinations available for processing messages and diagnostics.

REQ-F-035: [Ubiquitous] The system shall open the Custdata data store for sequential input access before retrieving any customer records.

REQ-F-036: [State-driven] While the end-of-file condition has not been reached, the system shall retrieve the next customer record from the Custdata data store sequentially and make it available for output.

REQ-F-037: [Event-driven] When a customer record is successfully retrieved and the end-of-file condition has not been reached, the system shall display the customer record to output.

REQ-F-038: [Event-driven] When the sequential read operation returns an end-of-file result (application result code 16), the system shall set the end-of-file flag to 'Y' to terminate the retrieval loop.

REQ-F-039: [Event-driven] When a sequential read request is initiated, the system shall set the application result to 0 on successful read, to 16 when end of file is reached, and to 12 when a file error occurs.

## 8. Cross-Reference File Sequential Retrieval and Display
As a batch operations team, I want card cross-reference records read sequentially from the card-to-account cross-reference data store so that all cross-reference data is retrieved and made available for downstream processing.

### Requirements

REQ-F-040: [Ubiquitous] The system shall open the card-to-account cross-reference data store (legacy: XREFFILE-FILE) in input mode before any records are retrieved.

REQ-F-041: [State-driven] While the end-of-file condition has not been reached, the system shall retrieve the next record sequentially from the card-to-account cross-reference data store and output the card cross-reference record upon each successful retrieval.

REQ-F-042: [Event-driven] When a read operation against the card-to-account cross-reference data store returns a successful status, the system shall set the application result code to 0 (success).

REQ-F-043: [Event-driven] When a read operation against the card-to-account cross-reference data store returns an end-of-file status, the system shall set the application result code to 16 (end-of-file condition).

REQ-F-044: [Event-driven] When a read operation against the card-to-account cross-reference data store returns any status other than success or end-of-file, the system shall set the application result code to 12 (I/O error condition).

REQ-F-045: [Event-driven] When the application result code is 16, the system shall set the end-of-file flag to 'Y' to terminate sequential retrieval.