# Statement and Report Generation — Requirements

## Global Preconditions
- All operations require valid input data and appropriate authorization.
- Processing constraints and scheduling dependencies are documented in the Job Dependencies section.

## 1. Interest Calculation and Account Settlement Processing
As a batch operations team, I want monthly interest charges computed and posted to each credit card account so that account balances reflect accrued interest and system-generated interest transactions are recorded for downstream statement and reporting use.

**Restart/Recovery:** The processing loop updates the account data store and writes interest transactions at account boundaries. If interrupted mid-run, partial updates may exist with no automatic rollback described in the rules.

### Requirements

REQ-F-001: [Ubiquitous] The system shall open the transaction category balance file (legacy: TCATBAL-FILE) in input mode, the account data store (legacy: ACCTFILE-FILE) in input-output mode, the card cross-reference data store (legacy: XREF-FILE) in input mode, the disclosure group data store (legacy: DISCGRP-FILE) in input mode, and the system transaction file (legacy: TRNX-FILE) in output mode before beginning interest calculation processing.

REQ-F-002: [State-driven] While transaction category balance records remain available in the transaction category balance file, the system shall read each record sequentially, detect account boundaries by comparing the current account ID with the previously processed account ID, and process each record for interest calculation.

REQ-F-003: [Event-driven] When a new account ID is encountered during transaction category balance processing, the system shall read the account master record from the account data store using the account ID as the key.

REQ-F-004: [Event-driven] When a new account ID is encountered during transaction category balance processing, the system shall read the card cross-reference record from the card cross-reference data store (legacy: CARDXREF) using the account ID as the key, to obtain the card number associated with the account.

REQ-F-005: [Complex] While the account group ID, transaction type code, and transaction category code have been set, when the interest rate lookup is requested for the current transaction category, the system shall read the disclosure group record from the disclosure group data store using the composite key of account group ID, transaction type code, and transaction category code; if the record is not found, the system shall retry the lookup using the account group ID value 'DEFAULT' combined with the same transaction type code and transaction category code.

REQ-F-006: [Event-driven] When the specific account group interest rate is not found in the disclosure group data store, the system shall read the disclosure group record using the account group ID 'DEFAULT' combined with the transaction type code and transaction category code as the composite key.

REQ-F-007: [Event-driven] When an applicable interest rate is found for the transaction category, the system shall compute the monthly interest charge as (transaction category balance × interest rate) / 1200, add the monthly interest to the accumulated total interest for the account, and write the resulting interest transaction record to the system transaction file.

REQ-F-008: [Event-driven] When monthly interest is computed for a transaction category, the system shall generate a unique transaction ID by concatenating the processing date with an incremented six-digit suffix; set the transaction type code to '01'; set the transaction category code to '05'; set the transaction source to 'System'; set the transaction description to 'Int. for a/c ' followed by the account ID; set the transaction amount to the computed monthly interest; clear all merchant fields (merchant ID, merchant name, merchant city, merchant ZIP); populate the card number from the card cross-reference record; set both the original timestamp and processed timestamp to the current date and time formatted as a DB2 timestamp; and write the completed record to the system transaction file.

REQ-F-009: [Ubiquitous] The system shall format the current system date and time as a DB2 timestamp by parsing year, month, day, hour, minute, second, and millisecond components, using hyphens as date separators, periods as time separators, and setting the fractional seconds remainder field to '0000'.

REQ-F-010: [Event-driven] When an account boundary is detected or end-of-file is reached, the system shall add the accumulated interest to the account's current balance, reset the account's current cycle credit to zero, reset the account's current cycle debit to zero, and rewrite the updated account record to the account data store.

REQ-F-011: [Complex] While the transaction category balance file is open, when the next record is requested and no more records are available, the system shall set the end-of-file flag to 'Y' to terminate processing.

REQ-F-012: [Complex] While the transaction category balance file is open, when a file I/O error occurs on read, the system shall set the processing result to an error state.

### Open Questions

OQ-001: REQ-F-010 states that cycle credit and cycle debit are reset to zero at account boundary. It is unclear whether this reset should occur only when accumulated interest is non-zero, or unconditionally for every account encountered. — Owner: business rules / product owner

OQ-002: REQ-F-008 specifies transaction type code '01' and category code '05' as magic numbers for interest postings. Confirmation is needed that these codes are stable reference values and not subject to configuration. — Owner: product owner / reference data team

