#!/usr/bin/env python3
"""Field-level parity: expected_fixture.json (raw bytes + COBOL edits) vs captured GET /api/accounts/{id} bodies.
Normalization (applied to BOTH sides, one rule): strings rstrip'd; money = remove blanks and commas, force a leading
sign ('+' if absent), insert '0' before a leading '.'; integers compared as int; dates as YYYY-MM-DD text.
Usage: compare_fields.py expected_fixture.json <http-dir> <acct> [<acct>...]"""
import json, sys

def money(s):
    s = str(s).replace(' ', '').replace(',', '')
    if not s or s[0] not in '+-':
        s = '+' + s
    if s[1] == '.':
        s = s[0] + '0' + s[1:]
    return s

# (screen field, JSON path, normaliser, note)
FIELDS = [
    ('ACCTSID', 'accountNumber', str.rstrip, 'cbl:465'),
    ('ACSTTUS', 'account.activeStatus', str.rstrip, 'cbl:473'),
    ('ACURBAL', 'account.currentBalance', money, 'cbl:475 PICOUT +ZZZ,ZZZ,ZZZ.99'),
    ('ACRDLIM', 'account.creditLimit', money, 'cbl:477'),
    ('ACSHLIM', 'account.cashCreditLimit', money, 'cbl:479'),
    ('ACRCYCR', 'account.currentCycleCredit', money, 'cbl:481'),
    ('ACRCYDB', 'account.currentCycleDebit', money, 'cbl:483'),
    ('ADTOPEN', 'account.openDate', str.rstrip, 'cbl:486'),
    ('AEXPDT', 'account.expirationDate', str.rstrip, 'cbl:488'),
    ('AREISDT', 'account.reissueDate', str.rstrip, 'cbl:489'),
    ('AADDGRP_DV06', 'account.groupId', str.rstrip, 'cbl:490 + D-0038 DV-06 (legacy blank)'),
    ('ACSTNUM', 'customer.customerId', str.rstrip, 'cbl:494'),
    ('ACSTSSN', 'customer.ssn', str.rstrip, 'cbl:496-504'),
    ('ACSTFCO', 'customer.ficoScore', lambda v: int(v), 'cbl:506'),
    ('ACSTDOB', 'customer.dateOfBirth', str.rstrip, 'cbl:508'),
    ('ACSFNAM', 'customer.firstName', str.rstrip, 'cbl:509'),
    ('ACSMNAM', 'customer.middleName', str.rstrip, 'cbl:510'),
    ('ACSLNAM', 'customer.lastName', str.rstrip, 'cbl:511'),
    ('ACSADL1', 'customer.addressLine1', str.rstrip, 'cbl:512'),
    ('ACSADL2', 'customer.addressLine2', str.rstrip, 'cbl:513'),
    ('ACSCITY', 'customer.city', str.rstrip, 'cbl:514'),
    ('ACSSTTE', 'customer.stateCode', str.rstrip, 'cbl:515'),
    ('ACSZIPC_DV05_full', 'customer.zipCode', str.rstrip, 'cbl:516 + DV-05 (legacy 5 of 10)'),
    ('ACSCTRY', 'customer.countryCode', str.rstrip, 'cbl:517'),
    ('ACSPHN1_DV05_full', 'customer.phone1', str.rstrip, 'cbl:518 + DV-05 (legacy 13 of 15)'),
    ('ACSPHN2_DV05_full', 'customer.phone2', str.rstrip, 'cbl:519 + DV-05'),
    ('ACSGOVT', 'customer.governmentIssuedId', str.rstrip, 'cbl:520'),
    ('ACSEFTC', 'customer.eftAccountId', str.rstrip, 'cbl:521'),
    ('ACSPFLG', 'customer.primaryCardHolder', str.rstrip, 'cbl:522'),
]

def dig(d, path):
    for k in path.split('.'):
        d = d.get(k) if isinstance(d, dict) else None
    return d

def main(expf, outdir, ids):
    exp = {e['acct_id']: e for e in json.load(open(expf))}
    total_pass = total = 0
    for a in ids:
        got = json.load(open(f'{outdir}/A-08-{a}.body'))
        screen = exp[a]['screen']
        p = 0
        print(f'--- account {a} (xref cust {exp[a]["xref_rows"][0]["XREF-CUST-ID"]}, expected outcome {exp[a]["outcome"]})')
        for fld, path, norm, note in FIELDS:
            e_raw = screen[fld]; g_raw = dig(got, path)
            try:
                e = norm(e_raw); g = norm(g_raw) if g_raw is not None else None
            except Exception as ex:
                e, g = e_raw, f'<{ex}>'
            ok = (e == g)
            p += ok
            flag = 'PASS' if ok else 'FAIL'
            print(f'{flag:4} {fld:18} expected={e_raw!r:>20} -> {e!r:>18} | observed={g_raw!r} -> {g!r}  ({note})')
        print(f'ACCOUNT-TOTAL {a}: {p}/{len(FIELDS)} fields match')
        total_pass += p; total += len(FIELDS)
    print(f'FIELD-PARITY-TOTAL {total_pass}/{total}')

if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2], sys.argv[3:])
