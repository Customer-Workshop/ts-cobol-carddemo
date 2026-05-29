/**
 * TypeScript interface derived from COBOL copybook CUSTREC.cpy
 * Record layout: CUSTOMER-RECORD (RECLN 500)
 * Source: app/cpy/CUSTREC.cpy
 */
export interface CustomerRecord {
  /** @cobol 05 CUST-ID PIC 9(09) */
  custId: number;

  /** @cobol 05 CUST-FIRST-NAME PIC X(25) */
  custFirstName: string;

  /** @cobol 05 CUST-MIDDLE-NAME PIC X(25) */
  custMiddleName: string;

  /** @cobol 05 CUST-LAST-NAME PIC X(25) */
  custLastName: string;

  /** @cobol 05 CUST-ADDR-LINE-1 PIC X(50) */
  custAddrLine1: string;

  /** @cobol 05 CUST-ADDR-LINE-2 PIC X(50) */
  custAddrLine2: string;

  /** @cobol 05 CUST-ADDR-LINE-3 PIC X(50) */
  custAddrLine3: string;

  /** @cobol 05 CUST-ADDR-STATE-CD PIC X(02) */
  custAddrStateCd: string;

  /** @cobol 05 CUST-ADDR-COUNTRY-CD PIC X(03) */
  custAddrCountryCd: string;

  /** @cobol 05 CUST-ADDR-ZIP PIC X(10) */
  custAddrZip: string;

  /** @cobol 05 CUST-PHONE-NUM-1 PIC X(15) */
  custPhoneNum1: string;

  /** @cobol 05 CUST-PHONE-NUM-2 PIC X(15) */
  custPhoneNum2: string;

  /** @cobol 05 CUST-SSN PIC 9(09) */
  custSsn: string;

  /** @cobol 05 CUST-GOVT-ISSUED-ID PIC X(20) */
  custGovtIssuedId: string;

  /** @cobol 05 CUST-DOB-YYYYMMDD PIC X(10) */
  custDobYyyymmdd: string;

  /** @cobol 05 CUST-EFT-ACCOUNT-ID PIC X(10) */
  custEftAccountId: string;

  /** @cobol 05 CUST-PRI-CARD-HOLDER-IND PIC X(01) */
  custPriCardHolderInd: string;

  /** @cobol 05 CUST-FICO-CREDIT-SCORE PIC 9(03) */
  custFicoCreditScore: number;
}