OQ-003: The processing date used to construct the transaction ID (REQ-F-008) is described as a "processing date parameter." It is unclear whether this is sourced from the date parameter file (legacy: DATEPARM) or from the system date. — Owner: batch operations / product owner

## 2. Account Statement Generation
As a batch operations team, I want account statements generated for all cardholders by reading transaction, cross-reference, customer, and account data so that each customer receives a complete, formatted statement reflecting their account activity.

**Restart/Recovery:** The statement generation process reads input files sequentially and writes output files. No explicit rollback or commit boundaries are described in the rules; partial output may exist if the job is interrupted.

### Requirements

REQ-F-013: [Ubiquitous] The system shall open the transaction file (Trnx File data store, legacy: TRNXFILE), cross-reference file (Xref File data store, legacy: XREFFILE), customer file (Custfile File data store, legacy: CUSTFILE), and account file (Acctfile File data store, legacy: ACCTFILE) for input in sequence before processing any statements, delegating each open operation to the file handler service.

REQ-F-014: [Ubiquitous] The system shall open the statement output file (Stmt File data store, legacy: STMT-FILE) and the HTML statement output file (Html File data store, legacy: HTML-FILE) for writing before processing any statements.

REQ-F-015: [State-driven] While transaction records remain available in the transaction file, the system shall read each transaction record sequentially, group transactions by card number, store each transaction's ID and data in an in-memory transaction table, and accumulate a transaction count per card group.

REQ-F-016: [Event-driven] When the card number on a transaction record differs from the previously saved card number, the system shall save the current transaction count to the transaction counter table, increment the credit count, and reset the transaction count before storing the new transaction.

REQ-F-017: [Event-driven] When end-of-file is reached during transaction reading, the system shall save the final transaction count to the transaction counter table and signal readiness to begin cross-reference file reading.

REQ-F-018: [State-driven] While cross-reference records remain available in the cross-reference file, the system shall retrieve each cross-reference record sequentially; for each record, retrieve the associated customer record by customer ID via keyed lookup and the associated account record by account ID via keyed lookup; then generate and write the formatted statement for that customer and account.

REQ-F-019: [Event-driven] When a cross-reference record is successfully retrieved, the system shall extract the customer ID and account ID from the cross-reference record and use them as keys to retrieve the corresponding customer record and account record from their respective data stores.

REQ-F-020: [Event-driven] When a customer record is requested using the customer ID from the cross-reference record, the system shall retrieve the customer record containing the customer's name, address, and FICO credit score via keyed lookup from the customer file.

REQ-F-021: [Event-driven] When an account record is requested using the account ID from the cross-reference record, the system shall retrieve the account record containing the account ID and current balance via keyed lookup from the account file.

REQ-F-022: [Event-driven] When end-of-file is reached on the cross-reference file, the system shall set the end-of-file flag to 'Y' to terminate the main statement generation loop.

REQ-F-023: [Event-driven] When a statement is to be generated for the current customer and account, the system shall assemble the customer name from first, middle, and last name fields; copy the customer address lines; and populate the account details (account ID, current balance, FICO score) into the statement-line structures.

REQ-F-024: [Ubiquitous] The system shall write the HTML document structure, head section, body opening, table opening, and account ID header row to the HTML statement output file for each statement.

REQ-F-025: [Ubiquitous] The system shall format and write the customer name, address lines, account ID, current balance, and FICO score as HTML-formatted detail rows to the HTML statement output file, followed by the transaction summary section header and column headers.

REQ-F-026: [State-driven] While transactions for the current card number exist in the in-memory transaction table, the system shall iterate through the table, format each matching transaction's ID, description, and amount into an HTML table row, and write each row to the HTML statement output file.

REQ-F-027: [Event-driven] When a transaction detail row is written, the system shall move the transaction ID, description, and amount to the statement-line structures and format each field into HTML paragraph tags before writing the complete transaction row to the output file.

REQ-F-028: [State-driven] While transactions for the current card number exist in the in-memory transaction table, the system shall write each transaction detail line (transaction ID, description, and amount) to the statement output file and accumulate the transaction amount into the running total for the current statement.

REQ-F-029: [Event-driven] When all transactions for the current card have been written, the system shall move the accumulated total transaction amount to the statement line, write the total line to the statement output file, write the statement footer line, and write the closing HTML tags (table row close, table cell close, table close, body close, HTML close) to the HTML statement output file.

