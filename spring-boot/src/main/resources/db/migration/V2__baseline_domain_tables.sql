-- Baseline schema for the remaining JPA entities (generated to match the
-- entity mappings exactly). Owning streams evolve these tables with ALTER
-- migrations in their Phase 1 rather than re-creating them.
CREATE TABLE accounts (
    acct_id                   bigint        NOT NULL PRIMARY KEY,
    acct_active_status        varchar(1),
    acct_cash_credit_limit    numeric(19,2),
    acct_credit_limit         numeric(19,2),
    acct_curr_bal             numeric(19,2),
    acct_curr_cyc_credit      numeric(19,2),
    acct_curr_cyc_debit       numeric(19,2),
    acct_expiration_date      date,
    acct_open_date            date,
    acct_reissue_date         date,
    acct_addr_zip             varchar(10),
    acct_group_id             varchar(10)
);

CREATE TABLE cards (
    card_number               varchar(16)   NOT NULL PRIMARY KEY,
    card_acct_id              bigint,
    card_cvv_code             integer,
    card_embossed_name        varchar(50),
    card_expiration_date      date,
    card_active_status        varchar(1)
);

CREATE TABLE card_xrefs (
    xref_card_number          varchar(16)   NOT NULL PRIMARY KEY,
    xref_cust_id              bigint,
    xref_acct_id              bigint
);

CREATE TABLE customers (
    cust_id                   bigint        NOT NULL PRIMARY KEY,
    cust_first_name           varchar(25),
    cust_middle_name          varchar(25),
    cust_last_name            varchar(25),
    cust_addr_line1           varchar(50),
    cust_addr_line2           varchar(50),
    cust_addr_line3           varchar(50),
    cust_addr_state_code      varchar(2),
    cust_addr_country_code    varchar(3),
    cust_addr_zip             varchar(10),
    cust_phone_num1           varchar(15),
    cust_phone_num2           varchar(15),
    cust_ssn                  bigint,
    cust_government_issued_id varchar(20),
    cust_dob                  date,
    cust_eft_account_id       varchar(10),
    cust_primary_card_holder_indicator varchar(1),
    cust_fico_credit_score    integer
);

CREATE TABLE transactions (
    tran_id                   varchar(16)   NOT NULL PRIMARY KEY,
    tran_type_code            varchar(2),
    tran_category_code        integer,
    tran_source               varchar(10),
    tran_description          varchar(100),
    tran_amount               numeric(19,2),
    tran_merchant_id          bigint,
    tran_merchant_name        varchar(50),
    tran_merchant_city        varchar(50),
    tran_merchant_zip         varchar(10),
    tran_card_number          varchar(16),
    tran_origin_timestamp     timestamp(6),
    tran_process_timestamp    timestamp(6)
);

CREATE TABLE transaction_types (
    tran_type                 varchar(2)    NOT NULL PRIMARY KEY,
    description               varchar(50)
);

CREATE TABLE transaction_categories (
    tran_type_code            varchar(2)    NOT NULL,
    tran_category_code        integer       NOT NULL,
    description               varchar(255),
    PRIMARY KEY (tran_category_code, tran_type_code)
);

CREATE TABLE transaction_category_balances (
    acct_id                   bigint        NOT NULL,
    type_code                 varchar(2)    NOT NULL,
    category_code             integer       NOT NULL,
    balance                   numeric(19,2),
    PRIMARY KEY (category_code, type_code, acct_id)
);

CREATE TABLE disclosure_groups (
    acct_group_id             varchar(10)   NOT NULL,
    tran_type_code            varchar(2)    NOT NULL,
    tran_category_code        integer       NOT NULL,
    interest_rate             numeric(19,2),
    PRIMARY KEY (tran_category_code, tran_type_code, acct_group_id)
);
