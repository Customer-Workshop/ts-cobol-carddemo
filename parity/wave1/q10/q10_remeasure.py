#!/usr/bin/env python3
"""Q-10 gate re-measure (D-0032): every date column in the raw ASCII extracts must be a real YYYY-MM-DD.
Offsets from the copybooks: CVACT01Y ACCT-OPEN-DATE @48, ACCT-EXPIRAION-DATE @58, ACCT-REISSUE-DATE @68 (10 bytes each);
CVCUS01Y CUST-DOB-YYYY-MM-DD @ 9+75+150+2+3+10+30+9+20 = 308. 50x3 + 50x1 = 200 values."""
import pathlib, re, datetime, collections
ROOT = pathlib.Path(__file__).resolve().parents[3]
pat = re.compile(r'^\d{4}-\d{2}-\d{2}$')
checked = 0; offenders = []; years = collections.Counter()
def check(src, key, v):
    global checked
    checked += 1
    ok = bool(pat.match(v))
    if ok:
        try: datetime.date.fromisoformat(v)
        except ValueError: ok = False
    if not ok: offenders.append((src, key, repr(v)))
    else: years[v[:4]] += 1
for line in (ROOT/'app/data/ASCII/acctdata.txt').read_text().splitlines():
    for name, off in (('ACCT-OPEN-DATE', 48), ('ACCT-EXPIRAION-DATE', 58), ('ACCT-REISSUE-DATE', 68)):
        check('acctdata:'+name, line[:11], line[off:off+10])
for line in (ROOT/'app/data/ASCII/custdata.txt').read_text().splitlines():
    check('custdata:CUST-DOB-YYYY-MM-DD', line[:9], line[308:318])
print(f'Q-10 re-measure: {checked} values checked, {len(offenders)} offending (pattern YYYY-MM-DD and calendar-valid)')
for o in offenders: print('  OFFENDER', o)
print('year distribution:', dict(sorted(years.items())))