REQ-F-030: [State-driven] While the statement generation loop is active, the system shall reset the transaction total accumulator to zero before processing transactions for each new card.

REQ-F-031: [Event-driven] When the file handler service is invoked, the system shall route the request to the appropriate file handler (transaction, cross-reference, customer, or account) based on the target file identifier, and return the file operation status code to the caller.

REQ-F-032: [Event-driven] When the operation type is sequential read, the system shall read the next sequential record from the specified file and return it to the caller along with the file status code.

REQ-F-033: [Event-driven] When the operation type is keyed read, the system shall extract the key value using the specified key length, locate the matching record in the specified file, return the record to the caller, and return the file status code.

REQ-F-034: [Event-driven] When the operation type is open, the system shall open the specified file for input and return the file status code to the caller.

REQ-F-035: [Event-driven] When the operation type is close, the system shall close the specified file and return the file status code to the caller.

REQ-F-036: [Event-driven] When all cross-reference records have been processed, the system shall close the transaction file, cross-reference file, customer file, and account file in sequence by delegating each close operation to the file handler service.

REQ-F-037: [Event-driven] When a sequential read of the transaction file returns a status code of '00', the system shall treat the record as successfully retrieved and continue reading. When the status code is '10', the system shall invoke the exit handler to terminate the transaction read loop.

REQ-F-038: [Event-driven] When a sequential read of the cross-reference file returns a status code of '00', the system shall move the record data into the cross-reference record structure for processing. When the status code is '10', the system shall set the end-of-file flag to 'Y'.

### Open Questions

OQ-004: The rules describe writing to both a statement output file (Stmt File data store) and an HTML statement output file (Html File data store). It is unclear whether both outputs are always produced in a single execution or whether they are produced by separate execution modes. — Owner: business/architecture team

OQ-005: The rules do not describe error handling when a keyed lookup for a customer or account record fails to find a matching record (i.e., a cross-reference record references a non-existent customer or account). Should such records be skipped, written to a reject file, or cause the job to abort? — Owner: business/architecture team

OQ-006: The rules do not specify the maximum size of the in-memory transaction table. If the number of transactions exceeds the table capacity, the behavior is undefined. — Owner: architecture team

## 3. Daily Transaction Report Generation
As a batch operations team, I want daily transaction records enriched with account and category information and written to a formatted report so that operations staff can review all transactions with full descriptive context, running totals, and summary lines.

### Requirements

REQ-F-039: [Ubiquitous] The system shall open the daily transaction working file (legacy: TRANSACT-FILE) for sequential input, the transaction report output file (legacy: REPORT-FILE) for output, the card cross-reference file (legacy: XREF-FILE) for random input, the transaction type reference file (legacy: TRANTYPE-FILE) for random input, the transaction category reference file (legacy: TRANCATG-FILE) for random input, and the date parameter file (legacy: DATE-PARMS-FILE) for sequential input before beginning batch processing.

REQ-F-040: [Event-driven] When the date parameter file is read, the system shall evaluate the file status and set the application result code to 0 for success, 16 for end-of-file, or 12 for error; if end-of-file or error occurs, the system shall set the end-of-file flag to 'Y' to terminate processing.

REQ-F-041: [Event-driven] When the first transaction record is processed, the system shall set the first-time flag to 'N', populate the report header with the start date and end date read from the date parameter file, and write the report header lines to the transaction report output file.

REQ-F-042: [Ubiquitous] The system shall write four header lines to the transaction report output file: the report-name header containing the report name and date range, a blank line, the column-header line, and a separator line of dashes.

REQ-F-043: [State-driven] While the end-of-file flag is 'N', the system shall read the next transaction record from the daily transaction working file, perform control-break logic when the card number changes, execute cross-reference, transaction type, and transaction category lookups to enrich the transaction record, and write the formatted transaction detail line to the transaction report output file.

REQ-F-044: [Event-driven] When the next transaction record is read from the daily transaction working file, the system shall evaluate the file status and set the application result code to 0 for success, 16 for end-of-file, or 12 for error; if end-of-file or error occurs, the system shall set the end-of-file flag to 'Y'.

REQ-F-045: [Event-driven] When the transaction's card number differs from the previously stored card number, the system shall, if not the first transaction, write the account totals for the previous card number, then update the current card number to the new transaction's card number and prepare for cross-reference lookup.

