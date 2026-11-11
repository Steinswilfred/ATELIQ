import { ReactNode } from 'react'

export function AuthCard({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return (
    <div className="mx-auto w-full max-w-md px-4 py-12">
      <h1 className="text-3xl font-bold">{title}</h1>
      {subtitle && <p className="mt-2 text-sm">{subtitle}</p>}
      <div className="mt-8">{children}</div>
    </div>
  )
}

export function Field({ label, error, hint, children }: { label: string; error?: string; hint?: string; children: ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-semibold text-ink">{label}</span>
      {children}
      {hint && !error && <span className="mt-1 block text-xs text-body/70">{hint}</span>}
      {error && <span role="alert" className="mt-1 block text-xs font-semibold text-primary">{error}</span>}
    </label>
  )
}

export const inputClass =
  'w-full rounded-lg border border-ink/20 bg-canvas px-3 py-2.5 text-sm focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/30'

export function FormError({ message }: { message?: string | null }) {
  if (!message) return null
  return <div role="alert" className="rounded-lg border border-primary/40 bg-primary/10 px-4 py-3 text-sm text-ink">{message}</div>
}
