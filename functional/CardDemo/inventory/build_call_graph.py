#!/usr/bin/env python3
"""Build the CardDemo core module call graph from source (app/).

Outputs (next to this script):
  call_graph.json   - programs, edges, unresolved edges, entry points, CSD, JCL, scheduler
  call_graph.md     - human-readable edge tables with file:line cites

Edge sources:
  * COBOL  CALL 'LIT' / CALL identifier (identifier resolved through MOVE 'lit' TO var,
           MOVE var2 TO var, VALUE 'lit' on the declaration, or option-table copybooks)
  * COBOL  EXEC CICS XCTL / LINK / START PROGRAM(...) (same resolution rules)
  * COBOL  EXEC CICS RETURN TRANSID(...)  (recorded as self-return, not as an edge)
  * COBOL  EXEC CICS WRITEQ TD QUEUE('JOBS') = internal-reader submit (online -> batch)
  * COBOL  EXEC CICS READ/WRITE/REWRITE/DELETE/STARTBR FILE|DATASET(...) = dataset usage
  * JCL    EXEC PGM= and EXEC PROC= / EXEC <proc>, PROC steps
  * CSD    DEFINE TRANSACTION -> PROGRAM, DEFINE PROGRAM, DEFINE FILE -> DSNAME
  * Control-M  JOB MEMNAME, INCOND / OUTCOND
Comment lines ('*' or '/' in column 7) are skipped for COBOL; '//*' for JCL.
"""
import json
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
APP = os.path.join(ROOT, "app")
OUT_DIR = os.path.dirname(os.path.abspath(__file__))

RUNTIME_MODULES = {"CEE3ABD", "CEEDAYS", "DFHCSDUP", "IDCAMS", "IEFBR14", "IEBGENER",
                   "SORT", "SDSF", "FTP", "IKJEFT1B", "DSNTEP4", "DSNTIAUL"}


def rel(p):
    return os.path.relpath(p, ROOT)


def cobol_lines(path):
    """Yield (lineno, text) skipping comment lines (col 7 '*' or '/') and seq numbers."""
    with open(path, encoding="ascii", errors="replace") as f:
        for i, raw in enumerate(f, 1):
            line = raw.rstrip("\n")
            if len(line) >= 7 and line[6] in "*/":
                continue
            if len(line) > 72:
                line = line[:72]
            yield i, line


def program_sources():
    progs = {}
    for d in ("cbl", "asm"):
        dd = os.path.join(APP, d)
        for fn in sorted(os.listdir(dd)):
            stem, ext = os.path.splitext(fn)
            if ext.lower() in (".cbl", ".cob", ".asm"):
                progs[stem.upper()] = rel(os.path.join(dd, fn))
    return progs


def extension_programs():
    out = []
    for d in sorted(os.listdir(APP)):
        if d.startswith("app-"):
            cbl = os.path.join(APP, d, "cbl")
            if os.path.isdir(cbl):
                for fn in sorted(os.listdir(cbl)):
                    stem, ext = os.path.splitext(fn)
                    if ext.lower() in (".cbl", ".cob"):
                        out.append({"program": stem.upper(), "path": rel(os.path.join(cbl, fn)),
                                    "extension": d})
    return out


COPYBOOK_DIRS = [os.path.join(APP, "cpy"), os.path.join(APP, "cpy-bms")]


def find_copybook(name):
    for d in COPYBOOK_DIRS:
        for fn in os.listdir(d):
            if os.path.splitext(fn)[0].upper() == name.upper():
                return os.path.join(d, fn)
    return None


def collect_text(path):
    """Program lines plus copied copybook lines, each tagged with (file, lineno, text)."""
    items = []
    for ln, text in cobol_lines(path):
        items.append((rel(path), ln, text))
    joined = "\n".join(t for _, _, t in items)
    for cp in re.findall(r"\bCOPY\s+([A-Z0-9-]+)", joined):
        cpath = find_copybook(cp)
        if cpath:
            for ln, text in cobol_lines(cpath):
                items.append((rel(cpath), ln, text))
    return items


