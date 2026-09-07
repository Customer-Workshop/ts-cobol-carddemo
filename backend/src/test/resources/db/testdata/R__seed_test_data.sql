-- Test fixture (subset of app/data, values copied from the extracts; NOT loaded by the import profile).
-- Records: acctdata.txt rows 1-2, custdata.txt row 1, cardxref.txt rows 1-2, USRSEC records 1 and 6.
-- Account 99999999999 / cards 4000000000000002 and 4000000000000001 are synthetic: the Q-04 two-card case.
DELETE FROM card_xrefs;
DELETE FROM accounts;
DELETE FROM customers;
DELETE FROM users;

INSERT INTO accounts (acct_id, acct_active_status, acct_curr_bal, acct_credit_limit, acct_cash_credit_limit,
                      acct_open_date, acct_expiraion_date, acct_reissue_date, acct_curr_cyc_credit,
                      acct_curr_cyc_debit, acct_addr_zip, acct_group_id)
VALUES (1, 'Y', 194.00, 2020.00, 1020.00, DATE '2014-11-20', DATE '2025-05-20', DATE '2025-05-20', 0.00, 0.00, NULL, 'A000000000'),
       (2, 'Y', 158.00, 6130.00, 5448.00, DATE '2013-06-19', DATE '2024-08-11', DATE '2024-08-11', 0.00, 0.00, NULL, 'A000000000'),
       (99999999999, 'N', -12.34, 1000.00, 500.00, DATE '2020-01-01', DATE '2030-01-01', DATE '2030-01-01', 0.00, 0.00, NULL, 'ZZZZZZZZZZ');

INSERT INTO customers (cust_id, cust_first_name, cust_middle_name, cust_last_name, cust_addr_line_1, cust_addr_line_2,
                       cust_addr_line_3, cust_addr_state_cd, cust_addr_country_cd, cust_addr_zip, cust_phone_num_1,
                       cust_phone_num_2, cust_ssn, cust_govt_issued_id, cust_dob_yyyy_mm_dd, cust_eft_account_id,
                       cust_pri_card_holder_ind, cust_fico_credit_score)
VALUES (1, 'Immanuel', 'Madeline', 'Kessler', '618 Deshaun Route', 'Apt. 802', 'Altenwerthshire', 'NC', 'USA',
        '12546', '(908)119-8310', '(373)693-8684', 20973888, '00000000000049368437', DATE '1961-06-08', '0053581756', 'Y', 274);

INSERT INTO card_xrefs (xref_card_number, xref_cust_id, xref_acct_id)
VALUES ('0500024453765740', 50, 50),
       ('0683586198171516', 27, 27),
       ('4000000000000002', 1, 99999999999),
       ('4000000000000001', 1, 99999999999);

INSERT INTO users (sec_usr_id, sec_usr_fname, sec_usr_lname, sec_usr_pwd_hash, sec_usr_pwd_legacy, sec_usr_type)
VALUES ('ADMIN001', 'MARGARET', 'GOLD', NULL, 'PASSWORD', 'A'),
       ('USER0001', 'LAWRENCE', 'THOMAS', NULL, 'PASSWORD', 'U');