REQ-F-046: [Event-driven] When a new card number is encountered, the system shall move the card number to the cross-reference file key and read the cross-reference record from the Cardxref data store (legacy: XREF-FILE) using the card number as the key to retrieve the associated account ID.

REQ-F-047: [Ubiquitous] The system shall move the transaction type code to the transaction type file key and read the transaction type record from the Trantype data store (legacy: TRANTYPE-FILE) using the type code as the key to retrieve the associated type description.

REQ-F-048: [Ubiquitous] The system shall move the transaction type code and category code to the transaction category file composite key and read the transaction category record from the Trancatg data store (legacy: TRANCATG-FILE) using the composite key to retrieve the associated category description.

REQ-F-049: [Ubiquitous] The system shall populate the transaction detail report line with the transaction ID, account ID, type code, type description, category code, category description, source, and formatted transaction amount, then write the assembled line to the transaction report output file.

REQ-F-050: [Ubiquitous] The system shall add the transaction amount to both the page total and the account total for each transaction record processed.

REQ-F-051: [Event-driven] When the line counter reaches a multiple of 20 lines, the system shall write the page totals to the transaction report output file and write a new set of report headers (report name, blank line, and column headers) to begin the next page.

REQ-F-052: [Ubiquitous] The system shall move the page total to the formatted page-total field, write the page-total summary line to the transaction report output file, add the page total to the grand total, reset the page total to zero, write a separator line of dashes to the transaction report output file, and increment the line counter after each write.

REQ-F-053: [Event-driven] When the card number changes (control break occurs), the system shall move the account total to the formatted account-total field, write the account-total summary line to the transaction report output file, reset the account total to zero, and write a separator line of dashes to the transaction report output file.

REQ-F-054: [Event-driven] When end-of-file is reached on the daily transaction working file, the system shall add the final transaction amount to the page total and account total, write the page totals to the transaction report output file, and write the grand totals to the transaction report output file.

REQ-F-055: [Event-driven] When end-of-file is reached and all transactions have been processed, the system shall move the grand total to the formatted grand-total field and write the grand-total summary line to the transaction report output file.

REQ-F-056: [Ubiquitous] The system shall write each formatted report line to the transaction report output file (legacy: REPORT-FILE).

## 4. Card Transaction Statement Generation Pipeline
As a batch operations team, I want card transaction data sorted, indexed, and used to generate formatted account statements so that customers receive accurate text and HTML statements reflecting their transaction activity.

**Restart/Recovery:** The pipeline deletes prior output files before creating new ones. If a prior file does not exist at deletion time, the deletion is skipped without error. Each phase is conditional on the successful completion of the preceding phase.

### Requirements

REQ-F-057: [Ubiquitous] The system shall delete the sequential transaction file (legacy: AWS.M2.CARDDEMO.TRANSACT.DALY) and the indexed transaction store (legacy: AWS.M2.CARDDEMO.TRXFL.VSAM.KSDS), including all associated data and index components, to prepare for fresh dataset creation; if either store does not exist, the deletion shall be skipped without error.

REQ-F-058: [Ubiquitous] The system shall define a new indexed transaction store configured with a 32-byte key starting at offset 0, fixed-length records of 350 bytes, shared read access (share option 2), shared write access (share option 3), and automatic erasure on deletion.

REQ-F-059: [Ubiquitous] The system shall sort transaction records from the existing transaction master store (legacy: AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS) by card number (16 bytes at offset 263) ascending, then by transaction ID (16 bytes at offset 1) ascending, reformat each record to place the card number in the first 16 bytes, the transaction ID in bytes 17–262, and the remaining 50 bytes of transaction data in bytes 279–328, and write the reformatted records to a sequential output file with fixed-length 350-byte records.

REQ-F-060: [Event-driven] When the sort step completes successfully, the system shall copy the sorted transaction records from the sequential output file into the indexed transaction store.

REQ-F-061: [Event-driven] When the copy step completes successfully, the system shall delete the prior HTML statement output file (legacy: AWS.M2.CARDDEMO.STATEMNT.HTML) and the prior text statement output file (legacy: AWS.M2.CARDDEMO.STATEMNT.PS) to prepare for fresh report generation; if either file does not exist, the deletion shall be skipped without error.

