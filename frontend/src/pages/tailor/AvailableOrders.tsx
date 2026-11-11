import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EmptyState } from '../../components/ui/EmptyState'
import { Skeleton } from '../../components/ui/Skeleton'
import { ApiError, get, post } from '../../lib/api'
import { money } from '../../lib/format'
import type { AvailableOrderT } from '../../lib/tailorOrderTypes'
import { useAsync } from '../../lib/useAsync'
import { Button } from '../../components/ui/Button'
import { toast } from '../../store/toastStore'

/** Refreshes every 15 seconds, so an order another tailor took disappears without a manual reload. */
export default function AvailableOrders() {
  const { data, loading, error, reload } = useAsync(() => get<AvailableOrderT[]>('/tailor/orders/available'), [])
  const navigate = useNavigate()
  const [pick, setPick] = useState<AvailableOrderT | null>(null)
  useEffect(() => { const t = setInterval(reload, 15000); return () => clearInterval(t) }, [reload])

  const accept = async () => {
    const o = pick!; setPick(null)
    try { await post(`/tailor/orders/${o.id}/accept`); toast.success(`Order ${o.orderNumber} is yours.`); navigate(`/tailor/assigned/${o.id}`) }
    catch (e) { toast.error((e as ApiError).message); reload() }
  }

  return (
    <div className="max-w-3xl space-y-6">
      <div><h1 className="text-3xl font-bold">Available orders</h1>
        <p className="mt-1 text-sm">Orders in your category waiting for a tailor. The first tailor to accept gets the order. Customer details and measurements are shown after you accept. Your status must be <Link to="/tailor" className="font-semibold text-primary">Available</Link>.</p></div>
      {error ? <EmptyState title="Could not load orders" hint={error.message} /> : loading && !data ? <Skeleton className="h-48" /> : (data ?? []).length === 0
        ? <EmptyState title="No orders waiting" hint="New orders in your category appear here and in your notifications." />
        : <ul className="space-y-4">{data!.map((o) => (
          <li key={o.id} className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10">
            <div className="flex flex-wrap items-center justify-between gap-2"><div><h2 className="text-lg font-bold text-ink">{o.orderNumber}</h2><p className="text-xs">Placed {new Date(o.placedAt).toLocaleString()} · {o.garmentCount} garment{o.garmentCount === 1 ? '' : 's'}</p></div>
              <div className="text-right"><div className="text-xl font-bold text-ink">{money(o.stitchingCharge)}</div><div className="text-xs">stitching charge</div></div></div>
            <ul className="mt-3 divide-y divide-ink/10">{o.items.map((i, n) => (
              <li key={n} className="flex gap-3 py-2"><div className="h-14 w-12 shrink-0 overflow-hidden rounded bg-surface">{i.imageUrl && <img src={i.imageUrl} alt="" className="h-full w-full object-cover" />}</div>
                <div className="text-sm"><strong className="text-ink">{i.name} × {i.quantity}</strong><p className="text-xs">{[i.fabric, i.color, ...i.selections.map((s) => `${s.group}: ${s.option}`)].filter(Boolean).join(' · ')}</p><p className="text-xs">{i.customMeasurements ? 'Custom measurements (shown after you accept)' : i.sizeCode ? `Standard size ${i.sizeCode}` : ''}</p></div></li>))}</ul>
            <Button className="mt-3" onClick={() => setPick(o)}>Accept this order</Button>
          </li>))}</ul>}
      <p className="text-xs">Commission and payout details appear under Earnings once payouts are enabled.</p>
      <ConfirmDialog open={!!pick} title={`Accept ${pick?.orderNumber}?`} message="You will be responsible for this order: preparing the fabric sample for the customer's approval, then cutting and stitching. Only accept what you can complete." confirmLabel="Accept order" onConfirm={accept} onCancel={() => setPick(null)} />
    </div>
  )
}
