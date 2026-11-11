import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EmptyState } from '../../components/ui/EmptyState'
import { Pagination } from '../../components/ui/Pagination'
import { Skeleton } from '../../components/ui/Skeleton'
import { ApiError, get, post } from '../../lib/api'
import { money } from '../../lib/format'
import { statusLabel } from '../../lib/orderTypes'
import type { PageResult } from '../../lib/types'
import { useAsync } from '../../lib/useAsync'
import { useAuth } from '../../store/authStore'
import { toast } from '../../store/toastStore'
import { selectClass } from './AdminUsers'

interface PaymentRow { id: number; orderId: number; orderNumber: string; customerName: string; method: string; status: string; amount: number; providerOrderId?: string; providerPaymentId?: string; instrument?: string; failureReason?: string; paidAt?: string; createdAt: string }
interface Detail { payment: PaymentRow; history: { type: string; amount?: number; providerRef?: string; detail?: string; at: string }[] }
const TABS = [['', 'All'], ['SUCCESS', 'Successful'], ['PENDING', 'Pending'], ['FAILED', 'Failed'], ['PENDING_COLLECTION', 'COD to collect']] as const

export function AdminPaymentList() {
  const [page, setPage] = useState(0); const [status, setStatus] = useState(''); const [method, setMethod] = useState(''); const [q, setQ] = useState(''); const [query, setQuery] = useState('')
  const { data, loading, error } = useAsync(() => get<PageResult<PaymentRow>>(`/admin/payments?page=${page}&size=20&status=${status}&method=${method}&q=${encodeURIComponent(query)}`), [page, status, method, query])
  return (
    <div>
      <h1 className="text-3xl font-bold">Payments</h1>
      <div className="mt-4 flex flex-wrap items-center gap-2">{TABS.map(([v, l]) => <Button key={l} variant={status === v ? 'dark' : 'ghost'} onClick={() => { setStatus(v); setPage(0) }}>{l}</Button>)}
        <select value={method} onChange={(e) => { setMethod(e.target.value); setPage(0) }} className={selectClass} aria-label="Method"><option value="">Any method</option><option value="RAZORPAY">Online (Razorpay)</option><option value="COD">Cash on delivery</option></select>
        <form className="ml-auto" onSubmit={(e) => { e.preventDefault(); setQuery(q); setPage(0) }}><input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Order or Razorpay id" aria-label="Search payments" className={`${selectClass} w-56`} /></form></div>
      <div className="mt-4 overflow-x-auto rounded-xl bg-canvas ring-1 ring-ink/10">
        {error ? <EmptyState title="Could not load payments" hint={error.message} /> : loading ? <div className="space-y-2 p-4"><Skeleton className="h-8" /><Skeleton className="h-8" /></div> : data!.items.length === 0 ? <EmptyState title="No payments match" />
          : <table className="w-full text-left text-sm"><thead className="border-b border-ink/10 text-xs text-body/70"><tr><th className="p-3">Order</th><th>Customer</th><th>Method</th><th>Status</th><th>Amount</th><th>Razorpay payment</th><th>Date</th></tr></thead>
            <tbody>{data!.items.map((p) => (<tr key={p.id} className="border-b border-ink/5 hover:bg-surface"><td className="p-3"><Link to={`/admin/payments/${p.id}`} className="font-semibold text-ink hover:text-primary">{p.orderNumber}</Link></td><td>{p.customerName}</td>
              <td>{p.method === 'COD' ? 'COD' : 'Online'}{p.instrument ? ` (${p.instrument})` : ''}</td><td>{statusLabel(p.status)}</td><td>{money(p.amount)}</td><td className="text-xs">{p.providerPaymentId ?? '—'}</td><td>{new Date(p.createdAt).toLocaleDateString()}</td></tr>))}</tbody></table>}
      </div>
      {data && <Pagination page={data.page} size={data.size} total={data.total} onPage={setPage} />}
    </div>
  )
}

export function AdminPaymentDetail() {
  const { id } = useParams()
  const can = useAuth((s) => s.can)
  const { data, loading, error, reload } = useAsync(() => get<Detail>(`/admin/payments/${id}`), [id])
  const [confirm, setConfirm] = useState(false)
  const collect = async () => { try { await post(`/admin/payments/${id}/collect-cod`); toast.success('Marked as collected.'); reload() } catch (e) { toast.error((e as ApiError).message) } setConfirm(false) }
  if (loading) return <Skeleton className="h-64" />
  if (error || !data) return <EmptyState title="Payment not found" hint={error?.message} />
  const p = data.payment
  return (
    <div className="max-w-3xl space-y-6">
      <Link to="/admin/payments" className="text-sm font-semibold text-primary">← All payments</Link>
      <div className="flex flex-wrap items-start justify-between gap-3"><div><h1 className="text-3xl font-bold">{money(p.amount)}</h1><p className="mt-1 text-sm">{p.method === 'COD' ? 'Cash on delivery' : 'Online'} · {statusLabel(p.status)} · <Link className="font-semibold text-primary" to={`/admin/orders/${p.orderId}`}>Order {p.orderNumber}</Link></p></div>
        {p.method === 'COD' && p.status === 'PENDING_COLLECTION' && can('PAYMENT_COD_COLLECT') && <Button onClick={() => setConfirm(true)}>Mark cash collected</Button>}</div>
      <section className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10"><dl className="grid gap-3 text-sm sm:grid-cols-2">
        <div><dt className="text-xs text-body/70">Customer</dt><dd>{p.customerName}</dd></div><div><dt className="text-xs text-body/70">Razorpay order</dt><dd className="break-all">{p.providerOrderId ?? '—'}</dd></div>
        <div><dt className="text-xs text-body/70">Razorpay payment</dt><dd className="break-all">{p.providerPaymentId ?? '—'}</dd></div><div><dt className="text-xs text-body/70">Paid at</dt><dd>{p.paidAt ? new Date(p.paidAt).toLocaleString() : '—'}</dd></div>
        {p.failureReason && <div className="sm:col-span-2"><dt className="text-xs text-body/70">Failure reason</dt><dd>{p.failureReason}</dd></div>}</dl></section>
      <section className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10"><h2 className="text-lg font-bold">History</h2>
        <ul className="mt-3 divide-y divide-ink/10 text-sm">{data.history.map((h, i) => <li key={i} className="flex flex-wrap justify-between gap-2 py-2"><span><strong>{statusLabel(h.type)}</strong>{h.detail ? ` · ${h.detail}` : ''}</span><time className="text-body/70">{new Date(h.at).toLocaleString()}</time></li>)}</ul></section>
      <p className="text-xs">Refunds and settlement reports arrive with the financial module.</p>
      <ConfirmDialog open={confirm} title="Mark cash as collected?" message="Only do this once the delivery agent has handed over the money. This is recorded in the audit log." confirmLabel="Mark collected" onConfirm={collect} onCancel={() => setConfirm(false)} />
    </div>
  )
}
