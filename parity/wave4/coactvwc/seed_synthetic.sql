-- COACTVWC parity synthetic rows (applied AFTER the wave-1 `import` profile loaded the 50/50/50 fixture).
-- Keys 9xx / 999xxxxxx / 999... are outside the fixture range (accounts 1..50, customers 1..50, cards from cardxref.txt).
-- Idempotent: re-runnable.
BEGIN;
DELETE FROM card_xrefs WHERE xref_card_number LIKE '999%';
DELETE FROM accounts   WHERE acct_id IN (902, 903);

-- A-05 (ACV-05, DV-01): xref exists, ACCTDAT missing -> E-10 only
INSERT INTO card_xrefs (xref_card_number, xref_cust_id, xref_acct_id) VALUES ('9990000000000001', 1, 901);

-- A-06 (ACV-06): xref + account exist, CUSTDAT missing -> E-11 with account block. Account values copied from fixture acct 1.
INSERT INTO card_xrefs (xref_card_number, xref_cust_id, xref_acct_id) VALUES ('9990000000000002', 999999902, 902);
INSERT INTO accounts SELECT 902, acct_active_status, acct_curr_bal, acct_credit_limit, acct_cash_credit_limit,
       acct_open_date, acct_expiraion_date, acct_reissue_date, acct_curr_cyc_credit, acct_curr_cyc_debit,
       acct_addr_zip, acct_group_id FROM accounts WHERE acct_id = 1;

-- A-11 (Q-04): two xref rows for one account, inserted higher card first; lowest card (-> cust 3) must win.
INSERT INTO card_xrefs (xref_card_number, xref_cust_id, xref_acct_id) VALUES ('9993000000000002', 2, 903);
INSERT INTO card_xrefs (xref_card_number, xref_cust_id, xref_acct_id) VALUES ('9993000000000001', 3, 903);
INSERT INTO accounts SELECT 903, acct_active_status, acct_curr_bal, acct_credit_limit, acct_cash_credit_limit,
       acct_open_date, acct_expiraion_date, acct_reissue_date, acct_curr_cyc_credit, acct_curr_cyc_debit,
       acct_addr_zip, acct_group_id FROM accounts WHERE acct_id = 27;
COMMIT;
