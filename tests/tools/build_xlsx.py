"""Builds tests/Test-Cases.xlsx from the Markdown test documents. Run: python tests/tools/build_xlsx.py"""
import re, pathlib
from openpyxl import Workbook
from openpyxl.styles import Alignment, Font, PatternFill, Border, Side
from openpyxl.worksheet.datavalidation import DataValidation

T = pathlib.Path(__file__).resolve().parent.parent
GREEN = PatternFill("solid", fgColor="1F6B3A"); LIGHT = PatternFill("solid", fgColor="E6F2EA")
thin = Side(style="thin", color="CCCCCC"); BOX = Border(left=thin, right=thin, top=thin, bottom=thin)
WRAP = Alignment(wrap_text=True, vertical="top")

def clean(s): return s.replace("**", "").replace("`", "").strip()

def tables(path):
    """Yield (heading, header, rows) for each Markdown table."""
    heading, lines, i = "", path.read_text(encoding="utf-8").split("\n"), 0
    while i < len(lines):
        l = lines[i]
        if l.startswith("#"): heading = l.lstrip("#").strip()
        if l.startswith("|") and i + 1 < len(lines) and re.match(r"^\|[\s:|-]+\|$", lines[i + 1]):
            cells = lambda x: [clean(c) for c in x.strip().strip("|").split("|")]
            header, rows, i = cells(l), [], i + 2
            while i < len(lines) and lines[i].startswith("|"):
                rows.append(cells(lines[i])); i += 1
            yield heading, header, rows
            continue
        i += 1

def style_header(ws, row, ncols):
    for c in range(1, ncols + 1):
        x = ws.cell(row, c); x.fill = GREEN; x.font = Font(bold=True, color="FFFFFF"); x.alignment = WRAP; x.border = BOX

wb = Workbook()

# Sheet 1: all black-box cases, normalised
ws = wb.active; ws.title = "Black-box"
cols = ["ID", "Area", "Priority", "Automation", "Test (steps)", "Expected result", "Result", "Tester notes"]
ws.append(cols); style_header(ws, 1, len(cols))
for heading, header, rows in tables(T / "02-black-box-test-cases.md"):
    for r in rows:
        if not r or not re.match(r"^TC-", r[0]) or r[0].startswith("TC-WF-100"): continue
        area = re.sub(r"^\d+(\.\d+)*\s*", "", heading)
        if header[:2] == ["ID", "Test (steps)"] or header[:2] == ["ID", "Test"]:
            pa = r[3]; pri, _, auto = pa.partition("·")
            ws.append([r[0], area, pri.strip(), auto.strip(), r[1], r[2], "Not run", ""])
        elif header[:3] == ["ID", "P", "Scenario"]:
            ws.append([r[0], area, "P" + r[1], "Manual", r[2], "All steps succeed; audit, notifications and budget are consistent", "Not run", ""])
for row in ws.iter_rows(min_row=2):
    for c in row: c.alignment = WRAP; c.border = BOX
for col, w in zip("ABCDEFGH", [14, 28, 9, 24, 60, 50, 12, 30]): ws.column_dimensions[col].width = w
ws.freeze_panes = "B2"; ws.auto_filter.ref = ws.dimensions
dv = DataValidation(type="list", formula1='"Not run,Pass,Fail,Blocked,N/A"', allow_blank=True)
ws.add_data_validation(dv); dv.add(f"G2:G{ws.max_row}")

# Other sheets: tables as blocks, each with its own header
for fname, title in [("03-white-box-test-cases.md", "White-box"), ("04-security-tests.md", "Security"),
                     ("05-non-functional-tests.md", "Non-functional")]:
    s = wb.create_sheet(title); widths = {}
    for heading, header, rows in tables(T / fname):
        if not rows or not re.match(r"^(WB|TC|NF)-", rows[0][0] if rows[0] else ""): continue
        s.append([heading]); s.cell(s.max_row, 1).font = Font(bold=True, size=12); s.cell(s.max_row, 1).fill = LIGHT
        s.append(header); style_header(s, s.max_row, len(header))
        for r in rows:
            s.append(r)
            for c in s[s.max_row]: c.alignment = WRAP; c.border = BOX
        s.append([])
        for n, h in enumerate(header):
            widths[n] = max(widths.get(n, 12), min(60, max([len(h)] + [len(r[n]) // 2 + 8 for r in rows if n < len(r)])))
    for n, w in widths.items(): s.column_dimensions[chr(65 + n)].width = w
    s.freeze_panes = "A1"

# Summary sheet
sm = wb.create_sheet("Summary", 0)
sm.append(["Area", "Cases", "Pass", "Fail", "Blocked", "Not run"]); style_header(sm, 1, 6)
areas = sorted({ws.cell(r, 2).value for r in range(2, ws.max_row + 1)})
for n, a in enumerate(areas, start=2):
    q = f"'Black-box'!$B:$B,$A{n}"
    sm.append([a, f"=COUNTIFS({q})"] + [f'=COUNTIFS({q},\'Black-box\'!$G:$G,"{st}")' for st in ["Pass", "Fail", "Blocked", "Not run"]])
last = sm.max_row
sm.append(["Total"] + [f"=SUM({c}2:{c}{last})" for c in "BCDEF"])
for c in sm[sm.max_row]: c.font = Font(bold=True)
sm.column_dimensions["A"].width = 40
out = T / "Test-Cases.xlsx"; wb.save(out); print("saved", out, "| black-box rows:", ws.max_row - 1)
