-- Stream S-01 AccountView, wave 1 data seams (B-0001 ACCTDAT, B-0002 CUSTDAT, B-0007 CXACAIX, B-0006 USRSEC).
-- Field dictionary: functional/CardDemo/AccountView_analysis.md §4.2-§4.5 (binding).
-- Q-10 gate (measured in wave 1): all 200 date values in app/data are YYYY-MM-DD -> DATE columns.

-- ACCOUNT-RECORD, app/cpy/CVACT01Y.cpy (300 bytes), VSAM KSDS key ACCT-ID
CREATE TABLE accounts (
    acct_id                 BIGINT          NOT NULL,   -- ACCT-ID 9(11)
    acct_active_status      VARCHAR(1),                 -- ACCT-ACTIVE-STATUS X(01)
    acct_curr_bal           NUMERIC(19, 2),             -- ACCT-CURR-BAL S9(10)V99
    acct_credit_limit       NUMERIC(19, 2),             -- ACCT-CREDIT-LIMIT S9(10)V99
    acct_cash_credit_limit  NUMERIC(19, 2),             -- ACCT-CASH-CREDIT-LIMIT S9(10)V99
    acct_open_date          DATE,                       -- ACCT-OPEN-DATE X(10)
    acct_expiraion_date     DATE,                       -- ACCT-EXPIRAION-DATE X(10) (source spelling kept)
    acct_reissue_date       DATE,                       -- ACCT-REISSUE-DATE X(10)
    acct_curr_cyc_credit    NUMERIC(19, 2),             -- ACCT-CURR-CYC-CREDIT S9(10)V99
    acct_curr_cyc_debit     NUMERIC(19, 2),             -- ACCT-CURR-CYC-DEBIT S9(10)V99
    acct_addr_zip           VARCHAR(10),                -- ACCT-ADDR-ZIP X(10)
    acct_group_id           VARCHAR(10),                -- ACCT-GROUP-ID X(10)
    CONSTRAINT pk_accounts PRIMARY KEY (acct_id)
);

-- CUSTOMER-RECORD, app/cpy/CVCUS01Y.cpy (500 bytes), VSAM KSDS key CUST-ID
CREATE TABLE customers (
    cust_id                   BIGINT        NOT NULL,   -- CUST-ID 9(09)
    cust_first_name           VARCHAR(25),              -- CUST-FIRST-NAME X(25)
    cust_middle_name          VARCHAR(25),              -- CUST-MIDDLE-NAME X(25)
    cust_last_name            VARCHAR(25),              -- CUST-LAST-NAME X(25)
    cust_addr_line_1          VARCHAR(50),              -- CUST-ADDR-LINE-1 X(50)
    cust_addr_line_2          VARCHAR(50),              -- CUST-ADDR-LINE-2 X(50)
    cust_addr_line_3          VARCHAR(50),              -- CUST-ADDR-LINE-3 X(50)
    cust_addr_state_cd        VARCHAR(2),               -- CUST-ADDR-STATE-CD X(02)
    cust_addr_country_cd      VARCHAR(3),               -- CUST-ADDR-COUNTRY-CD X(03)
    cust_addr_zip             VARCHAR(10),              -- CUST-ADDR-ZIP X(10)
    cust_phone_num_1          VARCHAR(15),              -- CUST-PHONE-NUM-1 X(15)
    cust_phone_num_2          VARCHAR(15),              -- CUST-PHONE-NUM-2 X(15)
    cust_ssn                  BIGINT,                   -- CUST-SSN 9(09)
    cust_govt_issued_id       VARCHAR(20),              -- CUST-GOVT-ISSUED-ID X(20)
    cust_dob_yyyy_mm_dd       DATE,                     -- CUST-DOB-YYYY-MM-DD X(10)
    cust_eft_account_id       VARCHAR(10),              -- CUST-EFT-ACCOUNT-ID X(10)
    cust_pri_card_holder_ind  VARCHAR(1),               -- CUST-PRI-CARD-HOLDER-IND X(01)
    cust_fico_credit_score    INTEGER,                  -- CUST-FICO-CREDIT-SCORE 9(03)
    CONSTRAINT pk_customers PRIMARY KEY (cust_id)
);

-- CARD-XREF-RECORD, app/cpy/CVACT03Y.cpy (50 bytes), base KSDS CCXREF key XREF-CARD-NUM
CREATE TABLE card_xrefs (
    xref_card_number  VARCHAR(16) NOT NULL,             -- XREF-CARD-NUM X(16)
    xref_cust_id      BIGINT,                           -- XREF-CUST-ID 9(09)
    xref_acct_id      BIGINT,                           -- XREF-ACCT-ID 9(11)
    CONSTRAINT pk_card_xrefs PRIMARY KEY (xref_card_number)
);

-- CXACAIX alternate index path substitute (B-0007, D-0013 D3)
CREATE INDEX idx_card_xrefs_acct_id ON card_xrefs (xref_acct_id);

-- SEC-USER-DATA, app/cpy/CSUSR01Y.cpy (80 bytes), VSAM KSDS key SEC-USR-ID
CREATE TABLE users (
    sec_usr_id          VARCHAR(8)  NOT NULL,           -- SEC-USR-ID X(08)
    sec_usr_fname       VARCHAR(20),                    -- SEC-USR-FNAME X(20)
    sec_usr_lname       VARCHAR(20),                    -- SEC-USR-LNAME X(20)
    sec_usr_pwd_hash    VARCHAR(72),                    -- BCrypt hash ({bcrypt} prefixed), filled by upgrade-on-login (B-0026)
    sec_usr_pwd_legacy  VARCHAR(8),                     -- SEC-USR-PWD X(08) plaintext, cleared on upgrade
    sec_usr_type        VARCHAR(1)  NOT NULL,           -- SEC-USR-TYPE X(01): 'A' admin / 'U' user
    CONSTRAINT pk_users PRIMARY KEY (sec_usr_id)
);