def resolve(var, items, depth=0, seen=None):
    """Return list of (literal, cite, how) for values a variable can hold."""
    base = re.sub(r"\(.*", "", var).strip()
    seen = set(seen or ())
    if base in seen or depth > 3:
        return []
    seen.add(base)
    out = []
    # MOVE 'lit' TO var  /  MOVE other TO var
    for f, ln, t in items:
        m = re.search(r"MOVE\s+(?:'([^']+)'|\"([^\"]+)\"|([A-Z0-9-]+(?:\([^)]*\))?))\s+TO\s+" + re.escape(base) + r"\b", t)
        if m:
            lit = m.group(1) or m.group(2)
            if lit:
                out.append((lit.strip(), f"{f}:{ln}", "MOVE literal"))
            else:
                src = m.group(3)
                if src in ("SPACES", "LOW-VALUES", "ZEROS"):
                    continue
                sub = resolve(src, items, depth + 1, seen)
                if sub:
                    out.extend((l, c, f"MOVE {src} -> " + h) for l, c, h in sub)
                else:
                    out.append((None, f"{f}:{ln}", f"MOVE {src} (dynamic)"))
    # VALUE 'lit' on declaration (may be on next line)
    for idx, (f, ln, t) in enumerate(items):
        if re.search(r"\b\d\d\s+" + re.escape(base) + r"\b", t):
            window = t + " " + (items[idx + 1][2] if idx + 1 < len(items) else "")
            m = re.search(r"VALUE\s+'([^']+)'", window)
            if m:
                out.append((m.group(1).strip(), f"{f}:{ln}", "VALUE clause"))
            # option table: subordinate of a group that REDEFINES a data block -> gather X(08) values
            if "OCCURS" in window or re.search(r"\(", var):
                pass
    if "(" in var:  # subscripted table item: collect PIC X(08) VALUE 'xxxxxxxx' from the copybook holding it
        cb = None
        for f, ln, t in items:
            if re.search(r"\b\d\d\s+" + re.escape(base) + r"\b.*(PIC|OCCURS)", t):
                cb = f
                break
        if cb:
            for f, ln, t in items:
                if f == cb:
                    m = re.search(r"PIC\s+X\(0?8\)\s+VALUE\s+'([A-Z0-9]{8})'", t)
                    if m:
                        out.append((m.group(1), f"{f}:{ln}", "option table entry"))
    return out


