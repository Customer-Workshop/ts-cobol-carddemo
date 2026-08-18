#!/usr/bin/env python3
"""Render the DJ-94 transaction summary CSV as a self-contained HTML dashboard.

Input format is defined by docs/tran_summary_contract.md. The output is a single
HTML file with all CSS inline and no external resources, so it renders offline
from a file:// URL.
"""

import argparse
import csv
import html
import sys
from decimal import Decimal, InvalidOperation

HEADER = [
    "TRAN_TYPE_CD",
    "TRAN_CAT_CD",
    "TYPE_DESC",
    "CAT_DESC",
    "TRAN_COUNT",
    "TOTAL_AMOUNT",
]


class ContractError(Exception):
    """Raised when the input CSV does not match the frozen contract."""


def read_rows(csv_path):
    with open(csv_path, newline="", encoding="utf-8") as handle:
        reader = csv.reader(handle)
        try:
            header = next(reader)
        except StopIteration:
            raise ContractError(f"{csv_path}: file is empty, expected a header row")
        if [field.strip() for field in header] != HEADER:
            raise ContractError(
                f"{csv_path}: unexpected header row {header!r}, expected {HEADER!r}"
            )
        rows = []
        for line_no, record in enumerate(reader, start=2):
            if not record or all(field.strip() == "" for field in record):
                continue
            if len(record) != len(HEADER):
                raise ContractError(
                    f"{csv_path}:{line_no}: expected {len(HEADER)} fields, got {len(record)}"
                )
            type_cd, cat_cd, type_desc, cat_desc, count, amount = record
            try:
                parsed_count = int(count)
            except ValueError:
                raise ContractError(f"{csv_path}:{line_no}: TRAN_COUNT {count!r} is not an integer")
            try:
                parsed_amount = Decimal(amount)
            except InvalidOperation:
                raise ContractError(
                    f"{csv_path}:{line_no}: TOTAL_AMOUNT {amount!r} is not a decimal"
                )
            rows.append(
                {
                    "type_cd": type_cd,
                    "cat_cd": cat_cd,
                    "type_desc": type_desc,
                    "cat_desc": cat_desc,
                    "count": parsed_count,
                    "amount": parsed_amount,
                }
            )
    rows.sort(key=lambda row: row["amount"], reverse=True)
    return rows


def money(value):
    return f"{value:,.2f}"


def bar_geometry(rows):
    """Zero-baseline offset and per-row bar widths, as percentages of the plot area."""
    max_positive = max([row["amount"] for row in rows] + [Decimal(0)])
    max_negative = -min([row["amount"] for row in rows] + [Decimal(0)])
    span = max_positive + max_negative
    if span == 0:
        return 0.0, [0.0 for _ in rows]
    zero_at = float(max_negative / span) * 100.0
    widths = [abs(float(row["amount"] / span)) * 100.0 for row in rows]
    return zero_at, widths


CSS = """
:root { color-scheme: light; }
* { box-sizing: border-box; }
body {
  margin: 0;
  padding: 32px;
  font-family: "Helvetica Neue", Helvetica, Arial, sans-serif;
  background: #f4f6f8;
  color: #1f2933;
}
h1 { margin: 0 0 4px; font-size: 24px; }
h2 { margin: 32px 0 12px; font-size: 18px; }
.subtitle { margin: 0 0 24px; color: #616e7c; font-size: 13px; }
.card {
  background: #fff;
  border: 1px solid #d9e2ec;
  border-radius: 6px;
  padding: 20px;
}
.totals { display: flex; flex-wrap: wrap; gap: 16px; }
.totals .card { flex: 1 1 220px; }
.totals .label {
  margin: 0 0 6px;
  font-size: 12px;
  letter-spacing: .06em;
  text-transform: uppercase;
  color: #616e7c;
}
.totals .value { margin: 0; font-size: 26px; font-weight: 600; }
table { width: 100%; border-collapse: collapse; font-size: 14px; }
th, td { padding: 8px 10px; border-bottom: 1px solid #e4e7eb; text-align: left; }
th {
  font-size: 12px;
  letter-spacing: .04em;
  text-transform: uppercase;
  color: #52606d;
  background: #f0f4f8;
}
tbody tr:last-child td { border-bottom: none; }
td.num, th.num { text-align: right; font-variant-numeric: tabular-nums; }
td.code { font-family: "SFMono-Regular", Consolas, monospace; }
.negative { color: #a4262c; }
.chart {
  display: grid;
  grid-template-columns: minmax(160px, 260px) 1fr auto;
  gap: 6px 14px;
  align-items: center;
  font-size: 13px;
}
.chart .name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chart .value { font-variant-numeric: tabular-nums; white-space: nowrap; }
.track { position: relative; height: 18px; background: #f0f4f8; border-radius: 3px; }
.track .axis { position: absolute; top: -2px; bottom: -2px; width: 1px; background: #9aa5b1; }
.bar { position: absolute; top: 3px; height: 12px; border-radius: 2px; min-width: 1px; }
.bar.pos { background: #2b6cb0; }
.bar.neg { background: #c05621; }
.legend { margin-top: 14px; display: flex; gap: 18px; font-size: 12px; color: #52606d; }
.swatch { display: inline-block; width: 10px; height: 10px; border-radius: 2px; margin-right: 6px; }
.swatch.pos { background: #2b6cb0; }
.swatch.neg { background: #c05621; }
.empty { margin: 0; color: #616e7c; font-size: 14px; }
"""


