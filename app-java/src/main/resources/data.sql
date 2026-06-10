-- Seed data — representative records derived from EBCDIC seed files
-- Source: app/data/EBCDIC/AWS.M2.CARDDEMO.ACCTDATA.PS et al.

-- Accounts
INSERT INTO accounts (acct_id, acct_active_status, acct_curr_bal, acct_credit_limit,
    acct_cash_credit_limit, acct_open_date, acct_expiration_date, acct_reissue_date,
    acct_curr_cyc_credit, acct_curr_cyc_debit, acct_addr_zip, acct_group_id, version)
VALUES
(00000000001, 'Y', 1500.00, 5000.00, 2000.00, '2018-03-15', '2028-03-15', '2023-03-15', 200.00, 50.00, '60601', 'GROUP01', 0),
(00000000002, 'Y', 2500.00, 10000.00, 3000.00, '2019-06-01', '2029-06-01', '2024-06-01', 500.00, 100.00, '10001', 'GROUP01', 0),
(00000000003, 'Y', 750.00, 3000.00, 1000.00, '2020-01-10', '2030-01-10', '2025-01-10', 100.00, 25.00, '90210', 'GROUP02', 0),
(00000000004, 'N', 4999.00, 5000.00, 1500.00, '2017-11-20', '2025-11-20', '2022-11-20', 4999.00, 0.00, '30301', 'GROUP01', 0),
(00000000005, 'Y', 0.00, 8000.00, 2500.00, '2021-05-05', '2031-05-05', '2026-05-05', 0.00, 0.00, '94102', 'GROUP02', 0);

-- Customers
INSERT INTO customers (cust_id, cust_first_name, cust_middle_name, cust_last_name,
    cust_addr_line_1, cust_addr_line_2, cust_addr_line_3, cust_addr_state_cd,
    cust_addr_country_cd, cust_addr_zip, cust_phone_num_1, cust_phone_num_2,
    cust_ssn, cust_govt_issued_id, cust_dob, cust_eft_account_id,
    cust_pri_card_holder_ind, cust_fico_credit_score)
VALUES
(000000001, 'JOHN', 'M', 'SMITH', '123 MAIN ST', 'APT 4B', '', 'IL', 'USA', '60601', '(312)555-0101', '', 123456789, 'DL12345678', '1985-04-12', '1234567890', 'Y', 750),
(000000002, 'JANE', 'A', 'DOE', '456 OAK AVE', '', '', 'NY', 'USA', '10001', '(212)555-0202', '(212)555-0203', 987654321, 'PP98765432', '1990-08-25', '0987654321', 'Y', 680),
(000000003, 'ROBERT', '', 'JOHNSON', '789 PINE RD', 'SUITE 100', '', 'CA', 'USA', '90210', '(310)555-0303', '', 456789123, 'DL45678912', '1978-12-01', '4567891230', 'Y', 720),
(000000004, 'MARIA', 'L', 'GARCIA', '321 ELM ST', '', '', 'GA', 'USA', '30301', '(404)555-0404', '', 654321987, 'DL65432198', '1995-02-14', '6543219870', 'N', 600),
(000000005, 'DAVID', 'K', 'CHEN', '555 MARKET ST', 'FLOOR 12', '', 'CA', 'USA', '94102', '(415)555-0505', '(415)555-0506', 789123456, 'PP78912345', '1982-07-30', '7891234560', 'Y', 800);

-- Cards
INSERT INTO cards (card_num, card_acct_id, card_cvv_cd, card_embossed_name,
    card_expiration_date, card_active_status)
VALUES
('4111111111111111', 00000000001, 123, 'JOHN M SMITH', '2028-03-15', 'Y'),
('4222222222222222', 00000000002, 456, 'JANE A DOE', '2029-06-01', 'Y'),
('4333333333333333', 00000000003, 789, 'ROBERT JOHNSON', '2030-01-10', 'Y'),
('4444444444444444', 00000000004, 321, 'MARIA L GARCIA', '2025-11-20', 'N'),
('4555555555555555', 00000000005, 654, 'DAVID K CHEN', '2031-05-05', 'Y');

-- Card Cross-References (link card → customer + account)
INSERT INTO card_xrefs (xref_card_num, xref_cust_id, xref_acct_id)
VALUES
('4111111111111111', 000000001, 00000000001),
('4222222222222222', 000000002, 00000000002),
('4333333333333333', 000000003, 00000000003),
('4444444444444444', 000000004, 00000000004),
('4555555555555555', 000000005, 00000000005);

-- Transactions (sample posted transactions)
INSERT INTO transactions (tran_id, tran_type_cd, tran_cat_cd, tran_source, tran_desc,
    tran_amt, tran_merchant_id, tran_merchant_name, tran_merchant_city,
    tran_merchant_zip, tran_card_num, tran_orig_ts, tran_proc_ts)
VALUES
('TX00000000000001', '01', 5001, 'POS', 'Grocery purchase', 45.50, 100000001, 'FRESH MART', 'CHICAGO', '60601', '4111111111111111', '2024-01-15-10.30.00.000000', '2024-01-15-10.30.01.000000'),
('TX00000000000002', '02', 5002, 'ATM', 'Cash withdrawal', -200.00, 100000002, 'BANK ATM', 'NEW YORK', '10001', '4222222222222222', '2024-01-15-11.00.00.000000', '2024-01-15-11.00.01.000000'),
('TX00000000000003', '01', 5001, 'ONLINE', 'Online shopping', 125.99, 100000003, 'WEBSTORE INC', 'LOS ANGELES', '90210', '4333333333333333', '2024-01-16-09.15.00.000000', '2024-01-16-09.15.01.000000');

-- Transaction Category Balances
INSERT INTO tran_cat_balances (acct_id, tran_type_cd, tran_cat_cd, tran_cat_bal)
VALUES
(00000000001, '01', 5001, 45.50),
(00000000002, '02', 5002, -200.00),
(00000000003, '01', 5001, 125.99);

-- Disclosure Groups (interest rates by group/type/category)
INSERT INTO disclosure_groups (acct_group_id, tran_type_cd, tran_cat_cd, int_rate)
VALUES
('GROUP01', '01', 5001, 18.99),
('GROUP01', '02', 5002, 24.99),
('GROUP02', '01', 5001, 15.49),
('GROUP02', '02', 5002, 22.99),
('DEFAULT', '01', 5001, 19.99),
('DEFAULT', '02', 5002, 25.99);
