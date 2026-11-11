import { statusLabel } from '../../lib/orderTypes'

const STEPS: { label: string; statuses: string[] }[] = [
  { label: 'Order placed', statuses: ['ORDER_PLACED'] }, { label: 'Tailor assigned', statuses: ['TAILOR_ASSIGNED'] },
  { label: 'Fabric sample submitted', statuses: ['FABRIC_SAMPLE_SUBMITTED'] }, { label: 'Fabric approved', statuses: ['FABRIC_APPROVED'] },
  { label: 'Cutting started', statuses: ['CUTTING_IN_PROGRESS'] }, { label: 'Stitching started', statuses: ['STITCHING_IN_PROGRESS'] },
  { label: 'Quality check', statuses: ['QUALITY_CHECK'] }, { label: 'Ready for dispatch', statuses: ['READY_FOR_DISPATCH'] },
  { label: 'Shipped', statuses: ['SHIPPED'] }, { label: 'Out for delivery', statuses: ['OUT_FOR_DELIVERY'] }, { label: 'Delivered', statuses: ['DELIVERED'] },
]

/** Progress derived from the order's recorded history, so a step is "done" only when it really happened. */
export function TrackingStepper({ status, timeline }: { status: string; timeline: { status: string; at: string }[] }) {
  if (status === 'CANCELLED' || status === 'REFUND_PENDING' || status === 'REFUNDED')
    return <p className="rounded-lg bg-surface p-4 text-sm font-semibold text-ink" role="status">This order is {statusLabel(status).toLowerCase()}.</p>
  const done = STEPS.map((s) => timeline.find((t) => s.statuses.includes(t.status)))
  const currentIndex = done.findIndex((d) => !d)
  return (
    <section className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10" aria-labelledby="track-heading"><h2 id="track-heading" className="text-lg font-bold">Order tracking</h2>
      <ol className="mt-4">{STEPS.map((s, i) => {
        const d = done[i]; const isCurrent = i === (currentIndex === -1 ? STEPS.length - 1 : currentIndex)
        return (
          <li key={s.label} className="relative flex gap-3 pb-4 last:pb-0" aria-current={isCurrent && currentIndex !== -1 ? 'step' : undefined}>
            {i < STEPS.length - 1 && <span className={`absolute left-[0.6rem] top-5 h-full w-0.5 ${d ? 'bg-primary' : 'bg-ink/15'}`} aria-hidden />}
            <span className={`relative z-10 mt-0.5 grid h-5 w-5 shrink-0 place-items-center rounded-full text-[10px] font-bold ${d ? 'bg-primary text-white' : isCurrent ? 'bg-ink text-white ring-4 ring-ink/15' : 'bg-ink/15 text-transparent'}`} aria-hidden>{d ? '✓' : '•'}</span>
            <div><div className={`text-sm ${d || isCurrent ? 'font-semibold text-ink' : 'text-body/60'}`}>{s.label}{isCurrent && currentIndex !== -1 && <span className="ml-2 rounded-full bg-ink px-2 py-0.5 text-[10px] font-semibold text-white">Now</span>}</div>
              {d && <time className="text-xs text-body/70">{new Date(d.at).toLocaleString()}</time>}<span className="sr-only">{d ? ' (done)' : isCurrent ? ' (current)' : ' (upcoming)'}</span></div>
          </li>)
      })}</ol></section>
  )
}
