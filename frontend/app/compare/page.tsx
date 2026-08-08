import { SiteFooter } from "@/components/site-footer"
import { SiteHeader } from "@/components/site-header"
import { CompareVerificationFlow } from "@/app/compare/_components/compare-verification-flow"

export default function ComparePage() {
  return (
    <div className="min-h-screen bg-[#f6f8fa] text-slate-900 dark:bg-background dark:text-foreground">
      <SiteHeader />
      <main className="mx-auto flex w-full max-w-5xl flex-col gap-8 px-4 py-8 sm:px-6 lg:px-8">
        <div>
          <p className="text-sm font-bold text-teal-600">Compare Verification</p>
          <h1 className="mt-1 text-3xl font-bold tracking-normal text-slate-950 dark:text-foreground">비교검증</h1>
          <p className="mt-1 max-w-2xl text-sm font-medium text-slate-500">
            사건에 등록된 기준 증거를 선택하고, 비교 대상 파일을 업로드해 내용 기준 일치 여부를 검증합니다.
          </p>
        </div>
        <CompareVerificationFlow />
      </main>
      <SiteFooter />
    </div>
  )
}
