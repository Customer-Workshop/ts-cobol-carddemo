# Data Store Initialization and Lifecycle — Requirements

## Global Preconditions
- All operations require valid input data and appropriate authorization.
- Processing constraints and scheduling dependencies are documented in the Job Dependencies section.

## 1. Account Master Data Store Initialization
As a batch operations team, I want the account master data store rebuilt from a flat-file source during initialization so that a clean, consistent, and fully populated account repository is available for transaction processing.

### Requirements

REQ-F-001: [Event-driven] When the account master data store initialization job executes, the system shall remove the existing primary keyed account master data store (legacy: AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS) before creating a new instance, ensuring no residual data carries forward.

REQ-F-002: [Event-driven] When the existing account master data store has been removed, the system shall define a new indexed-sequential account master data store with 1 primary cylinder and 5 secondary cylinders of capacity, and with the appropriate sharing parameters.

REQ-F-003: [Event-driven] When the new account master data store has been defined, the system shall copy all account records from the flat-file source (legacy: AWS.M2.CARDDEMO.ACCTDATA.PS) into the account master data store, producing a fully populated repository keyed by account ID.

### Non-Functional Requirements

REQ-N-001: [Ubiquitous] The system shall execute the delete, define, and populate phases in strict sequence so that the account master data store is always either fully replaced or left in a known prior state without partial initialization.

### Open Questions

OQ-001: The sharing parameters for the new account master data store are referenced but not fully specified in the available rules. What sharing/cross-region access parameters are required? — Owner: infrastructure/storage team

## 2. Customer Data Store Initialization
As a batch operations team, I want the customer data store deleted and re-provisioned from a clean state so that subsequent data load jobs can populate a fresh, conflict-free customer master without interference from stale definitions.

### Requirements

REQ-F-004: [Ubiquitous] The system shall delete the customer data store cluster (legacy: AWS.CCDA.CUSTDATA.CLUSTER) from the system catalog as an unconditional cleanup step before any new store definition is created.

REQ-F-005: [Ubiquitous] The system shall delete any existing customer data store named Cluster data store (legacy: AWS.CCDA.CUSTDATA.CLUSTER) to clear the namespace prior to provisioning the new definition, completing without error if no such store exists.

REQ-F-006: [Ubiquitous] The system shall create a new indexed customer data store (legacy: AWS.CUSTDATA.CLUSTER) with a 10-byte key, 500-byte fixed records, a primary allocation of 1 cylinder, a secondary allocation of 5 cylinders, configured for single-writer cross-region access and multi-reader cross-system access, with separately named data (legacy: AWS.CUSTDATA.CLUSTER.DATA) and index (legacy: AWS.CUSTDATA.CLUSTER.INDEX) components, and marked for erasure on deletion.

## 3. Disclosure Group Data Store Initialization
As a batch operations team, I want the disclosure group data store rebuilt from its authoritative source file on demand so that the interest rate and fee tier reference data is clean, complete, and ready for production use.

### Requirements

REQ-F-007: [Event-driven] When the disclosure group data store initialization job executes, the system shall delete the existing disclosure group keyed data store (legacy: `AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS`) if one exists, so that a clean data store can be created in its place.

REQ-F-008: [Event-driven] When the disclosure group data store has been deleted, the system shall create a new disclosure group data store and populate it by copying all records from the disclosure group sequential flat file (legacy: `AWS.M2.CARDDEMO.DISCGRP.PS`) into the newly created data store.

## 4. Transaction Category Balance Data Store Initialization
As a batch operations team, I want the transaction category balance data store rebuilt from a clean state and populated with current source records so that downstream processing operates against a consistent, fully initialized data store.

### Requirements

REQ-F-009: [Event-driven] When the transaction category balance data store initialization job executes, the system shall remove any existing instance of the transaction category balance keyed data store (legacy: `AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS`) to ensure no residual data remains before the new data store is created.

REQ-F-010: [Event-driven] When the existing transaction category balance data store has been removed, the system shall define a new indexed-sequential transaction category balance data store with a 17-byte key and 50-byte fixed record structure, configured with the required storage and sharing parameters.

REQ-F-011: [Event-driven] When the new transaction category balance data store has been defined, the system shall copy all transaction category balance records from the source sequential flat file (legacy: `AWS.M2.CARDDEMO.TCATBALF.PS`) into the transaction category balance data store, producing a fully populated data store ready for production use.

REQ-F-012: [Ubiquitous] The system shall execute the delete, define, and populate steps in strict sequence so that the transaction category balance data store is always in a clean, consistent state upon job completion.

## 5. Transaction Category Reference Data Store Initialization
As a batch operations team, I want the transaction category reference data store rebuilt from a known source file so that transaction categorization lookups always operate against a clean, fully populated, and correctly structured dataset.

### Requirements

REQ-F-013: [Event-driven] When the transaction category reference data store initialization job executes, the system shall remove any existing version of the transaction category reference data store (VSAM KSDS storing transaction category reference data, legacy: `AWS.M2.CARDDEMO.TRANCATG.VSAM.KSDS`) before creating a new one.

REQ-F-014: [Event-driven] When the existing transaction category reference data store has been removed, the system shall define a new keyed data store with 6-byte keys and 60-byte fixed-length records to replace it.

REQ-F-015: [Event-driven] When the new transaction category reference data store has been defined, the system shall copy all transaction category records from the transaction category reference flat file (sequential flat file of transaction category reference records, legacy: `AWS.M2.CARDDEMO.TRANCATG.PS`) into the newly created transaction category reference data store.

## 6. Transaction Type Reference Data Store Initialization
As a batch operations team, I want the transaction type reference data store rebuilt from a canonical flat-file source so that downstream card processing applications have a clean, consistent set of transaction type lookup records.

### Requirements

REQ-F-016: [Event-driven] When the transaction type reference data store initialization job executes, the system shall delete the existing transaction type data store (legacy: `AWS.M2.CARDDEMO.TRANTYPE.VSAM.KSDS`) if one is present before creating a new instance.

REQ-F-017: [Event-driven] When the existing transaction type data store has been removed, the system shall create a new, empty indexed transaction type data store keyed for transaction type reference data (copybook CVTRA03Y).

REQ-F-018: [Event-driven] When the new transaction type data store has been created, the system shall populate it by copying all transaction type records from the transaction type flat-file source (legacy: `AWS.M2.CARDDEMO.TRANTYPE.PS`) into the transaction type data store.