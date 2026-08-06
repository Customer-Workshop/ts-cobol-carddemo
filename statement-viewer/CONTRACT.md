# Statement JSON contract (DJ-82) — LOCKED

This directory holds the frozen interface between the COBOL/JCL statement emitter
(`app/cbl/CBSTM03A.CBL` + `app/jcl/CREASTMT.JCL`) and the web statement viewer.

`statement-contract.schema.json` is the authoritative schema. It is locked: neither the
emitter nor the viewer may change it.

## Output format

Multi-account output is **JSON Lines**: the emitter writes one complete, self-contained
JSON object per output record/line, which matches the fixed-record COBOL write model.
Consumers read the file line by line; a single-account file is therefore also a valid
one-line JSON Lines file, and a plain single JSON object parses identically.

## Conventions

- camelCase keys.
- Legacy misspellings normalised: `ACCT-EXPIRAION-DATE` becomes `expirationDate`.
- Amounts are JSON numbers with 2 decimals.
- Dates are `YYYY-MM-DD`.
- COBOL space-padded strings are trimmed.
- Numeric identifiers stay strings, zero-padded to their COBOL picture width, so leading
  zeros survive the round trip.

## Excluded fields

`CUST-SSN`, `CUST-GOVT-ISSUED-ID`, `CUST-DOB-YYYYMMDD` and `CUST-EFT-ACCOUNT-ID` are
never emitted — sensitive fields stay out of the channel payload.

## Field sources

| JSON | COBOL copybook |
| --- | --- |
| `accountId`, `account.*` | `app/cpy/CVACT01Y.cpy` |
| `customer.*` | `app/cpy/CUSTREC.cpy` |
| `cards[].cardNumber` | `app/cpy/CVACT03Y.cpy` |
| `transactions[]` | `app/cpy/COSTM01.CPY` |

## Shape

```json
{
  "accountId": "00000000011",
  "customer": {
    "customerId": "000000001",
    "firstName": "...", "middleName": "...", "lastName": "...",
    "addressLine1": "...", "addressLine2": "...", "addressLine3": "...",
    "stateCode": "..", "countryCode": "...", "zip": "...",
    "phone1": "...", "phone2": "...",
    "ficoScore": 700
  },
  "account": {
    "activeStatus": "Y",
    "currentBalance": 0.00,
    "creditLimit": 0.00,
    "cashCreditLimit": 0.00,
    "openDate": "YYYY-MM-DD",
    "expirationDate": "YYYY-MM-DD",
    "reissueDate": "YYYY-MM-DD",
    "currentCycleCredit": 0.00,
    "currentCycleDebit": 0.00,
    "groupId": "..."
  },
  "cards": [ { "cardNumber": "..." } ],
  "transactions": [
    {
      "transactionId": "...",
      "cardNumber": "...",
      "typeCode": "..",
      "categoryCode": "....",
      "source": "...",
      "description": "...",
      "amount": 0.00,
      "merchantId": "...",
      "merchantName": "...",
      "merchantCity": "...",
      "merchantZip": "...",
      "originTimestamp": "...",
      "processTimestamp": "..."
    }
  ]
}
```
