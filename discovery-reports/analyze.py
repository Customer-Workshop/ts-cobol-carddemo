#!/usr/bin/env python3
"""
CardDemo Reverse-Engineering Discovery Analyzer
================================================
Parses the mainframe (COBOL / JCL / PROC / Copybook / BMS / DB2 / IMS / VSAM)
source of the CardDemo application and produces the discovery-phase artifacts
required to establish an application-inventory baseline:

  1. Inventory Report with Lines of Code
  2. Program-to-Program Dependencies / Call Chain
  3. Program-to-File / Database Dependencies (CRUD)
  4. File-to-File Mapping (PF -> LF)          (VSAM base cluster -> alternate index)
  5. File (PF) -> Field Mapping                (record layout / copybook fields)
  6. Missing Source / Obsolete Report
  7. Grouping & Sequencing

Output: discovery-reports/data.json (structured) + reports/*.md (human readable).
No external dependencies - standard library only.
"""
import os, re, json, math, datetime
from collections import defaultdict, OrderedDict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "app")
OUT = os.path.join(ROOT, "discovery-reports")
REPORTS = os.path.join(OUT, "reports")
os.makedirs(REPORTS, exist_ok=True)

# ----------------------------------------------------------------------------
# Artifact-type classification by extension
# ----------------------------------------------------------------------------
TYPE_BY_EXT = {
    ".cbl": "COBOL Program", ".cob": "COBOL Program",
    ".cpy": "Copybook",
    ".jcl": "JCL", ".prc": "PROC", ".proc": "PROC",
    ".bms": "BMS Map",
    ".dcl": "DB2 DCLGEN", ".ddl": "DB2 DDL",
    ".dbd": "IMS DBD", ".psb": "IMS PSB",
    ".asm": "Assembler", ".mac": "Assembler Macro", ".maclib": "Assembler Macro",
    ".csd": "CICS CSD", ".ctl": "Control Card",
}
# Prefixes of IBM/vendor system or runtime programs (never customer source)
SYSTEM_PREFIXES = ("DFH", "IGZ", "CEE", "DSN", "DFS", "MQ", "ICE", "IKJ", "IEB", "IEF", "IDC")


def is_system_program(name):
    if name in SYSTEM_PROGRAMS:
        return True
    if name in ("CBLTDLI", "MVSWAIT"):
        return True
    return name.startswith(SYSTEM_PREFIXES)
# Well-known IBM/vendor utilities & runtime modules that will never have source here
SYSTEM_PROGRAMS = {
    "IDCAMS", "IEBGENER", "IEFBR14", "SORT", "ICEMAN", "ICETOOL", "IKJEFT1B",
    "IKJEFT01", "DFHCSDUP", "DSNTEP2", "DSNTEP4", "DSNTIAUL", "SDSF", "FTP",
    "DFSRRC00", "DFHDRP", "DSNUTILB", "CEE3ABD", "CEEDAYS", "CEELOCT",
    "COBDATFT", "IGZSRTCD", "DFHEIP", "DFHECI",
}
CICS_SYSTEM = {"DFHAID", "DFHBMSCA", "DFHEIBLK", "DFHCOMMAREA"}


def ext(path):
    return os.path.splitext(path)[1].lower()


def read_text(path):
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        return f.read()


# ----------------------------------------------------------------------------
# Fixed-format COBOL / JCL aware LOC counting
# ----------------------------------------------------------------------------
def count_loc(path, kind):
    lines = read_text(path).splitlines()
    total = len(lines)
    blank = comment = code = 0
    for raw in lines:
        stripped = raw.strip()
        if stripped == "":
            blank += 1
            continue
        is_comment = False
        if kind in ("COBOL Program", "Copybook", "DB2 DCLGEN", "BMS Map"):
            # fixed-format: indicator area is column 7 (index 6)
            ind = raw[6] if len(raw) > 6 else " "
            if ind in "*/":
                is_comment = True
            elif stripped.startswith("*"):
                is_comment = True
        elif kind in ("JCL", "PROC"):
            if stripped.startswith("//*"):
                is_comment = True
        else:
            if stripped.startswith("*") or stripped.startswith("--") or stripped.startswith("#"):
                is_comment = True
        if is_comment:
            comment += 1
        else:
            code += 1
    return {"total": total, "code": code, "comment": comment, "blank": blank}