def parse_cobol(prog, path, progs):
    edges, unresolved, datasets, returns, tdq = [], [], [], [], []
    items = collect_text(os.path.join(ROOT, path))
    own = [(f, ln, t) for f, ln, t in items if f == path]
    text_by_line = {ln: t for _, ln, t in own}
    n = len(own)
    for i, (f, ln, t) in enumerate(own):
        # static CALL
        for m in re.finditer(r"\bCALL\s+['\"]([A-Z0-9]+)['\"]", t):
            edges.append({"from": prog, "to": m.group(1), "kind": "CALL", "cite": f"{f}:{ln}"})
        m = re.search(r"\bCALL\s+([A-Z][A-Z0-9-]*)\b", t)
        if m and not re.search(r"\bCALL\s+['\"]", t):
            for lit, cite, how in resolve(m.group(1), items) or [(None, f"{f}:{ln}", "no MOVE/VALUE found")]:
                if lit:
                    edges.append({"from": prog, "to": lit, "kind": "CALL(dyn)", "cite": f"{f}:{ln}", "resolved_via": f"{how} @ {cite}"})
                else:
                    unresolved.append({"from": prog, "var": m.group(1), "kind": "CALL", "cite": f"{f}:{ln}", "note": how})
        # EXEC CICS ... (statement may span lines) - gather window until END-EXEC
        if re.search(r"EXEC\s+CICS", t):
            win = []
            for j in range(i, min(i + 25, n)):
                win.append(own[j][2])
                if "END-EXEC" in own[j][2]:
                    break
            w = " ".join(win)
            mm = re.search(r"EXEC\s+CICS\s+(XCTL|LINK|START)\b.*?PROGRAM\s*\(\s*([^)]+?)\s*\)", w)
            if mm:
                verb, arg = mm.group(1), mm.group(2).strip()
                lit = re.match(r"['\"]([A-Z0-9]+)['\"]$", arg)
                if lit:
                    edges.append({"from": prog, "to": lit.group(1), "kind": verb, "cite": f"{f}:{ln}"})
                else:
                    res = resolve(arg, items)
                    lits = {(l, c, h) for l, c, h in res if l}
                    dyn = [(c, h) for l, c, h in res if not l]
                    for l, c, h in sorted(lits):
                        edges.append({"from": prog, "to": l, "kind": f"{verb}(dyn)", "cite": f"{f}:{ln}", "resolved_via": f"{h} @ {c}"})
                    for c, h in dyn:
                        unresolved.append({"from": prog, "var": arg, "kind": verb, "cite": f"{f}:{ln}", "note": f"{h} @ {c}"})
                    if not res:
                        unresolved.append({"from": prog, "var": arg, "kind": verb, "cite": f"{f}:{ln}", "note": "no MOVE/VALUE found"})
            mm = re.search(r"EXEC\s+CICS\s+RETURN\b.*?TRANSID\s*\(\s*([^)]+?)\s*\)", w)
            if mm:
                arg = mm.group(1).strip()
                lit = re.match(r"['\"]([A-Z0-9]+)['\"]$", arg)
                vals = [lit.group(1)] if lit else sorted({l for l, _, _ in resolve(arg, items) if l})
                returns.append({"from": prog, "transid": vals, "cite": f"{f}:{ln}"})
            mm = re.search(r"EXEC\s+CICS\s+(READ|READNEXT|READPREV|STARTBR|WRITE|REWRITE|DELETE)\b.*?(?:FILE|DATASET)\s*\(\s*([^)]+?)\s*\)", w)
            if mm:
                verb, arg = mm.group(1), mm.group(2).strip()
                lit = re.match(r"['\"]([A-Z0-9 ]+)['\"]$", arg)
                vals = [lit.group(1).strip()] if lit else sorted({l.strip() for l, _, _ in resolve(arg, items) if l})
                for v in vals or [f"?{arg}"]:
                    datasets.append({"program": prog, "file": v, "verb": verb, "cite": f"{f}:{ln}"})
            mm = re.search(r"EXEC\s+CICS\s+WRITEQ\s+TD\b.*?QUEUE\s*\(\s*['\"]?([A-Z0-9]+)['\"]?\s*\)", w)
            if mm:
                tdq.append({"program": prog, "queue": mm.group(1), "cite": f"{f}:{ln}"})
    return edges, unresolved, datasets, returns, tdq


def parse_jcl():
    jobs = {}
    procs = {}
    for d, store in (("jcl", jobs), ("proc", procs)):
        dd = os.path.join(APP, d)
        for fn in sorted(os.listdir(dd)):
            path = os.path.join(dd, fn)
            steps = []
            with open(path, encoding="ascii", errors="replace") as f:
                for ln, raw in enumerate(f, 1):
                    line = raw.rstrip("\n")
                    if line.startswith("//*"):
                        continue
                    m = re.match(r"//(\S*)\s+EXEC\s+PGM=([A-Z0-9]+)", line)
                    if m:
                        steps.append({"step": m.group(1), "pgm": m.group(2), "cite": f"{rel(path)}:{ln}"})
                        continue
                    m = re.match(r"//(\S*)\s+EXEC\s+(?:PROC=)?([A-Z0-9]+)\b", line)
                    if m and m.group(2) not in ("PGM",):
                        steps.append({"step": m.group(1), "proc": m.group(2), "cite": f"{rel(path)}:{ln}"})
            store[os.path.splitext(fn)[0].upper()] = {"path": rel(path), "steps": steps}
    return jobs, procs


