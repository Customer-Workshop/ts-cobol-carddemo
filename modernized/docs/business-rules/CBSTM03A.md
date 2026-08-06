# CBSTM03A business-rule specification

## Purpose and trigger

`CREASTMT` invokes `CBSTM03A` to produce both an 80-byte plain-text account statement and 100-byte HTML statement records. It groups sequential transaction input by card and obtains account/customer details through `CBSTM03B`.

## Inputs and outputs

`TRNXFILE`, `XREFFILE`, `CUSTFILE`, and `ACCTFILE` are opened/read through the linkage subroutine. `STMTFILE` is opened `OUTPUT` with `FD-STMTFILE-REC PIC X(80)`; `HTMLFILE` is opened `OUTPUT` with `FD-HTMLFILE-REC PIC X(100)`.

### Exact working layouts

`WS-TOTAL-AMT PIC S9(9)V99 COMP-3`; `WS-TRN-AMT PIC S9(9)V99` display signed; `WS-SAVE-CARD PIC X(16)`; `END-OF-FILE PIC X(01)`.

Statement line fields include `ST-NAME PIC X(75)`, `ST-ADD1 PIC X(50)`, `ST-ADD2 PIC X(50)`, `ST-ADD3 PIC X(80)`, `ST-ACCT-ID PIC X(20)`, `ST-CURR-BAL PIC 9(9).99-`, `ST-FICO-SCORE PIC X(20)`, `ST-TRANID PIC X(16)`, `ST-TRANDT PIC X(49)`, `ST-TRANAMT PIC Z(9).99-`, and `ST-TOTAL-TRAMT PIC Z(9).99-`. HTML lines are each `PIC X(100)`.

## Validation and error rules (source order)

1. Open statement and HTML outputs; an unsuccessful open enters the abend paragraph.
2. Ask `CBSTM03B` to open `TRNXFILE`, `XREFFILE`, `CUSTFILE`, and `ACCTFILE`. Each call returns a two-byte file status in the linkage area; non-`00` statuses cause display/abend rather than a partial report.
3. Read the next transaction. EOF closes all four files and report outputs. A non-EOF read error displays the status and abends.
4. On a new card, lookup xref, customer, and account; populate header/basic-detail lines. Transactions for the same card are accumulated in `WS-TOTAL-AMT` until the card changes.
5. At card change/EOF, write the transaction total and closing statement/HTML sections, then begin the next card. There is no reject-record output.

## Calculations and source excerpt

`WS-TOTAL-AMT` is packed decimal with two fractional digits. The program executes `ADD TRNX-AMT TO WS-TOTAL-AMT`; each transaction amount is therefore accumulated at cent scale. Display edits use `PIC Z(9).99-`, which suppresses leading zeroes and places a trailing minus for negative amounts.

```cobol
IF WS-SAVE-CARD NOT = TRNX-CARD-NUM
    PERFORM 8000-CLOSE-STATEMENT
    MOVE TRNX-CARD-NUM TO WS-SAVE-CARD
    MOVE ZERO TO WS-TOTAL-AMT
END-IF
ADD TRNX-AMT TO WS-TOTAL-AMT
```

## Control flow and failure handling

The program uses the TIOT to derive DD names, then calls `CBSTM03B` with `O`, `R`, `K`, and `C` operations. It has no CICS transaction, PF keys, or COMMAREA. Any file failure calls the abend paragraph; output files are not append targets.

## Test cases

| # | Concrete input | Expected output |
|---:|---|---|
| 1 | Fixture card `0683586198171516`, account xref `00000000027`, two transactions `504.78` and `919.00` | One statement group for that card; `WS-TOTAL-AMT` is `1,423.78`; closing total line contains formatted `1,423.78`. |
| 2 | One transaction amount `0.00` for card `0500024453765740` | Statement is emitted with total `0.00`; no omitted group. |
| 3 | Same card receives `-10.25` then `5.00` | Packed total becomes `-5.25`; `ST-TOTAL-TRAMT` uses trailing minus edit. |
| 4 | Transaction references card absent from xref | `CBSTM03B` keyed read returns non-`00`; source displays file status and abends; no fabricated statement. |
| 5 | Empty transaction input (`EOF` on first read) | Headers are not invented; files close and report completes without a card statement. |
