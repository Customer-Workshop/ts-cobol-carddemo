# Transaction Processing and Validation — Requirements

## Global Preconditions
- All operations require valid input data and appropriate authorization.
- Processing constraints and scheduling dependencies are documented in the Job Dependencies section.

## 1. Daily Transaction Validation and Card Cross-Reference Processing
As a batch operations team, I want daily transaction records validated against card cross-reference and account master data so that only transactions with verified card numbers and existing accounts are processed, and unresolvable transactions are identified for investigation.

**Restart/Recovery:** The batch reads the daily transaction input file sequentially. No writes to persistent stores are described; the process is read-only against reference data.

### Requirements

REQ-F-001: [Ubiquitous] The system shall open the daily transaction input file (legacy: DALYTRAN-FILE), the card-to-account cross-reference file (legacy: XREF-FILE), and the credit card account master file (legacy: ACCOUNT-FILE) in read-only mode before processing any transaction records.

REQ-F-002: [State-driven] While daily transaction records remain to be processed, the system shall read the next record sequentially from the daily transaction input file, extract the payment card number, and submit it for cross-reference lookup.

REQ-F-003: [Event-driven] When a read of the daily transaction input file succeeds, the system shall set the application result to success (0) and continue processing.

REQ-F-004: [Event-driven] When a read of the daily transaction input file reaches end-of-file, the system shall set the application result to end-of-file (16) and mark daily transaction processing as complete, terminating the batch loop.

REQ-F-005: [Event-driven] When a read of the daily transaction input file encounters any condition other than success or end-of-file, the system shall set the application result to error (12).

REQ-F-006: [Event-driven] When a payment card number is submitted for cross-reference lookup, the system shall perform a keyed lookup against the card-to-account cross-reference file using the payment card number as the key, returning the associated account identifier and customer identifier when found, or a not-found status (4) when the card number does not exist in the cross-reference file.

REQ-F-007: [Event-driven] When the cross-reference lookup succeeds (read status equals 0), the system shall proceed to look up the account record in the credit card account master file using the account identifier retrieved from the cross-reference record.

REQ-F-008: [Event-driven] When the cross-reference lookup fails (read status not equal to 0), the system shall skip the account lookup for that transaction.

REQ-F-009: [Event-driven] When an account identifier lookup is requested against the credit card account master file, the system shall perform a keyed lookup using the account identifier as the key, returning the account record when found or a not-found status (4) when the account identifier does not exist.

REQ-F-010: [Unwanted] If the account record is not found in the credit card account master file (account read status not equal to 0), the system shall display an error message identifying the account identifier that could not be located.

REQ-F-011: [Event-driven] When a transaction record is successfully read and the card number is verified in the cross-reference file (read status equals 0), the system shall display the card number, account ID, and customer ID from the retrieved cross-reference record.

REQ-F-012: [Event-driven] When a transaction record is successfully read but the card number is not found in the cross-reference file (read status equals 4), the system shall display an error message showing the card number and transaction ID to indicate the transaction is being skipped.

## 2. Daily Transaction Validation, Rejection Handling, and Account Settlement
As a batch operations team, I want daily transaction records validated against card and account master data and either posted to update balances or captured as rejections so that account balances remain accurate and invalid transactions are traceable.

**Restart/Recovery:** The posting phase updates the account master data store, transaction category balance data store, and transaction archive data store in place. If interrupted, partial updates may exist with no automatic rollback described in the rules.

### Requirements

REQ-F-013: [Ubiquitous] The system shall open the daily transaction input file (legacy: DALYTRAN-FILE) for sequential input, the card cross-reference data store (legacy: CARDXREF) for random input, the account master data store (legacy: ACCTDATA) for read and update access, the transaction category balance data store (legacy: TCATBALF) for read and update access, the transaction archive data store (legacy: TRANSACT) for output, and the daily rejections output file (legacy: DALYREJS-FILE) for sequential output before processing begins.

REQ-F-014: [State-driven] While transaction records remain available in the daily transaction input file, the system shall read the next transaction record sequentially, validate it, and either post it or write it to the daily rejections output file based on the validation outcome; processing shall continue until end of file.

REQ-F-015: [Event-driven] When the next transaction record is read from the daily transaction input file, the system shall set the application result to 0 on a successful read, set the application result to 16 and the end-of-file flag to 'Y' when no more records exist, and set the application result to 12 on any other read error.

REQ-F-016: [Event-driven] When a transaction record is ready for validation, the system shall look up the card number (16-character alphanumeric) in the card cross-reference data store; if the card number is not found, the system shall set the validation failure reason to 100 with description 'INVALID CARD NUMBER FOUND'.

REQ-F-017: [Event-driven] When the card number is found in the card cross-reference data store, the system shall retrieve the associated account record from the account master data store using the account identifier (11-digit numeric) obtained from the cross-reference record; if the account record is not found, the system shall set the validation failure reason to 101 with description 'ACCOUNT RECORD NOT FOUND'.

