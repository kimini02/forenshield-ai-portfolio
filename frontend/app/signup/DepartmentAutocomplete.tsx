// 담당: 김민희
// 역할: 소속 기관/부서 자동완성 입력 (기관 유형 종속 → API 부서 목록)
"use client"

import { useEffect, useMemo, useState } from "react"
import { Building2, Search } from "lucide-react"
import { fetchDepartments } from "@/lib/signup-api"
import type { OrgType } from "./organizationData"

export default function DepartmentAutocomplete({
  orgType,
  value,
  onChange,
}: {
  orgType: OrgType | ""
  value: string
  onChange: (v: string) => void
}) {
  const [open, setOpen] = useState(false)
  const [departments, setDepartments] = useState<string[]>([])
  const [loading, setLoading] = useState(false)

  const disabled = !orgType

  useEffect(() => {
    if (!orgType) {
      setDepartments([])
      return
    }

    let cancelled = false
    setLoading(true)

    fetchDepartments(orgType)
      .then((response) => {
        if (!cancelled) {
          setDepartments([...response.departments, "기타"])
        }
      })
      .catch(() => {
        if (!cancelled) {
          setDepartments(["기타"])
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [orgType])

  const results = useMemo(() => {
    const q = value.trim().replace(/\s+/g, "").toLowerCase()
    if (!q) return departments
    return departments.filter((dept) =>
      dept.replace(/\s+/g, "").toLowerCase().includes(q)
    )
  }, [departments, value])

  const select = (dept: string) => {
    onChange(dept)
    setOpen(false)
  }

  return (
    <div className="relative">
      <div className={`rounded-xl border border-border bg-card ${disabled ? "opacity-60" : ""}`}>
        <div className="flex h-12 items-center gap-3 px-4">
          <Building2 className="size-5 shrink-0 text-muted-foreground" aria-hidden="true" />
          <input
            type="text"
            value={value}
            disabled={disabled || loading}
            onChange={(e) => {
              onChange(e.target.value)
              setOpen(true)
            }}
            onFocus={() => setOpen(true)}
            onBlur={() => setTimeout(() => setOpen(false), 120)}
            placeholder={
              disabled
                ? "기관 유형을 먼저 선택하세요"
                : loading
                  ? "부서 목록 불러오는 중..."
                  : "소속 기관 / 부서 검색"
            }
            className="min-w-0 flex-1 bg-transparent text-sm text-foreground outline-none placeholder:text-muted-foreground disabled:cursor-not-allowed"
            role="combobox"
            aria-expanded={open}
            aria-autocomplete="list"
          />
          {!disabled && <Search className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />}
        </div>
      </div>

      {open && !disabled && results.length > 0 && (
        <ul className="absolute left-0 right-0 top-full z-20 mt-1 max-h-60 overflow-y-auto rounded-xl border border-border bg-card py-1 shadow-lg">
          {results.map((dept) => (
            <li key={dept}>
              <button
                type="button"
                onMouseDown={(e) => {
                  e.preventDefault()
                  select(dept)
                }}
                className="block w-full px-4 py-2 text-left text-sm text-foreground transition-colors hover:bg-muted"
              >
                {dept}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
