# Data Backup,Archival,and Indexing — Requirements

## Global Preconditions
- All operations require valid input data and appropriate authorization.
- Processing constraints and scheduling dependencies are documented in the Job Dependencies section.

## 1. Daily Rejection Dataset Version Management
As a batch operations team, I want the daily rejected transactions output file managed as a versioned data store with automatic retention enforcement so that a rolling history of rejection records is maintained without manual cleanup.

### Requirements

REQ-F-001: [Ubiquitous] The system shall define the daily rejected transactions output file (legacy: AWS.M2.CARDDEMO.DALYREJS) as a versioned data store with a maximum retention limit of 5 versions, and shall automatically mark superseded versions for deletion when the retention limit is exceeded.

### Open Questions

OQ-001: The rule specifies a retention limit of exactly 5 generations. Should this limit be configurable at runtime, or is 5 a fixed business policy? — Owner: batch operations / data management team

## 2. Versioned Data Store Lifecycle Management
As a batch operations team, I want versioned data stores defined for transaction backups, archives, reports, and related datasets so that rolling histories are maintained automatically with controlled retention and storage consumption.

### Requirements

REQ-F-002: [Ubiquitous] The system shall define a versioned data store base for the transaction category balance backup data store (legacy: AWS.M2.CARDDEMO.TCATBALF.BKUP) that retains a maximum of 5 versions and automatically removes the oldest version when the retention limit is exceeded.

REQ-F-003: [Ubiquitous] The system shall define a versioned data store base for the combined transaction data store (legacy: AWS.M2.CARDDEMO.TRANSACT.COMBINED) that retains a maximum of 5 versions and automatically deletes older versions when the retention limit is exceeded.

REQ-F-004: [Ubiquitous] The system shall define a versioned data store base for the system transaction data store (legacy: AWS.M2.CARDDEMO.SYSTRAN) that retains a maximum of 5 versions and automatically deletes the oldest version when the retention limit is exceeded.

REQ-F-005: [Ubiquitous] The system shall define a versioned data store base for the daily transaction working data store (legacy: AWS.M2.CARDDEMO.TRANSACT.DALY) that retains a maximum of 5 versions and automatically removes the oldest version when the retention limit is exceeded.

REQ-F-006: [Ubiquitous] The system shall define a versioned data store base for the transaction master backup data store (legacy: AWS.M2.CARDDEMO.TRANSACT.BKUP) that retains a maximum of 5 versions and automatically deletes expired versions when the retention limit is exceeded.

REQ-F-007: [Ubiquitous] The system shall define a versioned data store base for the transaction report data store (legacy: AWS.M2.CARDDEMO.TRANREPT) that retains a maximum of 5 versions and automatically deletes the oldest version when the retention limit is exceeded.

## 3. Transaction Category Balance File Backup and Sort
As a batch operations team, I want the transaction category balance file backed up and sorted by account and category so that a versioned archive exists and downstream reporting receives records in a consistent, formatted order.

**Restart/Recovery:** The backup copy is a full unload of the source data store; re-running the job produces a new versioned backup. The sort and report output are derived entirely from the backup; both phases are restartable by re-executing the job.

### Requirements

REQ-F-008: [Ubiquitous] The system shall copy all records from the transaction category balance VSAM KSDS data store (legacy: AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS) to a new versioned backup copy of the transaction category balance file (legacy: AWS.M2.CARDDEMO.TCATBALF.BKUP), preserving each record without modification.

REQ-F-009: [Ubiquitous] The system shall sort the transaction category balance records from the backup copy in ascending order by account ID, then by transaction type code, then by category code, reformat each record to present the account ID, transaction type code, and category code as space-separated fields followed by the category balance edited in decimal format (TTTTTTTTT.TT), and write the sorted output to the report output file (legacy: AWS.M2.CARDDEMO.TCATBALF.REPT) for downstream reporting.

## 4. Transaction Report Version Retention
As a batch operations team, I want transaction report datasets stored as versioned archives with a defined retention limit so that historical report versions are preserved and older versions are automatically managed.

### Requirements

REQ-F-010: [Ubiquitous] The system shall maintain the transaction report output store (legacy: AWS.M2.CARDDEMO.TRANREPT) as a versioned archive with a maximum retention limit of 10 versions, such that each new report run creates a new version and any versions beyond the 10-version limit are automatically removed.

### Open Questions

OQ-002: The retention policy specifies a hard limit of 10 versions. Should the modernized platform enforce this limit via a managed versioning policy (e.g., object lifecycle rules), or is a configurable retention count acceptable? — Owner: batch operations / platform team

