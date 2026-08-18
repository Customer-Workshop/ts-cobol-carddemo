# statements.json contract (DJ-96)

Shared contract between:

* `app/cbl/CBSTM04C.cbl` (+ `app/jcl/CREASTMJ.jcl`) — COBOL batch extract that produces `statements.json`
* `samples/html/statement-viewer.html` — standalone HTML/JS statement viewer that renders it

Files here:

* `statements.schema.json` — JSON Schema (draft-07) definition, authoritative shape
* `statements.sample.json` — three statements extracted from `app/data/ASCII/*`, used as the viewer's fallback/demo data
* `statements.json` — full extract output (produced by CBSTM04C)

## Shape

```json
{
  "statements": [
    {
      "accountId": "00000000002",
      "currentBalance": 158.00,
      "ficoScore": 268,
      "customer": {
        "id": "000000002",
        "name": "Enrico April Rosenbaum",
        "address": ["4917 Myrna Flats", "Apt. 453", "West Bernita IN USA 22770"]
      },
      "transactions": [
        { "tranId": "0000000070754800", "details": "Purchase at Blick, Kris and Gerlach", "amount": 355.11 }
      ],
      "totalAmount": 1576.97
    }
  ]
}
```

## Field derivation

Field values mirror the statement produced by `app/cbl/CBSTM03A.CBL`:

| JSON field | COBOL source | CBSTM03A paragraph |
| --- | --- | --- |
| `accountId` | `ACCT-ID` (`CVACT01Y.cpy`) | `5000-CREATE-STATEMENT` |
| `currentBalance` | `ACCT-CURR-BAL` (`CVACT01Y.cpy`) | `5000-CREATE-STATEMENT` |
| `ficoScore` | `CUST-FICO-CREDIT-SCORE` (`CUSTREC.cpy`) | `5000-CREATE-STATEMENT` |
| `customer.id` | `CUST-ID` (`CUSTREC.cpy`) | `2000-CUSTFILE-GET` |
| `customer.name` | `CUST-FIRST-NAME` + `CUST-MIDDLE-NAME` + `CUST-LAST-NAME` | `5000-CREATE-STATEMENT` |
| `customer.address[0..1]` | `CUST-ADDR-LINE-1`, `CUST-ADDR-LINE-2` | `5000-CREATE-STATEMENT` |
| `customer.address[2]` | `CUST-ADDR-LINE-3` + `CUST-ADDR-STATE-CD` + `CUST-ADDR-COUNTRY-CD` + `CUST-ADDR-ZIP` | `5000-CREATE-STATEMENT` |
| `transactions[].tranId` | `TRNX-ID` (`COSTM01.CPY`) | `6000-WRITE-TRANS` |
| `transactions[].details` | `TRNX-DESC` (`COSTM01.CPY`) | `6000-WRITE-TRANS` |
| `transactions[].amount` | `TRNX-AMT` (`COSTM01.CPY`) | `6000-WRITE-TRANS` |
| `totalAmount` | `WS-TOTAL-AMT` | `4000-TRNXFILE-GET` |

## Rules

* Numeric IDs (`accountId`, `customer.id`) are strings so leading zeros survive; amounts and scores are JSON numbers.
* Amounts carry exactly 2 decimals and keep the COBOL sign (`S9(9)V99` / `S9(10)V99`); negative amounts are refunds/returns.
* All trailing spaces from fixed-width COBOL fields are trimmed; `"` and `\` in text fields are JSON-escaped.
* `totalAmount` equals the sum of `transactions[].amount`, matching the `Total EXP` line of the text statement.
* Accounts with no transactions are included with `"transactions": []` and `"totalAmount": 0.00`.
* Statement order follows the XREFFILE card-number sequence; consumers sort for display instead of relying on it.
* The DJ-96 ticket contract is the base; `currentBalance` and `ficoScore` are additionally required here so the
  viewer can show the same "Basic Details" block that CBSTM03A renders.

## Known divergence from CBSTM03A output

`CBSTM03A` assembles address line 3 with `STRING ... DELIMITED BY ' '`, which stops at the first space and so
truncates multi-word city names (`West Bernita IN USA 22770` prints as `West IN USA 22770`). `address[2]` here
carries the full, untruncated value straight from the customer record.

