#!/usr/bin/env python3
"""Independent raw-extract -> PostgreSQL comparison for the wave-1 data seams.

Expectations are derived ONLY from the copybooks (app/cpy/CVACT01Y.cpy, CVCUS01Y.cpy, CVACT03Y.cpy,
CSUSR01Y.cpy) and the raw files under app/data. The Java parser is not consulted.
Overpunch: COBOL DISPLAY S9(n) trailing-sign zoned decimal in the ASCII extract
  '{' = +0, 'A'..'I' = +1..+9, '}' = -0, 'J'..'R' = -1..-9 (IBM zoned sign nibble C/D rendered in ASCII).
Rows are pulled from PostgreSQL through psql (CSV), every row of every table is compared, and the first
five rows per table are printed field by field as the spot-check evidence.
Usage: python3 raw_vs_pg.py  (env PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE default to the runbook docker DB)
"""
import csv, io, os, subprocess, sys, pathlib, re
from decimal import Decimal

ROOT = pathlib.Path(__file__).resolve().parents[3]
DATA = ROOT / 'app' / 'data'
ENV = dict(os.environ, PGHOST=os.environ.get('PGHOST', 'localhost'), PGPORT=os.environ.get('PGPORT', '5433'),
           PGUSER=os.environ.get('PGUSER', 'carddemo'), PGPASSWORD=os.environ.get('PGPASSWORD', 'carddemo'),
           PGDATABASE=os.environ.get('PGDATABASE', 'carddemo'))

POS = {'{': 0, 'A': 1, 'B': 2, 'C': 3, 'D': 4, 'E': 5, 'F': 6, 'G': 7, 'H': 8, 'I': 9}
NEG = {'}': 0, 'J': 1, 'K': 2, 'L': 3, 'M': 4, 'N': 5, 'O': 6, 'P': 7, 'Q': 8, 'R': 9}


def s9_10v99(field):
    """PIC S9(10)V99 DISPLAY, 12 bytes, trailing overpunch sign."""
    assert len(field) == 12, field
    body, last = field[:-1], field[-1]
    if last in POS: digits, sign = body + str(POS[last]), 1
    elif last in NEG: digits, sign = body + str(NEG[last]), -1
    elif last.isdigit(): digits, sign = field, 1   # unsigned rendering
    else: raise ValueError(f'bad overpunch {field!r}')
    return (Decimal(digits) / 100) * sign


def cut(rec, spec):
    out, off = {}, 0
    for name, ln, kind in spec:
        raw = rec[off:off + ln]; off += ln
        if name == 'FILLER': continue
        if kind == 'X': out[name] = raw.rstrip()
        elif kind == '9': out[name] = int(raw)
        elif kind == 'M': out[name] = s9_10v99(raw)
        elif kind == 'D': out[name] = raw.strip()
    return out, off


ACCT = [('acct_id', 11, '9'), ('acct_active_status', 1, 'X'), ('acct_curr_bal', 12, 'M'), ('acct_credit_limit', 12, 'M'),
        ('acct_cash_credit_limit', 12, 'M'), ('acct_open_date', 10, 'D'), ('acct_expiraion_date', 10, 'D'),
        ('acct_reissue_date', 10, 'D'), ('acct_curr_cyc_credit', 12, 'M'), ('acct_curr_cyc_debit', 12, 'M'),
        ('acct_addr_zip', 10, 'X'), ('acct_group_id', 10, 'X'), ('FILLER', 178, 'X')]          # = 300
CUST = [('cust_id', 9, '9'), ('cust_first_name', 25, 'X'), ('cust_middle_name', 25, 'X'), ('cust_last_name', 25, 'X'),
        ('cust_addr_line_1', 50, 'X'), ('cust_addr_line_2', 50, 'X'), ('cust_addr_line_3', 50, 'X'),
        ('cust_addr_state_cd', 2, 'X'), ('cust_addr_country_cd', 3, 'X'), ('cust_addr_zip', 10, 'X'),
        ('cust_phone_num_1', 15, 'X'), ('cust_phone_num_2', 15, 'X'), ('cust_ssn', 9, '9'), ('cust_govt_issued_id', 20, 'X'),
        ('cust_dob_yyyy_mm_dd', 10, 'D'), ('cust_eft_account_id', 10, 'X'), ('cust_pri_card_holder_ind', 1, 'X'),
        ('cust_fico_credit_score', 3, '9'), ('FILLER', 168, 'X')]                                 # = 500
XREF = [('xref_card_number', 16, 'X'), ('xref_cust_id', 9, '9'), ('xref_acct_id', 11, '9'), ('FILLER', 14, 'X')]  # = 50
USER = [('sec_usr_id', 8, 'X'), ('sec_usr_fname', 20, 'X'), ('sec_usr_lname', 20, 'X'), ('sec_usr_pwd_legacy', 8, 'X'),
        ('sec_usr_type', 1, 'X'), ('FILLER', 23, 'X')]                                            # = 80
for spec, n in ((ACCT, 300), (CUST, 500), (XREF, 50), (USER, 80)):
    assert sum(l for _, l, _ in spec) == n


def ascii_records(name, reclen):
    txt = (DATA / 'ASCII' / name).read_text(encoding='ascii')
    lines = txt.split('\n')
    if lines and lines[-1] == '': lines.pop()
    widths = sorted({len(l) for l in lines})
    return [l.ljust(reclen) for l in lines], widths


