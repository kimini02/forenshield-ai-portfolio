// 담당: 윤형진
// 역할: 마이페이지 및 분석 기록 화면 구현
import { SiteHeader } from "@/components/site-header"
import { MyPageContent } from "@/app/mypage/_components/mypage-content"

export default function MyPage() {
  return (
    <div className="min-h-screen bg-background">
      <SiteHeader />
      <MyPageContent />
    </div>
  )
}
