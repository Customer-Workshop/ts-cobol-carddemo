-- S-21 (S21-B4): DB2 referential-integrity parity. COTRTLIC/COTRTUPC rely on
-- the TRANSACTION_CATEGORY -> TRANSACTION_TYPE RESTRICT relationship to
-- surface SQLCODE -532 on delete-with-children.
ALTER TABLE transaction_categories
    ADD CONSTRAINT fk_trcat_type
    FOREIGN KEY (tran_type_code)
    REFERENCES transaction_types (tran_type)
    ON DELETE RESTRICT;