def ebcdic_records(name, reclen):
    b = (DATA / 'EBCDIC' / name).read_bytes()
    assert len(b) % reclen == 0, (len(b), reclen)
    return [b[i:i + reclen].decode('cp037') for i in range(0, len(b), reclen)], len(b)


def pg(sql):
    r = subprocess.run(['psql', '--csv', '-c', sql], env=ENV, capture_output=True, text=True, check=True)
    return list(csv.DictReader(io.StringIO(r.stdout)))


def norm(v):
    if isinstance(v, Decimal): return f'{v:.2f}'
    return str(v)


def compare(table, key, spec, raw_recs, order):
    expected = [cut(r, spec)[0] for r in raw_recs]
    actual = pg(f'select * from {table} order by {order}')
    exp_by = {norm(e[key]): e for e in expected}
    act_by = {a[key]: a for a in actual}
    mism = []
    if set(exp_by) != set(act_by):
        mism.append(f'key sets differ: raw-only={sorted(set(exp_by)-set(act_by))} pg-only={sorted(set(act_by)-set(exp_by))}')
    for k in sorted(exp_by):
        if k not in act_by: continue
        for col, ev in exp_by[k].items():
            av = act_by[k].get(col)
            if av is None:
                mism.append(f'{table}[{k}].{col}: column missing in PostgreSQL'); continue
            if norm(ev) != av: mism.append(f'{table}[{k}].{col}: raw={norm(ev)!r} pg={av!r}')
    print(f'\n## {table}: raw records={len(expected)} pg rows={len(actual)} fields compared={len(expected)*len(spec_cols(spec))} mismatches={len(mism)}')
    for m in mism: print('  MISMATCH', m)
    print(f'### spot-check (first 5 by {order}), raw -> pg')
    for a in actual[:5]:
        e = exp_by[a[key]]
        print(f'- {key}={a[key]}')
        for col, ev in e.items():
            print(f'    {col:26} raw={norm(ev)!r:40} pg={a[col]!r:40} {"OK" if norm(ev)==a[col] else "DIFF"}')
    return len(expected), len(actual), len(mism)


def spec_cols(spec): return [n for n, _, _ in spec if n != 'FILLER']


def main():
    print('# Raw extract vs PostgreSQL (independent, copybook-derived)')
    acct, aw = ascii_records('acctdata.txt', 300); print(f'acctdata.txt: {len(acct)} lines, line widths {aw} (CVACT01Y RECLN 300)')
    cust, cw = ascii_records('custdata.txt', 500); print(f'custdata.txt: {len(cust)} lines, line widths {cw} (CVCUS01Y RECLN 500)')
    xref, xw = ascii_records('cardxref.txt', 50);  print(f'cardxref.txt: {len(xref)} lines, line widths {xw} (CVACT03Y RECLN 50; ASCII extract drops the 14-byte FILLER -> 36, padded here)')
    xref_e, xb = ebcdic_records('AWS.M2.CARDDEMO.CARDXREF.PS', 50); print(f'EBCDIC CARDXREF.PS: {xb} bytes = {xb//50} x 50')
    users, ub = ebcdic_records('AWS.M2.CARDDEMO.USRSEC.PS', 80); print(f'EBCDIC USRSEC.PS: {ub} bytes = {ub//80} x 80 (cp037 decode)')
    # ASCII xref vs EBCDIC xref agreement (first 36 bytes)
    diff = sum(1 for a, e in zip(xref, xref_e) if a[:36] != e[:36])
    print(f'ASCII cardxref vs EBCDIC CARDXREF (36 significant bytes): {diff} differing records')
    # overpunch census
    signs = {}
    for r in acct:
        for off in (12, 24, 36, 78, 90):
            signs[r[off + 11]] = signs.get(r[off + 11], 0) + 1
    print(f'overpunch sign census over 250 money fields: {signs}  (only "{{"=+0 present -> negatives UNTESTED on this fixture)')
    # multi-card accounts in raw
    from collections import Counter
    c = Counter(r[25:36] for r in xref)
    print(f'raw card_xrefs: distinct accounts={len(c)} multi-card accounts={[k for k, v in c.items() if v > 1]}')
    totals = []
    totals.append(compare('accounts', 'acct_id', ACCT, acct, 'acct_id'))
    totals.append(compare('customers', 'cust_id', CUST, cust, 'cust_id'))
    totals.append(compare('card_xrefs', 'xref_card_number', XREF, xref, 'xref_card_number'))
    totals.append(compare('users', 'sec_usr_id', USER, users, 'sec_usr_id'))
    hashes = pg("select count(*) filter (where sec_usr_pwd_hash is not null) h, count(*) filter (where sec_usr_pwd_legacy is not null) l from users")[0]
    print(f"\nD-0029 post-import: rows with sec_usr_pwd_hash={hashes['h']}, rows with sec_usr_pwd_legacy={hashes['l']} (expected 0 / 10: hash filled only by upgrade-on-login)")
    tm = sum(t[2] for t in totals)
    print(f'\nTOTAL mismatches: {tm}')
    sys.exit(1 if tm else 0)


if __name__ == '__main__':
    main()
