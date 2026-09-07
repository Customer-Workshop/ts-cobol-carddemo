-- Wave 1 data-seam evidence queries (run against the docker carddemo-pg PostgreSQL 16, port 5433)
select version();
select version, description, success from flyway_schema_history order by installed_rank;
select table_name, column_name, data_type, character_maximum_length, numeric_precision, numeric_scale
  from information_schema.columns where table_schema='public' and table_name in ('accounts','customers','card_xrefs','users')
  order by table_name, ordinal_position;
select indexname, indexdef from pg_indexes where schemaname='public' order by indexname;
select 'accounts' t, count(*) from accounts union all select 'customers', count(*) from customers
 union all select 'card_xrefs', count(*) from card_xrefs union all select 'users', count(*) from users;
select * from accounts order by acct_id limit 5;
select * from customers order by cust_id limit 5;
select * from card_xrefs order by xref_card_number limit 5;
select * from users order by sec_usr_id;
-- Q-04: accounts with more than one card
select xref_acct_id, count(*) from card_xrefs group by xref_acct_id having count(*)>1;
-- signed money: any negative values?
select count(*) filter (where acct_curr_bal<0) neg_bal, count(*) filter (where acct_curr_cyc_credit<0) neg_cyc_cr,
       count(*) filter (where acct_curr_cyc_debit<0) neg_cyc_db, min(acct_curr_bal), max(acct_curr_bal) from accounts;
-- D-0029 password state
select sec_usr_id, sec_usr_pwd_legacy is not null has_legacy, sec_usr_pwd_hash is not null has_hash, left(sec_usr_pwd_hash,7) hash_prefix from users order by sec_usr_id;
