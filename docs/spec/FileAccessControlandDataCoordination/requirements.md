# File Access Control and Data Coordination — Requirements

## Global Preconditions
- All operations require valid input data and appropriate authorization.
- Processing constraints and scheduling dependencies are documented in the Job Dependencies section.

## 1. Card Data Repository Initialization and Alternate Index Lifecycle
As a batch operations team, I want the card data store initialized with a clean alternate index structure so that card records can be efficiently retrieved by account ID during batch and online processing.

### Requirements

REQ-F-001: [Event-driven] When the batch job is initiated, the system shall close the credit card data file (CARDDAT, legacy: CARDDAT) and the credit card alternate index file (CARDAIX, legacy: CARDAIX) in the online transaction region to prevent online access during the batch window, and capture the operation output for audit.

REQ-F-002: [Event-driven] When the batch job begins execution after batch processing is complete, the system shall open the credit card data file and the credit card alternate index file in the online transaction region, transitioning both to open status for resumed online access.

REQ-F-003: [Ubiquitous] The system shall delete the existing alternate index on the primary credit card master keyed data store (legacy: AWS.M2.CARDDEMO.CARDDATA.VSAM.AIX) before creating a new one, ensuring a clean state prior to index definition.

REQ-F-004: [Ubiquitous] The system shall define a new alternate index on the credit card master keyed data store, keyed on account ID (positions 11–16 of the record), configured with non-unique key support to allow multiple card records to share the same account ID, and with automatic upgrade maintenance so the index is kept current whenever the base data store is updated.

REQ-F-005: [Ubiquitous] The system shall define an access path (legacy: AWS.M2.CARDDEMO.CARDDATA.VSAM.AIX.PATH) that routes queries through the credit card alternate index data store (legacy: AWS.M2.CARDDEMO.CARDDATA.VSAM.AIX) to enable alternate-key lookups on card records by account ID.

REQ-F-006: [Event-driven] When the card data repository is initialized, the system shall copy all card records from the sequential flat file containing credit card records (legacy: AWS.M2.CARDDEMO.CARDDATA.PS) into the primary credit card master keyed data store (legacy: AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS).

REQ-F-007: [Event-driven] When the card data repository is initialized, the system shall first delete any existing credit card master keyed data store to allow a fresh initialization before defining and populating the new store.

REQ-F-008: [Event-driven] When the card data repository population is complete, the system shall build an alternate index on the credit card master keyed data store to enable secondary key access by account ID.

### Non-Functional Requirements

REQ-N-001: [State-driven] While the credit card data file and credit card alternate index file are closed for batch processing, the system shall ensure online transactions cannot access either file until the files are explicitly reopened.

## 2. File Access Control and Data Coordination
As a batch operations team, I want a centralized file I/O dispatcher to route open, read, and close operations to the correct shared data store so that callers can access account, customer, cross-reference, and transaction data through a single, consistent interface.

### Requirements

REQ-F-009: [Event-driven] When the caller specifies the account data store (legacy: ACCTFILE) as the target and the operation type is open, the system shall open the credit card account master file (legacy: ACCOUNT-FILE) for input.

REQ-F-010: [Event-driven] When the caller specifies the account data store as the target and the operation type is keyed read, the system shall extract the key value at the specified key length, locate the matching account record by account ID (11-digit numeric), and return that record to the caller.

REQ-F-011: [Event-driven] When the caller specifies the account data store as the target and the operation type is close, the system shall close the account data store.

REQ-F-012: [Ubiquitous] The system shall return the account data store operation status code to the caller after every account file operation.

REQ-F-013: [Event-driven] When the caller specifies the customer data store (legacy: CUSTFILE) as the target and the operation type is open, the system shall open the customer master data store (legacy: CUST-FILE) for input.

REQ-F-014: [Event-driven] When the caller specifies the customer data store as the target and the operation type is keyed read, the system shall extract the key value at the specified key length, locate the matching customer record by customer ID (9-digit numeric), and return that record to the caller.

REQ-F-015: [Event-driven] When the caller specifies the customer data store as the target and the operation type is close, the system shall close the customer data store.

REQ-F-016: [Ubiquitous] The system shall return the customer data store operation status code to the caller after every customer file operation.

REQ-F-017: [Event-driven] When the caller specifies the card cross-reference data store (legacy: XREFFILE) as the target and the operation type is open, the system shall open the card-to-account cross-reference data store (legacy: XREF-FILE) for input.

REQ-F-018: [Event-driven] When the caller specifies the card cross-reference data store as the target and the operation type is sequential read, the system shall read the next sequential record from the card cross-reference data store and return it to the caller.

REQ-F-019: [Event-driven] When the caller specifies the card cross-reference data store as the target and the operation type is close, the system shall close the card cross-reference data store.

REQ-F-020: [Ubiquitous] The system shall return the card cross-reference data store operation status code to the caller after every cross-reference file operation.

REQ-F-021: [Event-driven] When the caller specifies the transaction data store (legacy: TRNXFILE) as the target and the operation type is open, the system shall open the transaction master data store (legacy: TRNX-FILE) for input.

