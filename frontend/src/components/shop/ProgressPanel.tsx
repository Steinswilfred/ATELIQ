import { useRef, useState } from 'react'
import { ApiEnvelope, ApiError, http, post } from '../../lib/api'
import { toast } from '../../store/toastStore'
import { Button } from '../ui/Button'
import { ConfirmDialog } from '../ui/ConfirmDialog'

const NEXT: Record<string, { to: string; label: string; message: string }> = {
  FABRIC_APPROVED: { to: 'CUTTING_IN_PROGRESS', label: 'Start cutting', message: 'The customer approved the fabric. Mark cutting as started?' },
  CUTTING_IN_PROGRESS: { to: 'STITCHING_IN_PROGRESS', label: 'Start stitching', message: 'Cutting is finished and you are starting to stitch?' },
  STITCHING_IN_PROGRESS: { to: 'QUALITY_CHECK', label: 'Stitching done, start quality check', message: 'Stitching is finished and you are starting your quality check?' },
}

/** The tailor's production steps. Each press tells the customer. The server only allows the next step in order. */
export function ProgressPanel({ orderId, status, onChanged }: { orderId: number; status: string; onChanged: () => void }) {
  const [confirm, setConfirm] = useState<{ to: string; label: string; message: string } | null>(null)
  const [urls, setUrls] = useState<string[]>([]); const [note, setNote] = useState(''); const [busy, setBusy] = useState(false); const [sending, setSending] = useState(false)
  const ref = useRef<HTMLInputElement>(null)

  const advance = async (to: string, extra: Record<string, unknown> = {}) => {
    try { await post(`/tailor/orders/${orderId}/progress`, { status: to, ...extra }); toast.success('Updated. The customer has been notified.'); setUrls([]); setNote(''); onChanged() }
    catch (e) { toast.error((e as ApiError).message); onChanged() }
  }
  const add = async (files: FileList | null) => {
    if (!files) return
    setBusy(true)
    try {
      for (const f of Array.from(files)) {
        if (urls.length >= 6) { toast.error('You can attach up to 6 photos.'); break }
        if (f.size > 5 * 1024 * 1024) { toast.error(`${f.name} is larger than 5 MB.`); continue }
        const fd = new FormData(); fd.append('file', f)
        const r = await http.post<ApiEnvelope<{ url: string }>>(`/tailor/orders/${orderId}/progress/upload`, fd, { headers: { 'Content-Type': 'multipart/form-data' } })
        setUrls((u) => [...u, r.data.data!.url])
      }
    } catch (e) { toast.error((e as ApiError).message) } finally { setBusy(false); if (ref.current) ref.current.value = '' }
  }

  const step = NEXT[status]
  if (!step && status !== 'QUALITY_CHECK' && !['READY_FOR_DISPATCH', 'SHIPPED', 'OUT_FOR_DELIVERY', 'DELIVERED'].includes(status)) return null
  return (
    <section className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10" aria-labelledby="progress-heading"><h2 id="progress-heading" className="text-lg font-bold">Production</h2>
      {step && <><p className="mt-2 text-sm">Next step: <strong>{step.label.toLowerCase()}</strong>.</p><Button className="mt-3" onClick={() => setConfirm(step)}>{step.label}</Button></>}
      {status === 'QUALITY_CHECK' && (
        <div className="mt-2"><p className="text-sm">Check the garment against the order. When it is right, photograph the finished garment and mark it ready for dispatch.</p>
          <div className="mt-3 flex flex-wrap gap-2">{urls.map((u) => <div key={u} className="relative"><img src={u} alt="Finished garment" className="h-20 w-20 rounded-lg object-cover" /><button aria-label="Remove photo" className="absolute -right-2 -top-2 grid h-6 w-6 place-items-center rounded-full bg-ink text-xs text-white" onClick={() => setUrls(urls.filter((x) => x !== u))}>×</button></div>)}
            {urls.length < 6 && <><input ref={ref} type="file" multiple accept="image/jpeg,image/png,image/webp,image/gif" className="sr-only" aria-label="Add photos of the finished garment" onChange={(e) => void add(e.target.files)} /><Button type="button" variant="ghost" loading={busy} onClick={() => ref.current?.click()}>{urls.length ? 'Add more' : 'Add photos'}</Button></>}</div>
          <label className="mt-3 block text-sm font-semibold text-ink">Note (optional)<input maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} className="mt-1 w-full rounded-lg border border-ink/20 bg-canvas px-3 py-2 text-sm font-normal" /></label>
          <div className="mt-3 flex flex-wrap gap-2"><Button loading={sending} disabled={urls.length === 0 || busy} onClick={async () => { setSending(true); await advance('READY_FOR_DISPATCH', { note: note || null, photoUrls: urls }); setSending(false) }}>Mark ready for dispatch</Button>
            <Button variant="ghost" onClick={() => setConfirm({ to: 'STITCHING_IN_PROGRESS', label: 'Back to stitching', message: 'Send this garment back to stitching for rework?' })}>Needs rework</Button></div></div>)}
      {status === 'READY_FOR_DISPATCH' && <p className="mt-2 text-sm">Finished and waiting for the team to dispatch it. Keep the garment safe until it is collected.</p>}
      {['SHIPPED', 'OUT_FOR_DELIVERY'].includes(status) && <p className="mt-2 text-sm">The order is on its way to the customer.</p>}
      {status === 'DELIVERED' && <p className="mt-2 text-sm font-semibold text-emerald-700">Delivered. Well done.</p>}
      <ConfirmDialog open={!!confirm} title={confirm?.label ?? ''} message={confirm?.message ?? ''} confirmLabel="Yes, continue" onConfirm={() => { const c = confirm!; setConfirm(null); void advance(c.to) }} onCancel={() => setConfirm(null)} />
    </section>
  )
}
