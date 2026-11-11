import { EmptyState } from '../../components/ui/EmptyState'
import { Skeleton } from '../../components/ui/Skeleton'
import { get } from '../../lib/api'
import { money } from '../../lib/format'
import { useAsync } from '../../lib/useAsync'

interface Data { summary: { pending: number; payable: number; paid: number; commissionRatePercent: number }
  earnings: { id: number; orderNumber: string; gross: number; commissionRate: number; commission: number; net: number; status: string; accruedAt: string; payableAfter: string; payable: boolean }[]
  payouts: { id: number; amount: number; reference: string; paidAt: string }[] }

export default function TailorEarnings() {
  const { data, loading, error } = useAsync(() => get<Data>('/tailor/earnings'), [])
  if (error) return <EmptyState title="Could not load your earnings" hint={error.message} />
  if (loading || !data) return <Skeleton className="h-64" />
  const s = data.summary
  const state = (e: Data['earnings'][number]) => e.status === 'PAID' ? 'Paid' : e.status === 'REVERSED' ? 'Reversed (order refunded)' : e.payable ? 'Ready to be paid' : `Held until ${new Date(e.payableAfter).toLocaleDateString()}`
  return (
    <div className="max-w-4xl space-y-6">
      <div><h1 className="text-3xl font-bold">Earnings</h1><p className="mt-1 text-sm">You earn the stitching charge minus a {s.commissionRatePercent}% platform commission, once the order is delivered. Payments are made by bank transfer after a short holding period.</p></div>
      <section className="grid gap-4 sm:grid-cols-3">{[['In holding period', s.pending], ['Ready to be paid', s.payable], ['Paid so far', s.paid]].map(([l, v]) => <div key={l as string} className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10"><div className="text-2xl font-bold text-ink">{money(v as number)}</div><div className="mt-1 text-sm">{l}</div></div>)}</section>
      <section className="overflow-x-auto rounded-xl bg-canvas ring-1 ring-ink/10">{data.earnings.length === 0 ? <p className="p-5 text-sm">No earnings yet. They appear after your first delivered order.</p>
        : <table className="w-full text-left text-sm"><caption className="p-3 text-left text-lg font-bold text-ink">By order</caption><thead className="border-b border-ink/10 text-xs text-body/70"><tr><th className="p-3">Order</th><th>Stitching charge</th><th>Commission</th><th>You earn</th><th>Status</th></tr></thead>
          <tbody>{data.earnings.map((e) => <tr key={e.id} className="border-b border-ink/5"><td className="p-3 font-semibold text-ink">{e.orderNumber}</td><td>{money(e.gross)}</td><td>−{money(e.commission)} ({e.commissionRate}%)</td><td className="font-semibold text-ink">{money(e.net)}</td><td>{state(e)}</td></tr>)}</tbody></table>}</section>
      {data.payouts.length > 0 && <section className="overflow-x-auto rounded-xl bg-canvas ring-1 ring-ink/10"><table className="w-full text-left text-sm"><caption className="p-3 text-left text-lg font-bold text-ink">Payments received</caption><tbody>{data.payouts.map((p) => <tr key={p.id} className="border-t border-ink/5"><td className="p-3">{new Date(p.paidAt).toLocaleDateString()}</td><td className="font-semibold text-ink">{money(p.amount)}</td><td>Ref {p.reference}</td></tr>)}</tbody></table></section>}
    </div>
  )
}