def parse_csd():
    path = os.path.join(APP, "csd", "CARDDEMO.CSD")
    trans, programs, files, mapsets = {}, {}, {}, {}
    cur = None
    with open(path) as f:
        for ln, raw in enumerate(f, 1):
            line = raw.rstrip("\n")
            m = re.match(r"\s*DEFINE\s+(TRANSACTION|PROGRAM|FILE|MAPSET)\((\w+)\)", line)
            if m:
                cur = (m.group(1), m.group(2), ln)
                {"TRANSACTION": trans, "PROGRAM": programs, "FILE": files, "MAPSET": mapsets}[m.group(1)][m.group(2)] = {"cite": f"{rel(path)}:{ln}"}
                continue
            if cur and cur[0] == "TRANSACTION":
                m = re.search(r"PROGRAM\((\w+)\)", line)
                if m:
                    trans[cur[1]]["program"] = m.group(1)
                    trans[cur[1]]["program_cite"] = f"{rel(path)}:{ln}"
            if cur and cur[0] == "FILE":
                m = re.search(r"DSNAME\(([\w.]+)\)", line)
                if m:
                    files[cur[1]]["dsname"] = m.group(1)
    return trans, programs, files, mapsets


def parse_controlm():
    path = os.path.join(APP, "scheduler", "CardDemo.controlm")
    jobs = []
    folder = None
    cur = None
    with open(path) as f:
        for ln, raw in enumerate(f, 1):
            m = re.search(r"<(?:SMART_)?FOLDER[^>]*FOLDER_NAME=\"([^\"]+)\"", raw)
            if m:
                folder = m.group(1)
            m = re.search(r"<JOB\s.*?JOBNAME=\"([^\"]+)\".*?MEMNAME=\"([^\"]+)\"", raw)
            if m:
                cur = {"folder": folder, "jobname": m.group(1), "memname": m.group(2), "cite": f"{rel(path)}:{ln}", "in": [], "out": []}
                jobs.append(cur)
                continue
            if cur is not None:
                m = re.search(r"<INCOND NAME=\"([^\"]+)\"", raw)
                if m:
                    cur["in"].append(m.group(1))
                m = re.search(r"<OUTCOND NAME=\"([^\"]+)\"[^>]*SIGN=\"\+\"", raw)
                if m:
                    cur["out"].append(m.group(1))
                if "</JOB>" in raw:
                    cur = None
    return jobs


