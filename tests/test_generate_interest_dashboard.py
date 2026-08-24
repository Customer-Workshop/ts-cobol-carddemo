import csv
import importlib.util
import io
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from decimal import Decimal
from pathlib import Path

MODULE_PATH = (
    Path(__file__).resolve().parents[1] / "scripts" / "generate_interest_dashboard.py"
)
SPEC = importlib.util.spec_from_file_location("generate_interest_dashboard", MODULE_PATH)
dashboard = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(dashboard)


def make_row(
    acct_id,
    group,
    category_count,
    balance,
    interest,
    fees,
    rate,
):
    return [
        acct_id,
        group,
        str(category_count),
        str(balance),
        str(interest),
        str(fees),
        str(rate),
    ]


def write_csv(directory, name, rows, header=None, trailing_blank=False):
    path = Path(directory) / name
    with path.open("w", newline="", encoding="ascii") as handle:
        writer = csv.writer(handle)
        writer.writerow(dashboard.HEADER if header is None else header)
        writer.writerows(rows)
        if trailing_blank:
            writer.writerow([])
    return path


class GenerateInterestDashboardTests(unittest.TestCase):
    def test_read_rows_parses_typed_rows_in_file_order_and_skips_blank_line(self):
        rows = [
            make_row("00000000002", " G2 ", 3, "12.34", "1.23", "0.00", "4.50"),
            make_row("00000000001", "", 1, "-5.00", "-0.50", "0.25", "10.00"),
        ]
        with tempfile.TemporaryDirectory() as directory:
            path = write_csv(directory, "interest.csv", rows, trailing_blank=True)

            parsed = dashboard.read_rows(path)

        self.assertEqual([row["acct_id"] for row in parsed], [
            "00000000002",
            "00000000001",
        ])
        self.assertEqual(parsed[0]["acct_group_id"], "G2")
        self.assertIsInstance(parsed[0]["category_count"], int)
        self.assertIsInstance(parsed[0]["total_balance"], Decimal)
        self.assertEqual(parsed[0]["category_count"], 3)
        self.assertEqual(parsed[0]["total_balance"], Decimal("12.34"))
        self.assertEqual(parsed[1]["acct_group_id"], "")
        self.assertEqual(parsed[1]["total_fees"], Decimal("0.25"))

    def test_read_rows_rejects_wrong_header_and_column_count(self):
        row = make_row("00000000001", "G1", 1, "1.00", "0.10", "0.00", "1.00")
        with tempfile.TemporaryDirectory() as directory:
            bad_header = list(dashboard.HEADER)
            bad_header[0] = "account_id"
            header_path = write_csv(directory, "bad-header.csv", [row], header=bad_header)
            short_path = write_csv(directory, "short-row.csv", [row[:-1]])

            with self.assertRaises(dashboard.ContractError):
                dashboard.read_rows(header_path)
            with self.assertRaises(dashboard.ContractError):
                dashboard.read_rows(short_path)

    def test_build_report_totals_weighted_rate_and_group_rollup(self):
        rows = [
            make_row("00000000001", "G1", 2, "100.00", "10.00", "1.00", "5.00"),
            make_row("00000000002", "G2", 1, "50.00", "15.00", "2.00", "7.00"),
            make_row("00000000003", "", 4, "-25.00", "-3.00", "0.50", "12.00"),
            make_row("00000000004", "ZERO", 1, "0.00", "0.00", "0.00", "0.00"),
        ]
        parsed = []
        with tempfile.TemporaryDirectory() as directory:
            path = write_csv(directory, "interest.csv", rows)
            parsed = dashboard.read_rows(path)

        report = dashboard.build_report(parsed)
        totals = report["totals"]
        self.assertEqual(totals["accounts"], 4)
        self.assertEqual(totals["balance"], Decimal("125.00"))
        self.assertEqual(totals["interest"], Decimal("22.00"))
        self.assertEqual(totals["fees"], Decimal("3.50"))
        self.assertEqual(totals["avg_rate"], Decimal("4.40"))
        self.assertIs(report["accounts_all"], parsed)

        self.assertEqual(
            [group["group"] for group in report["groups"]],
            ["G2", "G1", "ZERO", "(unknown)"],
        )
        groups = {group["group"]: group for group in report["groups"]}
        self.assertEqual(groups["(unknown)"]["accounts"], 1)
        self.assertEqual(groups["(unknown)"]["balance"], Decimal("-25.00"))
        self.assertEqual(groups["(unknown)"]["avg_rate"], Decimal("12.00"))
        self.assertEqual(groups["ZERO"]["avg_rate"], Decimal("0.00"))

    def test_aggregate_returns_zero_average_rate_when_total_balance_is_zero(self):
        rows = [
            {
                "acct_id": "00000000001",
                "acct_group_id": "G1",
                "category_count": 1,
                "total_balance": Decimal("10.00"),
                "total_interest": Decimal("1.00"),
                "total_fees": Decimal("0.00"),
                "avg_interest_rate": Decimal("4.00"),
            },
            {
                "acct_id": "00000000002",
                "acct_group_id": "G1",
                "category_count": 1,
                "total_balance": Decimal("-10.00"),
                "total_interest": Decimal("-1.00"),
                "total_fees": Decimal("0.00"),
                "avg_interest_rate": Decimal("6.00"),
            },
        ]

        report = dashboard.aggregate(rows)

        self.assertEqual(report["totals"]["balance"], Decimal("0.00"))
        self.assertEqual(report["totals"]["avg_rate"], Decimal("0.00"))
        self.assertEqual(report["groups"][0]["avg_rate"], Decimal("0.00"))

    def test_aggregate_truncates_top_accounts_to_twenty_and_orders_by_interest(self):
        rows = [
            {
                "acct_id": f"{index:011d}",
                "acct_group_id": "G1",
                "category_count": 1,
                "total_balance": Decimal("1.00"),
                "total_interest": Decimal(index),
                "total_fees": Decimal("0.00"),
                "avg_interest_rate": Decimal("1.00"),
            }
            for index in range(25)
        ]

        top_accounts = dashboard.aggregate(rows)["top_accounts"]

        self.assertEqual(len(top_accounts), 20)
        self.assertEqual(
            [row["acct_id"] for row in top_accounts],
            [f"{index:011d}" for index in range(24, 4, -1)],
        )

    def test_formatting(self):
        self.assertEqual(
            dashboard.format_currency(Decimal("1234567.89")), "1,234,567.89"
        )
        self.assertEqual(
            dashboard.format_currency(Decimal("-9876543.21")), "(9,876,543.21)"
        )
        self.assertEqual(dashboard.format_rate(Decimal("4.5")), "4.50%")
        self.assertEqual(dashboard.format_rate(Decimal("-1.25")), "-1.25%")
        self.assertEqual(dashboard.format_int(1234567), "1,234,567")

    def test_main_writes_no_data_dashboard_for_missing_and_header_only_input(self):
        with tempfile.TemporaryDirectory() as directory:
            missing_path = Path(directory) / "missing.csv"
            missing_output = Path(directory) / "missing.html"
            header_path = write_csv(directory, "header-only.csv", [])
            header_output = Path(directory) / "header-only.html"

            missing_stdout = io.StringIO()
            missing_stderr = io.StringIO()
            with redirect_stdout(missing_stdout), redirect_stderr(missing_stderr):
                missing_result = dashboard.main(
                    [str(missing_path), "-o", str(missing_output)]
                )

            header_stdout = io.StringIO()
            header_stderr = io.StringIO()
            with redirect_stdout(header_stdout), redirect_stderr(header_stderr):
                header_result = dashboard.main(
                    [str(header_path), "-o", str(header_output)]
                )

            self.assertEqual(missing_result, 0)
            self.assertEqual(header_result, 0)
            self.assertIn("No data", missing_output.read_text(encoding="utf-8"))
            self.assertIn("No data", header_output.read_text(encoding="utf-8"))
            self.assertNotIn("Traceback", missing_stderr.getvalue())
            self.assertNotIn("Traceback", header_stderr.getvalue())
            self.assertIn("no data", missing_stderr.getvalue().lower())
            self.assertIn("no account rows", header_stderr.getvalue().lower())
            self.assertIn("0 accounts", missing_stdout.getvalue())
            self.assertIn("0 accounts", header_stdout.getvalue())

    def test_populated_render_contains_kpis_and_negative_amount_class(self):
        rows = [
            {
                "acct_id": "00000000001",
                "acct_group_id": "G1",
                "category_count": 2,
                "total_balance": Decimal("-1234.50"),
                "total_interest": Decimal("-12.34"),
                "total_fees": Decimal("0.00"),
                "avg_interest_rate": Decimal("1.25"),
            }
        ]

        document = dashboard.render_html(
            dashboard.build_report(rows), "interest-summary.csv"
        )

        self.assertIn("Accounts", document)
        self.assertIn("(1,234.50)", document)
        self.assertIn("(12.34)", document)
        self.assertIn('class="kpi-value neg"', document)
        self.assertIn('class="num neg"', document)


if __name__ == "__main__":
    unittest.main()
