const tone: Record<string, string> = {
  ACTIVE: 'bg-emerald-100 text-emerald-800', APPROVED: 'bg-emerald-100 text-emerald-800',
  PENDING_APPROVAL: 'bg-amber-100 text-amber-800', PENDING_EMAIL_VERIFICATION: 'bg-amber-100 text-amber-800',
  PENDING_PHONE_VERIFICATION: 'bg-amber-100 text-amber-800',
  REJECTED: 'bg-primary/15 text-primary', BLOCKED: 'bg-primary/15 text-primary', SUSPENDED: 'bg-orange-100 text-orange-800',
  DEACTIVATED: 'bg-ink/10 text-ink',
}
export function StatusBadge({ status }: { status: string }) {
  return <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ${tone[status] ?? 'bg-ink/10 text-ink'}`}>{status.replace(/_/g, ' ').toLowerCase()}</span>
}
