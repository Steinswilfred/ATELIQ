import { useState } from 'react'
import { ApiError, get, post } from '../../lib/api'
import { money } from '../../lib/format'
import { statusLabel } from '../../lib/orderTypes'
import { useAsync } from '../../lib/useAsync'
import { toast } from '../../store/toastStore'
import { Button } from '../ui/Button'
import { PromptDialog } from '../ui/PromptDialog'

interface Preview { allowed: boolean; message: string; paid: number; refund: number; fee: number; stage: string }
interface RefundT { id: number; amount: number; feeRetained: number; status: string; method: string; reason?: string; failureReason?: string; createdAt: string; processedAt?: string }
const CLOSED = ['CANCELLED', 'REFUND_PENDING', 'REFUNDED', 'DELIVERED']

/** Cancel button with the exact refund shown first. The server re-checks every rule when you confirm. */
export function CancelOrderPanel({ orderId, status, onChanged }: { orderId: number; status: string; onChanged: () => void }) {
  const closed = CLOSED.includes(status) || ['SHIPPED', 'OUT_FOR_DELIVERY'].includes(status)
  const { data } = useAsync(() => (closed ? Promise.resolve(null) : get<Preview>(`/me/orders/${orderId}/cancellation`)), [orderId, status])
  const [open, setOpen] = useState(false)
  if (!data) return null
  if (!data.allowed) return <p className="rounded-lg bg-surface p-3 text-sm">{data.message}</p>
  const desc = `${data.message}${data.paid > 0 ? ` You paid ${money(data.paid)}; you will get back ${money(data.refund)}${data.fee > 0 ? ` (fee kept: ${money(data.fee)})` : ''}.` : ''}`
  return (
    <>
      <Button variant="ghost" onClick={() => setOpen(true)}>Cancel order</Button>
      <PromptDialog open={open} danger title="Cancel this order?" description={desc} fields={[{ name: 'reason', label: 'Why are you cancelling? (optional)', multiline: true }]} submitLabel="Cancel order" onCancel={() => setOpen(false)}
        onSubmit={async (v) => { try { await post(`/me/orders/${orderId}/cancel`, { reason: v.reason || null }); toast.success('Order cancelled.'); setOpen(false); onChanged() } catch (e) { toast.error((e as ApiError).message); setOpen(false); onChanged() } }} />
    </>
  )
}

export function RefundsPanel({ orderId, status }: { orderId: number; status: string }) {
  const { data } = useAsync(() => get<RefundT[]>(`/me/orders/${orderId}/refunds`), [orderId, status])
  if (!data || data.length === 0) return null
  return (
    <section className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10" aria-labelledby="refund-heading"><h2 id="refund-heading" className="text-lg font-bold">Refund</h2>
      <ul className="mt-3 space-y-3 text-sm">{data.map((r) => (
        <li key={r.id}><div className="flex justify-between gap-2"><strong className="text-ink">{money(r.amount)}</strong><span className="font-semibold text-ink">{r.status === 'PROCESSED' ? 'Refunded' : r.status === 'REJECTED' ? 'Not approved' : r.status === 'FAILED' ? 'Being retried' : 'In progress'}</span></div>
          {r.feeRetained > 0 && <div className="text-xs">Late-cancellation fee kept: {money(r.feeRetained)}</div>}
          <div className="text-xs">{r.processedAt ? `Refunded ${new Date(r.processedAt).toLocaleString()}. It can take a few days to show in your account.` : r.status === 'REJECTED' ? (r.failureReason ?? '') : 'We will notify you when it is done.'}{r.method === 'MANUAL' && r.status !== 'PROCESSED' ? ' Cash-on-delivery refunds are paid by bank transfer by our team.' : ''}</div></li>))}</ul></section>
  )
}

export const refundStatusLabel = (s: string) => statusLabel(s)
