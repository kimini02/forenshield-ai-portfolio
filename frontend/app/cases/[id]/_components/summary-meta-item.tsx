import type { LucideIcon } from "lucide-react"

type SummaryMetaItemProps = {
  icon: LucideIcon
  label: string
  value: string
}

export function SummaryMetaItem({ icon: Icon, label, value }: SummaryMetaItemProps) {
  return (
    <div className="flex min-w-0 items-center gap-3 border-b border-r border-slate-200 px-4 py-3 last:border-b-0 sm:[&:nth-child(2n)]:border-r-0 2xl:[&:nth-child(2n)]:border-r 2xl:[&:nth-child(3n)]:border-r-0 2xl:[&:nth-last-child(-n+3)]:border-b-0 dark:border-border">
      <span className="flex size-7 shrink-0 items-center justify-center rounded-md bg-white/70 text-slate-400 dark:bg-card">
        <Icon className="size-3.5" />
      </span>
      <div className="grid min-w-0 flex-1 grid-cols-[92px_minmax(0,1fr)] items-center gap-3">
        <p className="text-[11px] font-medium text-slate-400 dark:text-muted-foreground">{label}</p>
        <p className="truncate text-sm font-medium text-slate-600 dark:text-foreground">{value}</p>
      </div>
    </div>
  )
}
