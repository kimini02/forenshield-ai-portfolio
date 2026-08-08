import { redirect } from "next/navigation"

// 진입점: 로그인 후 서비스 이용 (feature/login 흐름 반영)
export default function HomePage() {
  redirect("/login")
}
