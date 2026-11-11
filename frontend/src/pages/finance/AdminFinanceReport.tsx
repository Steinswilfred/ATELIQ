import { useState } from 'react'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Skeleton } from '../../components/ui/Skeleton'
import { get } from '../../lib/api'
import { money } from '../../lib/format'
import { useAsync } from '../../lib/useAsync'
import { selectClass } from '../admin/AdminUsers'

interface Report { from: string; to: string; accounts: Record<string, number>; platformRevenue: number; outstandingTailorPayable: number; deliveredOrders: number; cancelledOrders: number; refundsProcessed: number; refundedTotal: number; daily: { day: string; account: string; amount: number }[] }
const NAMES: Record<string, string> = { CUSTOMER_PAYMENTS: 'Customer payments (net of refunds paid)', ITEM_REVENUE: 'Item revenue (after discounts)', DELIVERY_REVENUE: 'Delivery charges', PLATFORM_COMMISSION: 'Platform commission', TAX_COLLECTED: 'Tax collected (owed to the tax authority)', TAILOR_PAYABLE: 'Tailor payable (net movement)', REVENUE_REFUNDED: 'Revenue refunded after delivery' }
const iso = (d: Date) => d.toISOString().slice(0, 10)

export default function AdminFinanceReport() {
  const [from, setFrom] = useState(iso(new Date(Date.now() - 29 * 86400000))); const [to, setTo] = useState(iso(new Date()))
  const { data, loading, error } = useAsync(() => get<Report>(`/admin/reports/finance?from=${from}&to=${to}`), [from, to])
  const csv = () => {
    if (!data) return
    const rows = [['date', 'account', 'amount_inr'], ...data.daily.map((d) => [d.day, d.account, d.amount.toFixed(2)])]
    const blob = new Blob([rows.map((r) => r.join(',')).join('\n')], { type: 'text/csv' }); const a = document.createElement('a')
    a.href = URL.createObjectURL(blob); a.download = `finance-${data.from}-to-${data.to}.csv`; a.click(); URL.revokeObjectURL(a.href)
  }
  const tile = (label: string, v: string, hint?: string) => <div className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10"><div className="text-2xl font-bold text-ink">{v}</div><div className="mt-1 text-sm">{label}</div>{hint && <div className="text-xs text-body/70">{hint}</div>}</div>
  return (
    <div className="max-w-5xl space-y-6">
      <h1 className="text-3xl font-bold">Financial report</h1>
      <div className="flex flex-wrap items-end gap-3"><label className="text-sm font-semibold text-ink">From<input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} className={`${selectClass} mt-1 block`} /></label>
        <label className="text-sm font-semibold text-ink">To<input type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} className={`${selectClass} mt-1 block`} /></label><Button variant="ghost" onClick={csv} disabled={!data}>Download CSV</Button></div>
      {error ? <EmptyState title="Could not load the report" hint={error.message} /> : loading || !data ? <Skeleton className="h-64" /> : (<>
        <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {tile('Platform revenue', money(data.platformRevenue), 'items + delivery + commission − refunds after delivery')}{tile('Platform commission', money(data.accounts.PLATFORM_COMMISSION ?? 0))}
          {tile('Net customer payments', money(data.accounts.CUSTOMER_PAYMENTS ?? 0), 'received minus refunded')}{tile('Tax collected', money(data.accounts.TAX_COLLECTED ?? 0))}
          {tile('Owed to tailors, all time', money(data.outstandingTailorPayable), 'earned but not yet paid out')}{tile('Refunds processed', money(data.refundedTotal), `${data.refundsProcessed} refund(s)`)}</section>
        <p className="text-sm">{data.deliveredOrders} order(s) delivered and {data.cancelledOrders} cancelled in this period. Revenue is counted when an order is delivered; payments are counted when received.</p>
        <section className="overflow-x-auto rounded-xl bg-canvas ring-1 ring-ink/10"><table className="w-full text-left text-sm"><caption className="p-3 text-left text-lg font-bold text-ink">By account</caption><tbody>{Object.entries(data.accounts).map(([k, v]) => <tr key={k} className="border-t border-ink/5"><th scope="row" className="p-3 font-normal">{NAMES[k] ?? k}</th><td className="p-3 text-right font-semibold text-ink">{money(v)}</td></tr>)}</tbody></table></section>
        <p className="text-xs">Daily detail is in the CSV. More reports, charts and PDF export arrive with the analytics release.</p></>)}
    </div>
  )
}