REQ-F-018: [Event-driven] When the account record is found, the system shall compute the projected balance as current-cycle credits minus current-cycle debits plus the transaction amount; if the projected balance exceeds the account's credit limit, the system shall set the validation failure reason to 102 with description 'OVERLIMIT TRANSACTION'.

REQ-F-019: [Event-driven] When the account record is found and the credit limit check passes, the system shall compare the account expiration date against the transaction's original timestamp date (first 10 characters); if the account expiration date is earlier than the transaction date, the system shall set the validation failure reason to 103 with description 'TRANSACTION RECEIVED AFTER ACCT EXPIRATION'.

REQ-F-020: [Event-driven] When a transaction fails validation (validation failure reason is non-zero), the system shall assemble a reject record containing the original transaction data and the validation trailer (failure reason code and description), then write the reject record to the daily rejections output file.

REQ-F-021: [Event-driven] When a transaction passes all validation checks (validation failure reason is 0), the system shall copy all transaction fields from the daily transaction record — including transaction ID (16-character alphanumeric), type code (2-character alphanumeric), category code (4-digit numeric), source (10-character alphanumeric), description (100-character alphanumeric), amount (11-digit decimal), merchant ID (9-digit numeric), merchant name (50-character alphanumeric), merchant city (50-character alphanumeric), merchant ZIP (10-character alphanumeric), card number (16-character alphanumeric), and original timestamp (26-character alphanumeric) — to the transaction record, then obtain the current system date and time formatted as YYYY-MM-DD-HH.MM.SS.MIL0000 and store it as the processing timestamp.

REQ-F-022: [Event-driven] When a validated transaction is approved for posting, the system shall write the assembled transaction record to the transaction archive data store.

REQ-F-023: [Event-driven] When a validated transaction is approved for posting, the system shall add the transaction amount to the account's current balance; if the transaction amount is zero or positive, add it to the current-cycle credit total; if the transaction amount is negative, add it to the current-cycle debit total; then rewrite the updated account record to the account master data store.

REQ-F-024: [Event-driven] When a validated transaction is approved for posting, the system shall construct the transaction category balance lookup key from the account identifier (11-digit numeric), transaction type code (2-character alphanumeric), and category code (4-digit numeric), then attempt to read the existing transaction category balance record from the transaction category balance data store; if the record is not found, the system shall set the create flag to 'Y'; if the record is found, the system shall leave the create flag at 'N'.

REQ-F-025: [Event-driven] When the transaction category balance record does not exist for the account and category (create flag is 'Y'), the system shall initialize a new transaction category balance record populated with the account identifier, transaction type code, and category code from the current transaction, add the transaction amount to the category balance, and write the new record to the transaction category balance data store.

REQ-F-026: [Event-driven] When the transaction category balance record already exists for the account and category (create flag is 'N'), the system shall add the transaction amount to the existing category balance and rewrite the updated record to the transaction category balance data store.

## 3. Transaction Consolidation and Master File Loading
As a batch operations team, I want transaction records from backup and system-generated sources consolidated, sorted, and loaded into the transaction master file daily so that the master file contains a complete, correctly ordered set of transactions for downstream processing.

**Restart/Recovery:** The consolidation and sort phase produces a new version of the combined transaction dataset (legacy: AWS.M2.CARDDEMO.TRANSACT.COMBINED) and is re-runnable. The load phase writes records to the transaction master file (legacy: AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS) in place; if interrupted, partial writes may exist with no automatic rollback.

### Requirements

REQ-F-027: [Ubiquitous] The system shall consolidate transaction records from the transaction backup dataset (legacy: AWS.M2.CARDDEMO.TRANSACT.BKUP) and the system-generated transactions dataset (legacy: AWS.M2.CARDDEMO.SYSTRAN), sort the combined records by transaction identifier in ascending sequence, and write the sorted result to the combined transaction dataset (legacy: AWS.M2.CARDDEMO.TRANSACT.COMBINED).

REQ-F-028: [Event-driven] When the consolidation and sort phase completes, the system shall copy all records from the combined transaction dataset to the transaction master file (legacy: AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS), producing a complete and ordered master file for subsequent processing.

### Open Questions

OQ-001: Rules 5b239847 and ad899029 describe documentation of copyright, licensing, and step purposes as job-level requirements. It is unclear whether these represent a runtime compliance check, a metadata registration requirement, or purely static job comments. If there is a runtime or system-level obligation (e.g., storing metadata in a catalog or compliance store), the requirement should be elaborated. — Owner: compliance/operations team

## 4. Daily Transaction Posting and Master Update
As a batch operations team, I want daily transaction records validated against reference data and posted to the account master and transaction category balance files so that account balances and category totals accurately reflect each day's activity and invalid transactions are captured for investigation.

**Restart/Recovery:** The posting phase updates account records and transaction category balances in place. If interrupted, partial updates may exist with no automatic rollback.

### Requirements

