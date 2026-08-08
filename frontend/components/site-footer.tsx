export function SiteFooter() {
  return (
    <footer className="border-t border-slate-200 bg-[#f8fbfd] dark:border-border dark:bg-card">
      <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-2 px-4 py-6 text-xs text-slate-500 sm:flex-row sm:px-6 lg:px-8 dark:text-muted-foreground">
        <p>ForenShield AI · 디지털 미디어 인증 시스템 v1.0</p>
        <p className="font-mono">내부망 전용 · 외부 반출 금지</p>
      </div>
    </footer>
  )
}
