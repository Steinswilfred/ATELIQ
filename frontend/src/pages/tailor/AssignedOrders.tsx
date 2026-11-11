import { Link } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Skeleton } from '../../components/ui/Skeleton'
import { get } from '../../lib/api'
import { money } from '../../lib/format'
import { statusLabel } from '../../lib/orderTypes'
import type { TailorOrderRowT } from '../../lib/tailorOrderTypes'
import { useAsync } from '../../lib/useAsync'

export default function AssignedOrders({ scope = 'active' }: { scope?: 'active' | 'completed' }) {
  const { data, loading, error } = useAsync(() => get<TailorOrderRowT[]>(`/tailor/orders?scope=${scope}`), [scope])
  return (
    <div className="max-w-3xl space-y-6">
      <h1 className="text-3xl font-bold">{scope === 'active' ? 'Assigned orders' : 'Completed orders'}</h1>
      {error ? <EmptyState title="Could not load your orders" hint={error.message} /> : loading ? <Skeleton className="h-40" /> : (data ?? []).length === 0
        ? <EmptyState title={scope === 'active' ? 'No orders assigned to you' : 'Nothing completed yet'} hint={scope === 'active' ? 'Accept an order from Available orders.' : 'Delivered orders appear here.'} action={scope === 'active' ? <Link to="/tailor/available"><Button>See available orders</Button></Link> : undefined} />
        : <ul className="space-y-3">{data!.map((o) => (
          <li key={o.id}><Link to={`/tailor/assigned/${o.id}`} className="block rounded-xl bg-canvas p-4 ring-1 ring-ink/10 transition hover:ring-primary">
            <div className="flex justify-between gap-2"><strong className="text-ink">{o.orderNumber}</strong><span className="font-semibold text-ink">{money(o.stitchingCharge)}</span></div>
            <p className="text-sm">{o.firstItem}{o.garmentCount > 1 ? ` + ${o.garmentCount - 1} more` : ''} · {o.customerFirstName}{o.city ? `, ${o.city}` : ''}</p>
            <p className="text-xs"><strong>{statusLabel(o.status)}</strong> · placed {new Date(o.placedAt).toLocaleDateString()}</p></Link></li>))}</ul>}
    </div>
  )
}
