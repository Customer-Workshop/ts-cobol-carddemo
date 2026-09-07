#!/usr/bin/env python3
"""COACTVWC parity: derive the expected CACTVWA screen values for fixture accounts from the RAW
app/data/ASCII bytes and the COBOL MOVE/edit logic only (never from backend/ or frontend/).

Layouts: CVACT01Y.cpy (ACCOUNT-RECORD 300), CVCUS01Y.cpy (CUSTOMER-RECORD 500), CVACT03Y.cpy
(CARD-XREF-RECORD, 36 data bytes per line in cardxref.txt). Money PICOUT '+ZZZ,ZZZ,ZZZ.99'
(COACTVW.bms:120,141,162,174,195; COACTVW.CPY:302..344). SSN edit COACTVWC.cbl:496-504.
Zoned-decimal sign overpunch as rendered in the ASCII extract: '{'=+0, 'A'..'I'=+1..9,
'}'=-0, 'J'..'R'=-1..9 (07_runbook.md: -fsign=EBCDIC).

Usage: derive_expected.py <acct_id> [<acct_id> ...]  -> JSON on stdout (raw fields + expected screen).
"""
import json, sys, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..')
DATA = os.path.join(ROOT, 'app', 'data', 'ASCII')

POS = {chr(ord('A') + i): i + 1 for i in range(9)}; POS['{'] = 0
NEG = {chr(ord('J') + i): i + 1 for i in range(9)}; NEG['}'] = 0


def zoned(s):
    """PIC S9(10)V99 zoned decimal -> (sign, 12 digit string). Returns ('+'|'-', digits)."""
    last = s[-1]
    if last in POS:
        return '+', s[:-1] + str(POS[last])
    if last in NEG:
        return '-', s[:-1] + str(NEG[last])
    if last.isdigit():
        return '+', s
    raise ValueError('bad overpunch %r in %r' % (last, s))


def edit_money(s):
    """MOVE PIC S9(10)V99 TO PIC +ZZZ,ZZZ,ZZZ.99 (15 chars). High-order digit of the 10-digit
    integer part is truncated (only 9 Z positions). Zero suppression replaces leading zeros AND the
    commas inside the suppressed run with spaces; '.99' always shown; fixed leading sign '+'/'-'."""
    sign, d = zoned(s)
    ints, dec = d[:10][1:], d[10:]          # drop high-order digit (PICOUT has 9 integer positions)
    grouped = ints[0:3] + ',' + ints[3:6] + ',' + ints[6:9]
    out = list(grouped)
    for i, ch in enumerate(grouped):
        if ch.isdigit() and ch != '0':
            break
        out[i] = ' '
    else:
        out = [' '] * 11
    if ints.strip('0') == '' and sign == '-':
        sign = '-'                         # negative zero keeps the '-' in IBM editing (informational; fixture has none)
    return sign + ''.join(out) + '.' + dec


def decimal_value(s):
    sign, d = zoned(s)
    v = d[:10].lstrip('0') or '0'
    return ('-' if sign == '-' else '') + v + '.' + d[10:]


def load(name, reclen):
    with open(os.path.join(DATA, name)) as f:
        recs = [l for l in f.read().split('\n') if l]
    for r in recs:
        assert len(r) == reclen, (name, len(r))
    return recs


def account(rec):
    return {
        'ACCT-ID': rec[0:11], 'ACCT-ACTIVE-STATUS': rec[11:12],
        'ACCT-CURR-BAL': rec[12:24], 'ACCT-CREDIT-LIMIT': rec[24:36], 'ACCT-CASH-CREDIT-LIMIT': rec[36:48],
        'ACCT-OPEN-DATE': rec[48:58], 'ACCT-EXPIRAION-DATE': rec[58:68], 'ACCT-REISSUE-DATE': rec[68:78],
        'ACCT-CURR-CYC-CREDIT': rec[78:90], 'ACCT-CURR-CYC-DEBIT': rec[90:102],
        'ACCT-ADDR-ZIP': rec[102:112], 'ACCT-GROUP-ID': rec[112:122],
    }


def customer(rec):
    p = 0
    out = {}
    for name, ln in [('CUST-ID', 9), ('CUST-FIRST-NAME', 25), ('CUST-MIDDLE-NAME', 25), ('CUST-LAST-NAME', 25),
                     ('CUST-ADDR-LINE-1', 50), ('CUST-ADDR-LINE-2', 50), ('CUST-ADDR-LINE-3', 50),
                     ('CUST-ADDR-STATE-CD', 2), ('CUST-ADDR-COUNTRY-CD', 3), ('CUST-ADDR-ZIP', 10),
                     ('CUST-PHONE-NUM-1', 15), ('CUST-PHONE-NUM-2', 15), ('CUST-SSN', 9),
                     ('CUST-GOVT-ISSUED-ID', 20), ('CUST-DOB-YYYY-MM-DD', 10), ('CUST-EFT-ACCOUNT-ID', 10),
                     ('CUST-PRI-CARD-HOLDER-IND', 1), ('CUST-FICO-CREDIT-SCORE', 3)]:
        out[name] = rec[p:p + ln]; p += ln
    return out