def render(rows, source_path):
    esc = html.escape
    total_count = sum(row["count"] for row in rows)
    total_amount = sum((row["amount"] for row in rows), Decimal(0))
    zero_at, widths = bar_geometry(rows)

    out = [
        "<!DOCTYPE html>",
        '<html lang="en">',
        "<head>",
        '<meta charset="utf-8">',
        '<meta name="viewport" content="width=device-width, initial-scale=1">',
        "<title>CardDemo Transaction Summary</title>",
        f"<style>{CSS}</style>",
        "</head>",
        "<body>",
        "<h1>CardDemo Transaction Summary</h1>",
        (
            f'<p class="subtitle">Source: {esc(source_path)} &middot; '
            f"{len(rows)} type/category row{'' if len(rows) == 1 else 's'}</p>"
        ),
        '<div class="totals">',
        (
            '<div class="card"><p class="label">Total transactions</p>'
            f'<p class="value">{total_count:,}</p></div>'
        ),
        (
            '<div class="card"><p class="label">Total amount</p>'
            f'<p class="value{" negative" if total_amount < 0 else ""}">'
            f"{money(total_amount)}</p></div>"
        ),
        "</div>",
        "<h2>Summary by type and category</h2>",
        '<div class="card">',
    ]

    if not rows:
        out.append('<p class="empty">No transaction rows in the summary CSV.</p>')
    else:
        out += [
            "<table>",
            "<thead><tr><th>Type</th><th>Category</th><th>Type description</th>",
            '<th>Category description</th><th class="num">Transactions</th>',
            '<th class="num">Total amount</th></tr></thead>',
            "<tbody>",
        ]
        for row in rows:
            amount_class = "num negative" if row["amount"] < 0 else "num"
            out.append(
                f'<tr><td class="code">{esc(row["type_cd"])}</td>'
                f'<td class="code">{esc(row["cat_cd"])}</td>'
                f'<td>{esc(row["type_desc"])}</td>'
                f'<td>{esc(row["cat_desc"])}</td>'
                f'<td class="num">{row["count"]:,}</td>'
                f'<td class="{amount_class}">{money(row["amount"])}</td></tr>'
            )
        out += ["</tbody>", "</table>"]
    out += ["</div>", "<h2>Total amount per type / category</h2>", '<div class="card">']

    if not rows:
        out.append('<p class="empty">Nothing to chart.</p>')
    else:
        out.append('<div class="chart">')
        for row, width in zip(rows, widths):
            label = f'{row["type_cd"]}/{row["cat_cd"]} {row["type_desc"]} - {row["cat_desc"]}'
            if row["amount"] < 0:
                bar = (
                    f'<span class="bar neg" style="right:{100.0 - zero_at:.4f}%;'
                    f'width:{width:.4f}%"></span>'
                )
                value_class = "value negative"
            else:
                bar = f'<span class="bar pos" style="left:{zero_at:.4f}%;width:{width:.4f}%"></span>'
                value_class = "value"
            out.append(
                f'<span class="name" title="{esc(label)}">{esc(label)}</span>'
                f'<span class="track"><span class="axis" style="left:{zero_at:.4f}%"></span>'
                f'{bar}</span>'
                f'<span class="{value_class}">{money(row["amount"])}</span>'
            )
        out += [
            "</div>",
            (
                '<div class="legend">'
                '<span><span class="swatch pos"></span>Debit (positive)</span>'
                '<span><span class="swatch neg"></span>Credit (negative)</span>'
                "</div>"
            ),
        ]

    out += ["</div>", "</body>", "</html>", ""]
    return "\n".join(out)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv_path", nargs="?", default="reports/tran_summary.csv")
    parser.add_argument("output_path", nargs="?", default="reports/dashboard.html")
    args = parser.parse_args(argv)

    try:
        rows = read_rows(args.csv_path)
    except (ContractError, OSError) as exc:
        print(f"gen_dashboard: {exc}", file=sys.stderr)
        return 1

    with open(args.output_path, "w", encoding="utf-8") as handle:
        handle.write(render(rows, args.csv_path))
    print(f"gen_dashboard: wrote {args.output_path} from {args.csv_path} ({len(rows)} rows)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
