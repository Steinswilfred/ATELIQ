import { money } from '../../lib/format'
import { OrderDetailT, statusLabel } from '../../lib/orderTypes'

export const MEASURE_LABELS: Record<string, string> = { neck: 'Neck', shoulder: 'Shoulder', chest: 'Chest', waist: 'Waist', hip: 'Hip', sleeve_length: 'Sleeve', bicep: 'Upper arm', wrist: 'Wrist', shirt_length: 'Shirt length', inseam: 'Inseam', outseam: 'Trouser length', thigh: 'Thigh', knee: 'Knee', ankle: 'Ankle' }

/** Shared by the customer and admin order pages. */
export function OrderView({ o }: { o: OrderDetailT }) {
  const a = o.shippingAddress
  const Row = ({ k, v, neg }: { k: string; v: number; neg?: boolean }) => <div className="flex justify-between"><dt>{k}</dt><dd>{neg ? '−' : ''}{money(v)}</dd></div>
  return (
    <div className="space-y-6">
      <section className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10"><h2 className="text-lg font-bold">Items</h2>
        <ul className="mt-3 divide-y divide-ink/10">{o.items.map((i) => (
          <li key={i.id} className="flex gap-4 py-3"><div className="h-20 w-16 shrink-0 overflow-hidden rounded-lg bg-surface">{i.imageUrl && <img src={i.imageUrl} alt="" className="h-full w-full object-cover" />}</div>
            <div className="min-w-0 flex-1 text-sm"><div className="flex justify-between gap-2"><strong className="text-ink">{i.name} × {i.quantity}</strong><span className="font-semibold text-ink">{money(i.lineTotal)}</span></div>
              <p className="mt-1 text-xs">{[i.details?.fabric, i.details?.color, ...(i.details?.selections ?? []).map((s) => `${s.group}: ${s.option}`)].filter(Boolean).join(' · ')}</p>
              <p className="mt-1 text-xs">{i.sizeCode ? `Size ${i.sizeCode}` : i.measurementProfile ? `Custom measurements: ${i.measurementProfile}` : ''}</p>
              {i.measurements && <p className="mt-1 text-xs">{Object.entries(i.measurements).map(([k, v]) => `${MEASURE_LABELS[k] ?? k} ${v} cm`).join(' · ')}</p>}</div></li>))}</ul></section>
      {o.assignedTailor && <p className="rounded-lg bg-surface p-3 text-sm">Your tailor: <strong>{o.assignedTailor}</strong> ({o.tailorCategory}). You will be asked to approve the fabric before any cutting begins.</p>}
      <section className="grid gap-6 md:grid-cols-2">
        <div className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10"><h2 className="text-lg font-bold">Delivery</h2><p className="mt-2 text-sm">{o.customerName}<br />{a.line1}{a.line2 ? `, ${a.line2}` : ''}<br />{a.city}, {a.state} {a.postalCode}<br />{o.customerPhone}</p>
          {o.deliveryInstructions && <p className="mt-2 text-xs">Instructions: {o.deliveryInstructions}</p>}</div>
        <div className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10"><h2 className="text-lg font-bold">Payment</h2>
          <dl className="mt-2 space-y-1 text-sm"><Row k="Items" v={o.subtotal} />{o.discount > 0 && <Row k={`Discount${o.couponCode ? ` (${o.couponCode})` : ''}`} v={o.discount} neg />}<Row k={`Stitching · ${o.tailorCategory} × ${o.garmentCount}`} v={o.tailorCharges} /><Row k={`Tax (${o.taxRatePercent}%)`} v={o.tax} /><Row k="Delivery" v={o.deliveryCharge} />
            <div className="flex justify-between border-t border-ink/10 pt-2 text-base font-bold text-ink"><dt>Total</dt><dd>{money(o.total)}</dd></div></dl>
          <p className="mt-2 text-xs">{o.paymentMethod === 'COD' ? 'Cash on delivery' : 'Paid online'} · {statusLabel(o.paymentStatus)}</p></div>
      </section>
      <section className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10"><h2 className="text-lg font-bold">Progress</h2>
        <ol className="mt-3 space-y-3 border-l-2 border-ink/10 pl-4">{o.timeline.map((t, i) => (
          <li key={i} className="relative"><span className={`absolute -left-[1.4rem] top-1 h-3 w-3 rounded-full ${i === o.timeline.length - 1 ? 'bg-primary' : 'bg-ink/30'}`} aria-hidden />
            <div className="text-sm font-semibold text-ink">{statusLabel(t.status)}</div>{t.note && <div className="text-xs">{t.note}</div>}{t.photos && t.photos.length > 0 && <div className="mt-1 flex flex-wrap gap-2">{t.photos.map((u, n) => <a key={u} href={u} target="_blank" rel="noopener noreferrer" aria-label={`Open photo ${n + 1} of the finished garment`}><img src={u} alt="Finished garment" className="h-16 w-16 rounded-lg object-cover" /></a>)}</div>}<time className="text-xs text-body/70">{new Date(t.at).toLocaleString()}</time></li>))}</ol></section>
    </div>
  )
}
