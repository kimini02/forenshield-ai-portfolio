import { MainDashboardRouter } from "@/app/main/_components/main-dashboard-router"
import { SiteFooter } from "@/components/site-footer"
import { SiteHeader } from "@/components/site-header"

export default function MainPage() {
  return (
    <div className="min-h-screen bg-[#f6f8fa] text-slate-900 dark:bg-background dark:text-foreground">
      <SiteHeader />
      <MainDashboardRouter />
      <SiteFooter />
    </div>
  )
}
