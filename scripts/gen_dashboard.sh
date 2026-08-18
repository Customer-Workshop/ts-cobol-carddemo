#!/usr/bin/env bash
# DJ-94: render the transaction summary CSV as a self-contained HTML dashboard.
#
# Usage: scripts/gen_dashboard.sh [csv_path] [output_path]
#   csv_path     defaults to reports/tran_summary.csv
#   output_path  defaults to reports/dashboard.html
set -euo pipefail

CSV_PATH="${1:-reports/tran_summary.csv}"
OUT_PATH="${2:-reports/dashboard.html}"

if [ ! -f "$CSV_PATH" ]; then
  echo "gen_dashboard.sh: input CSV not found: $CSV_PATH" >&2
  exit 1
fi

if command -v python3 >/dev/null 2>&1; then
  PYTHON=python3
elif command -v python >/dev/null 2>&1; then
  PYTHON=python
else
  echo "gen_dashboard.sh: python 3 is required but was not found on PATH" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

mkdir -p "$(dirname "$OUT_PATH")"
exec "$PYTHON" "$SCRIPT_DIR/gen_dashboard.py" "$CSV_PATH" "$OUT_PATH"
