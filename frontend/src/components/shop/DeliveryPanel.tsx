import { useState } from 'react'
import { ApiError, get, post } from '../../lib/api'
import { useAsync } from '../../lib/useAsync'
import { useAuth } from '../../store/authStore'
import { toast } from '../../store/toastStore'
import { Button } from '../ui/Button'
import { ConfirmDialog } from '../ui/ConfirmDialog'
import { FormDialog } from '../ui/FormDialog'

interface Delivery { shipped: boolean; courierName?: string; trackingNumber?: string; trackingUrl?: string; estimatedDelivery?: string; shippedAt?: string; deliveredAt?: string; courierIntegrated: boolean
  events: { status: string; note?: string; location?: string; at: string; source: string }[] }
const LABEL: Record<string, string> = { SHIPPED: 'Shipped', OUT_FOR_DELIVERY: 'Out for delivery', DELIVERED: 'Delivered', UPDATE: 'Update' }

/** Customers see where the parcel is as recorded by us or the courier. Admins with delivery permission can ship and update. No GPS is shown or implied. */
export function DeliveryPanel({ viewer, orderId, orderStatus, onChanged }: { viewer: 'customer' | 'admin'; orderId: number; orderStatus: string; onChanged?: () => void }) {
  const can = useAuth((s) => s.can)
  const { data, loading, reload } = useAsync(() => get<Delivery>(viewer === 'admin' ? `/admin/orders/${orderId}/delivery` : `/me/orders/${orderId}/delivery`), [viewer, orderId, orderStatus])
  const [dlg, setDlg] = useState<'ship' | 'event' | 'out' | 'delivered' | null>(null)
  const manage = viewer === 'admin' && can('DELIVERY_MANAGE')
  const done = () => { reload(); onChanged?.() }
  const run = async (path: string, body: unknown, ok: string) => { try { await post(`/admin/orders/${orderId}/delivery/${path}`, body); toast.success(ok); setDlg(null); done() } catch (e) { toast.error((e as ApiError).message); setDlg(null) } }

  if (loading && !data) return null
  const d = data
  const canShip = manage && orderStatus === 'READY_FOR_DISPATCH'
  if (!d?.shipped && !canShip) return null

  return (
    <section className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10" aria-labelledby="delivery-heading"><h2 id="delivery-heading" className="text-lg font-bold">Delivery</h2>
      {!d?.shipped ? <><p className="mt-2 text-sm">The garment is finished and waiting to be shipped.</p><Button className="mt-3" onClick={() => setDlg('ship')}>Ship this order</Button></> : (<>
        <dl className="mt-3 grid gap-3 text-sm sm:grid-cols-2">
          <div><dt className="text-xs text-body/70">Courier</dt><dd className="font-semibold text-ink">{d.courierName}</dd></div>
          <div><dt className="text-xs text-body/70">Tracking number</dt><dd className="break-all">{d.trackingNumber ?? '—'}</dd></div>
          {d.estimatedDelivery && <div><dt className="text-xs text-body/70">Estimated delivery</dt><dd>{new Date(d.estimatedDelivery).toLocaleDateString()}</dd></div>}
          {d.deliveredAt && <div><dt className="text-xs text-body/70">Delivered</dt><dd>{new Date(d.deliveredAt).toLocaleString()}</dd></div>}</dl>
        {d.trackingUrl && <a href={d.trackingUrl} target="_blank" rel="noopener noreferrer" className="mt-3 inline-block text-sm font-semibold text-primary">Track on the courier’s website →</a>}
        <ol className="mt-4 space-y-2 border-l-2 border-ink/10 pl-4">{[...d.events].reverse().map((e, i) => (
          <li key={i} className="text-sm"><strong className="text-ink">{LABEL[e.status] ?? e.status}</strong>{e.location ? ` · ${e.location}` : ''}{e.source === 'COURIER' ? ' · courier scan' : ''}<div className="text-xs">{e.note}</div><time className="text-xs text-body/70">{new Date(e.at).toLocaleString()}</time></li>))}</ol>
        {viewer === 'customer' && !d.courierIntegrated && orderStatus !== 'DELIVERED' && <p className="mt-3 text-xs">Delivery updates are entered by our team as your parcel moves. Use the courier link above for their own scans.</p>}
        {manage && orderStatus !== 'DELIVERED' && (
          <div className="mt-4 flex flex-wrap gap-2 border-t border-ink/10 pt-4">
            {orderStatus === 'SHIPPED' && <Button onClick={() => setDlg('out')}>Mark out for delivery</Button>}
            {orderStatus === 'OUT_FOR_DELIVERY' && <Button onClick={() => setDlg('delivered')}>Mark delivered</Button>}
            <Button variant="ghost" onClick={() => setDlg('event')}>Add a tracking update</Button></div>)}
      </>)}

      <FormDialog open={dlg === 'ship'} title="Ship this order" submitLabel="Mark as shipped" onCancel={() => setDlg(null)}
        fields={[{ name: 'courierName', label: 'Courier', required: true, hint: 'e.g. DTDC, Delhivery, India Post' }, { name: 'trackingNumber', label: 'Tracking number' }, { name: 'trackingUrl', label: 'Tracking link (https://…)', hint: 'Customers can open this to see the courier’s own tracking.' },
          { name: 'estimatedDelivery', label: 'Estimated delivery date', hint: 'YYYY-MM-DD' }, { name: 'note', label: 'Note (optional)' }]}
        onSubmit={async (v) => { await post(`/admin/orders/${orderId}/delivery/ship`, { courierName: v.courierName, trackingNumber: v.trackingNumber || null, trackingUrl: v.trackingUrl || null, estimatedDelivery: v.estimatedDelivery || null, note: v.note || null }); toast.success('Marked as shipped. The customer was notified.'); setDlg(null); done() }} />
      <FormDialog open={dlg === 'event'} title="Add a tracking update" submitLabel="Add update" onCancel={() => setDlg(null)}
        fields={[{ name: 'note', label: 'What happened?', required: true, hint: 'e.g. Reached the Chennai hub' }, { name: 'location', label: 'Place (optional)' }]}
        onSubmit={async (v) => { await post(`/admin/orders/${orderId}/delivery/events`, { note: v.note, location: v.location || null }); toast.success('Update added.'); setDlg(null); done() }} />
      <ConfirmDialog open={dlg === 'out'} title="Mark out for delivery?" message="The customer is notified. For cash on delivery they are reminded to keep the amount ready." confirmLabel="Mark out for delivery" onConfirm={() => run('status', { status: 'OUT_FOR_DELIVERY' }, 'Marked out for delivery.')} onCancel={() => setDlg(null)} />
      <ConfirmDialog open={dlg === 'delivered'} title="Mark as delivered?" message="Only do this once the customer has received the parcel. This closes the delivery and, for cash on delivery, lets you record the cash collection." confirmLabel="Mark delivered" onConfirm={() => run('status', { status: 'DELIVERED' }, 'Marked as delivered.')} onCancel={() => setDlg(null)} />
    </section>
  )
}