def xrefs(recs):
    return [{'XREF-CARD-NUM': r[0:16], 'XREF-CUST-ID': r[16:25], 'XREF-ACCT-ID': r[25:36]} for r in recs]


def expected_for(acct_id, accts, custs, xr):
    rows = sorted([x for x in xr if x['XREF-ACCT-ID'] == acct_id], key=lambda x: x['XREF-CARD-NUM'])
    res = {'acct_id': acct_id, 'xref_rows': rows}
    if not rows:
        res['outcome'] = 'E-09'; return res
    x = rows[0]                                   # Q-04 lowest card number wins (legacy: first on AIX path)
    a = next((account(r) for r in accts if r[0:11] == acct_id), None)
    if a is None:
        res['outcome'] = 'E-10'; return res
    c = next((customer(r) for r in custs if r[0:9] == x['XREF-CUST-ID']), None)
    res['raw_account'] = a
    screen = {
        'ACCTSID': acct_id,
        'ACSTTUS': a['ACCT-ACTIVE-STATUS'],
        'ACURBAL': edit_money(a['ACCT-CURR-BAL']),
        'ACRDLIM': edit_money(a['ACCT-CREDIT-LIMIT']),
        'ACSHLIM': edit_money(a['ACCT-CASH-CREDIT-LIMIT']),
        'ACRCYCR': edit_money(a['ACCT-CURR-CYC-CREDIT']),
        'ACRCYDB': edit_money(a['ACCT-CURR-CYC-DEBIT']),
        'ADTOPEN': a['ACCT-OPEN-DATE'], 'AEXPDT': a['ACCT-EXPIRAION-DATE'], 'AREISDT': a['ACCT-REISSUE-DATE'],
        'AADDGRP_legacy': a['ACCT-GROUP-ID'],            # cbl:490 MOVE ACCT-GROUP-ID (bytes 113-122)
        'AADDGRP_DV06': a['ACCT-ADDR-ZIP'].strip() or a['ACCT-GROUP-ID'],  # D-0038 target rule (approved deviation)
    }
    res['decimal_account'] = {k: decimal_value(a[k]) for k in
                              ('ACCT-CURR-BAL', 'ACCT-CREDIT-LIMIT', 'ACCT-CASH-CREDIT-LIMIT',
                               'ACCT-CURR-CYC-CREDIT', 'ACCT-CURR-CYC-DEBIT')}
    if c is None:
        res['outcome'] = 'E-11'; res['screen'] = screen; res['cust_id'] = x['XREF-CUST-ID']; return res
    res['raw_customer'] = c
    ssn = c['CUST-SSN']
    screen.update({
        'ACSTNUM': c['CUST-ID'],
        'ACSTSSN': ssn[0:3] + '-' + ssn[3:5] + '-' + ssn[5:9],
        'ACSTFCO': c['CUST-FICO-CREDIT-SCORE'],
        'ACSTDOB': c['CUST-DOB-YYYY-MM-DD'],
        'ACSFNAM': c['CUST-FIRST-NAME'], 'ACSMNAM': c['CUST-MIDDLE-NAME'], 'ACSLNAM': c['CUST-LAST-NAME'],
        'ACSADL1': c['CUST-ADDR-LINE-1'], 'ACSADL2': c['CUST-ADDR-LINE-2'], 'ACSCITY': c['CUST-ADDR-LINE-3'],
        'ACSSTTE': c['CUST-ADDR-STATE-CD'],
        'ACSZIPC_legacy5': c['CUST-ADDR-ZIP'][0:5], 'ACSZIPC_DV05_full': c['CUST-ADDR-ZIP'],
        'ACSCTRY': c['CUST-ADDR-COUNTRY-CD'],
        'ACSPHN1_legacy13': c['CUST-PHONE-NUM-1'][0:13], 'ACSPHN1_DV05_full': c['CUST-PHONE-NUM-1'],
        'ACSPHN2_legacy13': c['CUST-PHONE-NUM-2'][0:13], 'ACSPHN2_DV05_full': c['CUST-PHONE-NUM-2'],
        'ACSGOVT': c['CUST-GOVT-ISSUED-ID'], 'ACSEFTC': c['CUST-EFT-ACCOUNT-ID'],
        'ACSPFLG': c['CUST-PRI-CARD-HOLDER-IND'],
    })
    res['outcome'] = 'E-00 (both blocks)'; res['screen'] = screen
    return res


def main(ids):
    accts = load('acctdata.txt', 300); custs = load('custdata.txt', 500); xr = xrefs(load('cardxref.txt', 36))
    out = [expected_for(i, accts, custs, xr) for i in ids]
    json.dump(out, sys.stdout, indent=1)
    print()


if __name__ == '__main__':
    main(sys.argv[1:] or ['00000000001'])