REQ-F-022: [Event-driven] When the caller specifies the transaction data store as the target and the operation type is sequential read, the system shall read the next sequential record from the transaction data store and return it to the caller.

REQ-F-023: [Event-driven] When the caller specifies the transaction data store as the target and the operation type is close, the system shall close the transaction data store.

REQ-F-024: [Ubiquitous] The system shall return the transaction data store operation status code to the caller after every transaction file operation.

## 3. Close Transaction and Reference Files for Batch Processing
As a batch operations team, I want the transaction log, cross-reference index, account data, account index, and user security files closed to exclusive-access status in the online region before batch processing begins, so that data consistency is maintained and concurrent online access is prevented during batch operations.

### Requirements

REQ-F-025: [Ubiquitous] The system shall close the transaction master file (legacy: TRANSACT), the card-to-account cross-reference file (legacy: CCXREF), the credit card account data file (legacy: ACCTDAT), the card cross-reference alternate index file (legacy: CXACAIX), and the user security file (legacy: USRSEC) in the online region to exclusive-access status, preventing any concurrent online transactions from accessing these files while batch processing runs.

## 4. Customer Master Data Store Initialization and Batch Access Coordination
As a batch operations team, I want the customer master data store closed for interactive access, rebuilt from source, and reopened for exclusive batch processing so that batch jobs operate against a clean, consistent customer dataset without contention from online users.

### Requirements

REQ-F-026: [Event-driven] When the batch job reaches the file-open step, the system shall send an open command to the online transaction processing region for the customer data file (legacy: CUSTDAT) and capture the command output in system logs for operational verification.

REQ-F-027: [Event-driven] When the batch job is initiated to process customer data, the system shall close the customer data file in the online transaction processing region to make it available for exclusive batch processing.

REQ-F-028: [Event-driven] When the customer master data store lifecycle process executes, the system shall delete the existing customer master data store (legacy: AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS) if one is present, then define a new keyed customer master data store, and populate it with all customer records from the sequential flat-file source (legacy: AWS.M2.CARDDEMO.CUSTDATA.PS).

## 5. CICS File Availability Preparation for Batch Processing
As a batch operations team, I want the transaction log, cross-reference, account data, account index, and user security files opened in the CICS region before batch processing begins so that batch jobs can access these shared data resources without interference from online transactions.

### Requirements

REQ-F-029: [Event-driven] When the batch file-open job begins execution, the system shall open the transaction master file (legacy: TRANSACT), the credit card cross-reference file (legacy: CCXREF), the credit card account data file (legacy: ACCTDAT), the card cross-reference alternate index file (legacy: CXACAIX), and the user security file (legacy: USRSEC) in the CICS region to enable batch processing access.

REQ-F-030: [Event-driven] When the CICS file-open commands are issued, the system shall capture the operator command output for audit and verification purposes.

### Open Questions

OQ-001: The rule states files are opened "for batch processing access" and "without interference from online CICS transactions." Does this imply the files must be placed in an exclusive-access state that prevents concurrent online use, or is shared-read access acceptable during the batch window? — Owner: batch operations / architecture team

OQ-002: The modernization category is listed as "Unknown." In a modernized platform without a CICS region, what is the equivalent mechanism for coordinating exclusive data store access between batch and interactive processing? — Owner: architecture team

## 6. Transaction Master Data Store Preparation and Indexing
As a batch operations team, I want the transaction master data store initialized with a clean indexed structure and alternate index so that batch and online processes can efficiently retrieve transaction records by both primary key and processed timestamp.

### Requirements

REQ-F-031: [Ubiquitous] The system shall close the transaction master file (legacy: TRANSACT) and the card cross-reference alternate index file (legacy: CXACAIX) in the online transaction processing region prior to batch processing, ensuring exclusive batch access without contention from online processing.

REQ-F-032: [Event-driven] When the batch job requires exclusive access to the transaction master file and the card cross-reference alternate index file, the system shall open both files in the online transaction processing region, allowing batch processing to proceed while online access is suspended.

REQ-F-033: [Ubiquitous] The system shall remove any existing alternate index on the transaction master data store (legacy: AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX) before creating a new one; deletion errors shall not halt the job.

REQ-F-034: [Ubiquitous] The system shall create a new alternate index on the transaction master data store keyed on the processed timestamp field starting at byte position 26 with a length of 304 bytes, configured to allow duplicate key values and to automatically maintain synchronization whenever the base transaction cluster is updated.

REQ-F-035: [Ubiquitous] The system shall define a path that relates the transaction master alternate index to the transaction master data store base cluster, enabling indexed access to transaction records through the alternate key.

### Open Questions

OQ-003: Rule 4a5d6d5b specifies the alternate index key at positions 26–304 (length 304 bytes). This is an unusually large key length for a timestamp field. Confirmation is needed that the key offset and length are correct and intentional. — Owner: data architecture team

OQ-004: The noise_context rules (339265ed, 6889e048, cb708d62, d599201c) describe deletion of the existing transaction master file, definition of a new indexed file with 16-byte keys and 350-byte records, loading from a flat source file, and building an alternate index. These were classified as working storage initialization but appear to describe business-meaningful data lifecycle steps. Confirmation is needed whether these steps should generate formal requirements. — Owner: batch operations team