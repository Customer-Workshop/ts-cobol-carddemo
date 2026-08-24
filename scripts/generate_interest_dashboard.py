#!/usr/bin/env python3
"""Render the DJ-106 interest & fees dashboard from the batch extract CSV.

Reads ``app/data/reports/interest-summary.csv`` (see ``docs/DJ-106-csv-contract.md``)
and writes a fully self-contained HTML page (inline CSS and inline SVG only) to
``docs/reports/interest-dashboard.html``.

Usage:
    python3 scripts/generate_interest_dashboard.py [INPUT_CSV] [-o OUTPUT_HTML]
"""

import argparse
import csv
import html
import os
import sys
from decimal import Decimal, InvalidOperation

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_INPUT = os.path.join(REPO_ROOT, "app", "data", "reports", "interest-summary.csv")
DEFAULT_OUTPUT = os.path.join(REPO_ROOT, "docs", "reports", "interest-dashboard.html")

HEADER = [
    "acct_id",
    "acct_group_id",
    "category_count",
    "total_balance",
    "total_interest",
    "total_fees",
    "avg_interest_rate",
]

UNKNOWN_GROUP = "(unknown)"
TOP_N = 20
ZERO = Decimal("0.00")


class ContractError(Exception):
    """The input file does not follow the locked CSV contract."""


def _decimal(value, column, line_no):
    try:
        return Decimal(value.strip())
    except (InvalidOperation, AttributeError):
        raise ContractError(
            f"line {line_no}: column {column} is not a valid decimal: {value!r}"
        )


def _int(value, column, line_no):
    try:
        return int(value.strip())
    except (ValueError, AttributeError):
        raise ContractError(
            f"line {line_no}: column {column} is not a valid integer: {value!r}"
        )


def read_rows(path):
    """Parse the extract CSV into a list of dicts, ordered as in the file."""
    with open(path, newline="", encoding="ascii") as handle:
        reader = csv.reader(handle)
        try:
            header = next(reader)
        except StopIteration:
            return []
        if [field.strip() for field in header] != HEADER:
            raise ContractError(f"unexpected header {header!r}, expected {HEADER!r}")
        rows = []
        for line_no, record in enumerate(reader, start=2):
            if not record or all(not field.strip() for field in record):
                continue
            if len(record) != len(HEADER):
                raise ContractError(
                    f"line {line_no}: expected {len(HEADER)} columns, "
                    f"found {len(record)}"
                )
            rows.append(
                {
                    "acct_id": record[0].strip(),
                    "acct_group_id": record[1].strip(),
                    "category_count": _int(record[2], "category_count", line_no),
                    "total_balance": _decimal(record[3], "total_balance", line_no),
                    "total_interest": _decimal(record[4], "total_interest", line_no),
                    "total_fees": _decimal(record[5], "total_fees", line_no),
                    "avg_interest_rate": _decimal(
                        record[6], "avg_interest_rate", line_no
                    ),
                }
            )
        return rows


def group_label(acct_group_id):
    return acct_group_id if acct_group_id else UNKNOWN_GROUP


def aggregate(rows):
    """Roll the account rows up into the figures the dashboard renders."""
    totals = {
        "accounts": len(rows),
        "balance": ZERO,
        "interest": ZERO,
        "fees": ZERO,
        "avg_rate": ZERO,
    }
    groups = {}
    weighted = ZERO
    for row in rows:
        totals["balance"] += row["total_balance"]
        totals["interest"] += row["total_interest"]
        totals["fees"] += row["total_fees"]
        row_weighted = row["total_balance"] * row["avg_interest_rate"]
        weighted += row_weighted
        label = group_label(row["acct_group_id"])
        group = groups.setdefault(
            label,
            {
                "group": label,
                "accounts": 0,
                "balance": ZERO,
                "interest": ZERO,
                "fees": ZERO,
                "weighted": ZERO,
            },
        )
        group["accounts"] += 1
        group["balance"] += row["total_balance"]
        group["interest"] += row["total_interest"]
        group["fees"] += row["total_fees"]
        group["weighted"] += row_weighted

    if totals["balance"] != 0:
        totals["avg_rate"] = weighted / totals["balance"]

    for group in groups.values():
        group["avg_rate"] = (
            group["weighted"] / group["balance"] if group["balance"] != 0 else ZERO
        )

    ordered_groups = sorted(
        groups.values(), key=lambda g: (-g["interest"], g["group"])
    )
    top_accounts = sorted(rows, key=lambda r: (-r["total_interest"], r["acct_id"]))[
        :TOP_N
    ]
    return {"totals": totals, "groups": ordered_groups, "top_accounts": top_accounts}


def format_currency(value):
    """Format a monetary amount with thousands separators and 2 decimals."""
    amount = Decimal(value)
    negative = amount < 0
    text = f"{abs(amount):,.2f}"
    return f"({text})" if negative else text


