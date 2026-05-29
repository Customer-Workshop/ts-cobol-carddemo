# CardDemo COBOL → Java (Spring Boot + Spring Batch) Migration Specification

> **Purpose**: This document is a self-contained migration specification for converting the CardDemo mainframe COBOL application to a modern Java stack (Spring Boot 3.x, Spring Batch, Spring Data JPA, Spring Security). A developer or AI agent with **no COBOL knowledge** should be able to read this document and implement a functionally equivalent Java application from scratch.

---

## Table of Contents

1. [Source Application Inventory](#1-source-application-inventory)
2. [Data Layer Migration Spec (Copybooks → JPA Entities + DDL)](#2-data-layer-migration-spec)
3. [Batch Program Migration Spec (COBOL → Spring Batch)](#3-batch-program-migration-spec)
4. [Online (CICS) Program Migration Spec (COBOL+CICS → Spring Boot REST API)](#4-online-cics-program-migration-spec)
5. [Target Java Project Structure](#5-target-java-project-structure)
6. [COBOL-to-Java Pitfalls Checklist](#6-cobol-to-java-pitfalls-checklist)
7. [Migration Execution Order](#7-migration-execution-order)

---

## 1. Source Application Inventory

### 1.1 COBOL Programs (`app/cbl/`)

#### Online (CICS) Programs

| Program       | Transaction ID | BMS Map   | Function                          |
|:--------------|:---------------|:----------|:----------------------------------|
| `COSGN00C.cbl` | CC00          | COSGN00   | Sign-on screen                    |
| `COMEN01C.cbl` | CM00          | COMEN01   | Main menu                         |
| `COACTVWC.cbl` | CAVW          | COACTVW   | Account view                      |
| `COACTUPC.cbl` | CAUP          | COACTUP   | Account update                    |
| `COCRDLIC.cbl` | CCLI          | COCRDLI   | Credit card list                  |
| `COCRDSLC.cbl` | CCDL          | COCRDSL   | Credit card view (detail/select)  |
| `COCRDUPC.cbl` | CCUP          | COCRDUP   | Credit card update                |
| `COTRN00C.cbl` | CT00          | COTRN00   | Transaction list                  |
| `COTRN01C.cbl` | CT01          | COTRN01   | Transaction view                  |
| `COTRN02C.cbl` | CT02          | COTRN02   | Transaction add                   |
| `CORPT00C.cbl` | CR00          | CORPT00   | Transaction reports               |
| `COBIL00C.cbl` | CB00          | COBIL00   | Bill payment                      |
| `COADM01C.cbl` | CA00          | COADM01   | Admin menu                        |
| `COUSR00C.cbl` | CU00          | COUSR00   | List users                        |
| `COUSR01C.cbl` | CU01          | COUSR01   | Add user                          |
| `COUSR02C.cbl` | CU02          | COUSR02   | Update user                       |
| `COUSR03C.cbl` | CU03          | COUSR03   | Delete user                       |

#### Batch Programs

| Program        | Function                                              | Invoked By JCL |
|:---------------|:------------------------------------------------------|:---------------|
| `CBACT01C.cbl` | Read account VSAM file; write to output files with format conversions (COMP-3, VB records, arrays, date formatting via assembler `COBDATFT`) | `READACCT`     |
| `CBACT02C.cbl` | Read and print card data file                         | `READCARD`     |
| `CBACT03C.cbl` | Read and print customer-card cross-reference file     | `READXREF`     |
| `CBACT04C.cbl` | Interest calculation: reads TCATBALF, looks up disclosure-group interest rate, computes monthly interest, updates account balance, writes interest transactions | `INTCALC`      |
| `CBCUS01C.cbl` | Read and print customer data file                     | `READCUST`     |
| `CBTRN01C.cbl` | Post daily transactions (variant of CBTRN02C)         | —              |
| `CBTRN02C.cbl` | **Core transaction posting**: reads daily transactions, validates card/account via XREF, posts to transaction file, updates account balances (CYC-CREDIT/CYC-DEBIT), updates transaction-category balances | `POSTTRAN`     |
| `CBTRN03C.cbl` | Print transaction detail report                       | `TRANREPT`     |
| `CBEXPORT.cbl` | Export all data (customers, accounts, xrefs, transactions, cards) into a multi-record-type sequential file for branch migration | `CBEXPORT`     |
| `CBIMPORT.cbl` | Import multi-record export file back into normalized target files with validation | `CBIMPORT`     |
| `CBSTM03A.CBL` | Generate account statements in plain text and HTML formats from transaction data; uses subroutine calls, `COMP`/`COMP-3` variables, 2D arrays, `ALTER`/`GO TO` | `CREASTMT`     |
| `CBSTM03B.CBL` | Subroutine for CBSTM03A: VSAM file I/O operations (open, close, read, read-by-key, write, rewrite) | —              |
| `CSUTLDTC.cbl` | Date conversion utility                               | —              |
| `COBSWAIT.cbl` | Timer wait for batch job scheduling                   | `WAITSTEP`     |

### 1.2 Copybooks (`app/cpy/`)

| Copybook       | Purpose                                          | Record Length |
|:---------------|:-------------------------------------------------|:-------------|
| `CVACT01Y.cpy` | Account record layout                            | 300          |
| `CVACT02Y.cpy` | Card record layout                               | 150          |
| `CVACT03Y.cpy` | Card cross-reference (customer-account-card)     | 50           |
| `CVCUS01Y.cpy` | Customer record layout                           | 500          |
| `CSUSR01Y.cpy` | Security user record layout                      | 80           |
| `CVTRA05Y.cpy` | Transaction record (online TRAN-RECORD)          | 350          |
| `CVTRA06Y.cpy` | Daily transaction record (DALYTRAN-RECORD)       | 350          |
| `CVTRA01Y.cpy` | Transaction category balance                     | 50           |
| `CVTRA02Y.cpy` | Disclosure group (interest rates)                | 50           |
| `CVTRA03Y.cpy` | Transaction type                                 | 60           |
| `CVTRA04Y.cpy` | Transaction category type                        | 60           |
| `CVTRA07Y.cpy` | Reporting data structures (headers, totals)      | —            |
| `CVEXPORT.cpy` | Multi-record export layout with `REDEFINES`      | 500          |
| `COSTM01.CPY`  | Transaction record layout for reporting (TRNX)   | —            |
| `CUSTREC.cpy`  | Alternate customer record layout                 | 500          |
| `CVCRD01Y.cpy` | Common card work areas (AID keys, navigation)    | —            |
| `COCOM01Y.cpy` | COMMAREA (inter-program communication)           | —            |
| `COTTL01Y.cpy` | Screen title constants                           | —            |
| `COADM02Y.cpy` | Admin menu option definitions                    | —            |
| `COMEN02Y.cpy` | Main menu option definitions                     | —            |
| `CSDAT01Y.cpy` | Date/time working storage                        | —            |
| `CSMSG01Y.cpy` | Common application messages                      | —            |
| `CSMSG02Y.cpy` | Abend/error data structures                      | —            |
| `CSSETATY.cpy` | Screen attribute setting (error highlighting)    | —            |
| `CSLKPCDY.cpy` | Lookup code repository (phone area codes, US state codes, zip prefixes) | — |
| `CSSTRPFY.cpy` | PF key storage/mapping paragraph                 | —            |
| `CSUTLDPY.cpy` | Date validation procedure paragraphs             | —            |
| `CSUTLDWY.cpy` | Date validation working storage                  | —            |
| `CODATECN.cpy` | Date conversion record (YYYYMMDD / YYYY-MM-DD)   | —            |
| `UNUSED1Y.cpy` | Unused placeholder record                        | —            |

### 1.3 BMS Maps (`app/bms/`)

| Map File       | Map Name | Used By Program | Screen Function       |
|:---------------|:---------|:----------------|:----------------------|
| `COSGN00.bms`  | COSGN0A  | COSGN00C        | Sign-on               |
| `COMEN01.bms`  | COMEN1A  | COMEN01C        | Main menu             |
| `COACTVW.bms`  | ACTVW0A  | COACTVWC        | Account view          |
| `COACTUP.bms`  | ACTUP0A  | COACTUPC        | Account update        |
| `COCRDLI.bms`  | CRDLI0A  | COCRDLIC        | Card list             |
| `COCRDSL.bms`  | CRDSL0A  | COCRDSLC        | Card detail/select    |
| `COCRDUP.bms`  | CRDUP0A  | COCRDUPC        | Card update           |
| `COTRN00.bms`  | COTRN0A  | COTRN00C        | Transaction list      |
| `COTRN01.bms`  | COTRN1A  | COTRN01C        | Transaction view      |
| `COTRN02.bms`  | COTRN2A  | COTRN02C        | Transaction add       |
| `CORPT00.bms`  | CORPT0A  | CORPT00C        | Transaction report    |
| `COBIL00.bms`  | COBIL0A  | COBIL00C        | Bill payment          |
| `COADM01.bms`  | COADM1A  | COADM01C        | Admin menu            |
| `COUSR00.bms`  | COUSR0A  | COUSR00C        | User list             |
| `COUSR01.bms`  | COUSR1A  | COUSR01C        | User add              |
| `COUSR02.bms`  | COUSR2A  | COUSR02C        | User update           |
| `COUSR03.bms`  | COUSR3A  | COUSR03C        | User delete           |

### 1.4 JCL Jobs (`app/jcl/`)

| Job              | Program/Utility | Purpose                                            |
|:-----------------|:----------------|:---------------------------------------------------|
| `DUSRSECJ.jcl`   | IEBGENER        | Initial load of user security file                 |
| `DEFGDGB.jcl`    | IDCAMS          | Setup GDG bases                                    |
| `DEFGDGD.jcl`    | IDCAMS          | Setup additional GDG bases for Db2                 |
| `ACCTFILE.jcl`   | IDCAMS          | Refresh account master VSAM file                   |
| `CARDFILE.jcl`   | IDCAMS          | Refresh card master VSAM file                      |
| `CUSTFILE.jcl`   | IDCAMS          | Refresh customer master VSAM file                  |
| `XREFFILE.jcl`   | IDCAMS          | Load customer-card-account cross-reference         |
| `TRANFILE.jcl`   | IDCAMS          | Load transaction master VSAM file                  |
| `DISCGRP.jcl`    | IDCAMS          | Load disclosure group file                         |
| `TCATBALF.jcl`   | IDCAMS          | Refresh transaction category balance               |
| `TRANCATG.jcl`   | IDCAMS          | Load transaction category types                    |
| `TRANTYPE.jcl`   | IDCAMS          | Load transaction types                             |
| `CLOSEFIL.jcl`   | IEFBR14         | Close VSAM files in CICS                           |
| `OPENFIL.jcl`    | IEFBR14         | Open files in CICS                                 |
| `POSTTRAN.jcl`   | CBTRN02C        | Core nightly transaction posting                   |
| `INTCALC.jcl`    | CBACT04C        | Run interest calculations                          |
| `COMBTRAN.jcl`   | SORT            | Combine transaction files                          |
| `CREASTMT.JCL`   | CBSTM03A        | Produce account statements                         |
| `TRANREPT.jcl`   | CBTRN03C        | Transaction detail report                          |
| `TRANBKP.jcl`    | IDCAMS          | Backup/refresh transaction master                  |
| `TRANIDX.jcl`    | IDCAMS          | Define alternate index on transaction file         |
| `READACCT.jcl`   | CBACT01C        | Read and display account file                      |
| `READCARD.jcl`   | CBACT02C        | Read and display card file                         |
| `READCUST.jcl`   | CBCUS01C        | Read and display customer file                     |
| `READXREF.jcl`   | CBACT03C        | Read and display cross-reference file              |
| `CBEXPORT.jcl`   | CBEXPORT        | Export data for branch migration                   |
| `CBIMPORT.jcl`   | CBIMPORT        | Import data from branch migration                  |
| `DALYREJS.jcl`   | —               | Process daily rejects                              |
| `DEFCUST.jcl`    | —               | Define customer VSAM cluster                       |
| `WAITSTEP.jcl`   | COBSWAIT        | Timer wait job                                     |
| `PRTCATBL.jcl`   | —               | Print category balance                             |
| `REPTFILE.jcl`   | —               | Report file setup                                  |
| `ESDSRRDS.jcl`   | IDCAMS          | Create ESDS and RRDS VSAM files                    |
| `CBADMCDJ.jcl`   | —               | Admin batch job                                    |

---

## 2. Data Layer Migration Spec

### 2.1 Type Mapping Rules

| COBOL PIC Clause           | Java Type        | Notes                                              |
|:---------------------------|:-----------------|:---------------------------------------------------|
| `PIC 9(n)` where n <= 9   | `Long`           | Numeric identifier                                 |
| `PIC 9(n)` where n > 9    | `Long`           | 11-digit IDs still fit in `Long` (max 19 digits)   |
| `PIC S9(n)V99`             | `BigDecimal`     | `precision = n+2`, `scale = 2`. **Never** use `double`/`float` |
| `PIC S9(n)V99 COMP-3`     | `BigDecimal`     | Packed decimal — same mapping, different storage    |
| `PIC S9(n)V99 COMP`       | `BigDecimal`     | Binary decimal — same mapping                      |
| `PIC X(n)`                 | `String`         | Trim trailing spaces on read                       |
| `PIC X(10)` (date fields)  | `LocalDate`      | Parse with `DateTimeFormatter.ofPattern("yyyy-MM-dd")` |
| `PIC X(26)` (timestamps)   | `LocalDateTime`  | Parse `"yyyy-MM-dd-HH.mm.ss.SSSSSS"` format       |
| `PIC 9(n) COMP`           | `Integer`/`Long` | Binary integer                                     |
| `PIC 9(n) COMP-3`         | `Integer`/`Long` | Packed decimal integer                             |
| `FILLER`                   | *(dropped)*      | Padding bytes — not mapped to Java                  |

### 2.2 Account Entity — from `CVACT01Y.cpy`

#### COBOL Definition (RECLN = 300)

```cobol
01  ACCOUNT-RECORD.
    05  ACCT-ID                           PIC 9(11).
    05  ACCT-ACTIVE-STATUS                PIC X(01).
    05  ACCT-CURR-BAL                     PIC S9(10)V99.
    05  ACCT-CREDIT-LIMIT                 PIC S9(10)V99.
    05  ACCT-CASH-CREDIT-LIMIT            PIC S9(10)V99.
    05  ACCT-OPEN-DATE                    PIC X(10).
    05  ACCT-EXPIRAION-DATE               PIC X(10).
    05  ACCT-REISSUE-DATE                 PIC X(10).
    05  ACCT-CURR-CYC-CREDIT              PIC S9(10)V99.
    05  ACCT-CURR-CYC-DEBIT               PIC S9(10)V99.
    05  ACCT-ADDR-ZIP                     PIC X(10).
    05  ACCT-GROUP-ID                     PIC X(10).
    05  FILLER                            PIC X(178).
```

#### Java Entity

```java
@Entity
@Table(name = "accounts")
public class Account {
    @Id
    @Column(name = "acct_id", nullable = false)
    private Long acctId;                          // PIC 9(11)

    @Column(name = "acct_active_status", length = 1)
    private String activeStatus;                  // PIC X(01): 'Y' or 'N'

    @Column(name = "acct_curr_bal", precision = 12, scale = 2)
    private BigDecimal currentBalance;            // PIC S9(10)V99

    @Column(name = "acct_credit_limit", precision = 12, scale = 2)
    private BigDecimal creditLimit;               // PIC S9(10)V99

    @Column(name = "acct_cash_credit_limit", precision = 12, scale = 2)
    private BigDecimal cashCreditLimit;           // PIC S9(10)V99

    @Column(name = "acct_open_date")
    private LocalDate openDate;                   // PIC X(10) -> yyyy-MM-dd

    @Column(name = "acct_expiration_date")
    private LocalDate expirationDate;             // PIC X(10) -> yyyy-MM-dd

    @Column(name = "acct_reissue_date")
    private LocalDate reissueDate;                // PIC X(10) -> yyyy-MM-dd

    @Column(name = "acct_curr_cyc_credit", precision = 12, scale = 2)
    private BigDecimal currentCycleCredit;        // PIC S9(10)V99

    @Column(name = "acct_curr_cyc_debit", precision = 12, scale = 2)
    private BigDecimal currentCycleDebit;         // PIC S9(10)V99

    @Column(name = "acct_addr_zip", length = 10)
    private String addressZip;                    // PIC X(10)

    @Column(name = "acct_group_id", length = 10)
    private String groupId;                       // PIC X(10)
}
```

#### DDL

```sql
CREATE TABLE accounts (
    acct_id              BIGINT         NOT NULL PRIMARY KEY,
    acct_active_status   VARCHAR(1),
    acct_curr_bal        DECIMAL(12,2)  NOT NULL DEFAULT 0,
    acct_credit_limit    DECIMAL(12,2)  NOT NULL DEFAULT 0,
    acct_cash_credit_limit DECIMAL(12,2) NOT NULL DEFAULT 0,
    acct_open_date       DATE,
    acct_expiration_date DATE,
    acct_reissue_date    DATE,
    acct_curr_cyc_credit DECIMAL(12,2)  NOT NULL DEFAULT 0,
    acct_curr_cyc_debit  DECIMAL(12,2)  NOT NULL DEFAULT 0,
    acct_addr_zip        VARCHAR(10),
    acct_group_id        VARCHAR(10)
);
```

### 2.3 Customer Entity — from `CVCUS01Y.cpy`

#### COBOL Definition (RECLN = 500)

```cobol
01  CUSTOMER-RECORD.
    05  CUST-ID                                 PIC 9(09).
    05  CUST-FIRST-NAME                         PIC X(25).
    05  CUST-MIDDLE-NAME                        PIC X(25).
    05  CUST-LAST-NAME                          PIC X(25).
    05  CUST-ADDR-LINE-1                        PIC X(50).
    05  CUST-ADDR-LINE-2                        PIC X(50).
    05  CUST-ADDR-LINE-3                        PIC X(50).
    05  CUST-ADDR-STATE-CD                      PIC X(02).
    05  CUST-ADDR-COUNTRY-CD                    PIC X(03).
    05  CUST-ADDR-ZIP                           PIC X(10).
    05  CUST-PHONE-NUM-1                        PIC X(15).
    05  CUST-PHONE-NUM-2                        PIC X(15).
    05  CUST-SSN                                PIC 9(09).
    05  CUST-GOVT-ISSUED-ID                     PIC X(20).
    05  CUST-DOB-YYYY-MM-DD                     PIC X(10).
    05  CUST-EFT-ACCOUNT-ID                     PIC X(10).
    05  CUST-PRI-CARD-HOLDER-IND                PIC X(01).
    05  CUST-FICO-CREDIT-SCORE                  PIC 9(03).
    05  FILLER                                  PIC X(168).
```

#### Java Entity

```java
@Entity
@Table(name = "customers")
public class Customer {
    @Id
    @Column(name = "cust_id", nullable = false)
    private Long custId;                          // PIC 9(09)

    @Column(name = "cust_first_name", length = 25)
    private String firstName;

    @Column(name = "cust_middle_name", length = 25)
    private String middleName;

    @Column(name = "cust_last_name", length = 25)
    private String lastName;

    @Column(name = "cust_addr_line_1", length = 50)
    private String addressLine1;

    @Column(name = "cust_addr_line_2", length = 50)
    private String addressLine2;

    @Column(name = "cust_addr_line_3", length = 50)
    private String addressLine3;

    @Column(name = "cust_addr_state_cd", length = 2)
    private String stateCode;

    @Column(name = "cust_addr_country_cd", length = 3)
    private String countryCode;

    @Column(name = "cust_addr_zip", length = 10)
    private String zip;

    @Column(name = "cust_phone_num_1", length = 15)
    private String phoneNum1;

    @Column(name = "cust_phone_num_2", length = 15)
    private String phoneNum2;

    @Column(name = "cust_ssn")
    private Long ssn;                             // PIC 9(09) — sensitive

    @Column(name = "cust_govt_issued_id", length = 20)
    private String govtIssuedId;

    @Column(name = "cust_dob")
    private LocalDate dateOfBirth;                // PIC X(10)

    @Column(name = "cust_eft_account_id", length = 10)
    private String eftAccountId;

    @Column(name = "cust_pri_card_holder_ind", length = 1)
    private String primaryCardHolderInd;

    @Column(name = "cust_fico_credit_score")
    private Integer ficoCreditScore;              // PIC 9(03)
}
```

#### DDL

```sql
CREATE TABLE customers (
    cust_id                  BIGINT       NOT NULL PRIMARY KEY,
    cust_first_name          VARCHAR(25),
    cust_middle_name         VARCHAR(25),
    cust_last_name           VARCHAR(25),
    cust_addr_line_1         VARCHAR(50),
    cust_addr_line_2         VARCHAR(50),
    cust_addr_line_3         VARCHAR(50),
    cust_addr_state_cd       VARCHAR(2),
    cust_addr_country_cd     VARCHAR(3),
    cust_addr_zip            VARCHAR(10),
    cust_phone_num_1         VARCHAR(15),
    cust_phone_num_2         VARCHAR(15),
    cust_ssn                 BIGINT,
    cust_govt_issued_id      VARCHAR(20),
    cust_dob                 DATE,
    cust_eft_account_id      VARCHAR(10),
    cust_pri_card_holder_ind VARCHAR(1),
    cust_fico_credit_score   INT
);
```

### 2.4 Card Entity — from `CVACT02Y.cpy`

#### COBOL Definition (RECLN = 150)

```cobol
01  CARD-RECORD.
    05  CARD-NUM                          PIC X(16).
    05  CARD-ACCT-ID                      PIC 9(11).
    05  CARD-CVV-CD                       PIC 9(03).
    05  CARD-EMBOSSED-NAME                PIC X(50).
    05  CARD-EXPIRAION-DATE               PIC X(10).
    05  CARD-ACTIVE-STATUS                PIC X(01).
    05  FILLER                            PIC X(59).
```

#### Java Entity

```java
@Entity
@Table(name = "cards")
public class Card {
    @Id
    @Column(name = "card_num", length = 16, nullable = false)
    private String cardNum;                       // PIC X(16) — primary key

    @Column(name = "card_acct_id", nullable = false)
    private Long acctId;                          // PIC 9(11)

    @Column(name = "card_cvv_cd")
    private Integer cvvCode;                      // PIC 9(03)

    @Column(name = "card_embossed_name", length = 50)
    private String embossedName;

    @Column(name = "card_expiration_date")
    private LocalDate expirationDate;             // PIC X(10)

    @Column(name = "card_active_status", length = 1)
    private String activeStatus;                  // PIC X(01)
}
```

#### DDL

```sql
CREATE TABLE cards (
    card_num             VARCHAR(16)  NOT NULL PRIMARY KEY,
    card_acct_id         BIGINT       NOT NULL,
    card_cvv_cd          INT,
    card_embossed_name   VARCHAR(50),
    card_expiration_date DATE,
    card_active_status   VARCHAR(1),
    CONSTRAINT fk_card_account FOREIGN KEY (card_acct_id) REFERENCES accounts(acct_id)
);
```

### 2.5 CardXref Entity — from `CVACT03Y.cpy`

#### COBOL Definition (RECLN = 50)

```cobol
01 CARD-XREF-RECORD.
    05  XREF-CARD-NUM                     PIC X(16).
    05  XREF-CUST-ID                      PIC 9(09).
    05  XREF-ACCT-ID                      PIC 9(11).
    05  FILLER                            PIC X(14).
```

#### Java Entity

```java
@Entity
@Table(name = "card_xref")
public class CardXref {
    @Id
    @Column(name = "xref_card_num", length = 16, nullable = false)
    private String cardNum;                       // Primary key

    @Column(name = "xref_cust_id", nullable = false)
    private Long custId;

    @Column(name = "xref_acct_id", nullable = false)
    private Long acctId;
}
```

#### DDL

```sql
CREATE TABLE card_xref (
    xref_card_num   VARCHAR(16)  NOT NULL PRIMARY KEY,
    xref_cust_id    BIGINT       NOT NULL,
    xref_acct_id    BIGINT       NOT NULL,
    CONSTRAINT fk_xref_customer FOREIGN KEY (xref_cust_id) REFERENCES customers(cust_id),
    CONSTRAINT fk_xref_account  FOREIGN KEY (xref_acct_id) REFERENCES accounts(acct_id)
);
```

### 2.6 SecUser Entity — from `CSUSR01Y.cpy`

#### COBOL Definition (RECLN = 80)

```cobol
01 SEC-USER-DATA.
    05 SEC-USR-ID                 PIC X(08).
    05 SEC-USR-FNAME              PIC X(20).
    05 SEC-USR-LNAME              PIC X(20).
    05 SEC-USR-PWD                PIC X(08).
    05 SEC-USR-TYPE               PIC X(01).
    05 SEC-USR-FILLER             PIC X(23).
```

#### Java Entity

```java
@Entity
@Table(name = "sec_users")
public class SecUser implements UserDetails {
    @Id
    @Column(name = "usr_id", length = 8, nullable = false)
    private String userId;

    @Column(name = "usr_first_name", length = 20)
    private String firstName;

    @Column(name = "usr_last_name", length = 20)
    private String lastName;

    @Column(name = "usr_pwd", length = 60)
    private String password;                      // Will store BCrypt hash

    @Column(name = "usr_type", length = 1)
    private String userType;                      // 'A' = Admin, 'U' = User
}
```

#### DDL

```sql
CREATE TABLE sec_users (
    usr_id          VARCHAR(8)   NOT NULL PRIMARY KEY,
    usr_first_name  VARCHAR(20),
    usr_last_name   VARCHAR(20),
    usr_pwd         VARCHAR(60)  NOT NULL,        -- BCrypt hash
    usr_type        VARCHAR(1)   NOT NULL DEFAULT 'U'
);
```

### 2.7 Transaction Entity — from `CVTRA05Y.cpy`

#### COBOL Definition (RECLN = 350)

```cobol
01  TRAN-RECORD.
    05  TRAN-ID                                 PIC X(16).
    05  TRAN-TYPE-CD                            PIC X(02).
    05  TRAN-CAT-CD                             PIC 9(04).
    05  TRAN-SOURCE                             PIC X(10).
    05  TRAN-DESC                               PIC X(100).
    05  TRAN-AMT                                PIC S9(09)V99.
    05  TRAN-MERCHANT-ID                        PIC 9(09).
    05  TRAN-MERCHANT-NAME                      PIC X(50).
    05  TRAN-MERCHANT-CITY                      PIC X(50).
    05  TRAN-MERCHANT-ZIP                       PIC X(10).
    05  TRAN-CARD-NUM                           PIC X(16).
    05  TRAN-ORIG-TS                            PIC X(26).
    05  TRAN-PROC-TS                            PIC X(26).
    05  FILLER                                  PIC X(20).
```

#### Java Entity

```java
@Entity
@Table(name = "transactions")
public class Transaction {
    @Id
    @Column(name = "tran_id", length = 16, nullable = false)
    private String tranId;

    @Column(name = "tran_type_cd", length = 2)
    private String typeCode;

    @Column(name = "tran_cat_cd")
    private Integer categoryCode;                 // PIC 9(04)

    @Column(name = "tran_source", length = 10)
    private String source;

    @Column(name = "tran_desc", length = 100)
    private String description;

    @Column(name = "tran_amt", precision = 11, scale = 2)
    private BigDecimal amount;                    // PIC S9(09)V99

    @Column(name = "tran_merchant_id")
    private Long merchantId;

    @Column(name = "tran_merchant_name", length = 50)
    private String merchantName;

    @Column(name = "tran_merchant_city", length = 50)
    private String merchantCity;

    @Column(name = "tran_merchant_zip", length = 10)
    private String merchantZip;

    @Column(name = "tran_card_num", length = 16)
    private String cardNum;

    @Column(name = "tran_orig_ts")
    private LocalDateTime originTimestamp;         // PIC X(26)

    @Column(name = "tran_proc_ts")
    private LocalDateTime processedTimestamp;      // PIC X(26)
}
```

#### DDL

```sql
CREATE TABLE transactions (
    tran_id              VARCHAR(16)   NOT NULL PRIMARY KEY,
    tran_type_cd         VARCHAR(2),
    tran_cat_cd          INT,
    tran_source          VARCHAR(10),
    tran_desc            VARCHAR(100),
    tran_amt             DECIMAL(11,2) NOT NULL DEFAULT 0,
    tran_merchant_id     BIGINT,
    tran_merchant_name   VARCHAR(50),
    tran_merchant_city   VARCHAR(50),
    tran_merchant_zip    VARCHAR(10),
    tran_card_num        VARCHAR(16),
    tran_orig_ts         TIMESTAMP,
    tran_proc_ts         TIMESTAMP
);
```

### 2.8 DailyTransaction Entity — from `CVTRA06Y.cpy`

Same structure as `Transaction` (RECLN = 350) but used for the staging/daily file. In Java, model as the same `Transaction` entity or a separate `DailyTransaction` staging table that gets merged into `transactions` after posting.

```sql
CREATE TABLE daily_transactions (
    -- identical columns to transactions table
    -- this is a staging table cleared after each nightly batch run
    LIKE transactions INCLUDING ALL
);
```

### 2.9 TransactionCategoryBalance Entity — from `CVTRA01Y.cpy`

#### COBOL Definition (RECLN = 50)

```cobol
01  TRAN-CAT-BAL-RECORD.
    05  TRAN-CAT-KEY.
       10 TRANCAT-ACCT-ID                       PIC 9(11).
       10 TRANCAT-TYPE-CD                       PIC X(02).
       10 TRANCAT-CD                            PIC 9(04).
    05  TRAN-CAT-BAL                            PIC S9(09)V99.
    05  FILLER                                  PIC X(22).
```

#### Java Entity

```java
@Entity
@Table(name = "tran_cat_balances")
@IdClass(TranCatBalanceId.class)
public class TranCatBalance {
    @Id
    @Column(name = "acct_id")
    private Long acctId;

    @Id
    @Column(name = "tran_type_cd", length = 2)
    private String typeCode;

    @Id
    @Column(name = "tran_cat_cd")
    private Integer categoryCode;

    @Column(name = "tran_cat_bal", precision = 11, scale = 2)
    private BigDecimal balance;
}
```

#### DDL

```sql
CREATE TABLE tran_cat_balances (
    acct_id         BIGINT       NOT NULL,
    tran_type_cd    VARCHAR(2)   NOT NULL,
    tran_cat_cd     INT          NOT NULL,
    tran_cat_bal    DECIMAL(11,2) NOT NULL DEFAULT 0,
    PRIMARY KEY (acct_id, tran_type_cd, tran_cat_cd),
    CONSTRAINT fk_tcb_account FOREIGN KEY (acct_id) REFERENCES accounts(acct_id)
);
```

### 2.10 DisclosureGroup Entity — from `CVTRA02Y.cpy`

#### COBOL Definition (RECLN = 50)

```cobol
01  DIS-GROUP-RECORD.
    05  DIS-GROUP-KEY.
       10 DIS-ACCT-GROUP-ID                     PIC X(10).
       10 DIS-TRAN-TYPE-CD                      PIC X(02).
       10 DIS-TRAN-CAT-CD                       PIC 9(04).
    05  DIS-INT-RATE                            PIC S9(04)V99.
    05  FILLER                                  PIC X(28).
```

#### Java Entity

```java
@Entity
@Table(name = "disclosure_groups")
@IdClass(DisclosureGroupId.class)
public class DisclosureGroup {
    @Id
    @Column(name = "acct_group_id", length = 10)
    private String acctGroupId;

    @Id
    @Column(name = "tran_type_cd", length = 2)
    private String tranTypeCode;

    @Id
    @Column(name = "tran_cat_cd")
    private Integer tranCatCode;

    @Column(name = "int_rate", precision = 6, scale = 2)
    private BigDecimal interestRate;              // PIC S9(04)V99
}
```

#### DDL

```sql
CREATE TABLE disclosure_groups (
    acct_group_id   VARCHAR(10)   NOT NULL,
    tran_type_cd    VARCHAR(2)    NOT NULL,
    tran_cat_cd     INT           NOT NULL,
    int_rate        DECIMAL(6,2)  NOT NULL DEFAULT 0,
    PRIMARY KEY (acct_group_id, tran_type_cd, tran_cat_cd)
);
```

### 2.11 TransactionType Entity — from `CVTRA03Y.cpy`

#### COBOL Definition (RECLN = 60)

```cobol
01  TRAN-TYPE-RECORD.
    05  TRAN-TYPE                               PIC X(02).
    05  TRAN-TYPE-DESC                          PIC X(50).
    05  FILLER                                  PIC X(08).
```

#### Java Entity

```java
@Entity
@Table(name = "transaction_types")
public class TransactionType {
    @Id
    @Column(name = "tran_type", length = 2)
    private String tranType;

    @Column(name = "tran_type_desc", length = 50)
    private String description;
}
```

#### DDL

```sql
CREATE TABLE transaction_types (
    tran_type       VARCHAR(2)  NOT NULL PRIMARY KEY,
    tran_type_desc  VARCHAR(50)
);
```

### 2.12 TransactionCategory Entity — from `CVTRA04Y.cpy`

#### COBOL Definition (RECLN = 60)

```cobol
01  TRAN-CAT-RECORD.
    05  TRAN-CAT-KEY.
       10  TRAN-TYPE-CD                         PIC X(02).
       10  TRAN-CAT-CD                          PIC 9(04).
    05  TRAN-CAT-TYPE-DESC                      PIC X(50).
    05  FILLER                                  PIC X(04).
```

#### Java Entity

```java
@Entity
@Table(name = "transaction_categories")
@IdClass(TransactionCategoryId.class)
public class TransactionCategory {
    @Id
    @Column(name = "tran_type_cd", length = 2)
    private String typeCode;

    @Id
    @Column(name = "tran_cat_cd")
    private Integer categoryCode;

    @Column(name = "tran_cat_type_desc", length = 50)
    private String description;
}
```

#### DDL

```sql
CREATE TABLE transaction_categories (
    tran_type_cd       VARCHAR(2)  NOT NULL,
    tran_cat_cd        INT         NOT NULL,
    tran_cat_type_desc VARCHAR(50),
    PRIMARY KEY (tran_type_cd, tran_cat_cd)
);
```

---

## 3. Batch Program Migration Spec

### 3.1 CBACT01C — Account File Reader/Writer

**Source**: `app/cbl/CBACT01C.cbl` (430 lines)

**Business Function**: Reads the account VSAM KSDS file sequentially, displays each record, and writes the data in multiple output formats: a fixed-length sequential file, an array-based file (with `OCCURS 5 TIMES` for balance fields), and a variable-length record file. Also calls the `COBDATFT` assembler subroutine for date format conversion.

**Input Files**:
- `ACCTFILE` — Account VSAM KSDS (copybook: `CVACT01Y`, key: `ACCT-ID`)

**Output Files**:
- `OUTFILE` — Fixed-length sequential file with selected account fields (includes `COMP-3` field for `ACCT-CURR-CYC-DEBIT`)
- `ARRYFILE` — Array-based output (account ID + 5 repeating balance groups)
- `VBRCFILE` — Variable-length record file (two record formats: short status record and longer balance record)

**Key Paragraphs (translated to pseudocode)**:
```
1000-ACCTFILE-GET-NEXT:
    READ next record from ACCTFILE into ACCOUNT-RECORD
    IF successful:
        CALL 1100-DISPLAY-ACCT-RECORD  (log all fields)
        CALL 1300-POPUL-ACCT-RECORD    (copy fields to output record)
        CALL 1350-WRITE-ACCT-RECORD    (write to OUTFILE)
        CALL 1400-POPUL-ARRAY-RECORD   (populate array structure)
        CALL 1450-WRITE-ARRY-RECORD    (write to ARRYFILE)
        CALL 1500-POPUL-VBRC-RECORD    (populate VB records)
        CALL 1550-WRITE-VB1-RECORD     (write short VB record)
        CALL 1575-WRITE-VB2-RECORD     (write long VB record)
    IF EOF: set END-OF-FILE = 'Y'
    IF error: display status, ABEND

1300-POPUL-ACCT-RECORD:
    Map ACCOUNT-RECORD fields to output record
    Call assembler 'COBDATFT' for date format conversion of ACCT-REISSUE-DATE
    Move formatted date to OUT-ACCT-REISSUE-DATE
```

**Spring Batch Target**:
```
Job: accountFileReaderJob
  Step 1: accountReadStep
    ItemReader: JPA reader for Account entity (or flat-file reader for VSAM migration)
    ItemProcessor: Format conversion (date formatting via java.time)
    ItemWriter: CompositeItemWriter writing to:
      - Fixed-format flat file
      - Array-format flat file
      - Variable-length flat file
```

### 3.2 CBTRN02C — Transaction Posting (Core Nightly Batch)

**Source**: `app/cbl/CBTRN02C.cbl` (731 lines)

**Business Function**: The core nightly batch job. Reads daily transactions from a sequential file, validates each against the cross-reference and account files, and if valid: (1) copies the transaction to the master transaction VSAM file, (2) updates the account's current balance and cycle credits/debits, and (3) updates the transaction-category balance file. Invalid transactions are written to a rejects file.

**Input Files**:
- `DALYTRAN` — Daily transaction sequential file (copybook: `CVTRA06Y`)
- `XREFFILE` — Card cross-reference VSAM KSDS (key: `XREF-CARD-NUM`)
- `ACCTFILE` — Account VSAM KSDS (key: `ACCT-ID`)
- `TCATBALF` — Transaction category balance VSAM KSDS (key: composite `ACCT-ID` + `TYPE-CD` + `CAT-CD`)

**Output Files**:
- `TRANFILE` — Transaction master VSAM KSDS (key: `TRAN-ID`)
- `DALYREJS` — Rejected transactions sequential file (350-byte record + 80-byte trailer)

**Key Paragraphs (translated to pseudocode)**:
```
MAIN:
    OPEN all files
    FOR EACH record in DALYTRAN:
        increment WS-TRANSACTION-COUNT
        CALL 1500-VALIDATE-TRAN
        IF validation passed:
            CALL 2000-POST-TRANSACTION
        ELSE:
            increment WS-REJECT-COUNT
            CALL 2500-WRITE-REJECT-REC
    CLOSE all files
    IF rejects > 0: set RETURN-CODE = 4

1500-VALIDATE-TRAN:
    1500-A-LOOKUP-XREF:
        READ XREF by DALYTRAN-CARD-NUM
        IF NOT FOUND: reject reason = 100, "INVALID CARD NUMBER"
    1500-B-LOOKUP-ACCT:
        READ ACCOUNT by XREF-ACCT-ID
        IF NOT FOUND: reject reason = 101, "ACCOUNT RECORD NOT FOUND"

2000-POST-TRANSACTION:
    Copy DALYTRAN fields to TRAN-RECORD
    Generate DB2-format processing timestamp
    CALL 2700-UPDATE-TCATBAL   (update category balance)
    CALL 2800-UPDATE-ACCOUNT-REC (update account)
    CALL 2900-WRITE-TRANSACTION-FILE (write to master)

2700-UPDATE-TCATBAL:
    Build composite key: XREF-ACCT-ID + DALYTRAN-TYPE-CD + DALYTRAN-CAT-CD
    READ TCATBAL by key
    IF NOT FOUND:
        CREATE new TRAN-CAT-BAL-RECORD, set balance = DALYTRAN-AMT
        WRITE new record
    ELSE:
        ADD DALYTRAN-AMT TO TRAN-CAT-BAL
        REWRITE record

2800-UPDATE-ACCOUNT-REC:
    ADD DALYTRAN-AMT TO ACCT-CURR-BAL
    IF DALYTRAN-AMT >= 0:
        ADD DALYTRAN-AMT TO ACCT-CURR-CYC-CREDIT
    ELSE:
        ADD DALYTRAN-AMT TO ACCT-CURR-CYC-DEBIT
    REWRITE ACCOUNT-RECORD
```

**Spring Batch Target**:
```
Job: transactionPostingJob
  Step 1: postTransactionsStep
    ItemReader: FlatFileItemReader<DailyTransaction> (or JPA reader from daily_transactions)
    ItemProcessor: TransactionPostingProcessor
      - Validate card via CardXrefRepository.findById(cardNum)
      - Validate account via AccountRepository.findById(acctId)
      - If invalid, route to rejects writer
    ItemWriter: CompositeItemWriter
      - TransactionRepository.save(transaction)
      - AccountService.updateBalance(acctId, amount)
        -> account.currentBalance += amount
        -> if amount >= 0: account.currentCycleCredit += amount
        -> else: account.currentCycleDebit += amount
      - TranCatBalanceService.updateOrCreate(acctId, typeCode, catCode, amount)
    Skip policy: Write rejects to flat file via ClassifierCompositeItemWriter
```

**Critical Business Rule** (from `2800-UPDATE-ACCOUNT-REC`):
```java
// In AccountService:
@Transactional
public void postTransaction(Long acctId, BigDecimal amount) {
    Account account = accountRepository.findById(acctId)
        .orElseThrow(() -> new AccountNotFoundException(acctId));
    account.setCurrentBalance(account.getCurrentBalance().add(amount));
    if (amount.compareTo(BigDecimal.ZERO) >= 0) {
        account.setCurrentCycleCredit(account.getCurrentCycleCredit().add(amount));
    } else {
        account.setCurrentCycleDebit(account.getCurrentCycleDebit().add(amount));
    }
    accountRepository.save(account);
}
```

### 3.3 CBACT04C — Interest Calculation

**Source**: `app/cbl/CBACT04C.cbl` (652 lines)

**Business Function**: Calculates monthly interest for each account based on transaction category balances and disclosure-group interest rates. Adds the total interest to the account's current balance, resets cycle credit/debit accumulators to zero, and writes interest-charge transactions to the transaction file.

**Input Files**:
- `TCATBALF` — Transaction category balance (sequential read)
- `XREFFILE` — Card cross-reference (random read by alternate key `XREF-ACCT-ID`)
- `ACCTFILE` — Account master (random read/update by `ACCT-ID`)
- `DISCGRP` — Disclosure group (random read by composite key: `GROUP-ID` + `TYPE-CD` + `CAT-CD`)

**Output Files**:
- `TRANSACT` — Transaction master (write new interest transactions)

**Parameters**:
- `PARM-DATE` — Processing date passed via JCL PARM (PIC X(10))

**Key Paragraphs (translated to pseudocode)**:
```
MAIN:
    OPEN all files
    FOR EACH TRAN-CAT-BAL-RECORD (sequentially):
        IF new account (ACCT-ID changed from last):
            IF not first record:
                CALL 1050-UPDATE-ACCOUNT  (apply accumulated interest)
            Reset WS-TOTAL-INT = 0
            CALL 1100-GET-ACCT-DATA   (read account by key)
            CALL 1110-GET-XREF-DATA   (read xref by alternate key ACCT-ID)
        Look up interest rate:
            CALL 1200-GET-INTEREST-RATE (read DISCGRP by GROUP-ID + TYPE + CAT)
            IF rate not found: try DEFAULT group
        IF interest rate != 0:
            CALL 1300-COMPUTE-INTEREST
            CALL 1400-COMPUTE-FEES     (placeholder — not yet implemented)
    CALL 1050-UPDATE-ACCOUNT  (process final account)
    CLOSE all files

1050-UPDATE-ACCOUNT:
    ADD WS-TOTAL-INT TO ACCT-CURR-BAL
    SET ACCT-CURR-CYC-CREDIT = 0
    SET ACCT-CURR-CYC-DEBIT = 0
    REWRITE account record

1300-COMPUTE-INTEREST:
    WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200
    ADD WS-MONTHLY-INT TO WS-TOTAL-INT
    Generate new transaction record:
        TRAN-ID = PARM-DATE + sequential suffix
        TRAN-TYPE-CD = '01'
        TRAN-CAT-CD = '05' (mapped to integer 5)
        TRAN-SOURCE = 'System'
        TRAN-DESC = 'Int. for a/c ' + ACCT-ID
        TRAN-AMT = WS-MONTHLY-INT
        TRAN-CARD-NUM = XREF-CARD-NUM
        TRAN-ORIG-TS = current timestamp
        TRAN-PROC-TS = current timestamp
    WRITE transaction record
```

**Interest Calculation Formula**:
```
monthlyInterest = (categoryBalance * annualInterestRate) / 1200
```
This divides by 1200 (= 12 months * 100 to convert percentage to decimal).

**Spring Batch Target**:
```
Job: interestCalculationJob
  Step 1: calculateInterestStep
    ItemReader: JPA reader for TranCatBalance (ordered by acctId)
    ItemProcessor: InterestCalculationProcessor
      - Group by account
      - For each category balance:
          Look up DisclosureGroup rate (fallback to 'DEFAULT' group)
          monthlyInterest = balance * rate / 1200
          Create interest Transaction record
      - Accumulate total interest per account
    ItemWriter: CompositeItemWriter
      - Save interest Transaction records
      - Update Account: balance += totalInterest, reset cycleCredit/Debit to 0
```

**Critical Business Rule** (from `1300-COMPUTE-INTEREST`):
```java
public BigDecimal calculateMonthlyInterest(BigDecimal categoryBalance, BigDecimal annualRate) {
    return categoryBalance.multiply(annualRate)
        .divide(new BigDecimal("1200"), 2, RoundingMode.HALF_UP);
}
```

### 3.4 CBEXPORT — Data Export for Branch Migration

**Source**: `app/cbl/CBEXPORT.cbl` (582 lines)

**Business Function**: Reads all CardDemo master files (customers, accounts, cross-references, transactions, cards) and writes them into a single multi-record-type export file. Each record is 500 bytes with a 1-byte record-type indicator ('C' = Customer, 'A' = Account, 'X' = Xref, 'T' = Transaction, 'D' = Card) followed by a 26-byte timestamp, 4-byte sequence number, branch ID, region code, and the record data. Uses `REDEFINES` for polymorphic records and `COMP`/`COMP-3` fields in the export layout (see `CVEXPORT.cpy`).

**Input Files**: CUSTFILE, ACCTFILE, XREFFILE, TRANSACT, CARDFILE (all VSAM KSDS)

**Output Files**: EXPFILE (indexed sequential, key: sequence number, RECLN = 500)

**Spring Batch Target**:
```
Job: dataExportJob
  Step 1: exportCustomersStep   (read customers -> write JSON/CSV export)
  Step 2: exportAccountsStep    (read accounts -> append to export)
  Step 3: exportXrefsStep       (read xrefs -> append to export)
  Step 4: exportTransactionsStep (read transactions -> append to export)
  Step 5: exportCardsStep       (read cards -> append to export)
```

In the Java world, replace the fixed-length multi-record COBOL format with JSON or CSV exports. If byte-level compatibility is needed for data migration, implement a `FlatFileItemWriter` with a custom `LineAggregator` that produces the exact 500-byte fixed-width format.

### 3.5 CBIMPORT — Data Import from Branch Migration

**Source**: `app/cbl/CBIMPORT.cbl` (487 lines)

**Business Function**: Reads the multi-record export file and splits it back into normalized target files. Routes records by `EXPORT-REC-TYPE`: 'C' -> Customer, 'A' -> Account, 'X' -> Xref, 'T' -> Transaction, 'D' -> Card. Validates data and writes errors to an error output file.

**Spring Batch Target**:
```
Job: dataImportJob
  Step 1: importStep
    ItemReader: FlatFileItemReader for export file (or JSON reader)
    ItemProcessor: ClassifierCompositeItemProcessor
      - Route by record type
      - Validate required fields
    ItemWriter: ClassifierCompositeItemWriter
      - 'C' -> CustomerRepository.save()
      - 'A' -> AccountRepository.save()
      - 'X' -> CardXrefRepository.save()
      - 'T' -> TransactionRepository.save()
      - 'D' -> CardRepository.save()
    SkipPolicy: Write validation errors to error file
```

### 3.6 CBSTM03A — Statement Generation

**Source**: `app/cbl/CBSTM03A.CBL` (924 lines)

**Business Function**: Generates account statements in two formats: plain text and HTML. Reads transaction records (via `COSTM01.CPY` layout with compound key `TRNX-CARD-NUM` + `TRNX-ID`), cross-references, customer records, and account records. Groups transactions by card/account, formats headers with customer name and address, itemizes transactions, and calculates page/account/grand totals. Uses `ALTER` and `GO TO` for control flow and calls subroutine `CBSTM03B` for all VSAM I/O.

**Spring Batch Target**:
```
Job: statementGenerationJob
  Step 1: generateStatementsStep
    ItemReader: JPA reader for transactions ordered by card_num, tran_id
    ItemProcessor: StatementProcessor
      - Group transactions by account (via CardXref lookup)
      - Look up customer name/address
      - Look up account balance info
      - Format statement lines with running totals
    ItemWriter: CompositeItemWriter
      - FlatFileItemWriter for plain-text statements
      - Custom ItemWriter for HTML statement output
```

### 3.7 CBTRN03C — Transaction Detail Report

**Source**: `app/cbl/CBTRN03C.cbl`

**Business Function**: Prints a formatted transaction detail report with headers, transaction lines (ID, account, type description, category description, source, amount), page totals, account totals, and grand totals. Uses `CVTRA07Y.cpy` for report layout structures.

**Spring Batch Target**:
```
Job: transactionReportJob
  Step 1: generateReportStep
    ItemReader: JPA reader for transactions
    ItemProcessor: ReportLineProcessor (join with TransactionType and TransactionCategory for descriptions)
    ItemWriter: FlatFileItemWriter with custom header/footer callbacks for totals
```

### 3.8 Other Batch Programs

| Program | Function | Spring Batch Equivalent |
|:--------|:---------|:------------------------|
| `CBACT02C` | Read/print card file | Simple `tasklet` or `ItemReader`/`ItemWriter` for card data dump |
| `CBACT03C` | Read/print xref file | Simple `tasklet` for xref data dump |
| `CBCUS01C` | Read/print customer file | Simple `tasklet` for customer data dump |
| `CBTRN01C` | Transaction posting variant | Merge logic into `CBTRN02C` job or create a variant step |
| `COBSWAIT` | Timer wait | Replace with Spring `@Scheduled` or cron trigger |

---

## 4. Online (CICS) Program Migration Spec

### 4.1 CICS-to-Spring Mapping Reference

| CICS Concept | Spring Boot Equivalent |
|:-------------|:-----------------------|
| `EXEC CICS READ DATASET` | `JpaRepository.findById()` |
| `EXEC CICS READ UPDATE` + `REWRITE` | `@Transactional` + `JpaRepository.save()` with `@Version` for optimistic locking |
| `EXEC CICS WRITE` | `JpaRepository.save()` (new entity) |
| `EXEC CICS DELETE` | `JpaRepository.deleteById()` |
| `EXEC CICS STARTBR` + `READNEXT` | `JpaRepository` with `Pageable` or custom query with cursor |
| `COMMAREA` | JWT claims or server-side session (Spring Session) |
| `EXEC CICS XCTL` | Internal service call or redirect |
| `EXEC CICS RETURN TRANSID` | HTTP response (pseudo-conversational -> stateless REST) |
| `BMS SEND MAP` | JSON response body (or Thymeleaf/React template) |
| `BMS RECEIVE MAP` | JSON request body / `@RequestBody` DTO |
| `EIBAID` (PF keys) | Separate REST endpoints or action parameter |
| `DFHCOMMAREA` | `@SessionAttribute` or JWT token |
| `CICS ABEND` | Throw application exception -> `@ControllerAdvice` |

### 4.2 COSGN00C — Sign-on

**Source**: `app/cbl/COSGN00C.cbl` (260 lines)  
**Transaction**: CC00 | **Map**: COSGN00

**Business Logic**:
1. Display sign-on screen with User ID and Password fields
2. On ENTER: validate that both fields are non-empty
3. Read `USRSEC` VSAM file by User ID (`EXEC CICS READ`)
4. Compare entered password with `SEC-USR-PWD`
5. If match: populate COMMAREA with user info, route to admin menu (`COADM01C`) if `SEC-USR-TYPE = 'A'`, or main menu (`COMEN01C`) if `SEC-USR-TYPE = 'U'`
6. If no match: display "Wrong Password" error
7. If user not found (RESP = 13): display "User not found" error
8. PF3: display thank-you message and exit

**REST API Target**:

```
POST /api/auth/login
  Request:  { "userId": "ADMIN001", "password": "PASSWORD" }
  Response: { "token": "jwt...", "userType": "A", "firstName": "...", "lastName": "..." }
  Errors:   401 Unauthorized ("Wrong Password" / "User not found")
```

**Implementation Notes**:
- Replace plaintext password comparison with BCrypt: `passwordEncoder.matches(rawPassword, user.getPassword())`
- Issue JWT token containing `userId`, `userType`, `firstName`, `lastName`
- Migrate existing users with a one-time script that hashes the current plaintext passwords
- Spring Security config: stateless session, JWT filter, role-based access (`ROLE_ADMIN`, `ROLE_USER`)

### 4.3 COACTVWC — Account View

**Source**: `app/cbl/COACTVWC.cbl` (941 lines)  
**Transaction**: CAVW | **Map**: COACTVW

**Business Logic**:
1. Accept account ID (and optionally customer ID) filter
2. Read account record from ACCTFILE VSAM by key
3. Look up customer via XREF: card -> customer
4. Display all account fields on screen (read-only)
5. PF3: return to menu, PF7/PF8: previous/next account

**REST API Target**:

```
GET /api/accounts/{acctId}
  Response: AccountDetailDTO
    {
      "acctId": 12345678901,
      "activeStatus": "Y",
      "currentBalance": 1500.00,
      "creditLimit": 5000.00,
      "cashCreditLimit": 1500.00,
      "openDate": "2020-01-15",
      "expirationDate": "2025-01-15",
      "reissueDate": "2023-06-01",
      "currentCycleCredit": 200.00,
      "currentCycleDebit": -50.00,
      "addressZip": "10001",
      "groupId": "DEFAULT",
      "customer": { "custId": 123456789, "firstName": "...", ... }
    }
  Errors: 404 Not Found
```

### 4.4 COACTUPC — Account Update

**Source**: `app/cbl/COACTUPC.cbl` (4236 lines — the largest program)  
**Transaction**: CAUP | **Map**: COACTUP

**Business Logic**:
1. Display account details for editing
2. Validate all input fields (extensive validation using `CSUTLDPY` / `CSUTLDWY` date validation, `CSLKPCDY` lookup codes for phone/state/zip):
   - Credit limit: must be valid signed number (`PIC S9(10)V99`)
   - Cash credit limit: must be valid signed number
   - Open/expiration/reissue dates: full CCYYMMDD validation (century check, month 1-12, day validation including leap year)
   - Address fields: alphanumeric validation
   - State code: must be valid US state
   - Zip code: first 2 digits must match state
   - Phone numbers: area code must be valid North American area code
3. If all valid: `EXEC CICS READ UPDATE` + `REWRITE` the account record
4. PF3: cancel and return to menu

**REST API Target**:

```
PUT /api/accounts/{acctId}
  Request:  AccountUpdateDTO (all editable fields)
  Response: AccountDetailDTO (updated)
  Errors:   400 Bad Request (validation errors), 404 Not Found, 409 Conflict (optimistic lock)
```

**Implementation Notes**:
- Add `@Version` column to `Account` entity for optimistic locking (replaces CICS `READ UPDATE` + `REWRITE`)
- Port all validation logic to a `@Validated` DTO with custom validators:
  - `@ValidSignedDecimal` for credit limit fields
  - `@ValidDate` for date fields
  - `@ValidUSState` for state code
  - `@ValidPhoneAreaCode` for phone numbers
  - `@ValidZipForState` for zip/state consistency
- The phone area code table from `CSLKPCDY.cpy` (1318 lines) should be loaded as reference data

### 4.5 Card Management Programs

#### COCRDLIC — Credit Card List

**Source**: `app/cbl/COCRDLIC.cbl` | **Transaction**: CCLI | **Map**: COCRDLI

```
GET /api/accounts/{acctId}/cards?page=0&size=10
  Response: Page<CardSummaryDTO>
```

Uses `EXEC CICS STARTBR` + `READNEXT` for browsing cards by account. Map to Spring Data pagination.

#### COCRDSLC — Credit Card View

**Source**: `app/cbl/COCRDSLC.cbl` | **Transaction**: CCDL | **Map**: COCRDSL

```
GET /api/cards/{cardNum}
  Response: CardDetailDTO
```

#### COCRDUPC — Credit Card Update

**Source**: `app/cbl/COCRDUPC.cbl` | **Transaction**: CCUP | **Map**: COCRDUP

```
PUT /api/cards/{cardNum}
  Request:  CardUpdateDTO { embossedName, expirationDate, activeStatus }
  Response: CardDetailDTO
```

### 4.6 Transaction Programs

#### COTRN00C — Transaction List

**Source**: `app/cbl/COTRN00C.cbl` | **Transaction**: CT00 | **Map**: COTRN00

```
GET /api/accounts/{acctId}/transactions?page=0&size=10
  Response: Page<TransactionSummaryDTO>
```

#### COTRN01C — Transaction View

**Source**: `app/cbl/COTRN01C.cbl` | **Transaction**: CT01 | **Map**: COTRN01

```
GET /api/transactions/{tranId}
  Response: TransactionDetailDTO
```

#### COTRN02C — Transaction Add (Online)

**Source**: `app/cbl/COTRN02C.cbl` | **Transaction**: CT02 | **Map**: COTRN02

```
POST /api/transactions
  Request:  TransactionCreateDTO { cardNum, typeCode, categoryCode, amount, description, source, merchantId, merchantName, merchantCity, merchantZip }
  Response: TransactionDetailDTO (201 Created)
```

### 4.7 Report & Bill Payment Programs

#### CORPT00C — Transaction Reports

**Source**: `app/cbl/CORPT00C.cbl` | **Transaction**: CR00 | **Map**: CORPT00

```
POST /api/reports/transactions
  Request:  { "startDate": "...", "endDate": "...", "acctId": ... }
  Response: Report file download (PDF or CSV) or async job ID
```

#### COBIL00C — Bill Payment

**Source**: `app/cbl/COBIL00C.cbl` | **Transaction**: CB00 | **Map**: COBIL00

```
POST /api/payments
  Request:  { "acctId": ..., "amount": ..., "paymentMethod": "..." }
  Response: PaymentConfirmationDTO
```

### 4.8 User Administration Programs

#### COUSR00C — List Users

```
GET /api/admin/users?page=0&size=10
  Response: Page<UserSummaryDTO>
  Auth: ROLE_ADMIN required
```

#### COUSR01C — Add User

```
POST /api/admin/users
  Request:  { "userId": "...", "firstName": "...", "lastName": "...", "password": "...", "userType": "U" }
  Response: UserDTO (201 Created)
  Auth: ROLE_ADMIN required
```

#### COUSR02C — Update User

```
PUT /api/admin/users/{userId}
  Request:  UserUpdateDTO
  Response: UserDTO
  Auth: ROLE_ADMIN required
```

#### COUSR03C — Delete User

```
DELETE /api/admin/users/{userId}
  Response: 204 No Content
  Auth: ROLE_ADMIN required
```

### 4.9 Menu/Admin Programs

#### COMEN01C — Main Menu

**Source**: `app/cbl/COMEN01C.cbl`

The main menu is defined in `COMEN02Y.cpy` with 11 options. In the REST API, this becomes the available endpoint listing. No separate endpoint needed — the frontend handles navigation. Optionally expose:

```
GET /api/menu
  Response: { "options": [ { "id": 1, "name": "Account View", "endpoint": "/api/accounts" }, ... ] }
```

#### COADM01C — Admin Menu

**Source**: `app/cbl/COADM01C.cbl`

Admin menu options are defined in `COADM02Y.cpy` (6 options including Db2 transaction type management). Same approach — frontend navigation or:

```
GET /api/admin/menu
  Response: admin-specific option list
  Auth: ROLE_ADMIN required
```

---

## 5. Target Java Project Structure

### 5.1 Maven Multi-Module Layout

```
carddemo-java/
├── pom.xml                               # Parent POM
├── carddemo-domain/                      # JPA entities, repositories, shared DTOs
│   ├── pom.xml
│   └── src/main/java/com/carddemo/domain/
│       ├── entity/
│       │   ├── Account.java
│       │   ├── Customer.java
│       │   ├── Card.java
│       │   ├── CardXref.java
│       │   ├── SecUser.java
│       │   ├── Transaction.java
│       │   ├── TranCatBalance.java
│       │   ├── DisclosureGroup.java
│       │   ├── TransactionType.java
│       │   └── TransactionCategory.java
│       ├── repository/
│       │   ├── AccountRepository.java
│       │   ├── CustomerRepository.java
│       │   ├── CardRepository.java
│       │   ├── CardXrefRepository.java
│       │   ├── SecUserRepository.java
│       │   ├── TransactionRepository.java
│       │   ├── TranCatBalanceRepository.java
│       │   ├── DisclosureGroupRepository.java
│       │   ├── TransactionTypeRepository.java
│       │   └── TransactionCategoryRepository.java
│       └── dto/
│           ├── AccountDetailDTO.java
│           ├── AccountUpdateDTO.java
│           ├── CardDetailDTO.java
│           ├── TransactionDetailDTO.java
│           ├── TransactionCreateDTO.java
│           └── ...
├── carddemo-batch/                       # Spring Batch jobs
│   ├── pom.xml
│   └── src/main/java/com/carddemo/batch/
│       ├── config/
│       │   └── BatchConfiguration.java
│       ├── job/
│       │   ├── TransactionPostingJob.java       # CBTRN02C
│       │   ├── InterestCalculationJob.java      # CBACT04C
│       │   ├── StatementGenerationJob.java      # CBSTM03A
│       │   ├── TransactionReportJob.java        # CBTRN03C
│       │   ├── DataExportJob.java               # CBEXPORT
│       │   ├── DataImportJob.java               # CBIMPORT
│       │   └── AccountFileReaderJob.java        # CBACT01C
│       ├── processor/
│       │   ├── TransactionPostingProcessor.java
│       │   ├── InterestCalculationProcessor.java
│       │   └── StatementProcessor.java
│       └── service/
│           ├── AccountBalanceService.java
│           └── TranCatBalanceService.java
├── carddemo-api/                         # Spring Boot REST controllers + services
│   ├── pom.xml
│   └── src/main/java/com/carddemo/api/
│       ├── CardDemoApplication.java
│       ├── controller/
│       │   ├── AuthController.java              # COSGN00C
│       │   ├── AccountController.java           # COACTVWC, COACTUPC
│       │   ├── CardController.java              # COCRDLIC, COCRDSLC, COCRDUPC
│       │   ├── TransactionController.java       # COTRN00C, COTRN01C, COTRN02C
│       │   ├── ReportController.java            # CORPT00C
│       │   ├── PaymentController.java           # COBIL00C
│       │   └── AdminUserController.java         # COUSR00C-03C
│       ├── service/
│       │   ├── AccountService.java
│       │   ├── CardService.java
│       │   ├── TransactionService.java
│       │   └── UserService.java
│       ├── validation/
│       │   ├── ValidUSState.java
│       │   ├── ValidPhoneAreaCode.java
│       │   ├── ValidZipForState.java
│       │   └── USStateValidator.java
│       └── exception/
│           ├── GlobalExceptionHandler.java
│           ├── AccountNotFoundException.java
│           └── InvalidTransactionException.java
├── carddemo-security/                    # Spring Security configuration
│   ├── pom.xml
│   └── src/main/java/com/carddemo/security/
│       ├── SecurityConfig.java
│       ├── JwtTokenProvider.java
│       ├── JwtAuthenticationFilter.java
│       └── UserDetailsServiceImpl.java
├── carddemo-web/                         # Optional frontend placeholder
│   └── pom.xml
└── src/main/resources/
    ├── db/migration/                     # Flyway migrations
    │   ├── V1__create_accounts.sql
    │   ├── V2__create_customers.sql
    │   ├── V3__create_cards.sql
    │   ├── V4__create_card_xref.sql
    │   ├── V5__create_sec_users.sql
    │   ├── V6__create_transactions.sql
    │   ├── V7__create_tran_cat_balances.sql
    │   ├── V8__create_disclosure_groups.sql
    │   ├── V9__create_transaction_types.sql
    │   ├── V10__create_transaction_categories.sql
    │   ├── V11__create_daily_transactions.sql
    │   └── V12__seed_reference_data.sql
    └── application.yml
```

### 5.2 Recommended Dependencies

```xml
<!-- Parent POM dependencies -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.0</version>
</parent>

<dependencies>
    <!-- Core -->
    <dependency>spring-boot-starter-web</dependency>
    <dependency>spring-boot-starter-data-jpa</dependency>
    <dependency>spring-boot-starter-validation</dependency>

    <!-- Batch -->
    <dependency>spring-boot-starter-batch</dependency>

    <!-- Security -->
    <dependency>spring-boot-starter-security</dependency>
    <dependency>io.jsonwebtoken:jjwt-api:0.12.5</dependency>
    <dependency>io.jsonwebtoken:jjwt-impl:0.12.5</dependency>
    <dependency>io.jsonwebtoken:jjwt-jackson:0.12.5</dependency>

    <!-- Database -->
    <dependency>org.postgresql:postgresql</dependency>
    <dependency>org.flywaydb:flyway-core</dependency>
    <dependency>org.flywaydb:flyway-database-postgresql</dependency>

    <!-- Mapping -->
    <dependency>org.mapstruct:mapstruct:1.6.0</dependency>
    <dependency>org.mapstruct:mapstruct-processor:1.6.0</dependency>

    <!-- Monetary -->
    <!-- Use java.math.BigDecimal for ALL monetary fields -->

    <!-- Testing -->
    <dependency>spring-boot-starter-test</dependency>
    <dependency>spring-batch-test</dependency>
    <dependency>com.h2database:h2 (test scope)</dependency>
</dependencies>
```

### 5.3 Application Configuration

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/carddemo
    username: carddemo
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate  # Flyway manages schema
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration
  batch:
    jdbc:
      initialize-schema: always
    job:
      enabled: false  # Jobs triggered by REST endpoint or scheduler

carddemo:
  jwt:
    secret: ${JWT_SECRET}
    expiration: 3600000  # 1 hour
```

---

## 6. COBOL-to-Java Pitfalls Checklist

### 6.1 Numeric Precision

| Pitfall | Details | Mitigation |
|:--------|:--------|:-----------|
| Packed decimal (`PIC S9(10)V99 COMP-3`) | COBOL stores in BCD format with implied decimal | Use `BigDecimal(precision=12, scale=2)`. **NEVER** use `double` or `float` |
| Implied decimal (`V`) | The `V` in `PIC S9(10)V99` means 2 implied decimal places — no actual decimal point in storage | Set `BigDecimal.setScale(2, RoundingMode.HALF_UP)` |
| COBOL `COMPUTE` rounding | COBOL uses `ROUNDED` phrase which defaults to round-half-away-from-zero | Use `RoundingMode.HALF_UP` (Java equivalent of COBOL rounding) |
| Interest calculation precision | `(balance * rate) / 1200` can produce repeating decimals | Compute with higher precision internally, then `setScale(2, RoundingMode.HALF_UP)` |
| Signed fields | `PIC S9(n)V99` allows negative values (e.g., debits) | Ensure `BigDecimal` columns allow negative; validate sign in business logic |

### 6.2 String Handling

| Pitfall | Details | Mitigation |
|:--------|:--------|:-----------|
| Fixed-length COBOL strings | `PIC X(n)` pads with trailing spaces | `String.trim()` on every field read from file/DB. Apply `@PrePersist`/`@PreUpdate` to trim before save |
| EBCDIC encoding | Mainframe data files in `app/data/EBCDIC/` are EBCDIC-encoded | Convert to UTF-8 before import: `new String(bytes, Charset.forName("IBM037"))` |
| UPPER-CASE conversion | COBOL `FUNCTION UPPER-CASE` used for user IDs | Apply `.toUpperCase()` in login flow; store IDs consistently |
| `LOW-VALUES` / `HIGH-VALUES` | COBOL uses `LOW-VALUES` (hex 00) for "empty" and `HIGH-VALUES` (hex FF) for "max" | Map to `null` or empty string; use `Optional` in Java |

### 6.3 Date Handling

| Pitfall | Details | Mitigation |
|:--------|:--------|:-----------|
| `PIC X(10)` date fields | Stored as `"yyyy-MM-dd"` string in COBOL | Parse with `LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd"))` |
| `COBDATFT` assembler call | Called in `CBACT01C.cbl` line 231 for date format conversion between `YYYYMMDD` and `YYYY-MM-DD` | Replace with `java.time.format.DateTimeFormatter` conversions |
| `CODATECN.cpy` date conversion | Supports two formats: type "1" = YYYYMMDD, type "2" = YYYY-MM-DD | Implement a `DateConversionUtil` class: `parseYYYYMMDD()`, `parseYYYY_MM_DD()`, `formatToYYYYMMDD()`, `formatToYYYY_MM_DD()` |
| Date validation | `CSUTLDPY.cpy` validates century (19/20 only), month (1-12), day (1-31 with month rules, leap year) | Use `LocalDate.of()` which inherently validates; add custom century check if needed |
| Timestamp format | DB2 format: `"YYYY-MM-DD-HH.mm.ss.SSSSSS"` (note hyphens and dots) | Parse with `DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS")` |

### 6.4 File and Data Migration

| Pitfall | Details | Mitigation |
|:--------|:--------|:-----------|
| VSAM KSDS -> RDBMS | COBOL accesses VSAM files by key with `READ`, `WRITE`, `REWRITE`, `DELETE` | Map to JPA `Repository` methods; indexed VSAM keys become DB primary keys or unique indexes |
| VSAM alternate index (AIX) | `CBACT04C` reads XREF by alternate key `XREF-ACCT-ID` | Add `@Index` on `xref_acct_id` column; use `findByAcctId()` repository method |
| Record FILLER fields | Padding to reach fixed record length | Drop completely in Java — no padding needed |
| `REDEFINES` clause | `CVEXPORT.cpy` uses `REDEFINES` for polymorphic records | Use Java inheritance or a discriminator pattern (e.g., `@DiscriminatorColumn` or a `recordType` field with factory pattern) |
| `OCCURS` (arrays) | `CBACT01C` output has `OCCURS 5 TIMES` for balance arrays | Map to `List<BalanceEntry>` or a separate child table |
| Variable-length records | `CBACT01C` writes VB records with `RECORDING MODE IS V` | Not needed in Java/RDBMS; if file output required, use `FlatFileItemWriter` with multiple record formats |
| `COMP` fields | Binary storage (e.g., `PIC 9(9) COMP` = 4-byte binary integer) | Map to `Integer` or `Long` in Java |
| `COMP-3` fields | Packed decimal storage (e.g., `PIC S9(10)V99 COMP-3` = 7 bytes) | Map to `BigDecimal` in Java; if reading raw EBCDIC files, use a packed-decimal parser |
| Sequence numbers | Export file uses `PIC 9(9) COMP` for sequence | Use `@GeneratedValue` or `AtomicLong` counter |

### 6.5 Transaction and Locking

| Pitfall | Details | Mitigation |
|:--------|:--------|:-----------|
| CICS pseudo-conversational | State saved in COMMAREA between screen interactions | Use stateless REST with JWT; store any needed state client-side |
| `READ UPDATE` + `REWRITE` | CICS locks record between read and rewrite | Use `@Version` (optimistic locking) with `@Transactional` |
| Batch file locking | COBOL batch opens files with `OPEN I-O` for exclusive access | Use database-level `SELECT ... FOR UPDATE` or Spring Batch chunk transactions |
| ABEND handling | COBOL ABENDs with error code on unrecoverable errors | Throw custom exceptions caught by `@ControllerAdvice` (REST) or Spring Batch `SkipPolicy` (batch) |

---

## 7. Migration Execution Order

### Phase 1: Data Layer (Weeks 1-3)

**Goal**: Establish the database schema and seed with migrated data.

1. **Set up project skeleton**
   - Create Maven multi-module project structure
   - Configure Spring Boot, Spring Data JPA, Flyway, PostgreSQL
2. **Create Flyway migrations** (all DDL from Section 2)
   - `V1` through `V11`: Create all tables with constraints and indexes
   - `V12`: Seed reference data (transaction types from `CVTRA03Y`, transaction categories from `CVTRA04Y`, disclosure groups from `CVTRA02Y`)
3. **Implement JPA entities** (all entities from Section 2)
4. **Implement JPA repositories** with necessary custom queries
5. **Data migration scripts**:
   - Write EBCDIC-to-UTF-8 converter for files in `app/data/EBCDIC/`
   - Write fixed-width file parsers for each copybook record layout
   - Load accounts, customers, cards, xrefs, transactions, users
   - Verify record counts match source

**Validation Milestone**: All tables populated, JPA tests pass with H2 in-memory DB.

### Phase 2: Batch Programs (Weeks 3-5)

**Goal**: Migrate batch processing to Spring Batch.

1. **CBACT01C — Account File Reader** (simplest end-to-end batch validation)
   - Good first batch job: reads accounts, writes to output files
   - Validates JPA reader, item processing, and flat-file writer work correctly
2. **CBTRN02C — Transaction Posting** (core business logic)
   - Most critical job: daily transaction processing
   - Implement validation, account balance updates, category balance updates
   - Implement reject handling
3. **CBACT04C — Interest Calculation**
   - Depends on correct transaction posting (TCATBALF populated)
   - Interest formula: `(balance * rate) / 1200`
   - Test with known data to verify calculation matches COBOL output
4. **CBSTM03A — Statement Generation**
   - Depends on populated transaction data
   - Generate plain-text and HTML statements
5. **CBTRN03C — Transaction Report**
6. **CBEXPORT / CBIMPORT — Export/Import**
   - Data migration utilities

**Validation Milestone**: Run full nightly batch sequence (POSTTRAN -> INTCALC -> CREASTMT) and compare output with COBOL batch results.

### Phase 3: Online Programs — Read Operations (Weeks 5-7)

**Goal**: Implement read-only REST APIs first to validate data access patterns.

1. **COACTVWC — Account View** (`GET /api/accounts/{id}`)
   - Start here: simplest read-only endpoint
   - Validates entity mapping, repository queries, DTO transformation
2. **COCRDLIC — Card List** (`GET /api/accounts/{acctId}/cards`)
3. **COCRDSLC — Card View** (`GET /api/cards/{cardNum}`)
4. **COTRN00C — Transaction List** (`GET /api/accounts/{acctId}/transactions`)
5. **COTRN01C — Transaction View** (`GET /api/transactions/{tranId}`)
6. **COUSR00C — User List** (`GET /api/admin/users`)

**Validation Milestone**: All read endpoints return correct data for test accounts.

### Phase 4: Online Programs — Write Operations + Security (Weeks 7-9)

**Goal**: Implement write operations and authentication.

1. **COSGN00C — Authentication** (`POST /api/auth/login`)
   - Spring Security configuration
   - JWT token generation and validation
   - BCrypt password migration
2. **COACTUPC — Account Update** (`PUT /api/accounts/{id}`)
   - Port all validation logic (dates, phone numbers, state codes, zip codes)
   - Optimistic locking
3. **COCRDUPC — Card Update** (`PUT /api/cards/{cardNum}`)
4. **COTRN02C — Transaction Add** (`POST /api/transactions`)
5. **COBIL00C — Bill Payment** (`POST /api/payments`)
6. **CORPT00C — Reports** (`POST /api/reports/transactions`)
7. **COUSR01C-03C — User CRUD** (admin-only endpoints)

**Validation Milestone**: Full API test suite with authenticated requests passes.

### Phase 5: Web UI (Weeks 9-12)

**Goal**: Replace BMS 3270 screens with a modern web frontend.

1. **Technology choice**: React, Angular, or Thymeleaf (server-side)
2. **Screen-by-screen replacement** following the BMS map inventory:
   - Login page (COSGN00)
   - Main menu / Dashboard (COMEN01)
   - Account view/update forms (COACTVW, COACTUP)
   - Card list/view/update (COCRDLI, COCRDSL, COCRDUP)
   - Transaction list/view/add (COTRN00, COTRN01, COTRN02)
   - Report request form (CORPT00)
   - Bill payment form (COBIL00)
   - Admin user management (COUSR00-03)
3. **PF key mapping**: Map COBOL PF keys to UI buttons/actions:
   - PF3 = Cancel/Back, PF7 = Previous Page, PF8 = Next Page, ENTER = Submit

**Validation Milestone**: Full end-to-end user workflow matches mainframe 3270 experience.

### Cross-Cutting Concerns (Throughout All Phases)

- **Logging**: Replace COBOL `DISPLAY` statements with SLF4J logging
- **Error handling**: Replace ABEND codes with structured exception hierarchy
- **Testing**: Unit tests for each service, integration tests for batch jobs, API tests with MockMvc
- **CI/CD**: Maven build, JUnit/integration tests, Flyway migration validation
- **Monitoring**: Spring Actuator endpoints for health checks and metrics
- **Documentation**: OpenAPI/Swagger for REST API documentation
