# Security,Validation,and Application Setup — Requirements

## Global Preconditions
- All operations require valid input data and appropriate authorization.
- Processing constraints and scheduling dependencies are documented in the Job Dependencies section.

## 1. CARDDEMO Application Resource Registration
As a platform operations team, I want the CARDDEMO application's programs, screen mapsets, and transaction routing entries registered as a complete resource group so that the runtime environment can locate and dispatch all application components correctly.

**Category:** setup
**Data flow:** Reads CSD control statements; writes resource definitions to the CICS system definition (CSD) file (legacy: OEM.CICSTS.DFHCSD).
**Migration relevance:** Defines the complete set of runtime-addressable resources required to operate the card-management application. The resource registration pattern (program names, transaction identifiers, and their mappings) must be preserved; the registration mechanism is implementation-specific.

### Requirements

REQ-F-001: [Ubiquitous] The system shall register the application resource library (COM2DOLL) in the CARDDEMO group, pointing to the application load library, so that the runtime environment can locate and load all CARDDEMO application programs.

REQ-F-002: [Ubiquitous] The system shall register the login program in the CARDDEMO group with transaction identifier CC00, configured to accept any data area size, as the entry point for user authentication and session initiation.

REQ-F-003: [Ubiquitous] The system shall register the administration program in the CARDDEMO group with transaction identifier CCAD, configured to accept any data area size.

REQ-F-004: [Ubiquitous] The system shall register the bill-pay program in the CARDDEMO group.

REQ-F-005: [Ubiquitous] The system shall register the four account management programs (account menu, view-account, update-account, and deactivate-account) in the CARDDEMO group.

REQ-F-006: [Ubiquitous] The system shall register the four card management programs (card menu, view-card, update-card, and deactivate-card) in the CARDDEMO group.

REQ-F-007: [Ubiquitous] The system shall register the four transaction management programs (transaction entry, transaction report, transaction details, and add-transaction) in the CARDDEMO group.

REQ-F-008: [Ubiquitous] The system shall register the four test programs in the CARDDEMO group with their respective transaction identifiers: test program 1 with transaction CCT1, test program 2 with transaction CCT2, test program 3 with transaction CCT3, and test program 4 with transaction CCT4.

REQ-F-009: [Ubiquitous] The system shall register the login screen mapset in the CARDDEMO group.

REQ-F-010: [Ubiquitous] The system shall register the administration menu screen mapset in the CARDDEMO group.

REQ-F-011: [Ubiquitous] The system shall register the bill-pay setup screen mapset in the CARDDEMO group.

REQ-F-012: [Ubiquitous] The system shall register the four account management screen mapsets (account menu screen, view-account screen, update-account screen, and deactivate-account screen) in the CARDDEMO group.

REQ-F-013: [Ubiquitous] The system shall register the four card management screen mapsets (card menu screen, view-card screen, update-card screen, and deactivate-card screen) in the CARDDEMO group.

REQ-F-014: [Ubiquitous] The system shall register the four transaction management screen mapsets (transaction entry screen, transaction report screen, transaction details screen, and add-transaction screen) in the CARDDEMO group.

REQ-F-015: [Ubiquitous] The system shall register the four test program screen mapsets in the CARDDEMO group.

REQ-F-016: [Ubiquitous] The system shall register transaction routing entries in the CARDDEMO group mapping the following transaction identifiers to their corresponding programs: CCDM to the administration program, CCT1 to test program 1, CCT2 to test program 2, CCT3 to test program 3, and CCT4 to test program 4, each configured to accept any task data area size.

REQ-F-017: [Ubiquitous] The system shall enumerate and verify all resources registered in the CARDDEMO group, producing a report of all defined mapsets, programs, and transactions to confirm successful installation.

REQ-F-018: [Ubiquitous] The system shall execute the resource definition utility with read-write access to the CICS system definition (CSD) file (legacy: OEM.CICSTS.DFHCSD), with a 60-line page size and compatibility mode disabled.

### Open Questions

OQ-001: Rule 51113c8b states the login screen mapset definition appears twice in the control input, described as possibly a duplicate entry or a correction. Should the modernized system deduplicate this registration, or is the double registration intentional? — Owner: platform operations / application team

## 2. Date Validation and Feedback Reporting
As a batch operations team, I want date strings validated against a format mask and a complete diagnostic result returned to the caller so that downstream processes can reliably determine whether a date value is valid and understand the specific nature of any validation failure.

### Requirements

REQ-F-019: [Ubiquitous] The system shall accept an input date string, a date format mask, and a result buffer from the caller as the three input parameters for a date validation request.

REQ-F-020: [Event-driven] When the input date string and format mask are prepared, the system shall invoke the date validation service to convert the date to Lillian format, capture the returned feedback code, and extract the severity level and message number from the feedback response into the diagnostic message record.

REQ-F-021: [Event-driven] When the feedback code is returned from the date validation service, the system shall map the feedback code to a human-readable result message according to the following rules: feedback indicating a valid date maps to "Date is valid"; insufficient data maps to "Insufficient"; invalid date value maps to "Datevalue error"; invalid era maps to "Invalid Era"; unsupported range maps to "Unsupp. Range"; invalid month maps to "Invalid month"; invalid format string maps to "Bad Pic String"; non-numeric input maps to "Nonnumeric data"; zero year in era maps to "YearInEra is 0"; and all other feedback codes map to "Date is invalid".

REQ-F-022: [Ubiquitous] The system shall return the complete diagnostic message record — containing the validation result text, severity, message number, input date, and format mask — to the caller's result buffer.

## 3. User Security Credential Initialization
As a batch operations team, I want the user security data store initialized with administrator and standard user credentials so that the application has a valid, queryable set of user security records available at startup.

**Category:** setup  
**Data flow:** Generates a sequential flat file of user security records, defines a keyed indexed data store, then loads the records from the sequential file into the indexed store.  
**Restart/Recovery:** This job is a full initialization; re-execution deletes and recreates the indexed data store from scratch, making the job idempotent by design.

### Requirements

REQ-F-023: [Event-driven] When the user security credential initialization job executes, the system shall generate a sequential flat file — the user security flat file (AWS.M2.CARDDEMO.USRSEC.PS) — containing user records, where each record includes a user identifier, name, surname, and password type.

REQ-F-024: [Event-driven] When the user security credential initialization job executes, the system shall delete any existing keyed indexed user security data store (AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS) before defining a new one, ensuring no stale credential data persists.

REQ-F-025: [Event-driven] When the user security credential initialization job executes, the system shall define the user security data store as a keyed indexed store with an 8-byte primary key to support efficient credential lookup by user identifier.

REQ-F-026: [Event-driven] When the user security data store has been defined, the system shall copy all user security records from the user security flat file into the user security data store, making the credentials available for operational use.

### Non-Functional Requirements

REQ-N-001: [Unwanted] If the deletion of the existing user security data store fails, the system shall not proceed to define or populate a new store, preventing partial or inconsistent credential state.