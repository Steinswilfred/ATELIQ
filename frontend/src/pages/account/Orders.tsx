import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Pagination } from '../../components/ui/Pagination'
import { Skeleton } from '../../components/ui/Skeleton'
import { get } from '../../lib/api'
import { money } from '../../lib/format'
import { OrderSummaryT, statusLabel } from '../../lib/orderTypes'
import type { PageResult } from '../../lib/types'
import { useAsync } from '../../lib/useAsync'

export default function Orders() {
  const [page, setPage] = useState(0)
  const { data, loading, error } = useAsync(() => get<PageResult<OrderSummaryT>>(`/me/orders?page=${page}&size=10`), [page])
  return (
    <div>
      <h1 className="text-3xl font-bold">My orders</h1>
      <div className="mt-6">
        {error ? <EmptyState title="Could not load your orders" hint={error.message} /> : loading ? <Skeleton className="h-40" /> : data!.items.length === 0
          ? <EmptyState title="No orders yet" hint="Your orders and their progress will appear here." action={<Link to="/shop"><Button>Browse styles</Button></Link>} />
          : <ul className="space-y-3">{data!.items.map((o) => (
            <li key={o.id}><Link to={`/account/orders/${o.id}`} className="flex items-center gap-4 rounded-xl bg-canvas p-4 ring-1 ring-ink/10 transition hover:ring-primary">
              <div className="h-16 w-14 shrink-0 overflow-hidden rounded-lg bg-surface">{o.firstItemImage && <img src={o.firstItemImage} alt="" className="h-full w-full object-cover" />}</div>
              <div className="min-w-0 flex-1"><div className="flex justify-between gap-2"><span className="font-bold text-ink">{o.orderNumber}</span><span className="font-semibold text-ink">{money(o.total)}</span></div>
                <p className="truncate text-sm">{o.firstItemName}{o.garmentCount > 1 ? ` + ${o.garmentCount - 1} more` : ''}</p>
                <p className="text-xs">{statusLabel(o.status)} · {new Date(o.placedAt).toLocaleDateString()} · {o.paymentMethod === 'COD' ? 'Cash on delivery' : 'Online'} ({statusLabel(o.paymentStatus).toLowerCase()})</p></div></Link></li>))}</ul>}
      </div>
      {data && <Pagination page={data.page} size={data.size} total={data.total} onPage={setPage} />}
    </div>
  )
}