REQ-F-062: [Event-driven] When the statement file cleanup step completes successfully, the system shall invoke the statement generation process to read transaction records from the indexed transaction store, retrieve card reference data from the card cross-reference store (legacy: AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS), retrieve account details from the account master store (legacy: AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS), and retrieve customer information from the customer master store (legacy: AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS), producing a text statement output file and an HTML statement output file.

REQ-F-063: [Ubiquitous] The system shall open the transaction file, cross-reference file, customer file, and account file in sequence before beginning statement generation processing.

REQ-F-064: [Ubiquitous] The system shall open the statement output file for writing before any statement lines are produced.

REQ-F-065: [Ubiquitous] The system shall read transaction records from the transaction file, group them by card number, and store each transaction's ID and data in an in-memory transaction table; when end-of-file is reached, the system shall finalize the transaction table and prepare to begin cross-reference file processing.

REQ-F-066: [Event-driven] When end-of-file is reached during transaction reading, the system shall save the final transaction count for the last card group and signal readiness to begin cross-reference file processing.

REQ-F-067: [State-driven] While cross-reference records remain available, the system shall retrieve each cross-reference record sequentially; when a record is successfully retrieved, the system shall perform a keyed lookup of the associated customer record using the customer ID from the cross-reference record and a keyed lookup of the associated account record using the account ID from the cross-reference record.

REQ-F-068: [Complex] While cross-reference records remain available, when a cross-reference record is successfully retrieved, the system shall assemble the customer name from first, middle, and last name fields; copy the customer address lines; populate account details including account ID, current balance, and FICO credit score; and write the formatted statement header and customer details to the output file.

REQ-F-069: [Ubiquitous] The system shall write the HTML document structure, head section, body opening, table opening, and account ID header row to the HTML output file for each statement.

REQ-F-070: [Ubiquitous] The system shall format and write the customer name, address lines, account ID, current balance, FICO credit score, and transaction summary section header and column headers to the statement output.

REQ-F-071: [State-driven] While transactions for the current card number exist in the in-memory transaction table, the system shall iterate through the table, format each matching transaction's ID, description, and amount into a detail line, write the detail line to the output file, and accumulate the transaction amount into the running total.

REQ-F-072: [Event-driven] When a transaction detail line is to be written, the system shall move the transaction ID, description, and amount to the statement line buffer and write the formatted line to the output file.

REQ-F-073: [State-driven] While transactions for the current card number have been written, the system shall move the accumulated total to the statement line and write the total line and statement footer to the output file.

REQ-F-074: [State-driven] While transactions for the current card number exist in the in-memory transaction table, the system shall write each matching transaction as an HTML table row containing the transaction ID, description, and amount formatted in HTML paragraph tags, and after all transactions are written, write the closing HTML tags to the output file.

REQ-F-075: [Event-driven] When a cross-reference record read returns end-of-file, the system shall set the end-of-file flag to 'Y' to terminate the statement generation loop.

REQ-F-076: [Ubiquitous] The system shall close the transaction file, cross-reference file, customer file, and account file upon completion of all statement generation processing.

## 5. Interest and Fee Calculation
As a batch operations team, I want interest charges and applicable fees computed for every account's transaction category balances daily so that account balances reflect the correct interest postings and downstream statement generation receives accurate transaction data.

**Restart/Recovery:** The job reads the transaction category balance data store sequentially and writes computed interest transactions to a new version of the transaction data store. Account records are rewritten in place with updated balances; if the job is interrupted, partial account updates may exist with no automatic rollback.

### Requirements

REQ-F-077: [Event-driven] When the interest and fee calculation job is submitted with business date 2022-07-18, the system shall invoke the interest calculation process to read all transaction category balance records from the transaction category balance data store (legacy: `AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS`) and compute interest charges and fees for each account, writing the results to a new version of the transaction data store (legacy: `AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS`).

REQ-F-078: [State-driven] While transaction category balance records remain available in the transaction category balance data store, the system shall read each record sequentially, detect account boundaries by comparing the current account ID with the previously processed account ID, and process each account's records as a group.

REQ-F-079: [Event-driven] When a new account ID is encountered during transaction category balance processing, the system shall read the account master record from the credit card account master file (legacy: `AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS`) using the account ID as the key.

REQ-F-080: [Event-driven] When a new account ID is encountered during transaction category balance processing, the system shall read the card cross-reference record from the card cross-reference data store (legacy: `AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS`) using the account ID as the key, to obtain the card number associated with the account.