## 5. Transaction File Backup and Archival
As a batch operations team, I want processed transaction records copied to a versioned backup archive after each processing cycle so that transaction history is preserved for audit and recovery purposes.

### Requirements

REQ-F-011: [Ubiquitous] The system shall copy all processed transaction records from the transaction master data store (legacy: AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS), accessed in shared mode, to a new version of the transaction backup data store (legacy: AWS.M2.CARDDEMO.TRANSACT.BKUP), catalog the backup upon successful completion, and preserve the versioned archive for audit and recovery.

---

## 6. Transaction Master Alternate Index Cleanup
As a batch operations team, I want the transaction master alternate index removed before index-related processing begins so that a clean, consistent state is guaranteed for any subsequent alternate index creation.

### Requirements

REQ-F-012: [Ubiquitous] The system shall delete the alternate index for the transaction master data store (legacy: AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX) as part of the batch initialization sequence; if the alternate index does not exist, the operation shall complete without error.

---

## 7. Transaction Master File Reinitialization
As a batch operations team, I want the transaction master file deleted and recreated as a fresh indexed-sequential data store so that transaction processing begins from a clean, consistent state.

### Requirements

REQ-F-013: [Ubiquitous] The system shall delete the existing transaction master data store cluster before defining a new indexed-sequential transaction master data store with cross-region sharing enabled.

REQ-F-014: [Event-driven] When the deletion of the existing transaction master data store completes successfully, the system shall define a new indexed-sequential transaction master data store with the specified storage allocation and key structure.

## 8. Transaction Timestamp Index Creation and Alternate Index Management
As a batch operations team, I want an alternate index created on the transaction master data store keyed by processed timestamp so that transactions can be efficiently retrieved by when they were processed, and so that applications can query transactions through a stable named access path.

### Requirements

REQ-F-015: [Ubiquitous] The system shall create an alternate index on the transaction master data store (legacy: AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS), keyed by processed timestamp, configured to allow duplicate key values, with automatic maintenance enabled so that the index remains synchronized whenever transaction records are added, updated, or deleted in the base data store.

REQ-F-016: [Ubiquitous] The system shall build the alternate index (legacy: AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX) from the transaction master data store and store the resulting index structure in the dedicated transaction alternate index data store.

REQ-F-017: [Ubiquitous] The system shall define an access path — the Transact data store path (legacy: AWS.M2.CARDDEMO.TRANSACT.VSAM.AIX.PATH) — that routes queries through the transaction alternate index data store to the base transaction master data store, enabling applications to retrieve transaction records by alternate key without directly referencing the index data store.

## 9. Card Cross-Reference Dataset Lifecycle Management
As a batch operations team, I want the card cross-reference dataset rebuilt from source data on each run so that card transaction processing always operates against a clean, fully indexed dataset.

### Requirements

REQ-F-018: [Ubiquitous] The system shall delete the existing card cross-reference keyed data store (legacy: AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS) if present, clearing the catalog entry and freeing any allocated storage before creating a new version.

REQ-F-019: [Ubiquitous] The system shall copy all card reference records from the sequential card cross-reference flat file (legacy: AWS.M2.CARDDEMO.CARDXREF.PS) into the newly created card cross-reference keyed data store, loading the dataset with initial content.

REQ-F-020: [Ubiquitous] The system shall build an alternate index on the card cross-reference keyed data store to enable secondary-key lookups, storing the result as the card cross-reference alternate index (legacy: AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX).

---

## 10. Card Reference Alternate Index Lifecycle
As a batch operations team, I want the alternate index on the card reference file rebuilt on each run so that account-ID-based lookups remain accurate and consistent with the base data.

### Requirements

REQ-F-021: [Ubiquitous] The system shall delete the existing alternate index on the card cross-reference keyed data store if present; if no alternate index exists, the deletion shall be skipped without error.

REQ-F-022: [Ubiquitous] The system shall create a new alternate index on the card cross-reference keyed data store keyed by account ID (positions 11–25 of the record), configured with non-unique key values and automatic upgrade maintenance so that updates to the base data store are automatically reflected in the alternate index.

---

## 11. Alternate Index Path Definition for Card Reference Data
As a batch operations team, I want an access path defined between the card cross-reference alternate index and its base cluster so that applications can query card reference records using the alternate account-ID key.

### Requirements

REQ-F-023: [Ubiquitous] The system shall define a path that logically connects the card cross-reference alternate index (legacy: AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX) to the card cross-reference keyed data store, assigning it the path name card cross-reference alternate index path (legacy: AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX.PATH), enabling applications to access card reference records through the alternate key.