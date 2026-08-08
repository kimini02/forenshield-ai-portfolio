#!/usr/bin/env python3
"""Extract RQ/FN from docs/requirements/source/*.xlsx into JSON artifacts."""

from __future__ import annotations

import json
import re
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / "docs" / "requirements" / "source"
OUT_DIR = ROOT / "docs" / "requirements" / "_extracted"

NS = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
REL_NS = "{http://schemas.openxmlformats.org/package/2006/relationships}"


def col_index(col: str) -> int:
    n = 0
    for ch in col:
        n = n * 26 + (ord(ch) - ord("A") + 1)
    return n


def read_xlsx(path: Path) -> dict[str, list[list[str]]]:
    with zipfile.ZipFile(path) as z:
        wb = ET.fromstring(z.read("xl/workbook.xml"))
        rels = ET.fromstring(z.read("xl/_rels/workbook.xml.rels"))
        rid_to_target = {
            r.get("Id"): r.get("Target") for r in rels.findall(f"{REL_NS}Relationship")
        }

        shared: list[str] = []
        if "xl/sharedStrings.xml" in z.namelist():
            sst = ET.fromstring(z.read("xl/sharedStrings.xml"))
            for si in sst.findall(".//m:si", NS):
                shared.append("".join((t.text or "") for t in si.findall(".//m:t", NS)))

        sheets: dict[str, list[list[str]]] = {}
        for sheet in wb.findall(".//m:sheet", NS):
            name = sheet.get("name") or "sheet"
            rid = sheet.get("{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id")
            target = rid_to_target.get(rid, "")
            if target.startswith("/"):
                target = target[1:]
            sheet_path = "xl/" + target.replace("xl/", "")

            root = ET.fromstring(z.read(sheet_path))
            row_maps: dict[int, dict[int, str]] = {}
            max_col = 0
            for row_el in root.findall(".//m:sheetData/m:row", NS):
                r_idx = int(row_el.get("r", "0"))
                cells: dict[int, str] = {}
                for c in row_el.findall("m:c", NS):
                    ref = c.get("r", "")
                    m = re.match(r"([A-Z]+)", ref)
                    if not m:
                        continue
                    c_idx = col_index(m.group(1))
                    max_col = max(max_col, c_idx)
                    v_el = c.find("m:v", NS)
                    if v_el is None or v_el.text is None:
                        val = ""
                    elif c.get("t") == "s":
                        val = shared[int(v_el.text)] if v_el.text.isdigit() else v_el.text
                    else:
                        val = v_el.text
                    cells[c_idx] = val.strip()
                if cells:
                    row_maps[r_idx] = cells

            if not row_maps:
                sheets[name] = []
                continue

            max_row = max(row_maps)
            table: list[list[str]] = []
            for r in range(1, max_row + 1):
                row = row_maps.get(r, {})
                table.append([row.get(c, "") for c in range(1, max_col + 1)])
            sheets[name] = table
        return sheets


def rows_to_dicts(table: list[list[str]]) -> list[dict[str, str]]:
    if not table:
        return []
    headers = [h.strip() for h in table[0]]
    rows: list[dict[str, str]] = []
    for raw in table[1:]:
        if not any(cell.strip() for cell in raw):
            continue
        item = {}
        for i, h in enumerate(headers):
            if not h:
                continue
            item[h] = raw[i].strip() if i < len(raw) else ""
        rows.append(item)
    return rows


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    summary: dict[str, object] = {}

    rq_path = SOURCE_DIR / "요구사항명세서_최종 (1).xlsx"
    fn_path = SOURCE_DIR / "기능명세서_최종.xlsx"

    if rq_path.exists():
        rq_sheets = read_xlsx(rq_path)
        summary["rq_sheets"] = list(rq_sheets.keys())
        for name, table in rq_sheets.items():
            (OUT_DIR / f"rq_{name}.json").write_text(
                json.dumps(rows_to_dicts(table), ensure_ascii=False, indent=2),
                encoding="utf-8",
            )

    if fn_path.exists():
        fn_sheets = read_xlsx(fn_path)
        summary["fn_sheets"] = list(fn_sheets.keys())
        for name, table in fn_sheets.items():
            safe = re.sub(r"[^\w\-]+", "_", name)
            (OUT_DIR / f"fn_{safe}.json").write_text(
                json.dumps(rows_to_dicts(table), ensure_ascii=False, indent=2),
                encoding="utf-8",
            )

    (OUT_DIR / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