# ----------------------------------------------------------------------------
# Collect all source files
# ----------------------------------------------------------------------------
def collect_files():
    files = []
    for dirpath, dirs, names in os.walk(APP):
        if ".git" in dirpath:
            continue
        for n in names:
            p = os.path.join(dirpath, n)
            e = ext(p)
            kind = TYPE_BY_EXT.get(e)
            if kind is None:
                # data / feed files
                if e in (".txt", ".ps", ".dat", ".controlm", ".ca7", ".template"):
                    kind = "Data/Control"
                else:
                    continue
            rel = os.path.relpath(p, ROOT)
            loc = count_loc(p, kind)
            files.append({
                "path": rel,
                "name": n,
                "module": rel.split(os.sep)[1] if rel.startswith("app" + os.sep) and len(rel.split(os.sep)) > 2 and rel.split(os.sep)[1].startswith("app-") else "base",
                "kind": kind,
                "loc": loc,
            })
    return files


# ----------------------------------------------------------------------------
# COBOL program parsing
# ----------------------------------------------------------------------------
def cobol_code_area(text):
    """Return text limited to cols 8-72 (drop seq numbers + comments)."""
    out = []
    for raw in text.splitlines():
        if len(raw) > 6 and raw[6] in "*/":
            continue
        # columns 7..72 (index 6..71) is area A/B; drop the 6-char sequence area
        area = raw[6:72] if len(raw) > 6 else raw
        out.append(area)
    return "\n".join(out)