def format_rate(value):
    return f"{Decimal(value):,.2f}%"


def format_int(value):
    return f"{int(value):,d}"


def _amount_cell(value, tag="td"):
    css = "num neg" if Decimal(value) < 0 else "num"
    return f'<{tag} class="{css}">{format_currency(value)}</{tag}>'


def _kpi(label, value, negative=False):
    css = "kpi-value neg" if negative else "kpi-value"
    return (
        "      <div class=\"kpi\">\n"
        f"        <div class=\"kpi-label\">{html.escape(label)}</div>\n"
        f"        <div class=\"{css}\">{html.escape(value)}</div>\n"
        "      </div>"
    )


def render_group_chart(groups):
    """Inline SVG bar chart of total interest per disclosure group."""
    if not groups:
        return "<p class=\"empty\">No disclosure groups to chart.</p>"
    bar_height, gap, left, top = 22, 10, 150, 16
    width, chart_width = 880, 560
    height = top * 2 + len(groups) * (bar_height + gap)
    scale_base = max((abs(g["interest"]) for g in groups), default=ZERO)
    parts = [
        (
            f'<svg class="chart" viewBox="0 0 {width} {height}" role="img" '
            'aria-label="Total interest by disclosure group" '
            'xmlns="http://www.w3.org/2000/svg">'
        )
    ]
    for index, group in enumerate(groups):
        y = top + index * (bar_height + gap)
        interest = group["interest"]
        length = 0
        if scale_base != 0:
            length = int(abs(interest) / scale_base * chart_width)
        fill = "#c62828" if interest < 0 else "#2f6f4f"
        parts.append(
            f'<text x="{left - 8}" y="{y + bar_height - 6}" '
            f'class="bar-label">{html.escape(group["group"])}</text>'
        )
        parts.append(
            f'<rect x="{left}" y="{y}" width="{max(length, 1)}" '
            f'height="{bar_height}" fill="{fill}" rx="2"></rect>'
        )
        parts.append(
            f'<text x="{left + max(length, 1) + 8}" '
            f'y="{y + bar_height - 6}" class="bar-value">'
            f'{html.escape(format_currency(interest))}</text>'
        )
    parts.append("</svg>")
    return "\n      ".join(parts)


