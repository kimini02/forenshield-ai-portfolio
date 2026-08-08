"use client"

import { useEffect, useRef, useState } from "react"
import { Settings } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import { useUserSettings } from "@/hooks/use-user-settings"
import type {
  DateFormat,
  ListSort,
  ThemeMode,
} from "@/lib/user-settings"
import { cn } from "@/lib/utils"

const themeOptions: { value: ThemeMode; label: string }[] = [
  { value: "light", label: "라이트" },
  { value: "dark", label: "다크" },
  { value: "system", label: "시스템" },
]

const dateFormatOptions: { value: DateFormat; label: string }[] = [
  { value: "kr", label: "2026. 06. 18. 14:30" },
  { value: "us", label: "06/18/2026, 2:30 PM" },
  { value: "iso", label: "2026-06-18 14:30" },
]

const listSortOptions: { value: ListSort; label: string }[] = [
  { value: "newest", label: "최신순" },
  { value: "status", label: "상태순" },
]

function SettingRow({
  label,
  description,
  children,
}: {
  label: string
  description?: string
  children: React.ReactNode
}) {
  return (
    <div className="space-y-2">
      <div>
        <p className="text-sm font-medium text-foreground">{label}</p>
        {description && (
          <p className="text-xs text-muted-foreground">{description}</p>
        )}
      </div>
      {children}
    </div>
  )
}

function SelectSetting<T extends string | number>({
  value,
  options,
  onChange,
}: {
  value: T
  options: { value: T; label: string }[]
  onChange: (value: T) => void
}) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value as T)}
      className="h-8 w-full rounded-lg border border-border bg-background px-2.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
    >
      {options.map((option) => (
        <option key={String(option.value)} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  )
}

export function SettingsDropdown() {
  const { settings, updateSettings } = useUserSettings()
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return

    function handlePointerDown(event: MouseEvent) {
      if (!containerRef.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }

    function handleEscape(event: KeyboardEvent) {
      if (event.key === "Escape") setOpen(false)
    }

    document.addEventListener("mousedown", handlePointerDown)
    document.addEventListener("keydown", handleEscape)

    return () => {
      document.removeEventListener("mousedown", handlePointerDown)
      document.removeEventListener("keydown", handleEscape)
    }
  }, [open])

  return (
    <div ref={containerRef} className="relative">
      <Button
        variant="outline"
        size="sm"
        aria-expanded={open}
        aria-haspopup="menu"
        aria-label="설정"
        onClick={() => setOpen((prev) => !prev)}
      >
        <Settings data-icon="inline-start" />
        설정
      </Button>

      {open && (
        <div
          role="menu"
          aria-label="사용자 설정"
          className="absolute right-0 z-50 mt-2 w-72 rounded-xl border border-border bg-popover p-4 shadow-lg"
        >
          <p className="mb-3 text-sm font-semibold text-popover-foreground">설정</p>

          <div className="space-y-4">
            <SettingRow label="다크모드" description="모든 페이지에 적용됩니다.">
              <div className="grid grid-cols-3 gap-1.5">
                {themeOptions.map((option) => (
                  <button
                    key={option.value}
                    type="button"
                    role="menuitemradio"
                    aria-checked={settings.theme === option.value}
                    onClick={() => updateSettings({ theme: option.value })}
                    className={cn(
                      "rounded-lg border px-2 py-1.5 text-xs font-medium transition-colors",
                      settings.theme === option.value
                        ? "border-primary bg-primary/10 text-primary"
                        : "border-border text-muted-foreground hover:bg-muted"
                    )}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            </SettingRow>

            <Separator />

            <SettingRow label="날짜 형식">
              <SelectSetting
                value={settings.dateFormat}
                options={dateFormatOptions}
                onChange={(value) => updateSettings({ dateFormat: value })}
              />
            </SettingRow>

            <SettingRow
              label="분석 완료 알림"
              description="분석이 완료되면 알림을 받습니다."
            >
              <button
                type="button"
                role="menuitemcheckbox"
                aria-checked={settings.analysisCompleteNotification}
                onClick={() =>
                  updateSettings({
                    analysisCompleteNotification: !settings.analysisCompleteNotification,
                  })
                }
                className={cn(
                  "flex h-8 w-full items-center justify-between rounded-lg border px-3 text-sm transition-colors",
                  settings.analysisCompleteNotification
                    ? "border-primary/40 bg-primary/10 text-primary"
                    : "border-border text-muted-foreground hover:bg-muted"
                )}
              >
                <span>알림 받기</span>
                <span className="font-medium">
                  {settings.analysisCompleteNotification ? "ON" : "OFF"}
                </span>
              </button>
            </SettingRow>

            <Separator />

            <SettingRow label="목록 기본 정렬" description="내 분석 기록 목록에 적용">
              <SelectSetting
                value={settings.listSort}
                options={listSortOptions}
                onChange={(value) => updateSettings({ listSort: value })}
              />
            </SettingRow>

          </div>
        </div>
      )}
    </div>
  )
}
