#!/usr/bin/env python3
"""Generate requirements/index.md and traceability.md from _extracted JSON."""

from __future__ import annotations

import json
import re
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXTRACTED = ROOT / "docs" / "requirements" / "_extracted"
INDEX_OUT = ROOT / "docs" / "requirements" / "index.md"
RTM_OUT = ROOT / "docs" / "requirements" / "traceability.md"

# Minimal BE implementation hints (api/specification.md 기준). 나머지는 ⬜.
IMPLEMENTED_FN = {
    "FN-LOGIN-020-BE",
    "FN-LOGIN-021-BE",
    "FN-SIGNUP-037-BE",
    "FN-DSH-043-BE",
    "FN-REQ-047-BE",
    "FN-REQ-049-BE",
    "FN-REQ-051-BE",
    "FN-DTL-053-BE",
    "FN-DTL-055-BE",
    "FN-HIS-106-BE",
    "FN-MY-108-BE",
    "FN-MY-109-BE",
    "FN-ADMIN-119-BE",
    "FN-ADMIN-120-BE",
    "FN-ADMIN-124-BE",
    "FN-ADMIN-133-BE",
    "FN-ADMIN-137-BE",
    "FN-ADMIN-146-BE",
}

FE_PRIMARY_PREFIXES = ("FN-DSH-041", "FN-DSH-042", "FN-COM-", "FN-LOGIN-017", "FN-LOGIN-018", "FN-LOGIN-019")


def load_json(name: str) -> list[dict]:
    path = EXTRACTED / name
    if not path.exists():
        return []
    return json.loads(path.read_text(encoding="utf-8"))


def rq_area(rq_id: str) -> str:
    parts = rq_id.split("-")
    return parts[1] if len(parts) >= 2 else "OTHER"


def extract_api_hint(row: dict) -> str:
    for key in ("4.프로세스흐름", "4.주요인터페이스", "7.출력", "5.구성요소"):
        text = row.get(key, "")
        if not text:
            continue
        paths = re.findall(r"`((?:GET|POST|PATCH|DELETE|PUT)\s+/[^`]+)`", text)
        if paths:
            return ", ".join(dict.fromkeys(paths))
        apis = re.findall(r"`([A-Za-z]+Controller(?:\.[A-Za-z0-9_]+)?)`", text)
        if apis:
            return ", ".join(dict.fromkeys(apis))
        if "controller/" in text.lower():
            return "`controller/` · `service/`"
    return "—"


def impl_status(fn_id: str, row: dict) -> str:
    if fn_id in IMPLEMENTED_FN:
        return "✅"
    scope = row.get("2.범위(제외)", "")
    if "UI" in scope and "백엔드" not in row.get("2.범위(포함)", ""):
        pass
    name = row.get("기능명", "")
    if any(fn_id.startswith(p) for p in FE_PRIMARY_PREFIXES):
        return "— (FE 주도)"
    if "PDF" in name or "비교" in name or "블록체인" in name or "X.509" in name:
        return "⬜"
    if fn_id in IMPLEMENTED_FN:
        return "✅"
    return "🟡"