def parse_cobol(path):
    text = read_text(path)
    code = cobol_code_area(text)
    flat = re.sub(r"\s+", " ", code)

    prog = {}
    m = re.search(r"PROGRAM-ID\s*\.\s*([A-Z0-9][A-Z0-9-]*)", flat)
    prog["program_id"] = m.group(1) if m else os.path.splitext(os.path.basename(path))[0].upper()

    # header comment metadata
    header = {}
    for line in text.splitlines()[:25]:
        hm = re.search(r"\*\s*(Type|Function|Author|Application|Transaction)\s*:\s*(.+?)\s*\**\s*$", line, re.I)
        if hm:
            header[hm.group(1).lower()] = hm.group(2).strip()
    prog["type"] = "CICS Online" if "EXEC CICS" in code.upper() else "Batch"
    prog["function"] = header.get("function", "")
    prog["author"] = header.get("author", "")

    # copybooks used
    prog["copybooks"] = sorted(set(re.findall(r"\bCOPY\s+([A-Z0-9][A-Z0-9-]*)", flat)))

    # static program calls (literal only; drop COBOL data-names which contain '-')
    calls = set(re.findall(r"\bCALL\s+['\"]([A-Z0-9][A-Z0-9-]*)['\"]", flat))
    prog["calls"] = sorted(c for c in calls if "-" not in c)

    # CICS transfers. A quoted literal target is static; an unquoted operand is a
    # data-name (dynamic navigation, resolved via the menu copybooks).
    xctl = set()
    dynamic = False
    for m in re.finditer(r"\b(XCTL|LINK)\s+PROGRAM\s*\(\s*(['\"]?)([A-Z0-9][A-Z0-9-]*)", flat):
        quoted, tgt = m.group(2), m.group(3)
        if quoted and "-" not in tgt:
            xctl.add(tgt)
        else:
            dynamic = True
    prog["cics_xctl"] = sorted(xctl)
    prog["dynamic_xctl"] = dynamic

    # SELECT ... ASSIGN TO ddname  + ORGANIZATION
    files = OrderedDict()
    for m in re.finditer(r"SELECT\s+([A-Z0-9][A-Z0-9-]*)\s+ASSIGN\s+TO\s+([A-Z0-9][A-Z0-9-]*)(.*?)(?=SELECT\s|\bFD\s|\bDATA DIVISION|$)", flat):
        fname, dd, tail = m.group(1), m.group(2), m.group(3)
        org = "SEQUENTIAL"
        om = re.search(r"ORGANIZATION\s+IS\s+([A-Z]+)", tail)
        if om:
            org = om.group(1)
        files[fname] = {"ddname": dd, "organization": org, "crud": set(), "record": None}

    # FD file -> 01 record
    for m in re.finditer(r"\bFD\s+([A-Z0-9][A-Z0-9-]*).*?\b01\s+([A-Z0-9][A-Z0-9-]*)", flat):
        fdn, rec = m.group(1), m.group(2)
        if fdn in files:
            files[fdn]["record"] = rec
    rec_to_file = {v["record"]: k for k, v in files.items() if v["record"]}

    # CRUD - sequential/VSAM native COBOL verbs
    for m in re.finditer(r"\bOPEN\s+(INPUT|OUTPUT|I-O|EXTEND)\s+([A-Z0-9][A-Z0-9-]*)", flat):
        mode, fn = m.group(1), m.group(2)
        if fn in files:
            if mode == "INPUT":
                files[fn]["crud"].add("R")
            elif mode in ("OUTPUT", "EXTEND"):
                files[fn]["crud"].add("C")
            elif mode == "I-O":
                files[fn]["crud"].update({"R", "U"})
    for verb, letter in (("READ", "R"), ("START", "R"), ("DELETE", "D")):
        for m in re.finditer(r"\b%s\s+([A-Z0-9][A-Z0-9-]*)" % verb, flat):
            fn = m.group(1)
            if fn in files:
                files[fn]["crud"].add(letter)
    for verb, letter in (("WRITE", "C"), ("REWRITE", "U")):
        for m in re.finditer(r"\b%s\s+([A-Z0-9][A-Z0-9-]*)" % verb, flat):
            rec = m.group(1)
            fn = rec_to_file.get(rec)
            if fn in files:
                files[fn]["crud"].add(letter)

    # CICS file control
    cics_files = defaultdict(set)
    for m in re.finditer(r"EXEC\s+CICS\s+(READ|WRITE|REWRITE|DELETE|STARTBR|READNEXT|READPREV)\b(.*?)END-EXEC", flat):
        verb, body = m.group(1), m.group(2)
        fm = re.search(r"(?:FILE|DATASET)\s*\(\s*['\"]?([A-Z0-9][A-Z0-9-]*)", body)
        if not fm:
            continue
        fn = fm.group(1)
        letter = {"READ": "R", "READNEXT": "R", "READPREV": "R", "STARTBR": "R",
                  "WRITE": "C", "REWRITE": "U", "DELETE": "D"}[verb]
        cics_files[fn].add(letter)

    # DB2 SQL
    db2 = defaultdict(set)
    for m in re.finditer(r"EXEC\s+SQL\b(.*?)END-EXEC", flat, re.S):
        body = m.group(1)
        for tm in re.finditer(r"\bSELECT\b.*?\bFROM\s+([A-Z0-9_.]+)", body, re.S):
            db2[tm.group(1)].add("R")
        for tm in re.finditer(r"\bINSERT\s+INTO\s+([A-Z0-9_.]+)", body):
            db2[tm.group(1)].add("C")
        for tm in re.finditer(r"\bUPDATE\s+([A-Z0-9_.]+)\s+SET", body):
            db2[tm.group(1)].add("U")
        for tm in re.finditer(r"\bDELETE\s+FROM\s+([A-Z0-9_.]+)", body):
            db2[tm.group(1)].add("D")

    prog["files"] = {k: {"ddname": v["ddname"], "organization": v["organization"],
                         "record": v["record"], "crud": "".join(sorted(v["crud"]))}
                     for k, v in files.items()}
    prog["cics_files"] = {k: "".join(sorted(v)) for k, v in cics_files.items()}
    prog["db2_tables"] = {k: "".join(sorted(v)) for k, v in db2.items()}
    prog["path"] = os.path.relpath(path, ROOT)
    return prog


