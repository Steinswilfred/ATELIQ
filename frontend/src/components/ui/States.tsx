import type { ReactNode } from 'react';

export function EmptyState({ title, hint, action }: { title: string; hint?: string; action?: ReactNode }) {
  return (
    <div className="rounded-card border border-dashed border-ink/20 p-10 text-center">
      <h3 className="text-lg">{title}</h3>
      {hint && <p className="mx-auto mt-1 max-w-sm text-sm text-body/70">{hint}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="rounded-card border border-red-200 bg-red-50 p-6 text-sm text-red-800">
      <p>{message}</p>
      {onRetry && (
        <button onClick={onRetry} className="mt-3 font-semibold underline underline-offset-2">Try again</button>
      )}
    </div>
  );
}