def generate_index(rq_rows: list[dict]) -> str:
    functional = sum(1 for r in rq_rows if r.get("대분류") == "기능")
    non_functional = len(rq_rows) - functional
    grouped: dict[str, list[dict]] = defaultdict(list)
    for row in rq_rows:
        rq_id = row.get("RQ-ID", "")
        if not rq_id.startswith("RQ-"):
            continue
        grouped[rq_area(rq_id)].append(row)

    lines = [
        "# ForenShield 요구사항 목록 (RQ Index)",
        "",
        "> **기준 문서:** `docs/requirements/source/요구사항명세서_최종 (1).xlsx`",
        f"> **총 요구사항:** {len(rq_rows)}건 (기능 {functional} · 비기능 {non_functional})",
        "> **지원 미디어:** **영상(VIDEO)만** — 음성·이미지 제외",
        "",
        "본 문서는 Excel에서 자동 추출한 **검색용 색인**입니다. 정본은 source Excel입니다.",
        "구현 추적은 [traceability.md](./traceability.md)를 참조하세요.",
        "",
        "재생성: `python scripts/extract_requirements_from_excel.py && python scripts/generate_requirements_markdown.py`",
        "",
    ]

    for area in sorted(grouped.keys()):
        lines.append(f"## {area}")
        lines.append("")
        lines.append("| RQ-ID | 중요도 | 분류 | 요구사항명 | 요구사항 내용 |")
        lines.append("| :--- | :---: | :--- | :--- | :--- |")
        for row in sorted(grouped[area], key=lambda r: r.get("RQ-ID", "")):
            content = row.get("요구사항 내용", "").replace("|", "\\|").replace("\n", " ")
            lines.append(
                f"| `{row.get('RQ-ID','')}` | {row.get('중요도','')} | {row.get('분류','')} | "
                f"{row.get('요구사항명','')} | {content} |"
            )
        lines.append("")

    return "\n".join(lines).rstrip() + "\n"


def generate_traceability(be_rows: list[dict]) -> str:
    lines = [
        "# ForenShield 요구사항 추적 매트릭스 (RTM)",
        "",
        "> **기준:** `docs/requirements/source/기능명세서_최종.xlsx` · **백엔드(BE) 시트**",
        "> **미디어 스코프:** 영상(VIDEO) 전용 — Excel REQ·DTL·CMP는 영상 전제",
        "",
        "구현 상태는 `api/specification.md`·코드와 교차검토하세요. ✅/🟡/⬜는 자동 힌트입니다.",
        "",
        "## 1. ID 체계",
        "",
        "| 구분 | 형식 | 예시 |",
        "| :--- | :--- | :--- |",
        "| 요구사항 | `RQ-{영역}-{번호}` | `RQ-REQ-047` |",
        "| 기능(파트별) | `FN-{영역}-{번호}-{접미}` | `FN-REQ-047-BE` |",
        "",
        "접미사: `FE` · `BE` · `AI` · `INF`",
        "",
        "## 2. 백엔드 기능 ↔ RQ 매핑",
        "",
        "| FN-ID | RQ-ID | 기능명 | API/컴포넌트 | 구현 상태 |",
        "| :--- | :--- | :--- | :--- | :---: |",
    ]

    for row in sorted(be_rows, key=lambda r: r.get("FN-ID", "")):
        fn_id = row.get("FN-ID", "")
        if not fn_id.endswith("-BE"):
            continue
        api = extract_api_hint(row).replace("|", "\\|")
        lines.append(
            f"| `{fn_id}` | `{row.get('RQ-ID','')}` | {row.get('기능명','')} | {api} | {impl_status(fn_id, row)} |"
        )

    lines.extend(
        [
            "",
            "## 3. AI · INF (RTM 요약)",
            "",
            "전체 RQ↔FN 매핑은 Excel `RTM` 시트 또는 `_extracted/fn_RTM.json`을 참조하세요.",
            "",
            "## 4. 재생성",
            "",
            "```bash",
            "python scripts/extract_requirements_from_excel.py",
            "python scripts/generate_requirements_markdown.py",
            "```",
            "",
        ]
    )
    return "\n".join(lines)


def main() -> None:
    rq_rows = load_json("rq_요구사항명세서.json")
    be_rows = load_json("fn_백엔드.json")
    if not rq_rows:
        raise SystemExit("Run extract_requirements_from_excel.py first")
    INDEX_OUT.write_text(generate_index(rq_rows), encoding="utf-8")
    RTM_OUT.write_text(generate_traceability(be_rows), encoding="utf-8")
    print(f"Wrote {INDEX_OUT} ({len(rq_rows)} RQ)")
    print(f"Wrote {RTM_OUT} ({len(be_rows)} BE FN)")


if __name__ == "__main__":
    main()
