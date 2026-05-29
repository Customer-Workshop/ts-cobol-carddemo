/**
 * TypeScript interface derived from COBOL copybook CVACT02Y.cpy
 * Record layout: CARD-RECORD (RECLN 150)
 * Source: app/cpy/CVACT02Y.cpy
 */
export interface CardRecord {
  /** @cobol 05 CARD-NUM PIC X(16) */
  cardNum: string;

  /** @cobol 05 CARD-ACCT-ID PIC 9(11) */
  cardAcctId: number;

  /** @cobol 05 CARD-CVV-CD PIC 9(03) */
  cardCvvCd: string;

  /** @cobol 05 CARD-EMBOSSED-NAME PIC X(50) */
  cardEmbossedName: string;

  /** @cobol 05 CARD-EXPIRAION-DATE PIC X(10) */
  cardExpirationDate: string;

  /** @cobol 05 CARD-ACTIVE-STATUS PIC X(01) */
  cardActiveStatus: string;
}
