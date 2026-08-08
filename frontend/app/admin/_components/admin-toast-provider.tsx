"use client"

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
} from "react"
import { cn } from "@/lib/utils"

type ToastMessage = {
  title: string
  description?: string
  variant?: "default" | "destructive"
}

type AdminToastContextValue = {
  toast: (message: ToastMessage) => void
}

const AdminToastContext = createContext<AdminToastContextValue | null>(null)

export function AdminToastProvider({ children }: { children: React.ReactNode }) {
  const [message, setMessage] = useState<ToastMessage | null>(null)

  const toast = useCallback((next: ToastMessage) => {
    setMessage(next)
    window.setTimeout(() => setMessage(null), 3000)
  }, [])

  const value = useMemo(() => ({ toast }), [toast])

  return (
    <AdminToastContext.Provider value={value}>
      {children}
      {message && (
        <div
          role="status"
          className={cn(
            "fixed right-4 bottom-4 z-50 w-full max-w-sm rounded-xl border px-4 py-3 shadow-lg",
            message.variant === "destructive"
              ? "border-destructive/40 bg-destructive/10 text-destructive"
              : "border-border bg-card text-foreground"
          )}
        >
          <p className="text-sm font-semibold">{message.title}</p>
          {message.description && (
            <p className="mt-1 text-sm opacity-90">{message.description}</p>
          )}
        </div>
      )}
    </AdminToastContext.Provider>
  )
}

export function useAdminToast() {
  const context = useContext(AdminToastContext)
  if (!context) {
    throw new Error("useAdminToast must be used within AdminToastProvider")
  }
  return context
}
