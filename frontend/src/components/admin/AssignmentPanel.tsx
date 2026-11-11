import { useState } from 'react'
import { ApiError, get, post } from '../../lib/api'
import { AssignmentInfoT } from '../../lib/tailorOrderTypes'
import { useAsync } from '../../lib/useAsync'
import { useAuth } from '../../store/authStore'
import { toast } from '../../store/toastStore'
import { Button } from '../ui/Button'
import { Skeleton } from '../ui/Skeleton'

const input = 'w-full rounded-lg border border-ink/20 bg-canvas px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/30'

export function AssignmentPanel({ orderId, orderKey, onChanged }: { orderId: number; orderKey: string; onChanged: () => void }) {
  const can = useAuth((s) => s.can)
  const { data, loading, error, reload } = useAsync(() => get<AssignmentInfoT>(`/admin/orders/${orderId}/assignment`), [orderId, orderKey])
  const [tailor, setTailor] = useState(''); const [reason, setReason] = useState(''); const [busy, setBusy] = useState(false)
  if (loading && !data) return <Skeleton className="h-32" />
  if (error || !data) return null
  const pending = data.status === 'TAILOR_ASSIGNMENT_PENDING'
  const picked = data.tailors.find((t) => String(t.userId) === tailor)

  const submit = async (userId: number | null) => {
    setBusy(true)
    try { await post(`/admin/orders/${orderId}/assignment`, { tailorUserId: userId, reason }); toast.success(userId ? 'Tailor assigned.' : 'Order returned to the pool; eligible tailors were notified.'); setReason(''); setTailor(''); reload(); onChanged() }
    catch (e) { toast.error((e as ApiError).message); reload() } finally { setBusy(false) }
  }
  return (
    <section className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10"><h2 className="text-lg font-bold">Tailor assignment</h2>
      <p className="mt-1 text-sm">{data.currentTailorName ? <>Assigned to <strong>{data.currentTailorName}</strong>.</> : pending ? 'Waiting for a tailor to accept. Eligible tailors in the order’s category have been notified.' : 'No tailor assigned.'}</p>
      {data.history.length > 0 && <ul className="mt-3 divide-y divide-ink/10 text-sm">{data.history.map((h, i) => (
        <li key={i} className="py-2"><strong className="text-ink">{h.tailorName}</strong> · {h.method === 'ACCEPTED' ? 'accepted' : `assigned by ${h.assignedBy ?? 'admin'}`} {new Date(h.assignedAt).toLocaleString()}{h.reason ? ` (${h.reason})` : ''}
          {h.status === 'RELEASED' && <div className="text-xs">Released {h.releasedAt ? new Date(h.releasedAt).toLocaleString() : ''}: {h.releaseReason}</div>}</li>))}</ul>}
      {data.canChange && can('ORDER_MANAGE') && (
        <div className="mt-4 space-y-3 border-t border-ink/10 pt-4">
          <label className="block text-sm font-semibold text-ink">{pending ? 'Assign to' : 'Reassign to'}
            <select className={`${input} mt-1 font-normal`} value={tailor} onChange={(e) => setTailor(e.target.value)}><option value="">Choose a tailor…</option>
              {data.tailors.filter((t) => t.userId !== data.currentTailorId).map((t) => <option key={t.userId} value={t.userId}>{t.name} · {t.category} · {t.availability.toLowerCase().replace('_', ' ')} · {t.activeOrders}/{t.maxActiveOrders} orders{t.eligible ? '' : ' · outside usual rules'}</option>)}</select></label>
          {picked && !picked.eligible && <p className="text-xs">{picked.name} would not normally be offered this order ({!picked.inOrderCategory ? 'different category' : 'unavailable or at capacity'}). You can still assign them as an administrator.</p>}
          <label className="block text-sm font-semibold text-ink">Reason (recorded)<input className={`${input} mt-1 font-normal`} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Customer needs it faster; tailor unavailable" /></label>
          <div className="flex flex-wrap gap-2"><Button loading={busy} disabled={!tailor || reason.trim().length < 5} onClick={() => submit(Number(tailor))}>{pending ? 'Assign tailor' : 'Reassign'}</Button>
            {!pending && <Button variant="ghost" disabled={busy || reason.trim().length < 5} onClick={() => submit(null)}>Return to pool</Button>}</div>
          {!pending && <p className="text-xs">The tailor can only be changed before they submit the fabric sample.</p>}
        </div>)}
    </section>
  )
}