# ----------------------------------------------------------------------------
# Menu copybook parsing (CICS dynamic navigation targets)
# ----------------------------------------------------------------------------
def parse_menu(path):
    """Extract (option-name, target-program) pairs from a menu copybook."""
    text = cobol_code_area(read_text(path))
    # 8-char program names appear as PIC X(08) VALUE 'XXXXXXXX'
    progs = re.findall(r"PIC\s+X\(0?8\)\s+VALUE\s+'([A-Z0-9]{6,8})'", text)
    names = re.findall(r"PIC\s+X\(35\)\s+VALUE\s*\n?\s*'([^']+)'", read_text(path))
    return progs, names


# ----------------------------------------------------------------------------
# JCL parsing
# ----------------------------------------------------------------------------
def parse_jcl(path):
    text = read_text(path)
    # Build logical JCL statements, honouring true continuation: a JCL statement
    # continues when it ends with ',' and the next line is '//' + blank label.
    logical = []
    buf = ""
    for raw in text.splitlines():
        if raw.startswith("//*") or raw.strip() == "":
            if buf:
                logical.append(buf); buf = ""
            continue
        is_cont = re.match(r"//\s", raw) is not None  # // followed by blank label field
        if is_cont and buf.rstrip().endswith(","):
            buf = buf.rstrip() + raw[2:].strip()
            continue
        if raw.startswith("//") or raw.startswith("/*"):
            if buf:
                logical.append(buf)
            buf = raw.rstrip()
        else:
            buf += " " + raw.strip()
    if buf:
        logical.append(buf)
    joined = "\n".join(logical)

    job = {"name": None, "steps": [], "dd": [], "vsam": [], "path": os.path.relpath(path, ROOT)}
    jm = re.search(r"^//([A-Z0-9#$@]+)\s+JOB", text, re.M)
    job["name"] = jm.group(1) if jm else os.path.splitext(os.path.basename(path))[0].upper()

    cur_step = None
    for line in logical:
        sm = re.match(r"//(\S+)\s+EXEC\s+(.*)", line)
        if sm:
            stepname, rest = sm.group(1), sm.group(2)
            pgm = None; proc = None
            pm = re.search(r"PGM=([A-Z0-9#$@]+)", rest)
            if pm:
                pgm = pm.group(1)
            else:
                prm = re.search(r"(?:PROC=)?([A-Z0-9#$@]+)", rest)
                if prm and "=" not in rest.split(",")[0]:
                    proc = prm.group(1)
                elif re.match(r"[A-Z0-9#$@]+", rest) and "PGM" not in rest:
                    proc = rest.split(",")[0].strip()
            # indirect invocation: IMS region controller carries the real
            # program as the 2nd positional of PARM= (BMP,PGM,PSB / DLI,PGM,..)
            indirect = None
            if pgm == "DFSRRC00":
                im = re.search(r"PARM=['\"]?(?:BMP|DLI|DBB|ULU|PLI|BATCH)\s*,\s*([A-Z0-9#$@]+)", rest)
                if im:
                    indirect = im.group(1)
            cur_step = {"step": stepname, "pgm": pgm, "proc": proc, "indirect": indirect}
            job["steps"].append(cur_step)
            continue
        dm = re.match(r"//(\S+)\s+DD\s+(.*)", line)
        if dm:
            ddname, rest = dm.group(1), dm.group(2)
            dsn = None
            dnm = re.search(r"DSN=([A-Z0-9#$@.()+-]+)", rest)
            if dnm:
                dsn = dnm.group(1)
            disp = None
            dpm = re.search(r"DISP=\(?([A-Z,]+)\)?", rest)
            if dpm:
                disp = dpm.group(1).split(",")[0]
            if ddname not in ("SYSIN", "SYSPRINT", "SYSOUT", "SYSUT1", "SYSUT2") or dsn:
                job["dd"].append({"step": cur_step["step"] if cur_step else None,
                                  "ddname": ddname, "dsn": dsn, "disp": disp})

    # VSAM IDCAMS DEFINE parsing (whole text, continuation-aware)
    idc = re.sub(r"-\s*\n", "", text)  # join IDCAMS continuation (trailing '-')
    idc = re.sub(r"\s+", " ", idc)
    for m in re.finditer(r"DEFINE\s+CLUSTER\s*\(\s*NAME\(([^)]+)\)", idc):
        job["vsam"].append({"kind": "CLUSTER", "name": m.group(1).strip()})
    for m in re.finditer(r"DEFINE\s+ALTERNATEINDEX\s*\(\s*NAME\(([^)]+)\).*?RELATE\(([^)]+)\)", idc):
        job["vsam"].append({"kind": "AIX", "name": m.group(1).strip(), "relate": m.group(2).strip()})
    for m in re.finditer(r"DEFINE\s+PATH\s*\(?\s*NAME\(([^)]+)\).*?PATHENTRY\(([^)]+)\)", idc):
        job["vsam"].append({"kind": "PATH", "name": m.group(1).strip(), "pathentry": m.group(2).strip()})
    for m in re.finditer(r"BLDINDEX\s+INDATASET\(([^)]+)\)\s+OUTDATASET\(([^)]+)\)", idc):
        job["vsam"].append({"kind": "BLDINDEX", "base": m.group(1).strip(), "aix": m.group(2).strip()})

    # Indirect program invocations: IMS region controller (from steps) + DB2 TSO
    # (RUN PROGRAM(x) under IKJEFT01) + IMS DLI/BMP already captured per-step.
    indirect = set()
    for s in job["steps"]:
        if s.get("indirect"):
            indirect.add(s["indirect"])
    for m in re.finditer(r"RUN\s+PROGRAM\(([A-Z0-9#$@]+)\)", joined):
        indirect.add(m.group(1))
    job["indirect_pgms"] = sorted(indirect)
    return job