def render_html(report, source_path):
    totals = report["totals"]
    if totals["accounts"] == 0:
        body = (
            "    <section class=\"panel\">\n"
            "      <h2>No data</h2>\n"
            f"      <p class=\"empty\">The extract <code>{html.escape(source_path)}</code> "
            "contained no account "
            "rows. Run the COBOL interest extract, then regenerate this dashboard.</p>\n"
            "    </section>"
        )
    else:
        kpis = "\n".join(
            [
                _kpi("Accounts", format_int(totals["accounts"])),
                _kpi(
                    "Total balance",
                    format_currency(totals["balance"]),
                    totals["balance"] < 0,
                ),
                _kpi(
                    "Total interest",
                    format_currency(totals["interest"]),
                    totals["interest"] < 0,
                ),
                _kpi("Total fees", format_currency(totals["fees"]), totals["fees"] < 0),
                _kpi(
                    "Weighted avg interest rate",
                    format_rate(totals["avg_rate"]),
                    totals["avg_rate"] < 0,
                ),
            ]
        )
        group_rows = "\n".join(
            f'          <tr><td>{html.escape(group["group"])}</td>'
            f'<td class="num">{format_int(group["accounts"])}</td>'
            f'{_amount_cell(group["balance"])}{_amount_cell(group["interest"])}'
            f'{_amount_cell(group["fees"])}'
            f'<td class="num">{format_rate(group["avg_rate"])}</td></tr>'
            for group in report["groups"]
        )

        def rows_for(records):
            return "\n".join(
                f'          <tr><td class="mono">{html.escape(row["acct_id"])}</td>'
                f'<td>{html.escape(group_label(row["acct_group_id"]))}</td>'
                f'<td class="num">{format_int(row["category_count"])}</td>'
                f'{_amount_cell(row["total_balance"])}'
                f'{_amount_cell(row["total_interest"])}'
                f'{_amount_cell(row["total_fees"])}'
                f'<td class="num">{format_rate(row["avg_interest_rate"])}</td></tr>'
                for row in records
            )

        account_header = (
            "          <tr><th>Account</th><th>Group</th><th class=\"num\">Categories</th>"
            "<th class=\"num\">Balance</th><th class=\"num\">Interest</th>"
            "<th class=\"num\">Fees</th><th class=\"num\">Avg rate</th></tr>"
        )
        body = f"""    <section class="kpis">
{kpis}
    </section>

    <section class="panel">
      <h2>Interest by disclosure group</h2>
      {render_group_chart(report["groups"])}
      <table>
        <thead>
          <tr><th>Group</th><th class="num">Accounts</th><th class="num">Balance</th><th class="num">Interest</th><th class="num">Fees</th><th class="num">Avg rate</th></tr>
        </thead>
        <tbody>
{group_rows}
        </tbody>
      </table>
    </section>

    <section class="panel">
      <h2>Top {TOP_N} accounts by interest</h2>
      <table>
        <thead>
{account_header}
        </thead>
        <tbody>
{rows_for(report["top_accounts"])}
        </tbody>
      </table>
    </section>

    <section class="panel">
      <h2>All accounts ({format_int(totals["accounts"])})</h2>
      <table>
        <thead>
{account_header}
        </thead>
        <tbody>
{rows_for(report["accounts_all"])}
        </tbody>
      </table>
    </section>"""

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>CardDemo — Interest &amp; Fees Dashboard (DJ-106)</title>
<style>
  :root {{ color-scheme: light; }}
  body {{ margin: 0; padding: 24px; background: #f4f6f8; color: #1c2733;
         font-family: -apple-system, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }}
  h1 {{ font-size: 22px; margin: 0 0 4px; }}
  h2 {{ font-size: 16px; margin: 0 0 12px; }}
  .source {{ color: #5b6b7b; font-size: 12px; margin: 0 0 20px; }}
  .kpis {{ display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 20px; }}
  .kpi {{ flex: 1 1 180px; background: #fff; border: 1px solid #dbe2e8; border-radius: 6px;
         padding: 14px 16px; }}
  .kpi-label {{ font-size: 11px; text-transform: uppercase; letter-spacing: .06em;
               color: #5b6b7b; margin-bottom: 6px; }}
  .kpi-value {{ font-size: 22px; font-weight: 600; font-variant-numeric: tabular-nums; }}
  .panel {{ background: #fff; border: 1px solid #dbe2e8; border-radius: 6px;
           padding: 16px; margin-bottom: 20px; overflow-x: auto; }}
  table {{ border-collapse: collapse; width: 100%; font-size: 13px; }}
  th, td {{ padding: 6px 10px; border-bottom: 1px solid #eceff2; text-align: left; }}
  th {{ background: #f7f9fb; font-size: 11px; text-transform: uppercase;
       letter-spacing: .05em; color: #5b6b7b; }}
  td.num, th.num {{ text-align: right; font-variant-numeric: tabular-nums; }}
  td.mono {{ font-family: ui-monospace, "SFMono-Regular", Menlo, Consolas, monospace; }}
  .neg {{ color: #c62828; }}
  .empty {{ color: #5b6b7b; font-size: 13px; }}
  .chart {{ width: 100%; height: auto; margin-bottom: 16px; }}
  .bar-label {{ font-size: 12px; fill: #1c2733; text-anchor: end; }}
  .bar-value {{ font-size: 12px; fill: #5b6b7b; }}
  code {{ background: #f0f3f6; padding: 1px 4px; border-radius: 3px; }}
</style>
</head>
<body>
  <main>
    <h1>Interest &amp; Fees Dashboard</h1>
    <p class="source">Generated from <code>{html.escape(source_path)}</code> — CardDemo DJ-106</p>
{body}
  </main>
</body>
</html>
"""


def build_report(rows):
    report = aggregate(rows)
    report["accounts_all"] = rows
    return report


def relative_source(path):
    try:
        relative = os.path.relpath(os.path.abspath(path), REPO_ROOT)
    except ValueError:
        return path
    return path if relative.startswith(os.pardir) else relative


def parse_args(argv):
    parser = argparse.ArgumentParser(
        description="Generate the DJ-106 interest & fees HTML dashboard."
    )
    parser.add_argument(
        "input",
        nargs="?",
        default=DEFAULT_INPUT,
        help=f"extract CSV to read (default: {relative_source(DEFAULT_INPUT)})",
    )
    parser.add_argument(
        "-o",
        "--output",
        default=DEFAULT_OUTPUT,
        help=f"HTML file to write (default: {relative_source(DEFAULT_OUTPUT)})",
    )
    return parser.parse_args(argv)


def main(argv=None):
    args = parse_args(argv)
    source = relative_source(args.input)
    try:
        rows = read_rows(args.input)
    except FileNotFoundError:
        rows = []
        sys.stderr.write(
            f"warning: extract {source} not found; writing a 'no data' dashboard\n"
        )
    except OSError as error:
        sys.stderr.write(f"error: cannot read {source}: {error}\n")
        return 1
    except ContractError as error:
        sys.stderr.write(
            f"error: {source} does not match the CSV contract: {error}\n"
        )
        return 1

    if not rows:
        sys.stderr.write(f"warning: no account rows in {source}\n")

    document = render_html(build_report(rows), source)
    output_dir = os.path.dirname(os.path.abspath(args.output))
    if output_dir:
        os.makedirs(output_dir, exist_ok=True)
    with open(args.output, "w", encoding="utf-8") as handle:
        handle.write(document)
    sys.stdout.write(f"wrote {relative_source(args.output)} ({len(rows)} accounts)\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