REQ-F-029: [Ubiquitous] The system shall open the daily transaction input file (legacy: DALYTRAN-FILE) for sequential input, the card cross-reference file (legacy: XREF-FILE) for random input, the account master file (legacy: ACCOUNT-FILE) for read and update access, the transaction category balance file (legacy: TCATBAL-FILE) for read and update access, and the daily rejected transactions output file (legacy: DALYREJS-FILE) for sequential output before processing begins.

REQ-F-030: [Ubiquitous] The system shall open the transaction archive output file (legacy: TRANSACT-FILE) for output to receive approved transaction records.

REQ-F-031: [State-driven] While transaction records remain available in the daily transaction input file, the system shall read the next transaction record sequentially, validate it, and either post it or write it to the daily rejected transactions output file based on the validation outcome.

REQ-F-032: [Event-driven] When a transaction record is read successfully, the system shall set the application result to 0 (success); when no more records exist, the system shall set the application result to 16 and set the end-of-file flag to 'Y' to terminate processing; when any other read error occurs, the system shall set the application result to 12.

REQ-F-033: [Event-driven] When a transaction record is ready for validation, the system shall look up the card number in the card cross-reference file; if the card is not found, the system shall set the validation failure reason to 100 with description 'INVALID CARD NUMBER FOUND'.

REQ-F-034: [Event-driven] When the card number is found in the card cross-reference file, the system shall retrieve the account record from the account master file using the associated account identifier; if the account is not found, the system shall set the validation failure reason to 101 with description 'ACCOUNT RECORD NOT FOUND'.

REQ-F-035: [Event-driven] When the account record is found, the system shall compute the projected balance as current-cycle credits minus current-cycle debits plus the transaction amount; if the projected balance exceeds the account's credit limit, the system shall set the validation failure reason to 102 with description 'OVERLIMIT TRANSACTION'.

REQ-F-036: [Event-driven] When the account record is found and the credit limit check passes, the system shall compare the account expiration date against the transaction date (the date portion of the original transaction timestamp); if the account expiration date is earlier than the transaction date, the system shall set the validation failure reason to 103 with description 'TRANSACTION RECEIVED AFTER ACCT EXPIRATION'.

REQ-F-037: [Event-driven] When a transaction fails validation (failure reason is non-zero), the system shall assemble a reject record containing the original transaction data and the validation trailer (failure reason code and description), then write the reject record to the daily rejected transactions output file.

REQ-F-038: [Event-driven] When a transaction passes all validation checks (failure reason is 0), the system shall copy all transaction fields (transaction ID, type code, category code, source, description, amount, merchant ID, merchant name, merchant city, merchant ZIP, card number, and original timestamp) from the daily transaction record to the transaction record, obtain the current system date and time formatted as a DB2 timestamp (YYYY-MM-DD-HH.MM.SS.MIL0000), store it as the processing timestamp, and write the assembled transaction record to the transaction archive file.

REQ-F-039: [Event-driven] When a validated transaction is approved for posting, the system shall add the transaction amount to the account's current balance; if the transaction amount is zero or positive, the system shall add it to the current-cycle credit total; if the transaction amount is negative, the system shall add it to the current-cycle debit total; and rewrite the updated account record to the account master file.

REQ-F-040: [Event-driven] When a validated transaction is ready to be posted to the transaction category balance file, the system shall construct the lookup key from the account identifier, transaction type code, and transaction category code, then attempt to read the existing transaction category balance record; if the record is not found, the system shall set the create flag to 'Y'; if the record is found, the system shall leave the create flag at 'N'.

REQ-F-041: [Event-driven] When the transaction category balance record does not exist for the account, type, and category combination (create flag is 'Y'), the system shall initialize a new transaction category balance record populated with the account identifier, transaction type code, and category code, add the transaction amount to the category balance, and write the new record to the transaction category balance file.

REQ-F-042: [Event-driven] When the transaction category balance record already exists for the account, type, and category combination (create flag is 'N'), the system shall add the transaction amount to the existing category balance and rewrite the updated record to the transaction category balance file.

REQ-F-043: [Ubiquitous] The system shall obtain the current system date and time, extract each component (year, month, day, hour, minute, second, millisecond), and format it into DB2 timestamp format with hyphens separating date components, periods separating time components, and '0000' as the trailing field (YYYY-MM-DD-HH.MM.SS.MIL0000).

### Open Questions

OQ-002: The validation failure reason codes (100, 101, 102, 103) and descriptions are specified, but there is no rule describing what the system shall do if the transaction archive write fails or the account rewrite fails mid-batch. Should these conditions trigger a job abort, a rejection record, or another recovery action? — Owner: batch operations / modernization team

OQ-003: The rules describe the transaction category balance file being updated by this job and also read/written by a downstream job. Is there a sequencing or locking requirement to prevent concurrent access between this job and the downstream consumer? — Owner: batch operations / integration team

## Job Dependencies
Inferred from data flow (writer → reader via shared data store):

- CBTRN02C → POSTTRAN (via AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS)
- CBTRN02C → POSTTRAN (via AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS)
- POSTTRAN → CBTRN02C (via AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS)
- POSTTRAN → CBTRN02C (via AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS)