# ----------------------------------------------------------------------------
# Copybook field parsing (PF -> Field mapping)
# ----------------------------------------------------------------------------
def pic_length(pic, usage):
    if not pic:
        return 0
    p = pic.upper()
    # expand parenthesised repeats: X(10) 9(4) etc.
    def expand(s):
        out = ""
        i = 0
        while i < len(s):
            c = s[i]
            if i + 1 < len(s) and s[i + 1] == "(":
                j = s.find(")", i)
                n = int(re.sub(r"\D", "", s[i + 2:j]) or "1")
                out += c * n
                i = j + 1
            else:
                out += c
                i += 1
        return out
    body = expand(p)
    digits = body.count("9")
    x = body.count("X") + body.count("A")
    if usage and ("COMP-3" in usage or "PACKED" in usage):
        return math.ceil((digits + 1) / 2.0)
    if usage and ("COMP" in usage or "BINARY" in usage):
        if digits <= 4:
            return 2
        if digits <= 9:
            return 4
        return 8
    # DISPLAY
    return digits + x


def parse_copybook(path):
    text = read_text(path)
    fields = []
    records = []
    lines = text.splitlines()
    # join statements terminated by '.'
    codelines = []
    for raw in lines:
        if len(raw) > 6 and raw[6] in "*/":
            continue
        area = raw[6:72] if len(raw) > 6 else raw
        if area.strip():
            codelines.append(area)
    buf = ""
    stmts = []
    for area in codelines:
        buf += " " + area.strip()
        if area.rstrip().endswith("."):
            stmts.append(buf.strip())
            buf = ""
    if buf.strip():
        stmts.append(buf.strip())

    for s in stmts:
        m = re.match(r"(\d{2})\s+([A-Z0-9][A-Z0-9-]*)", s)
        if not m:
            continue
        level = int(m.group(1))
        name = m.group(2)
        pic = None
        pm = re.search(r"\bPIC(?:TURE)?\s+(?:IS\s+)?([X9AVSP(),.0-9/BZ+-]+)", s)
        if pm:
            pic = pm.group(1).rstrip(".")
        usage = None
        um = re.search(r"\b(COMP-3|COMP-5|COMP-4|COMP-1|COMP-2|COMP|BINARY|PACKED-DECIMAL|DISPLAY)\b", s)
        if um:
            usage = um.group(1)
        occurs = None
        om = re.search(r"OCCURS\s+(\d+)", s)
        if om:
            occurs = int(om.group(1))
        redef = None
        rm = re.search(r"REDEFINES\s+([A-Z0-9][A-Z0-9-]*)", s)
        if rm:
            redef = rm.group(1)
        is88 = level == 88
        length = pic_length(pic, usage) if pic and not is88 else 0
        if occurs and length:
            length *= occurs
        fld = {"level": level, "name": name, "pic": pic, "usage": usage,
               "occurs": occurs, "redefines": redef, "length": length, "is88": is88}
        fields.append(fld)
        if level == 1:
            records.append(name)
    return {"path": os.path.relpath(path, ROOT), "records": records, "fields": fields}


