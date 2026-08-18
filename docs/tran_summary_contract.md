# Transaction Summary CSV Contract (DJ-94)

This contract is **frozen**. The COBOL batch extract (`app/cbl/CBTRN04C.cbl`, job
`app/jcl/TRANSUMM.jcl`) is the sole producer; the dashboard generator
(`scripts/gen_dashboard.sh`) is the sole consumer. Neither side may change the
format unilaterally.

## File

- Path: `reports/tran_summary.csv`
- Encoding: UTF-8
- Line 1 is the header row, exactly:

```
TRAN_TYPE_CD,TRAN_CAT_CD,TYPE_DESC,CAT_DESC,TRAN_COUNT,TOTAL_AMOUNT
```

## Columns

| Column | Format | Notes |
| --- | --- | --- |
| `TRAN_TYPE_CD` | 2 characters | Transaction type code, e.g. `01` |
| `TRAN_CAT_CD` | 4 digits | Transaction category code, zero padded, e.g. `0001` |
| `TYPE_DESC` | text | Transaction type description; quoted if it contains a comma |
| `CAT_DESC` | text | Transaction category description; quoted if it contains a comma |
| `TRAN_COUNT` | integer | Number of transactions in the pair; no thousands separators |
| `TOTAL_AMOUNT` | signed decimal, exactly 2 fraction digits | e.g. `1234.56`, `-123.45`; no thousands separators, no currency symbol |

## Rows

- Exactly one row per `(TRAN_TYPE_CD, TRAN_CAT_CD)` pair present in the transaction data.
- Pairs with no transactions are not emitted.
- No footer / totals row: grand totals are computed by the consumer.

## Quoting

- A field is wrapped in double quotes only when its value contains a comma.
- Embedded double quotes inside a quoted field are escaped by doubling (`""`).

## Example

```
TRAN_TYPE_CD,TRAN_CAT_CD,TYPE_DESC,CAT_DESC,TRAN_COUNT,TOTAL_AMOUNT
01,0001,Purchase,Regular Sales Draft,1204,254318.77
03,0001,Credit,"Returns, refunds and credits",96,-18422.10
```

## Development mock

`reports/tran_summary.mock.csv` is a temporary, contract-shaped fixture used by
the dashboard generator until the batch extract lands. The generator must accept
the CSV path as its first argument, defaulting to `reports/tran_summary.csv`, so
the mock can be deleted without any code change.
