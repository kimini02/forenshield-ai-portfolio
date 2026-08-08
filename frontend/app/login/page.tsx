// 담당: 장현준
// 역할: 로그인 페이지 구현
import { SiteHeader } from "@/components/site-header"
import { SiteFooter } from "@/components/site-footer"
import { LoginForm } from "@/components/login-form"

export default function LoginPage() {
  return (
    <div className="min-h-screen bg-background">
      <SiteHeader variant="minimal" />

      <main className="mx-auto flex min-h-[calc(100vh-8rem)] max-w-7xl flex-col items-center justify-center px-4 py-12 sm:px-6 lg:px-8">
        <LoginForm />
      </main>

      <SiteFooter />
    </div>
  )
}
