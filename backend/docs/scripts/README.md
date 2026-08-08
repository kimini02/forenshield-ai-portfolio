# Excel → requirements Markdown 재생성

정본: `docs/requirements/source/`

| 파일 | 용도 |
| :--- | :--- |
| `요구사항명세서_최종 (1).xlsx` | RQ → `requirements/index.md` |
| `기능명세서_최종.xlsx` | BE FN → `requirements/traceability.md` |

**미디어 스코프:** 영상(VIDEO)만 — 음성·이미지 제외

```bash
python scripts/extract_requirements_from_excel.py
python scripts/generate_requirements_markdown.py
```

중간 JSON은 `docs/requirements/_extracted/` (gitignore, 로컬 재생성용).