REQ-F-081: [Event-driven] When the interest rate lookup is requested for the current transaction category, the system shall read the discount group record from the disclosure group data store (legacy: `AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS`) using the composite key of account group ID, transaction type code, and transaction category code.

REQ-F-082: [Event-driven] When the specific account group interest rate is not found in the disclosure group data store, the system shall retry the lookup using the account group ID value 'DEFAULT' combined with the transaction type code and transaction category code to obtain the default interest rate.

REQ-F-083: [Event-driven] When an applicable interest rate is found for a transaction category, the system shall compute the monthly interest charge as (transaction category balance × interest rate) / 1200 and add the result to the accumulated total interest for the account.

REQ-F-084: [Event-driven] When monthly interest is computed for a transaction category, the system shall write an interest transaction record to the transaction data store with: a unique transaction ID formed by concatenating the processing date with an incremented six-digit suffix; transaction type code '01' (interest posting); category code '05' (interest charges); source set to 'System'; description constructed as 'Int. for a/c ' followed by the account ID; amount set to the computed monthly interest; merchant fields cleared; card number populated from the card cross-reference record; and original and processed timestamps set to the current date and time in DB2 timestamp format.

REQ-F-085: [Event-driven] When an account boundary is detected or end-of-file is reached, the system shall add the accumulated interest to the account's current balance, reset the current cycle credit to zero, reset the current cycle debit to zero, and rewrite the updated account record to the credit card account master file.

REQ-F-086: [Ubiquitous] The system shall format the current system date and time into DB2 timestamp format by parsing year, month, day, hour, minute, second, and millisecond components, using hyphens as date separators, periods as time separators, and setting the rest field to '0000'.

REQ-F-087: [State-driven] While the transaction category balance data store is open, when a file I/O error other than end-of-file is encountered during a read, the system shall set the processing result to an error state (indicator 12).

### Non-Functional Requirements

REQ-N-001: [Event-driven] When the account balance update (accumulated interest addition, cycle credit reset, cycle debit reset, and account record rewrite) is performed for an account, the system shall treat these field updates as a single atomic operation so that no account record is partially updated.

## 6. Transaction Report Generation and Filtering
As a batch operations team, I want processed transaction records extracted, filtered, backed up, and formatted into an enriched report so that stakeholders can review card activity within a defined date range with full reference data context.

**Restart/Recovery:** The backup phase preserves the full transaction source before filtering. The filtering and sorting phase produces a derived working dataset. The report-generation phase reads the sorted working dataset and writes to the report output; if interrupted, the report output may be incomplete and the job must be rerun from the beginning.

### Requirements

REQ-F-088: [Ubiquitous] The system shall copy all records from the primary transaction master data store (legacy: AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS) to a timestamped backup copy of the transaction master data store (legacy: AWS.M2.CARDDEMO.TRANSACT.BKUP), preserving the original record structure and content without filtering.

REQ-F-089: [Ubiquitous] The system shall filter the backed-up transaction records to include only those whose process date falls between 2022-01-01 and 2022-07-06 (inclusive), excluding all records outside that date range from further processing.

REQ-F-090: [Ubiquitous] The system shall sort the date-filtered transaction records in ascending order by card number and write the sorted result to a timestamped daily transaction working data store (legacy: AWS.M2.CARDDEMO.TRANSACT.DALY) for use as input to the report-generation step.

REQ-F-091: [Ubiquitous] The system shall open the transaction input file (legacy: TRANSACT-FILE) for sequential input, the report output file (legacy: REPORT-FILE) for output, the cross-reference lookup file (legacy: XREF-FILE) for random input, the transaction type lookup file (legacy: TRANTYPE-FILE) for random input, the transaction category lookup file (legacy: TRANCATG-FILE) for random input, and the date parameter file (legacy: DATE-PARMS-FILE) for sequential input before beginning report generation.

REQ-F-092: [Event-driven] When the date parameter file is read, the system shall evaluate the file status and set the application result code to 0 for a successful read, 16 for end-of-file, or 12 for any other error; if end-of-file or error occurs, the system shall set the end-of-file flag to 'Y' to terminate processing.

REQ-F-093: [Event-driven] When the first transaction record is processed, the system shall set the first-time flag to 'N', populate the report header with the start date and end date read from the date parameter file, and write the report header lines (report name with date range, a blank line, column headers, and a separator line of dashes) to the report output file.

