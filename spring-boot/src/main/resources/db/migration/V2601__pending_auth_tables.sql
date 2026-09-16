-- S-19 Pending Authorization View: the IMS PAUTSUM0/PAUTDTL1 segments
-- (CIPAUSMY.cpy / CIPAUDTY.cpy) and the DB2 AUTHFRDS journal (AUTHFRDS.ddl)
-- as relational tables. S-19 owns all three; S-20 (auth producer) writes
-- pending_auth_summary/pending_auth_detail through the same repositories.
--
-- The PAUT9CTS segment key maps to (acct_id, auth_date_9c, auth_time_9c):
-- PA-AUTH-DATE-9C = 99999 - YYDDD (Julian date, COPAUA0C.cbl:868-875) and
-- PA-AUTH-TIME-9C = 999999999 - HHMMSSmmm. Ascending order of the raw
-- complement columns is newest-first real-time order (S19-B3), so no
-- derived/reversed columns are stored.
CREATE TABLE pending_auth_summary (
    acct_id            bigint        NOT NULL PRIMARY KEY,
    cust_id            bigint,
    auth_status        varchar(1),
    account_status     varchar(10),
    credit_limit       numeric(11,2),
    cash_limit         numeric(11,2),
    credit_balance     numeric(11,2),
    cash_balance       numeric(11,2),
    approved_auth_cnt  integer,
    declined_auth_cnt  integer,
    approved_auth_amt  numeric(11,2),
    declined_auth_amt  numeric(11,2)
);

CREATE TABLE pending_auth_detail (
    acct_id                bigint        NOT NULL,
    auth_date_9c           integer       NOT NULL,
    auth_time_9c           integer       NOT NULL,
    auth_orig_date         varchar(6),
    auth_orig_time         varchar(6),
    card_num               varchar(16),
    auth_type              varchar(4),
    card_expiry_date       varchar(4),
    message_type           varchar(6),
    message_source         varchar(6),
    auth_id_code           varchar(6),
    auth_resp_code         varchar(2),
    auth_resp_reason       varchar(4),
    processing_code        integer,
    transaction_amt        numeric(12,2),
    approved_amt           numeric(12,2),
    merchant_category_code varchar(4),
    acqr_country_code      varchar(3),
    pos_entry_mode         smallint,
    merchant_id            varchar(15),
    merchant_name          varchar(22),
    merchant_city          varchar(13),
    merchant_state         varchar(2),
    merchant_zip           varchar(9),
    transaction_id         varchar(15),
    match_status           varchar(1),
    auth_fraud             varchar(1),
    fraud_rpt_date         varchar(8),
    PRIMARY KEY (acct_id, auth_date_9c, auth_time_9c),
    FOREIGN KEY (acct_id) REFERENCES pending_auth_summary (acct_id)
);

-- AUTHFRDS (app/app-authorization-ims-db2-mq/ddl/AUTHFRDS.ddl): one row per
-- fraud report/remove action, keyed by the real (uncomplemented) auth
-- timestamp. The -803 duplicate path in COPAUS2C becomes an upsert on this
-- primary key (S19-B5).
CREATE TABLE auth_frauds (
    card_num               varchar(16)   NOT NULL,
    auth_ts                timestamp     NOT NULL,
    auth_type              varchar(4),
    card_expiry_date       varchar(4),
    message_type           varchar(6),
    message_source         varchar(6),
    auth_id_code           varchar(6),
    auth_resp_code         varchar(2),
    auth_resp_reason       varchar(4),
    processing_code        varchar(6),
    transaction_amt        numeric(12,2),
    approved_amt           numeric(12,2),
    merchant_category_code varchar(4),
    acqr_country_code      varchar(3),
    pos_entry_mode         smallint,
    merchant_id            varchar(15),
    merchant_name          varchar(22),
    merchant_city          varchar(13),
    merchant_state         varchar(2),
    merchant_zip           varchar(9),
    transaction_id         varchar(15),
    match_status           varchar(1),
    auth_fraud             varchar(1),
    fraud_rpt_date         date,
    acct_id                decimal(11,0),
    cust_id                decimal(9,0),
    PRIMARY KEY (card_num, auth_ts)
);
