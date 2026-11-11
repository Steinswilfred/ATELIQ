import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EmptyState } from '../../components/ui/EmptyState'
import { FormDialog } from '../../components/ui/FormDialog'
import { Pagination } from '../../components/ui/Pagination'
import { Skeleton } from '../../components/ui/Skeleton'
import { ApiError, get, post } from '../../lib/api'
import { money } from '../../lib/format'
import { statusLabel } from '../../lib/orderTypes'
import type { PageResult } from '../../lib/types'
import { useAsync } from '../../lib/useAsync'
import { useAuth } from '../../store/authStore'
import { toast } from '../../store/toastStore'

interface RefundRow { id: number; orderId: number; orderNumber: string; kind: string; amount: number; feeRetained: number; status: string; method: string; reason?: string; requestedBy: string; approvedBy?: string; failureReason?: string; reverseTailorEarning: boolean; createdAt: string; processedAt?: string }
const TABS = [['REQUESTED', 'To approve'], ['APPROVED', 'Approved'], ['PROCESSING', 'Processing'], ['FAILED', 'Failed'], ['PROCESSED', 'Refunded'], ['REJECTED', 'Rejected'], ['', 'All']] as const

export default function AdminRefunds() {
  const can = useAuth((s) => s.can)
  const [status, setStatus] = useState('REQUESTED'); const [page, setPage] = useState(0)
  const { data, loading, error, reload } = useAsync(() => get<PageResult<RefundRow>>(`/admin/refunds?status=${status}&page=${page}&size=20`), [status, page])
  const [dlg, setDlg] = useState<{ kind: 'approve' | 'reject' | 'manual' | 'retry'; r: RefundRow } | null>(null)
  const act = async (path: string, body: unknown, ok: string) => { try { await post(`/admin/refunds/${dlg!.r.id}/${path}`, body); toast.success(ok); setDlg(null); reload() } catch (e) { toast.error((e as ApiError).message); setDlg(null); reload() } }
  const manage = can('REFUND_APPROVE')
  return (
    <div>
      <h1 className="text-3xl font-bold">Refunds</h1>
      <p className="mt-1 text-sm">Refunds an administrator requested need approval from a different administrator. Cash-on-delivery refunds are paid by bank transfer and recorded here.</p>
      <div className="mt-4 flex flex-wrap gap-2">{TABS.map(([v, l]) => <Button key={l} variant={status === v ? 'dark' : 'ghost'} onClick={() => { setStatus(v); setPage(0) }}>{l}</Button>)}</div>
      <div className="mt-4 overflow-x-auto rounded-xl bg-canvas ring-1 ring-ink/10">
        {error ? <EmptyState title="Could not load refunds" hint={error.message} /> : loading ? <div className="space-y-2 p-4"><Skeleton className="h-8" /><Skeleton className="h-8" /></div> : data!.items.length === 0 ? <EmptyState title="No refunds here" />
          : <table className="w-full text-left text-sm"><thead className="border-b border-ink/10 text-xs text-body/70"><tr><th className="p-3">Order</th><th>Amount</th><th>Type</th><th>Requested by</th><th>Status</th><th>Date</th><th /></tr></thead>
            <tbody>{data!.items.map((r) => (
              <tr key={r.id} className="border-b border-ink/5 align-top"><td className="p-3"><Link to={`/admin/orders/${r.orderId}`} className="font-semibold text-ink hover:text-primary">{r.orderNumber}</Link><div className="max-w-xs text-xs">{r.reason}</div></td>
                <td>{money(r.amount)}{r.feeRetained > 0 && <div className="text-xs">fee kept {money(r.feeRetained)}</div>}</td><td>{r.kind === 'CANCELLATION' ? 'Customer cancellation' : 'Admin'} · {r.method === 'MANUAL' ? 'by hand' : 'Razorpay'}{r.reverseTailorEarning ? ' · reverses tailor earning' : ''}</td>
                <td>{r.requestedBy}</td><td>{statusLabel(r.status)}{r.failureReason && <div className="max-w-xs text-xs text-primary">{r.failureReason}</div>}</td><td>{new Date(r.createdAt).toLocaleDateString()}</td>
                <td className="space-x-3 whitespace-nowrap p-3 text-right font-semibold">{manage && r.status === 'REQUESTED' && <button className="text-primary" onClick={() => setDlg({ kind: 'approve', r })}>Approve</button>}
                  {manage && r.status === 'FAILED' && <button className="text-primary" onClick={() => setDlg({ kind: 'retry', r })}>Retry</button>}
                  {manage && r.status === 'APPROVED' && r.method === 'MANUAL' && <button className="text-primary" onClick={() => setDlg({ kind: 'manual', r })}>Record payment</button>}
                  {manage && ['REQUESTED', 'APPROVED', 'FAILED'].includes(r.status) && <button className="text-ink/70 hover:text-primary" onClick={() => setDlg({ kind: 'reject', r })}>Reject</button>}</td></tr>))}</tbody></table>}
      </div>
      {data && <Pagination page={data.page} size={data.size} total={data.total} onPage={setPage} />}
      <ConfirmDialog open={dlg?.kind === 'approve'} title="Approve this refund?" message={dlg ? `${money(dlg.r.amount)} will be refunded for ${dlg.r.orderNumber}${dlg.r.method === 'RAZORPAY' ? ' through Razorpay right away.' : '. You will then pay it by hand and record the reference.'}` : ''} confirmLabel="Approve refund" onConfirm={() => act('approve', undefined, 'Approved.')} onCancel={() => setDlg(null)} />
      <ConfirmDialog open={dlg?.kind === 'retry'} title="Retry this refund?" message="The refund is sent to the payment provider again." confirmLabel="Retry" onConfirm={() => act('retry', undefined, 'Retrying.')} onCancel={() => setDlg(null)} />
      <FormDialog open={dlg?.kind === 'reject'} title="Reject this refund" submitLabel="Reject" fields={[{ name: 'reason', label: 'Reason (the customer is told)', type: 'textarea', required: true }]} onCancel={() => setDlg(null)} onSubmit={async (v) => act('reject', { reason: v.reason }, 'Rejected.')} />
      <FormDialog open={dlg?.kind === 'manual'} title="Record the manual payment" submitLabel="Mark refunded" fields={[{ name: 'reference', label: 'Bank transfer reference', required: true, hint: 'Pay the customer first, then enter the reference.' }]} onCancel={() => setDlg(null)} onSubmit={async (v) => act('manual-processed', { reference: v.reference }, 'Recorded.')} />
    </div>
  )
}
