import { ReactNode } from 'react'

export function EmptyState({ title, hint, action }: { title: string; hint?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-ink/20 bg-surface px-6 py-14 text-center">
      <h3 className="text-lg font-bold">{title}</h3>
      {hint && <p className="max-w-sm text-sm text-body/80">{hint}</p>}
      {action}
    </div>
  )
}
