-- CardDemo relational schema — migrated from VSAM KSDS copybooks
-- Replaces: CVACT01Y (accounts), CVACT02Y (cards), CVACT03Y (xref),
--           CVCUS01Y (customers), CVTRA05Y (transactions),
--           CVTRA01Y (tran_cat_balances), CVTRA02Y (disclosure_groups)

DROP TABLE IF EXISTS disclosure_groups;
DROP TABLE IF EXISTS tran_cat_balances;
DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS card_xrefs;
DROP TABLE IF EXISTS cards;
DROP TABLE IF EXISTS customers;
DROP TABLE IF EXISTS accounts;

CREATE TABLE accounts (
    acct_id             BIGINT          PRIMARY KEY,
    acct_active_status  VARCHAR(1),
    acct_curr_bal       DECIMAL(12,2)   DEFAULT 0,
    acct_credit_limit   DECIMAL(12,2)   DEFAULT 0,
    acct_cash_credit_limit DECIMAL(12,2) DEFAULT 0,
    acct_open_date      VARCHAR(10),
    acct_expiration_date VARCHAR(10),
    acct_reissue_date   VARCHAR(10),
    acct_curr_cyc_credit DECIMAL(12,2)  DEFAULT 0,
    acct_curr_cyc_debit  DECIMAL(12,2)  DEFAULT 0,
    acct_addr_zip       VARCHAR(10),
    acct_group_id       VARCHAR(10),
    version             BIGINT          DEFAULT 0
);

CREATE TABLE cards (
    card_num            VARCHAR(16)     PRIMARY KEY,
    card_acct_id        BIGINT,
    card_cvv_cd         INT,
    card_embossed_name  VARCHAR(50),
    card_expiration_date VARCHAR(10),
    card_active_status  VARCHAR(1)
);

CREATE TABLE card_xrefs (
    xref_card_num       VARCHAR(16)     PRIMARY KEY,
    xref_cust_id        BIGINT,
    xref_acct_id        BIGINT
);

CREATE TABLE customers (
    cust_id             BIGINT          PRIMARY KEY,
    cust_first_name     VARCHAR(25),
    cust_middle_name    VARCHAR(25),
    cust_last_name      VARCHAR(25),
    cust_addr_line_1    VARCHAR(50),
    cust_addr_line_2    VARCHAR(50),
    cust_addr_line_3    VARCHAR(50),
    cust_addr_state_cd  VARCHAR(2),
    cust_addr_country_cd VARCHAR(3),
    cust_addr_zip       VARCHAR(10),
    cust_phone_num_1    VARCHAR(15),
    cust_phone_num_2    VARCHAR(15),
    cust_ssn            BIGINT,
    cust_govt_issued_id VARCHAR(20),
    cust_dob            VARCHAR(10),
    cust_eft_account_id VARCHAR(10),
    cust_pri_card_holder_ind VARCHAR(1),
    cust_fico_credit_score INT
);

CREATE TABLE transactions (
    tran_id             VARCHAR(16)     PRIMARY KEY,
    tran_type_cd        VARCHAR(2),
    tran_cat_cd         INT,
    tran_source         VARCHAR(10),
    tran_desc           VARCHAR(100),
    tran_amt            DECIMAL(11,2),
    tran_merchant_id    BIGINT,
    tran_merchant_name  VARCHAR(50),
    tran_merchant_city  VARCHAR(50),
    tran_merchant_zip   VARCHAR(10),
    tran_card_num       VARCHAR(16),
    tran_orig_ts        VARCHAR(26),
    tran_proc_ts        VARCHAR(26)
);

CREATE TABLE tran_cat_balances (
    acct_id             BIGINT,
    tran_type_cd        VARCHAR(2),
    tran_cat_cd         INT,
    tran_cat_bal        DECIMAL(11,2)   DEFAULT 0,
    PRIMARY KEY (acct_id, tran_type_cd, tran_cat_cd)
);

CREATE TABLE disclosure_groups (
    acct_group_id       VARCHAR(10),
    tran_type_cd        VARCHAR(2),
    tran_cat_cd         INT,
    int_rate            DECIMAL(6,2)    DEFAULT 0,
    PRIMARY KEY (acct_group_id, tran_type_cd, tran_cat_cd)
);