REQ-F-094: [Ubiquitous] The system shall write four header lines to the report output file: the report-name header containing the report name and date range, a blank line, the column-header line, and a separator line of dashes; the line counter shall be incremented after each line is written.

REQ-F-095: [State-driven] While the end-of-file flag is 'N', the system shall read the next transaction record from the transaction input file; if the read is successful, perform control-break logic when the card number changes, execute lookup operations to enrich the transaction, and write the transaction detail line; if end-of-file is reached, accumulate the final transaction amount to page and account totals, write page totals and grand totals, and exit the loop.

REQ-F-096: [Event-driven] When the next transaction record is read from the transaction input file, the system shall evaluate the file status and set the application result code to 0 for success, 16 for end-of-file, or 12 for any other error; if end-of-file or error occurs, the system shall set the end-of-file flag to 'Y' to signal loop termination.

REQ-F-097: [Event-driven] When the transaction's card number differs from the previously stored card number, the system shall, if this is not the first transaction, write the account totals for the previous card number; then update the current card number to the new transaction's card number and prepare for cross-reference lookup.

REQ-F-098: [Event-driven] When a new card number is encountered, the system shall move the card number to the cross-reference file key and retrieve the associated account ID from the indexed cross-reference data store (legacy: XREF-FILE).

REQ-F-099: [Ubiquitous] The system shall read the cross-reference record from the indexed cross-reference data store using the card number as the key to retrieve the associated account ID.

REQ-F-100: [Ubiquitous] The system shall move the transaction type code to the transaction type file key and retrieve the transaction type description from the indexed transaction type data store (legacy: TRANTYPE-FILE).

REQ-F-101: [Ubiquitous] The system shall read the transaction type record from the indexed transaction type data store using the type code as the key to retrieve the associated type description.

REQ-F-102: [Ubiquitous] The system shall move the transaction type code and category code to the transaction category file composite key and retrieve the transaction category description from the indexed transaction category data store (legacy: TRANCATG-FILE).

REQ-F-103: [Ubiquitous] The system shall read the transaction category record from the indexed transaction category data store using the composite key of type code and category code to retrieve the associated category description.

REQ-F-104: [Ubiquitous] The system shall populate each transaction detail report line with the transaction ID, account ID, type code, type description, category code, category description, source, and formatted transaction amount, then write the assembled line to the report output file.

REQ-F-105: [Ubiquitous] The system shall add the transaction amount to both the page total and the account total for each transaction record processed, accumulating running totals for summary reporting.

REQ-F-106: [Event-driven] When the line counter reaches a multiple of the page size (20 lines), the system shall write the page totals to the report output file and write a new set of report headers (report name, blank line, and column headers) to begin the next page.

REQ-F-107: [Ubiquitous] The system shall move the page total to the formatted page-total field, write the page-total summary line to the report output file, add the page total to the grand total, reset the page total to 0, increment the line counter, write a separator line of dashes to the report output file, and increment the line counter again.

REQ-F-108: [Event-driven] When the card number changes (control break occurs), the system shall move the account total to the formatted account-total field, write the account-total summary line to the report output file, reset the account total to 0, increment the line counter, write a separator line of dashes to the report output file, and increment the line counter again.

REQ-F-109: [Event-driven] When end-of-file is reached on the transaction input file, the system shall add the final transaction amount to the page total and account total, write the page totals to the report output file, and write the grand totals to the report output file.

REQ-F-110: [Event-driven] When end-of-file is reached and all transactions have been processed, the system shall move the grand total to the formatted grand-total field and write the grand-total summary line to the report output file.

REQ-F-111: [Ubiquitous] The system shall write the formatted report line to the report output file (legacy: REPORT-FILE) for every report line produced, including transaction detail lines, page-total lines, account-total lines, grand-total lines, header lines, and separator lines.

### Open Questions

OQ-007: The filter date range is hardcoded as 2022-01-01 to 2022-07-06. Should this range be driven by the date parameter file at runtime rather than being fixed? — Owner: batch operations / business owner

OQ-008: The page size is specified as 20 lines. Is this a configurable parameter or a fixed business rule? — Owner: reporting team

## Job Dependencies
Inferred from data flow (writer → reader via shared data store):

- CBACT04C → INTCALC (via AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS)
- INTCALC → CBACT04C (via AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS)
