type AdminPageHeaderProps = {
  title: string
  description?: string
  action?: React.ReactNode
}

export function AdminPageHeader({ title, description, action }: AdminPageHeaderProps) {
  return (
    <header className="border-b border-slate-200 bg-white px-8 py-5">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="text-xs font-medium text-slate-500">{title}</p>
          <h1 className="mt-1 text-2xl font-bold tracking-tight text-slate-900">{title}</h1>
          {description ? (
            <p className="mt-1 text-sm text-slate-500">{description}</p>
          ) : null}
        </div>
        <div className="flex shrink-0 items-center gap-3">
          {action}
          <div className="flex items-center gap-2 rounded-full border border-emerald-200 bg-emerald-50 px-3 py-1.5 text-xs font-medium text-emerald-700">
            <span className="size-2 rounded-full bg-emerald-500" />
            시스템 정상
          </div>
        </div>
      </div>
    </header>
  )
}
