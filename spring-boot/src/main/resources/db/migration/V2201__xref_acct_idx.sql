-- S-15 (S15-B2): XREFFIL1 is a path over the CARDXREF alternate index keyed by
-- XREF-ACCT-ID. cbact04Job resolves each account's card through that index on
-- every account break, so card_xrefs gets a secondary index on xref_acct_id to
-- keep the AIX-path access cost on Postgres.
CREATE INDEX idx_card_xrefs_acct_id ON card_xrefs (xref_acct_id);