# ============================================================================
# MAIN
# ============================================================================
def main():
    files = collect_files()

    programs = {}
    for f in files:
        if f["kind"] == "COBOL Program":
            p = os.path.join(ROOT, f["path"])
            pr = parse_cobol(p)
            pr["loc"] = f["loc"]
            pr["module"] = f["module"]
            programs[pr["program_id"]] = pr

    copybooks = {}
    for f in files:
        if f["kind"] == "Copybook":
            p = os.path.join(ROOT, f["path"])
            cb = parse_copybook(p)
            cb["name"] = os.path.splitext(f["name"])[0].upper()
            cb["loc"] = f["loc"]
            copybooks[cb["name"]] = cb

    jobs = {}
    for f in files:
        if f["kind"] in ("JCL", "PROC"):
            p = os.path.join(ROOT, f["path"])
            jb = parse_jcl(p)
            jb["kind"] = f["kind"]
            jobs[os.path.splitext(f["name"])[0].upper()] = jb

    # CSD parsing: CICS transactions, program registrations, file definitions
    csd = {"transactions": {}, "programs": set(), "files": {}}
    for f in files:
        if f["kind"] == "CICS CSD":
            txt = re.sub(r"\s+", " ", read_text(os.path.join(ROOT, f["path"])))
            for m in re.finditer(r"DEFINE\s+TRANSACTION\(([A-Z0-9]+)\).*?PROGRAM\(([A-Z0-9]+)\)", txt):
                csd["transactions"][m.group(1)] = m.group(2)
            for m in re.finditer(r"DEFINE\s+PROGRAM\(([A-Z0-9]+)\)", txt):
                csd["programs"].add(m.group(1))
            for m in re.finditer(r"DEFINE\s+FILE\(([A-Z0-9]+)\).*?DSNAME\(([A-Z0-9.$#@]+)\)", txt):
                csd["files"][m.group(1)] = m.group(2)
    csd["programs"] = sorted(csd["programs"])

    # menu navigation edges
    menu_edges = []
    for mname in ("COMEN02Y", "COADM02Y"):
        pth = None
        for f in files:
            if f["name"].upper().startswith(mname):
                pth = os.path.join(ROOT, f["path"]); break
        if pth:
            progs, names = parse_menu(pth)
            menu_prog = "COMEN01C" if mname == "COMEN02Y" else "COADM01C"
            for i, tgt in enumerate(progs):
                menu_edges.append({"from": menu_prog, "to": tgt,
                                   "type": "CICS menu", "label": names[i] if i < len(names) else ""})

    data = {
        "generated": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S") + "Z",
        "application": "AWS CardDemo (mainframe credit-card management)",
        "files": files,
        "programs": programs,
        "copybooks": copybooks,
        "jobs": jobs,
        "menu_edges": menu_edges,
        "csd": csd,
    }
    with open(os.path.join(OUT, "data.json"), "w") as f:
        json.dump(data, f, indent=2, default=str)
    print("Parsed: %d files, %d programs, %d copybooks, %d jobs, %d menu edges" % (
        len(files), len(programs), len(copybooks), len(jobs), len(menu_edges)))
    return data


if __name__ == "__main__":
    main()
