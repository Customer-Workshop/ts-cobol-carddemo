#!/usr/bin/env python3
"""
Render the CardDemo discovery artifacts (data.json) into:
  * Markdown reports  -> discovery-reports/reports/*.md
  * HTML dashboard     -> discovery-reports/index.html
"""
import json, os, html, datetime
from collections import defaultdict, OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = json.load(open(os.path.join(HERE, "data.json")))
REPORTS = os.path.join(HERE, "reports")
os.makedirs(REPORTS, exist_ok=True)

PROGRAMS = DATA["programs"]
COPYBOOKS = DATA["copybooks"]
JOBS = DATA["jobs"]
FILES = DATA["files"]
CSD = DATA["csd"]
MENU = DATA["menu_edges"]

SYSTEM_PREFIXES = ("DFH", "IGZ", "CEE", "DSN", "DFS", "MQ", "ICE", "IKJ", "IEB", "IEF", "IDC")
SYSTEM_NAMES = {"IDCAMS", "SORT", "FTP", "SDSF", "COBDATFT", "CBLTDLI", "MVSWAIT",
                "IEBGENER", "IEFBR14", "DFSRRC00"}


def is_system(n):
    return n in SYSTEM_NAMES or n.startswith(SYSTEM_PREFIXES)


# Verified PF (data store) -> record-layout copybook, grounded in copybook RECLN
PF_COPYBOOK = OrderedDict([
    ("AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS", ("CVACT01Y", "Account Master")),
    ("AWS.M2.CARDDEMO.CARDDATA.VSAM.KSDS", ("CVACT02Y", "Card Master")),
    ("AWS.M2.CARDDEMO.CARDXREF.VSAM.KSDS", ("CVACT03Y", "Card/Account/Customer Cross-Reference")),
    ("AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS", ("CUSTREC", "Customer Master")),
    ("AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS", ("CVTRA05Y", "Transaction Master")),
    ("AWS.M2.CARDDEMO.TCATBALF.VSAM.KSDS", ("CVTRA01Y", "Transaction Category Balance")),
    ("AWS.M2.CARDDEMO.DISCGRP.VSAM.KSDS", ("CVTRA02Y", "Disclosure Group")),
    ("AWS.M2.CARDDEMO.TRANTYPE.VSAM.KSDS", ("CVTRA03Y", "Transaction Type")),
    ("AWS.M2.CARDDEMO.TRANCATG.VSAM.KSDS", ("CVTRA04Y", "Transaction Category")),
    ("AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS", ("CSUSR01Y", "User Security")),
])

# Functional grouping rules (prefix -> business domain)
DOMAIN_RULES = [
    ("CBACT", "Account Management"), ("COACT", "Account Management"),
    ("CBTRN", "Transaction Processing"), ("COTRN", "Transaction Processing"),
    ("COCRD", "Card Management"),
    ("CBCUS", "Customer Management"),
    ("COUSR", "User / Security Admin"),
    ("COBIL", "Bill Payment"),
    ("CORPT", "Reporting & Statements"), ("CBSTM", "Reporting & Statements"),
    ("COPAU", "Authorization (optional)"), ("CBPAU", "Authorization (optional)"),
    ("PAUDB", "Authorization (optional)"), ("DBUNL", "Authorization (optional)"),
    ("COTRT", "Transaction Type (optional DB2)"), ("COBTU", "Transaction Type (optional DB2)"),
    ("COSGN", "Sign-on & Navigation"), ("COMEN", "Sign-on & Navigation"),
    ("COADM", "Sign-on & Navigation"),
    ("CODATE", "Date/MQ Services (optional)"), ("COACCT01", "Date/MQ Services (optional)"),
    ("CSUTL", "Common Utilities"), ("CSDAT", "Common Utilities"), ("COBSW", "Common Utilities"),
    ("CB", "Batch (other)"), ("CO", "Online (other)"), ("CS", "Common Utilities"),
]


def domain_of(pgm):
    for pre, dom in DOMAIN_RULES:
        if pgm.startswith(pre):
            return dom
    return "Other"


# README batch run book (execution order) - the operational sequence
BATCH_SEQUENCE = [
    ("CLOSEFIL", "Close VSAM files held by CICS", "IEFBR14"),
    ("ACCTFILE", "Load / refresh Account Master", "IDCAMS"),
    ("CARDFILE", "Load / refresh Card Master (+AIX)", "IDCAMS"),
    ("XREFFILE", "Load Card-Account-Customer cross-reference (+AIX)", "IDCAMS"),
    ("CUSTFILE", "Create / refresh Customer Master", "IDCAMS"),
    ("TRANBKP", "Create / back up Transaction Master", "IDCAMS"),
    ("TRANCATG", "Load Transaction Category file", "IDCAMS"),
    ("TRANTYPE", "Load Transaction Type file", "IDCAMS"),
    ("DISCGRP", "Load Disclosure Group file", "IDCAMS"),
    ("TCATBALF", "Load Transaction Category Balance file", "IDCAMS"),
    ("DUSRSECJ", "Set up User Security file", "IEBGENER"),
    ("POSTTRAN", "Post daily transactions (validate, update balances)", "CBTRN02C"),
    ("INTCALC", "Calculate interest and generate interest transactions", "CBACT04C"),
    ("COMBTRAN", "Combine system transactions with daily transactions", "SORT"),
    ("CREASTMT", "Produce customer transaction statements", "CBSTM03A"),
    ("TRANIDX", "Define alternate index (LF) on Transaction Master", "IDCAMS"),
    ("OPENFIL", "Re-open VSAM files for CICS", "IEFBR14"),
]

