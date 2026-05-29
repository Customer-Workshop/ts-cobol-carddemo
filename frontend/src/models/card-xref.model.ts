/**
 * TypeScript interface derived from COBOL copybook CVACT03Y.cpy
 * Record layout: CARD-XREF-RECORD (RECLN 50)
 * Source: app/cpy/CVACT03Y.cpy
 */
export interface CardXrefRecord {
  /** @cobol 05 XREF-CARD-NUM PIC X(16) */
  xrefCardNum: string;

  /** @cobol 05 XREF-CUST-ID PIC 9(09) */
  xrefCustId: number;

  /** @cobol 05 XREF-ACCT-ID PIC 9(11) */
  xrefAcctId: number;
}
