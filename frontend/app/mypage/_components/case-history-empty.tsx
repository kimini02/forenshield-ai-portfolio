import { FolderOpen } from "lucide-react"

export function CaseHistoryEmpty() {
  return (
    <div className="flex flex-col items-center gap-3 px-5 py-16 text-center">
      <div className="flex size-12 items-center justify-center rounded-full bg-muted text-muted-foreground">
        <FolderOpen className="size-5" aria-hidden="true" />
      </div>
      <div>
        <p className="text-sm font-medium text-foreground">
          등록된 사건이 없습니다
        </p>
        <p className="mt-1 text-sm text-muted-foreground">
          사건 등록 버튼으로 새 사건을 먼저 만들어 주세요.
        </p>
      </div>
    </div>
  )
}