TS = DATA.get("generated", datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S") + "Z")


# ---------------------------------------------------------------------------
# Build derived structures
# ---------------------------------------------------------------------------
def build_edges():
    edges = []
    for pid, p in PROGRAMS.items():
        for t in p["calls"]:
            edges.append((pid, t, "COBOL CALL"))
        for t in p["cics_xctl"]:
            edges.append((pid, t, "CICS XCTL/LINK"))
    for e in MENU:
        edges.append((e["from"], e["to"], "CICS menu (XCTL)"))
    for jname, j in JOBS.items():
        for s in j["steps"]:
            if s.get("pgm") and not is_system(s["pgm"]):
                edges.append((jname + " (JCL)", s["pgm"], "JCL EXEC PGM"))
        for ip in j.get("indirect_pgms", []):
            edges.append((jname + " (JCL)", ip, "JCL indirect (IMS/DB2)"))
    return edges


def reachable_and_missing():
    src = set(PROGRAMS.keys())
    entry = set(CSD["programs"]) | set(CSD["transactions"].values())
    refs = set()
    for p in PROGRAMS.values():
        refs |= set(p["calls"]) | set(p["cics_xctl"])
    for e in MENU:
        refs.add(e["to"])
    for j in JOBS.values():
        for s in j["steps"]:
            if s.get("pgm"):
                entry.add(s["pgm"])
        entry |= set(j.get("indirect_pgms", []))
    refs |= entry
    missing = sorted(r for r in (refs - src) if not is_system(r))
    obsolete = sorted(src - refs)
    # copybook usage
    used_cpy = set()
    for p in PROGRAMS.values():
        used_cpy |= set(p["copybooks"])
    for cb in COPYBOOKS.values():
        import re
        txt = open(os.path.join(os.path.dirname(HERE), cb["path"])).read()
        used_cpy |= set(re.findall(r"\bCOPY\s+([A-Z0-9]+)", txt))
    unused_cpy = sorted(set(COPYBOOKS) - used_cpy)
    return refs, entry, missing, obsolete, unused_cpy


EDGES = build_edges()
REFS, ENTRY, MISSING, OBSOLETE, UNUSED_CPY = reachable_and_missing()


# ---------------------------------------------------------------------------
# Markdown helpers
# ---------------------------------------------------------------------------
def md_table(headers, rows):
    out = ["| " + " | ".join(headers) + " |",
           "| " + " | ".join(["---"] * len(headers)) + " |"]
    for r in rows:
        out.append("| " + " | ".join(str(c) for c in r) + " |")
    return "\n".join(out)


def write_md(name, title, body):
    with open(os.path.join(REPORTS, name), "w") as f:
        f.write("# %s\n\n_Application: %s_  \n_Generated: %s_\n\n%s\n" %
                (title, DATA["application"], TS, body))


# ===========================================================================
# REPORT 1 - Inventory with LOC
# ===========================================================================
def report_inventory():
    by_kind = defaultdict(lambda: {"n": 0, "total": 0, "code": 0, "comment": 0, "blank": 0})
    for f in FILES:
        k = by_kind[f["kind"]]
        k["n"] += 1
        for m in ("total", "code", "comment", "blank"):
            k[m] += f["loc"][m]
    rows = []
    tot = {"n": 0, "total": 0, "code": 0, "comment": 0, "blank": 0}
    for kind in sorted(by_kind, key=lambda k: -by_kind[k]["total"]):
        v = by_kind[kind]
        rows.append([kind, v["n"], v["total"], v["code"], v["comment"], v["blank"]])
        for m in tot:
            tot[m] += v[m]
    rows.append(["**TOTAL**", tot["n"], tot["total"], tot["code"], tot["comment"], tot["blank"]])
    prog_rows = []
    for pid in sorted(PROGRAMS):
        p = PROGRAMS[pid]
        prog_rows.append([pid, p["type"], p["module"], p["loc"]["total"],
                          p["loc"]["code"], p["loc"]["comment"],
                          (p.get("function") or "")[:60]])
    return by_kind, rows, tot, prog_rows


def md_inventory():
    _, rows, tot, prog_rows = report_inventory()
    body = "## Summary by artifact type\n\n"
    body += md_table(["Artifact Type", "Files", "Total LOC", "Code", "Comment", "Blank"], rows)
    body += "\n\n## COBOL program inventory (LOC)\n\n"
    body += md_table(["Program", "Type", "Module", "Total", "Code", "Comment", "Function"], prog_rows)
    write_md("01_inventory_loc.md", "1. Inventory Report with Lines of Code", body)
    return tot


# ===========================================================================
# REPORT 2 - Program-to-Program / Call Chain
# ===========================================================================
def md_callchain():
    rows = [[f, t, typ, "yes" if t not in PROGRAMS and not is_system(t) else ("system" if is_system(t) else "")]
            for (f, t, typ) in sorted(EDGES)]
    body = "Static call graph derived from COBOL `CALL`, CICS `XCTL`/`LINK`, the "
    body += "menu-navigation copybooks (`COMEN02Y`, `COADM02Y`), and JCL `EXEC PGM` "
    body += "(including indirect IMS `DFSRRC00` and DB2 `IKJEFT01 RUN PROGRAM`).\n\n"
    body += md_table(["Caller", "Callee", "Mechanism", "Unresolved"], rows)
    write_md("02_program_to_program.md", "2. Program-to-Program Dependencies / Call Chain", body)


# ===========================================================================
# REPORT 3 - Program-to-File / DB CRUD
# ===========================================================================
def crud_rows():
    rows = []
    for pid in sorted(PROGRAMS):
        p = PROGRAMS[pid]
        for fname, fv in p["files"].items():
            rows.append([pid, fv["ddname"], fv["organization"], "VSAM/SEQ", fv["crud"] or "-"])
        for fn, crud in p.get("cics_files", {}).items():
            rows.append([pid, fn, "CICS FCT", "VSAM (CICS)", crud or "-"])
        for tbl, crud in p.get("db2_tables", {}).items():
            rows.append([pid, tbl, "SQL", "DB2 table", crud or "-"])
    return rows


def md_crud():
    rows = crud_rows()
    body = "CRUD derived from COBOL file `SELECT/ASSIGN`, `OPEN` mode, `READ/WRITE/"
    body += "REWRITE/DELETE/START`, CICS file-control (`EXEC CICS READ/WRITE/...`), "
    body += "and embedded DB2 SQL (`SELECT/INSERT/UPDATE/DELETE`).  \n"
    body += "**Legend:** C=Create  R=Read  U=Update  D=Delete.\n\n"
    body += md_table(["Program", "File / DD / Table", "Access Path", "Store Type", "CRUD"], rows)
    write_md("03_program_to_file_db_crud.md", "3. Program-to-File / Database Dependencies (CRUD)", body)


# ===========================================================================
# REPORT 4 - PF -> LF mapping
# ===========================================================================
def pf_lf_pairs():
    clusters = {}
    aix = []
    paths = []
    for jname, j in JOBS.items():
        for v in j["vsam"]:
            if v["kind"] == "CLUSTER":
                clusters.setdefault(v["name"], jname)
            elif v["kind"] == "AIX":
                aix.append((v["name"], v["relate"], jname))
            elif v["kind"] == "PATH":
                paths.append((v["name"], v["pathentry"]))
    aix_to_path = {}
    for pn, pe in paths:
        aix_to_path[pe] = pn
    rows = []
    for aixname, base, jname in sorted(set(aix)):
        rows.append([base, "KSDS (base cluster)", aixname, aix_to_path.get(aixname, "-"), jname])
    return clusters, rows


def md_pflf():
    clusters, rows = pf_lf_pairs()
    body = "In z/OS VSAM, the **base cluster (KSDS)** is the physical file (PF); an "
    body += "**alternate index (AIX)** with its **PATH** is the logical file (LF) that "
    body += "provides an alternate access key. Mapping extracted from IDCAMS "
    body += "`DEFINE ALTERNATEINDEX ... RELATE(...)`, `DEFINE PATH`, and `BLDINDEX`.\n\n"
    body += "## Physical File (PF) -> Logical File (LF)\n\n"
    body += md_table(["Physical File (PF / base cluster)", "PF Type", "Logical File (LF / AIX)", "Access PATH", "Defined in"], rows)
    body += "\n\n## All physical data stores (VSAM clusters)\n\n"
    body += md_table(["Dataset (PF)", "Created by job"], [[c, clusters[c]] for c in sorted(clusters)])
    write_md("04_pf_to_lf_mapping.md", "4. File-to-File Mapping (PF to LF)", body)


# ===========================================================================
# REPORT 5 - PF -> Field mapping
# ===========================================================================
def field_layout(cbname):
    cb = COPYBOOKS.get(cbname)
    if not cb:
        return []
    rows = []
    offset = 1
    for fld in cb["fields"]:
        if fld["is88"]:
            rows.append((fld["level"], fld["name"], "88-cond", fld["pic"] or "", "", "", fld["redefines"]))
            continue
        if fld["level"] == 1:
            rows.append((fld["level"], fld["name"], "GROUP", "", "", "", ""))
            continue
        if fld["pic"] is None:  # group item
            rows.append((fld["level"], fld["name"], "GROUP", "", "", "", fld["redefines"] or ""))
            continue
        ln = fld["length"]
        start = offset
        usage = fld["usage"] or "DISPLAY"
        rows.append((fld["level"], fld["name"], usage, fld["pic"], start, ln, fld["redefines"] or ""))
        if not fld["redefines"]:
            offset += ln
    return rows


def md_pffield():
    body = "Record layouts (fields, PICTURE, USAGE, byte length and offset) for each "
    body += "physical file, parsed from the associated COBOL copybook. Offsets assume "
    body += "the primary (non-REDEFINES) path.\n\n"
    for dsn, (cbname, label) in PF_COPYBOOK.items():
        rows = field_layout(cbname)
        body += "\n## %s  \n_PF: `%s`  |  Copybook: `%s`_\n\n" % (label, dsn, cbname)
        body += md_table(["Lvl", "Field", "Usage", "PIC", "Start", "Len", "Redefines"],
                         [[r[0], r[1], r[2], r[3], r[4], r[5], r[6]] for r in rows])
        body += "\n"
    write_md("05_pf_to_field_mapping.md", "5. File (PF) to Field Mapping", body)


# ===========================================================================
# REPORT 6 - Missing source / obsolete
# ===========================================================================
def md_missing():
    body = "## Missing source (referenced but no source member)\n\n"
    mrows = []
    for m in MISSING:
        why = []
        if m in CSD["programs"]:
            why.append("CICS PROGRAM in CSD")
        trans = [t for t, pg in CSD["transactions"].items() if pg == m]
        if trans:
            why.append("CICS TRANSACTION " + ",".join(trans))
        mrows.append([m, ", ".join(why) or "referenced by call/JCL"])
    body += md_table(["Program", "Referenced as"], mrows) if mrows else "_None._"
    body += "\n\n## Obsolete / orphaned programs (source present, never invoked)\n\n"
    orows = [[o, PROGRAMS[o]["type"], PROGRAMS[o]["module"], PROGRAMS[o]["path"]] for o in OBSOLETE]
    body += md_table(["Program", "Type", "Module", "Path"], orows) if orows else "_None._"
    body += "\n\n## Unused copybooks (never COPY'd)\n\n"
    urows = [[u, COPYBOOKS[u]["path"]] for u in UNUSED_CPY]
    body += md_table(["Copybook", "Path"], urows) if urows else "_None._"
    write_md("06_missing_obsolete.md", "6. Missing Source / Obsolete Report", body)


# ===========================================================================
# REPORT 7 - Grouping & Sequencing
# ===========================================================================
def grouping():
    groups = defaultdict(list)
    for pid in sorted(PROGRAMS):
        groups[domain_of(pid)].append(pid)
    return groups


def md_grouping():
    groups = grouping()
    body = "## Functional grouping (by business domain)\n\n"
    grows = [[dom, len(groups[dom]), ", ".join(groups[dom])] for dom in sorted(groups)]
    body += md_table(["Business Domain", "# Programs", "Programs"], grows)
    body += "\n\n## Batch execution sequence (run book)\n\n"
    body += "Operational ordering of the nightly batch stream, from JCL scheduling "
    body += "and the application run book.\n\n"
    srows = [[i + 1, j, pgm, desc] for i, (j, desc, pgm) in enumerate(BATCH_SEQUENCE)]
    body += md_table(["#", "Job", "Program/Utility", "Purpose"], srows)
    body += "\n\n## Per-job step sequence (from JCL)\n\n"
    jrows = []
    for jname in sorted(JOBS):
        j = JOBS[jname]
        seq = " -> ".join("%s:%s" % (s["step"], s.get("pgm") or s.get("proc") or "?")
                          for s in j["steps"]) or "(no EXEC steps)"
        jrows.append([jname, len(j["steps"]), seq])
    body += md_table(["Job", "# Steps", "Step sequence"], jrows)
    write_md("07_grouping_sequencing.md", "7. Grouping & Sequencing", body)


# ===========================================================================
# HTML DASHBOARD
# ===========================================================================
def h(s):
    return html.escape(str(s))


def htable(headers, rows, classes=""):
    out = ['<table class="%s"><thead><tr>' % classes]
    out += ["<th>%s</th>" % h(x) for x in headers]
    out.append("</tr></thead><tbody>")
    for r in rows:
        out.append("<tr>" + "".join("<td>%s</td>" % (c if isinstance(c, str) and c.startswith("<") else h(c)) for c in r) + "</tr>")
    out.append("</tbody></table>")
    return "".join(out)


def crud_badge(crud):
    order = [("C", "c"), ("R", "r"), ("U", "u"), ("D", "d")]
    out = []
    for letter, cls in order:
        if letter in crud:
            out.append('<span class="crud %s">%s</span>' % (cls, letter))
        else:
            out.append('<span class="crud off">·</span>')
    return "".join(out)


def html_dashboard():
    by_kind, inv_rows, tot, prog_rows = report_inventory()

    # ---- summary cards ----
    n_prog = len(PROGRAMS)
    n_online = sum(1 for p in PROGRAMS.values() if p["type"] == "CICS Online")
    n_batch = n_prog - n_online
    n_cpy = len(COPYBOOKS)
    n_jcl = sum(1 for f in FILES if f["kind"] in ("JCL", "PROC"))
    n_stores = len(PF_COPYBOOK)
    cards = [
        ("Source files", len(FILES), "across all artifact types"),
        ("Lines of code", "{:,}".format(tot["total"]), "%s code / %s comment" % ("{:,}".format(tot["code"]), "{:,}".format(tot["comment"]))),
        ("Programs", n_prog, "%d batch / %d CICS online" % (n_batch, n_online)),
        ("Copybooks", n_cpy, "record layouts & shared code"),
        ("JCL / PROC", n_jcl, "batch job control"),
        ("Data stores", n_stores, "VSAM + DB2 + IMS"),
        ("Missing source", len(MISSING), "referenced, no member"),
        ("Obsolete / unused", len(OBSOLETE) + len(UNUSED_CPY), "orphaned programs & copybooks"),
    ]
    card_html = "".join(
        '<div class="card"><div class="cval">%s</div><div class="ck">%s</div><div class="cs">%s</div></div>' % (h(v), h(k), h(s))
        for (k, v, s) in cards)

    sections = []

    # ---- Overview ----
    kind_rows = [[k, by_kind[k]["n"], "{:,}".format(by_kind[k]["total"]),
                  "{:,}".format(by_kind[k]["code"]), "{:,}".format(by_kind[k]["comment"])]
                 for k in sorted(by_kind, key=lambda k: -by_kind[k]["total"])]
    kind_rows.append(["<b>TOTAL</b>", "<b>%d</b>" % tot["n"], "<b>%s</b>" % "{:,}".format(tot["total"]),
                      "<b>%s</b>" % "{:,}".format(tot["code"]), "<b>%s</b>" % "{:,}".format(tot["comment"])])
    sections.append(("overview", "Overview", """
      <p>Automated reverse-engineering discovery baseline for the <b>%s</b> mainframe
      application, generated directly from source (COBOL, Copybooks, JCL/PROC, BMS,
      CICS CSD, DB2 DDL/DCLGEN, IMS DBD/PSB, VSAM IDCAMS). The seven discovery
      artifacts below establish the application inventory required before a
      Business Rule Extraction (BRE) and modernization effort.</p>
      <div class="cards">%s</div>
      <h3>Composition by artifact type</h3>%s
    """ % (h(DATA["application"]), card_html, htable(
        ["Artifact type", "Files", "Total LOC", "Code", "Comment"], kind_rows, "grid"))))

    # ---- 1 Inventory ----
    ir = [[r[0], r[1], r[2], "{:,}".format(r[3]), "{:,}".format(r[4]), r[5], h(r[6])] for r in prog_rows]
    sections.append(("r1", "1 · Inventory + LOC", """
      <p class="lead">Every source member classified by type with line-of-code metrics
      (fixed-format aware: COBOL indicator column, JCL comments). Establishes the size
      and shape of the estate.</p>
      <h3>Program inventory (%d programs)</h3>%s
    """ % (len(prog_rows), htable(
        ["Program", "Type", "Module", "Total", "Code", "Cmt", "Function"], ir, "grid"))))

    # ---- 2 Program-to-program ----
    e_rows = []
    for (f, t, typ) in sorted(EDGES):
        flag = ""
        if t not in PROGRAMS and not is_system(t):
            flag = '<span class="tag warn">missing</span>'
        elif is_system(t):
            flag = '<span class="tag sys">system</span>'
        e_rows.append([f, '<span class="arrow">→</span> ' + h(t), typ, flag])
    sections.append(("r2", "2 · Call Chain", """
      <p class="lead">Program-to-program dependencies from COBOL <code>CALL</code>,
      CICS <code>XCTL/LINK</code>, the menu-navigation copybooks, and JCL
      <code>EXEC PGM</code> (including indirect IMS <code>DFSRRC00</code> and DB2
      <code>IKJEFT01 RUN PROGRAM</code>). %d edges.</p>%s
    """ % (len(e_rows), htable(["Caller", "Callee", "Mechanism", ""],
                               [[a, b, c, d] for (a, b, c, d) in e_rows], "grid"))))

    # ---- 3 CRUD ----
    cr = [[r[0], r[1], r[2], r[3], crud_badge(r[4])] for r in crud_rows()]
    sections.append(("r3", "3 · File/DB CRUD", """
      <p class="lead">Program-to-file and program-to-database access with resolved
      CRUD intent, from COBOL file I/O verbs, CICS file control, and embedded DB2 SQL.
      <span class="crud c">C</span> create <span class="crud r">R</span> read
      <span class="crud u">U</span> update <span class="crud d">D</span> delete.</p>%s
    """ % htable(["Program", "File / DD / Table", "Access path", "Store", "C R U D"], cr, "grid")))

    # ---- 4 PF -> LF ----
    clusters, lf_rows = pf_lf_pairs()
    lr = [[r[0], r[1], '<span class="arrow">→</span> ' + h(r[2]), r[3], r[4]] for r in lf_rows]
    sections.append(("r4", "4 · PF → LF", """
      <p class="lead">Physical-to-logical file mapping. The VSAM <b>base cluster (KSDS)</b>
      is the physical file (PF); each <b>alternate index (AIX)</b> + <b>PATH</b> is a
      logical file (LF) giving an alternate access key. Extracted from IDCAMS
      <code>DEFINE ALTERNATEINDEX … RELATE()</code>.</p>%s
      <h3>All physical data stores</h3>%s
    """ % (htable(["Physical File (PF)", "PF type", "Logical File (LF / AIX)", "Access PATH", "Defined in"], lr, "grid"),
           htable(["Dataset (PF)", "Created by job"], [[c, clusters[c]] for c in sorted(clusters)], "grid"))))

    # ---- 5 PF -> Field ----
    blocks = []
    for dsn, (cbname, label) in PF_COPYBOOK.items():
        rows = field_layout(cbname)
        fr = []
        for (lvl, name, usage, pic, start, ln, red) in rows:
            cls = "grp" if usage in ("GROUP", "88-cond") else ""
            fr.append(['<span class="lvl">%02d</span>' % lvl if isinstance(lvl, int) else lvl,
                       '<span class="%s">%s</span>' % (cls, h(name)), usage, pic or "",
                       start or "", ln or "", red or ""])
        blocks.append('<h3>%s <span class="sub">%s · <code>%s</code></span></h3>%s' % (
            h(label), h(dsn), h(cbname),
            htable(["Lvl", "Field", "Usage", "PIC", "Start", "Len", "Redefines"], fr, "grid tight")))
    sections.append(("r5", "5 · PF → Field", """
      <p class="lead">Record-layout (field) mapping for every physical file: name,
      PICTURE, USAGE (COMP-3 packed / COMP binary / DISPLAY), byte length and offset,
      parsed from the associated copybook.</p>%s
    """ % "".join(blocks)))

    # ---- 6 Missing / obsolete ----
    m_rows = []
    for m in MISSING:
        why = []
        if m in CSD["programs"]:
            why.append("CICS PROGRAM in CSD")
        trans = [t for t, pg in CSD["transactions"].items() if pg == m]
        if trans:
            why.append("CICS TRANSACTION " + ",".join(trans))
        m_rows.append([m, ", ".join(why) or "referenced by call/JCL"])
    o_rows = [[o, PROGRAMS[o]["type"], PROGRAMS[o]["module"], PROGRAMS[o]["path"]] for o in OBSOLETE]
    u_rows = [[u, COPYBOOKS[u]["path"]] for u in UNUSED_CPY]
    sections.append(("r6", "6 · Missing / Obsolete", """
      <p class="lead">Gaps and dead code. <b>Missing source</b>: registered/called but no
      member exists (blocks compile & migration). <b>Obsolete</b>: source present but never
      invoked — candidates to retire.</p>
      <h3>Missing source <span class="tag warn">%d</span></h3>%s
      <h3>Obsolete / orphaned programs <span class="tag">%d</span></h3>%s
      <h3>Unused copybooks <span class="tag">%d</span></h3>%s
    """ % (len(m_rows), htable(["Program", "Referenced as"], m_rows or [["—", "none"]], "grid"),
           len(o_rows), htable(["Program", "Type", "Module", "Path"], o_rows or [["—", "", "", "none"]], "grid"),
           len(u_rows), htable(["Copybook", "Path"], u_rows or [["—", "none"]], "grid"))))

    # ---- 7 Grouping & sequencing ----
    groups = grouping()
    g_rows = [[dom, len(groups[dom]),
               " ".join('<span class="chip">%s</span>' % h(p) for p in groups[dom])]
              for dom in sorted(groups)]
    s_rows = [[i + 1, j, '<span class="chip">%s</span>' % h(pgm), desc]
              for i, (j, desc, pgm) in enumerate(BATCH_SEQUENCE)]
    sections.append(("r7", "7 · Grouping & Sequencing", """
      <p class="lead">Programs grouped by business domain, and the operational batch
      execution stream (run book) that sequences the nightly cycle.</p>
      <h3>Functional grouping</h3>%s
      <h3>Batch execution sequence</h3>%s
    """ % (htable(["Business domain", "#", "Programs"], g_rows, "grid"),
           htable(["#", "Job", "Program / Utility", "Purpose"], s_rows, "grid"))))

    nav = "".join('<a class="navitem%s" data-t="%s">%s</a>' %
                  (" active" if i == 0 else "", sid, h(title))
                  for i, (sid, title, _) in enumerate(sections))
    secs = "".join('<section id="%s" class="sec%s">%s</section>' %
                   (sid, " show" if i == 0 else "", body)
                   for i, (sid, title, body) in enumerate(sections))

    page = """<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>CardDemo · Reverse-Engineering Discovery Baseline</title>
<style>
:root{--bg:#0f172a;--panel:#fff;--ink:#0f172a;--mut:#64748b;--line:#e2e8f0;--accent:#2563eb;--accent2:#0891b2;}
*{box-sizing:border-box}
body{margin:0;font:14px/1.5 -apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:var(--ink);background:#f1f5f9}
header{background:linear-gradient(100deg,#0f172a,#1e3a8a);color:#fff;padding:18px 26px;position:sticky;top:0;z-index:20;box-shadow:0 2px 10px rgba(0,0,0,.15)}
header h1{margin:0;font-size:19px;letter-spacing:.2px}
header .sub{color:#93c5fd;font-size:12.5px;margin-top:3px}
header .badge{float:right;background:rgba(255,255,255,.12);border:1px solid rgba(255,255,255,.25);padding:6px 12px;border-radius:20px;font-size:12px}
.wrap{display:flex;min-height:calc(100vh - 62px)}
nav{width:212px;flex:0 0 212px;background:#0f172a;color:#cbd5e1;padding:14px 0;position:sticky;top:62px;height:calc(100vh - 62px);overflow:auto}
.navitem{display:block;padding:10px 20px;color:#cbd5e1;cursor:pointer;font-size:13px;border-left:3px solid transparent;text-decoration:none}
.navitem:hover{background:#1e293b;color:#fff}
.navitem.active{background:#1e293b;color:#fff;border-left-color:var(--accent2)}
main{flex:1;padding:26px 34px;overflow:auto}
.sec{display:none}.sec.show{display:block;animation:f .25s ease}
@keyframes f{from{opacity:0;transform:translateY(6px)}to{opacity:1}}
h2.title{margin:0 0 4px}
.lead{color:var(--mut);max-width:900px}
h3{margin:26px 0 10px;font-size:15px;border-bottom:2px solid var(--line);padding-bottom:6px}
h3 .sub{font-weight:400;color:var(--mut);font-size:12px}
.cards{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin:16px 0 6px}
.card{background:var(--panel);border:1px solid var(--line);border-radius:12px;padding:16px;box-shadow:0 1px 2px rgba(0,0,0,.04)}
.card .cval{font-size:26px;font-weight:700;color:var(--accent)}
.card .ck{font-weight:600;margin-top:2px}
.card .cs{color:var(--mut);font-size:12px}
table{border-collapse:collapse;width:100%;background:#fff;border:1px solid var(--line);border-radius:8px;overflow:hidden;font-size:13px}
table.tight{font-size:12px}
th{background:#f8fafc;text-align:left;padding:8px 10px;color:#334155;font-weight:600;border-bottom:2px solid var(--line);position:sticky;top:0}
td{padding:6px 10px;border-bottom:1px solid #f1f5f9;vertical-align:top}
tr:hover td{background:#f8fbff}
code{background:#eef2ff;color:#3730a3;padding:1px 5px;border-radius:4px;font-size:12px}
.crud{display:inline-block;width:17px;text-align:center;border-radius:4px;font-weight:700;font-size:11px;margin-right:2px;color:#fff}
.crud.c{background:#059669}.crud.r{background:#2563eb}.crud.u{background:#d97706}.crud.d{background:#dc2626}
.crud.off{background:#f1f5f9;color:#cbd5e1;font-weight:400}
.tag{background:#e2e8f0;color:#475569;border-radius:20px;padding:1px 9px;font-size:11px;font-weight:600}
.tag.warn{background:#fee2e2;color:#b91c1c}.tag.sys{background:#e0e7ff;color:#3730a3}
.arrow{color:var(--accent2);font-weight:700}
.chip{display:inline-block;background:#eff6ff;color:#1d4ed8;border:1px solid #dbeafe;border-radius:6px;padding:1px 7px;margin:1px;font-size:11.5px;font-family:ui-monospace,Menlo,monospace}
.lvl{color:#94a3b8;font-family:ui-monospace,monospace}
.grp{font-weight:700;color:#0f172a}
#capbar{position:fixed;left:0;right:0;bottom:0;z-index:60;background:rgba(15,23,42,.94);color:#fff;
  padding:14px 26px;display:none;align-items:center;gap:16px;box-shadow:0 -3px 14px rgba(0,0,0,.28);
  border-top:3px solid var(--accent2)}
#capbar.on{display:flex}
#capbar .who{background:var(--accent2);color:#fff;font-weight:700;border-radius:6px;padding:3px 10px;font-size:12px;white-space:nowrap}
#capbar .txt{font-size:17px;line-height:1.4}
#capbar .num{margin-left:auto;color:#93c5fd;font-size:12px;white-space:nowrap}
body.tour main{padding-bottom:110px}
</style></head><body>
<header><span class="badge">Generated __GEN__</span>
<h1>CardDemo — Reverse-Engineering Discovery Baseline</h1>
<div class="sub">Automated application inventory &amp; dependency analysis · generated from source by Devin</div></header>
<div class="wrap"><nav>__NAV__</nav><main>__SECS__</main></div>
<div id="capbar"><span class="who">Devin</span><span class="txt" id="captxt"></span><span class="num" id="capnum"></span></div>
<script>
function goto(sid){
  document.querySelectorAll('.navitem').forEach(x=>x.classList.toggle('active',x.dataset.t===sid));
  document.querySelectorAll('.sec').forEach(x=>x.classList.toggle('show',x.id===sid));
  window.scrollTo(0,0);
}
document.querySelectorAll('.navitem').forEach(function(a){
  a.addEventListener('click',function(){goto(a.dataset.t);});
});
var TOUR=__TOUR__;
var ti=-1;
function showCap(){
  var s=TOUR[ti];
  goto(s[0]);
  if(s[2]) window.scrollTo(0,s[2]);
  document.getElementById('captxt').textContent=s[1];
  document.getElementById('capnum').textContent=(ti+1)+' / '+TOUR.length;
  document.getElementById('capbar').classList.add('on');
  document.body.classList.add('tour');
}
document.addEventListener('keydown',function(e){
  if(e.key==='ArrowRight'||e.key===' '||e.key==='n'){ if(ti<TOUR.length-1){ti++;showCap();} e.preventDefault(); }
  else if(e.key==='ArrowLeft'||e.key==='p'){ if(ti>0){ti--;showCap();} e.preventDefault(); }
  else if(e.key==='Escape'){ document.getElementById('capbar').classList.remove('on'); document.body.classList.remove('tour'); ti=-1; }
});
</script></body></html>"""
    tour = [
        ["overview", "Discovery phase: Devin reverse-engineers the CardDemo mainframe application directly from source \u2014 COBOL, JCL, copybooks, CICS, DB2 and IMS.", 0],
        ["overview", "A single run produces the full application-inventory baseline: 233 source files and 57,100 lines of code across 13 artifact types.", 0],
        ["r1", "Artifact 1 \u2014 Inventory with Lines of Code. Every program classified as batch or CICS online, with fixed-format-aware LOC counts.", 0],
        ["r2", "Artifact 2 \u2014 Program-to-Program Call Chain. Resolved from COBOL CALL, CICS XCTL/LINK, menu copybooks and JCL EXEC PGM.", 0],
        ["r3", "Artifact 3 \u2014 Program-to-File / Database CRUD. Create / Read / Update / Delete intent per file, across VSAM, CICS and DB2 SQL.", 0],
        ["r4", "Artifact 4 \u2014 File-to-File Mapping (PF to LF). VSAM base cluster to alternate index and PATH, extracted from IDCAMS DEFINE.", 0],
        ["r5", "Artifact 5 \u2014 File to Field Mapping. Every record layout: PICTURE, USAGE, byte length and offset, parsed from copybooks.", 0],
        ["r6", "Artifact 6 \u2014 Missing Source / Obsolete. COCRDSEC is registered in CICS but has no source; CBTRN01C is orphaned code.", 0],
        ["r7", "Artifact 7 \u2014 Grouping & Sequencing. Programs grouped by business domain, plus the nightly batch execution run book.", 0],
        ["r7", "Plus Business Rule Extraction (BRE) documents. Every artifact is delivered as this interactive dashboard and as Markdown.", 0],
    ]
    page = (page.replace("__GEN__", h(TS[:19].replace("T", " ") + " UTC"))
                .replace("__NAV__", nav)
                .replace("__SECS__", secs)
                .replace("__TOUR__", json.dumps(tour)))

    with open(os.path.join(HERE, "index.html"), "w") as f:
        f.write(page)


if __name__ == "__main__":
    tot = md_inventory()
    md_callchain()
    md_crud()
    md_pflf()
    md_pffield()
    md_missing()
    md_grouping()
    html_dashboard()
    print("Markdown reports + index.html written")
    print("Totals:", tot)
    print("Missing:", MISSING, "Obsolete:", OBSOLETE, "Unused cpy:", UNUSED_CPY)
