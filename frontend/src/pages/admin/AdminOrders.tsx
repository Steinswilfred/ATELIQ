import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { AssignmentPanel } from '../../components/admin/AssignmentPanel'
import { OrderMoneyActions } from '../../components/admin/OrderMoneyActions'
import { DeliveryPanel } from '../../components/shop/DeliveryPanel'
import { OrderView } from '../../components/shop/OrderView'
import { SamplePanel } from '../../components/shop/SamplePanel'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Pagination } from '../../components/ui/Pagination'
import { Skeleton } from '../../components/ui/Skeleton'
import { get } from '../../lib/api'
import { money } from '../../lib/format'
import { OrderDetailT, OrderSummaryT, statusLabel } from '../../lib/orderTypes'
import type { PageResult } from '../../lib/types'
import { useAsync } from '../../lib/useAsync'
import { openInvoice } from '../account/OrderDetail'
import { selectClass } from './AdminUsers'

const STATUSES = ['ORDER_PLACED', 'PAYMENT_PENDING', 'PAYMENT_CONFIRMED', 'TAILOR_ASSIGNMENT_PENDING', 'TAILOR_ASSIGNED', 'FABRIC_SAMPLE_PENDING', 'FABRIC_SAMPLE_SUBMITTED', 'CUSTOMER_APPROVAL_PENDING', 'FABRIC_APPROVED', 'FABRIC_REJECTED', 'CUTTING_IN_PROGRESS', 'STITCHING_IN_PROGRESS', 'QUALITY_CHECK', 'READY_FOR_DISPATCH', 'SHIPPED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED', 'REFUND_PENDING', 'REFUNDED']

export function AdminOrderList() {
  const [page, setPage] = useState(0); const [status, setStatus] = useState(''); const [q, setQ] = useState(''); const [query, setQuery] = useState('')
  const { data, loading, error } = useAsync(() => get<PageResult<OrderSummaryT>>(`/admin/orders?page=${page}&size=20&status=${status}&q=${encodeURIComponent(query)}`), [page, status, query])
  return (
    <div>
      <h1 className="text-3xl font-bold">Orders</h1>
      <form className="mt-4 flex flex-wrap gap-2" onSubmit={(e) => { e.preventDefault(); setQuery(q); setPage(0) }}>
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Order number, name or phone" aria-label="Search orders" className={`${selectClass} w-64`} />
        <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }} className={selectClass} aria-label="Status"><option value="">All statuses</option>{STATUSES.map((s) => <option key={s} value={s}>{statusLabel(s)}</option>)}</select>
        <Button type="submit" variant="dark">Search</Button></form>
      <div className="mt-4 overflow-x-auto rounded-xl bg-canvas ring-1 ring-ink/10">
        {error ? <EmptyState title="Could not load orders" hint={error.message} /> : loading ? <div className="space-y-2 p-4"><Skeleton className="h-8" /><Skeleton className="h-8" /></div> : data!.items.length === 0 ? <EmptyState title="No orders yet" />
          : <table className="w-full text-left text-sm"><thead className="border-b border-ink/10 text-xs text-body/70"><tr><th className="p-3">Order</th><th>Customer</th><th>Status</th><th>Payment</th><th>Total</th><th>Placed</th></tr></thead>
            <tbody>{data!.items.map((o) => (<tr key={o.id} className="border-b border-ink/5 hover:bg-surface"><td className="p-3"><Link to={`/admin/orders/${o.id}`} className="font-semibold text-ink hover:text-primary">{o.orderNumber}</Link><div className="text-xs">{o.firstItemName}{o.garmentCount > 1 ? ` +${o.garmentCount - 1}` : ''}</div></td>
              <td>{o.customerName}</td><td>{statusLabel(o.status)}</td><td>{o.paymentMethod === 'COD' ? 'COD' : 'Online'} · {statusLabel(o.paymentStatus)}</td><td>{money(o.total)}</td><td>{new Date(o.placedAt).toLocaleDateString()}</td></tr>))}</tbody></table>}
      </div>
      {data && <Pagination page={data.page} size={data.size} total={data.total} onPage={setPage} />}
    </div>
  )
}

export function AdminOrderDetail() {
  const { id } = useParams()
  const { data, loading, error, reload } = useAsync(() => get<OrderDetailT>(`/admin/orders/${id}`), [id])
  if (loading && !data) return <Skeleton className="h-64" />
  if (error || !data) return <EmptyState title="Order not found" hint={error?.message} />
  return (
    <div className="max-w-4xl space-y-6">
      <Link to="/admin/orders" className="text-sm font-semibold text-primary">← All orders</Link>
      <div className="flex flex-wrap items-start justify-between gap-3"><div><h1 className="text-3xl font-bold">{data.orderNumber}</h1><p className="mt-1 text-sm">{data.customerName} · {data.customerEmail} · {statusLabel(data.status)}</p></div>
        {data.invoiceNumber && <Button variant="ghost" onClick={() => openInvoice(`/admin/orders/${data.id}/invoice`)}>Invoice</Button>}</div>
      <AssignmentPanel orderId={data.id} orderKey={data.status} onChanged={reload} />
      <SamplePanel viewer="admin" orderId={data.id} orderStatus={data.status} />
      <DeliveryPanel viewer="admin" orderId={data.id} orderStatus={data.status} onChanged={reload} />
      <OrderMoneyActions orderId={data.id} status={data.status} onChanged={reload} />
      <p className="rounded-lg bg-surface p-3 text-sm">Only the customer can approve fabric. Cutting, stitching and quality steps are done by the tailor.</p>
      <OrderView o={data} />
    </div>
  )
}