def main():
    progs = program_sources()
    edges, unresolved, datasets, returns, tdq = [], [], [], [], []
    for prog, path in progs.items():
        if path.lower().endswith(".asm"):
            continue
        e, u, d, r, q = parse_cobol(prog, path, progs)
        edges += e; unresolved += u; datasets += d; returns += r; tdq += q
    jobs, procs = parse_jcl()
    trans, csd_programs, csd_files, mapsets = parse_csd()
    ctm = parse_controlm()

    # JCL step -> program edges (expanding PROCs)
    jcl_edges = []
    for job, j in jobs.items():
        for s in j["steps"]:
            if "pgm" in s:
                jcl_edges.append({"job": job, "step": s["step"], "pgm": s["pgm"], "cite": s["cite"], "via": None})
            else:
                p = procs.get(s["proc"])
                if p:
                    for ps in p["steps"]:
                        if "pgm" in ps:
                            jcl_edges.append({"job": job, "step": f"{s['step']}.{ps['step']}", "pgm": ps["pgm"], "cite": ps["cite"], "via": f"PROC {s['proc']} @ {s['cite']}"})
                else:
                    jcl_edges.append({"job": job, "step": s["step"], "pgm": None, "cite": s["cite"], "via": f"PROC {s['proc']} NOT IN app/proc"})

    # reverse index
    callers = defaultdict(list)
    for e in edges:
        callers[e["to"]].append(e)

    # reachability classification
    in_module = set(progs)
    cics_roots = {t["program"] for t in trans.values() if "program" in t}
    jcl_roots = {e["pgm"] for e in jcl_edges if e["pgm"] in in_module}
    called = {e["to"] for e in edges if e["to"] in in_module}
    absent_csd = sorted(p for p in cics_roots if p not in in_module)
    orphan = sorted(p for p in in_module if p not in cics_roots and p not in jcl_roots and p not in called)
    no_source_call_targets = sorted({e["to"] for e in edges if e["to"] not in in_module and e["to"] not in RUNTIME_MODULES})
    ctm_missing_jcl = sorted({j["memname"] for j in ctm if j["memname"].upper() not in jobs})

    result = {
        "denominator": {"count": len(progs), "programs": progs},
        "extensions_out_of_module": extension_programs(),
        "edges": edges,
        "unresolved": unresolved,
        "datasets": datasets,
        "returns": returns,
        "tdq_writes": tdq,
        "jcl_jobs": jobs, "procs": procs, "jcl_edges": jcl_edges,
        "csd": {"transactions": trans, "programs": csd_programs, "files": csd_files, "mapsets": mapsets},
        "controlm": ctm,
        "findings": {
            "csd_program_without_source": absent_csd,
            "orphan_roots_no_csd_no_jcl_no_caller": orphan,
            "call_targets_without_source": no_source_call_targets,
            "controlm_memname_without_jcl": ctm_missing_jcl,
            "jcl_only_roots_not_scheduled": sorted(j for j in jobs if j not in {c["memname"].upper() for c in ctm}),
        },
    }
    with open(os.path.join(OUT_DIR, "call_graph.json"), "w") as f:
        json.dump(result, f, indent=1)

    md = ["# CardDemo core - generated call graph (do not edit; regenerate with build_call_graph.py)", ""]
    md.append(f"Denominator: **{len(progs)} program sources** in `app/cbl` + `app/asm`.")
    md.append("")
    md.append("## Program -> program edges")
    md.append("")
    md.append("| From | To | Kind | Cite | Resolution |")
    md.append("|---|---|---|---|---|")
    for e in sorted(edges, key=lambda x: (x["from"], x["cite"])):
        md.append(f"| {e['from']} | {e['to']} | {e['kind']} | `{e['cite']}` | {e.get('resolved_via','literal')} |")
    md += ["", "## Unresolved / dynamic edges", "", "| From | Variable | Kind | Cite | Note |", "|---|---|---|---|---|"]
    for u in unresolved:
        md.append(f"| {u['from']} | `{u['var']}` | {u['kind']} | `{u['cite']}` | {u['note']} |")
    md += ["", "## JCL step -> program", "", "| Job | Step | Program | Cite | Via |", "|---|---|---|---|---|"]
    for e in jcl_edges:
        md.append(f"| {e['job']} | {e['step']} | {e['pgm']} | `{e['cite']}` | {e['via'] or ''} |")
    md += ["", "## CICS transactions (CSD)", "", "| Trancode | Program | Source present | Cite |", "|---|---|---|---|"]
    for t, v in sorted(trans.items()):
        md.append(f"| {t} | {v.get('program')} | {'yes' if v.get('program') in in_module else 'NO'} | `{v['cite']}` |")
    md += ["", "## Control-M jobs", "", "| Folder | Job | MEMNAME | JCL present | IN conds | OUT conds | Cite |", "|---|---|---|---|---|---|---|"]
    for j in ctm:
        md.append(f"| {j['folder']} | {j['jobname']} | {j['memname']} | {'yes' if j['memname'].upper() in jobs else 'NO'} | {', '.join(j['in']) or '-'} | {', '.join(j['out']) or '-'} | `{j['cite']}` |")
    md += ["", "## Dataset access (EXEC CICS)", "", "| Program | File | Verb | Cite |", "|---|---|---|---|"]
    for d in datasets:
        md.append(f"| {d['program']} | {d['file']} | {d['verb']} | `{d['cite']}` |")
    md += ["", "## RETURN TRANSID", "", "| Program | TRANSID | Cite |", "|---|---|---|"]
    for r in returns:
        md.append(f"| {r['from']} | {', '.join(r['transid']) or '?'} | `{r['cite']}` |")
    md += ["", "## TD queue writes (internal reader)", ""]
    for q in tdq:
        md.append(f"- {q['program']} -> TDQ `{q['queue']}` (`{q['cite']}`)")
    md += ["", "## Findings", ""]
    for k, v in result["findings"].items():
        md.append(f"- **{k}**: {', '.join(v) or 'none'}")
    with open(os.path.join(OUT_DIR, "call_graph.md"), "w") as f:
        f.write("\n".join(md) + "\n")
    print(json.dumps(result["findings"], indent=1))
    print(f"programs={len(progs)} edges={len(edges)} unresolved={len(unresolved)} jcl_edges={len(jcl_edges)} ctm_jobs={len(ctm)}")


if __name__ == "__main__":
    main()
