import { useState } from 'react'
import { ApiError, post } from '../../lib/api'
import { useAuth } from '../../store/authStore'
import { toast } from '../../store/toastStore'
import { Button } from '../ui/Button'
import { FormDialog } from '../ui/FormDialog'

const NO_CANCEL = ['SHIPPED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED', 'REFUND_PENDING', 'REFUNDED']

/** Admin cancel and refund. Refunds are only requested here: a different administrator approves them under Refunds. */
export function OrderMoneyActions({ orderId, status, onChanged }: { orderId: number; status: string; onChanged: () => void }) {
  const can = useAuth((s) => s.can)
  const [dlg, setDlg] = useState<'cancel' | 'refund' | null>(null)
  const canCancel = can('ORDER_MANAGE') && !NO_CANCEL.includes(status)
  const canRefund = can('REFUND_INITIATE') && ['DELIVERED', 'CANCELLED'].includes(status)
  if (!canCancel && !canRefund) return null
  const done = (msg: string) => { toast.success(msg); setDlg(null); onChanged() }
  return (
    <section className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10"><h2 className="text-lg font-bold">Cancel or refund</h2>
      <p className="mt-1 text-sm">Cancelling releases the tailor. Any refund is requested here and must be approved by a different administrator under Refunds.</p>
      <div className="mt-3 flex flex-wrap gap-2">{canCancel && <Button variant="danger" onClick={() => setDlg('cancel')}>Cancel this order</Button>}{canRefund && <Button variant="ghost" onClick={() => setDlg('refund')}>Request a refund</Button>}</div>
      <FormDialog open={dlg === 'cancel'} title="Cancel this order" submitLabel="Cancel order" onCancel={() => setDlg(null)}
        fields={[{ name: 'reason', label: 'Reason (shown in the history)', type: 'textarea', required: true }, { name: 'refundAmount', label: 'Refund amount (₹)', type: 'number', step: '0.01', hint: 'Leave empty to refund everything the customer paid. Enter 0 for no refund.' }]}
        onSubmit={async (v) => { await post(`/admin/orders/${orderId}/cancel`, { reason: v.reason, refundAmount: String(v.refundAmount ?? '').trim() === '' ? null : Number(v.refundAmount) }); done('Order cancelled.') }} />
      <FormDialog open={dlg === 'refund'} title="Request a refund" submitLabel="Request refund" onCancel={() => setDlg(null)}
        fields={[{ name: 'amount', label: 'Amount (₹)', type: 'number', step: '0.01', required: true }, { name: 'reason', label: 'Reason', type: 'textarea', required: true }, { name: 'reverseTailorEarning', label: 'Also reverse the tailor’s earning for this order', type: 'checkbox' }]}
        onSubmit={async (v) => { try { await post(`/admin/orders/${orderId}/refund`, { amount: Number(v.amount), reason: v.reason, reverseTailorEarning: !!v.reverseTailorEarning }); done('Refund requested. Another administrator must approve it.') } catch (e) { throw e as ApiError } }} />
    </section>
  )
}
