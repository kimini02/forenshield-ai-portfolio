import { AdminToastProvider } from "@/app/admin/_components/admin-toast-provider"
import { AdminAuthGuard } from "@/app/admin/_components/admin-auth-guard"
import { AdminShell } from "@/app/admin/_components/admin-shell"

export default function AdminLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <AdminAuthGuard>
      <AdminToastProvider>
        <AdminShell>{children}</AdminShell>
      </AdminToastProvider>
    </AdminAuthGuard>
  )
}
