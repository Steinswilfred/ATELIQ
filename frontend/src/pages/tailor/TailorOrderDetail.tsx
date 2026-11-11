import { Link, useParams } from 'react-router-dom'
import { MEASURE_LABELS } from '../../components/shop/OrderView'
import { ProgressPanel } from '../../components/shop/ProgressPanel'
import { SamplePanel } from '../../components/shop/SamplePanel'
import { EmptyState } from '../../components/ui/EmptyState'
import { Skeleton } from '../../components/ui/Skeleton'
import { get } from '../../lib/api'
import { money } from '../../lib/format'
import { statusLabel } from '../../lib/orderTypes'
import type { TailorOrderDetailT } from '../../lib/tailorOrderTypes'
import { useAsync } from '../../lib/useAsync'

export default function TailorOrderDetail() {
  const { id } = useParams()
  const { data, loading, error, reload } = useAsync(() => get<TailorOrderDetailT>(`/tailor/orders/${id}`), [id])
  if (loading) return <Skeleton className="h-64" />
  if (error || !data) return <EmptyState title="Order not found" hint={error?.message} />
  const o = data.order
  return (
    <div className="max-w-3xl space-y-6">
      <Link to="/tailor/assigned" className="text-sm font-semibold text-primary">← Assigned orders</Link>
      <div className="flex flex-wrap items-start justify-between gap-3"><div><h1 className="text-3xl font-bold">{o.orderNumber}</h1><p className="mt-1 text-sm">{o.customerFirstName}{o.city ? `, ${o.city}` : ''} · <strong>{statusLabel(o.status)}</strong></p></div>
        <div className="text-right"><div className="text-xl font-bold text-ink">{money(o.stitchingCharge)}</div><div className="text-xs">stitching charge ({data.category})</div></div></div>
      <SamplePanel viewer="tailor" orderId={o.id} orderStatus={o.status} onChanged={reload} />
      <ProgressPanel orderId={o.id} status={o.status} onChanged={reload} />
      <section className="space-y-4">{data.items.map((i, n) => (
        <article key={n} className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10"><div className="flex gap-4"><div className="h-24 w-20 shrink-0 overflow-hidden rounded-lg bg-surface">{i.imageUrl && <img src={i.imageUrl} alt="" className="h-full w-full object-cover" />}</div>
          <div className="text-sm"><h2 className="text-lg font-bold text-ink">{i.name} × {i.quantity}</h2>
            <dl className="mt-2 space-y-1"><div><dt className="inline font-semibold text-ink">Fabric: </dt><dd className="inline">{i.details?.fabric ?? '—'}{i.details?.color ? `, ${i.details.color}` : ''}</dd></div>
              {(i.details?.selections ?? []).map((s) => <div key={s.group}><dt className="inline font-semibold text-ink">{s.group}: </dt><dd className="inline">{s.option}</dd></div>)}
              <div><dt className="inline font-semibold text-ink">Fit: </dt><dd className="inline">{i.sizeCode ? `Standard size ${i.sizeCode}` : `Custom measurements (${i.measurementProfile ?? 'profile'})`}</dd></div></dl></div></div>
          {i.measurements && <table className="mt-4 w-full text-sm"><caption className="mb-1 text-left text-xs font-semibold text-ink">Measurements (centimetres)</caption><tbody>{Object.entries(i.measurements).map(([k, v]) => <tr key={k} className="border-t border-ink/10"><th scope="row" className="py-1 text-left font-normal">{MEASURE_LABELS[k] ?? k}</th><td className="py-1 text-right font-semibold text-ink">{v} cm</td></tr>)}</tbody></table>}
        </article>))}</section>
      <section className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10"><h2 className="text-lg font-bold">Progress</h2>
        <ol className="mt-3 space-y-3 border-l-2 border-ink/10 pl-4">{data.timeline.map((t, i) => <li key={i} className="relative"><span className={`absolute -left-[1.4rem] top-1 h-3 w-3 rounded-full ${i === data.timeline.length - 1 ? 'bg-primary' : 'bg-ink/30'}`} aria-hidden />
          <div className="text-sm font-semibold text-ink">{statusLabel(t.status)}</div>{t.note && <div className="text-xs">{t.note}</div>}{t.photos && t.photos.length > 0 && <div className="mt-1 flex flex-wrap gap-2">{t.photos.map((u) => <a key={u} href={u} target="_blank" rel="noopener noreferrer"><img src={u} alt="Finished garment" className="h-16 w-16 rounded-lg object-cover" /></a>)}</div>}<time className="text-xs text-body/70">{new Date(t.at).toLocaleString()}</time></li>)}</ol></section>
      <p className="text-xs">For privacy, the customer's phone number and street address are held by the administrator, who handles dispatch.</p>
    </div>
  )
}
