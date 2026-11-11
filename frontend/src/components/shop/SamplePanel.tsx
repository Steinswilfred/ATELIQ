import { AnimatePresence, motion } from 'framer-motion'
import { useRef, useState } from 'react'
import { ApiEnvelope, ApiError, get, http, post } from '../../lib/api'
import type { SampleT } from '../../lib/sampleTypes'
import { useAsync } from '../../lib/useAsync'
import { toast } from '../../store/toastStore'
import { Button } from '../ui/Button'
import { ConfirmDialog } from '../ui/ConfirmDialog'
import { FormDialog } from '../ui/FormDialog'
import { Skeleton } from '../ui/Skeleton'

type Viewer = 'customer' | 'tailor' | 'admin'
const BASE: Record<Viewer, (id: number) => string> = { customer: (id) => `/me/orders/${id}/samples`, tailor: (id) => `/tailor/orders/${id}/samples`, admin: (id) => `/admin/orders/${id}/samples` }
const TONE = { PENDING: 'bg-amber-100 text-amber-800', APPROVED: 'bg-emerald-100 text-emerald-800', REJECTED: 'bg-primary/15 text-primary' }
const LABEL = { PENDING: 'Waiting for approval', APPROVED: 'Approved', REJECTED: 'Rejected' }
const when = (s: string) => new Date(s).toLocaleString()

function Lightbox({ url, onClose }: { url: string | null; onClose: () => void }) {
  return (
    <AnimatePresence>{url && (
      <motion.div className="fixed inset-0 z-50 grid place-items-center bg-ink/80 p-4" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose} role="dialog" aria-modal="true" aria-label="Fabric photo">
        <img src={url} alt="Fabric sample, enlarged" className="max-h-full max-w-full rounded-lg object-contain" /><button className="absolute right-4 top-4 rounded-full bg-canvas px-3 py-1 text-sm font-semibold" onClick={onClose}>Close</button>
      </motion.div>)}</AnimatePresence>
  )
}

/** Tailor's upload form: pick photos (uploaded one by one to the server), add a note, send for approval. */
function UploadForm({ orderId, revision, onSent }: { orderId: number; revision: number; onSent: () => void }) {
  const [urls, setUrls] = useState<string[]>([]); const [note, setNote] = useState(''); const [busy, setBusy] = useState(false); const [sending, setSending] = useState(false)
  const ref = useRef<HTMLInputElement>(null)
  const add = async (files: FileList | null) => {
    if (!files) return
    setBusy(true)
    try {
      for (const f of Array.from(files)) {
        if (urls.length >= 6) { toast.error('You can send up to 6 photos.'); break }
        if (f.size > 5 * 1024 * 1024) { toast.error(`${f.name} is larger than 5 MB.`); continue }
        const fd = new FormData(); fd.append('file', f)
        const r = await http.post<ApiEnvelope<{ url: string }>>(`/tailor/orders/${orderId}/samples/upload`, fd, { headers: { 'Content-Type': 'multipart/form-data' } })
        setUrls((u) => [...u, r.data.data!.url])
      }
    } catch (e) { toast.error((e as ApiError).message) } finally { setBusy(false); if (ref.current) ref.current.value = '' }
  }
  const send = async () => {
    setSending(true)
    try { await post(`/tailor/orders/${orderId}/samples`, { photoUrls: urls, note: note || null }); toast.success('Sample sent. The customer has been notified.'); setUrls([]); setNote(''); onSent() }
    catch (e) { toast.error((e as ApiError).message) } finally { setSending(false) }
  }
  return (
    <div className="mt-4 rounded-lg bg-surface p-4"><h3 className="font-bold text-ink">{revision > 1 ? `Send a revised sample (revision ${revision})` : 'Send the fabric sample'}</h3>
      <p className="text-xs">Photograph the actual fabric in good light. Do not cut or stitch until the customer approves.</p>
      <div className="mt-3 flex flex-wrap gap-2">{urls.map((u) => <div key={u} className="relative"><img src={u} alt="Uploaded sample" className="h-20 w-20 rounded-lg object-cover" /><button aria-label="Remove photo" className="absolute -right-2 -top-2 grid h-6 w-6 place-items-center rounded-full bg-ink text-xs text-white" onClick={() => setUrls(urls.filter((x) => x !== u))}>×</button></div>)}
        {urls.length < 6 && <><input ref={ref} type="file" multiple accept="image/jpeg,image/png,image/webp,image/gif" className="sr-only" aria-label="Add sample photos" onChange={(e) => void add(e.target.files)} />
          <Button type="button" variant="ghost" loading={busy} onClick={() => ref.current?.click()}>{urls.length ? 'Add more' : 'Add photos'}</Button></>}</div>
      <label className="mt-3 block text-sm font-semibold text-ink">Note for the customer (optional)<input maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} className="mt-1 w-full rounded-lg border border-ink/20 bg-canvas px-3 py-2 text-sm font-normal" /></label>
      <Button className="mt-3" loading={sending} disabled={urls.length === 0 || busy} onClick={send}>Send for approval</Button>
    </div>
  )
}

export function SamplePanel({ viewer, orderId, orderStatus, onChanged }: { viewer: Viewer; orderId: number; orderStatus: string; onChanged?: () => void }) {
  const { data, loading, error, reload } = useAsync(() => get<SampleT[]>(BASE[viewer](orderId)), [viewer, orderId, orderStatus])
  const [zoom, setZoom] = useState<string | null>(null)
  const [approving, setApproving] = useState<SampleT | null>(null); const [rejecting, setRejecting] = useState<SampleT | null>(null)
  const done = () => { reload(); onChanged?.() }

  const decide = async (s: SampleT, approve: boolean, reason?: string) => {
    try { await post(`/me/orders/${orderId}/samples/${s.id}/${approve ? 'approve' : 'reject'}`, approve ? undefined : { reason }); toast.success(approve ? 'Fabric approved. Your tailor can now start cutting.' : 'Sent back to your tailor for a revised sample.'); done() }
    catch (e) { toast.error((e as ApiError).message); done() }
  }

  if (loading && !data) return <Skeleton className="h-32" />
  if (error) return null
  const list = data ?? []
  const waiting = list.find((s) => s.status === 'PENDING')
  const tailorMayUpload = viewer === 'tailor' && (orderStatus === 'FABRIC_SAMPLE_PENDING' || orderStatus === 'FABRIC_REJECTED')
  if (list.length === 0 && !tailorMayUpload && !(viewer === 'customer' && orderStatus === 'FABRIC_SAMPLE_PENDING')) return null

  return (
    <section className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10" aria-labelledby="sample-heading">
      <h2 id="sample-heading" className="text-lg font-bold">Fabric approval</h2>
      {viewer === 'customer' && orderStatus === 'FABRIC_SAMPLE_PENDING' && <p className="mt-2 text-sm">Your tailor is preparing a sample of the fabric. You will be asked to approve it before anything is cut.</p>}
      {viewer === 'customer' && waiting && <p className="mt-2 rounded-lg bg-amber-50 p-3 text-sm font-semibold text-ink" role="status">Your fabric sample is ready. Please approve it or ask for a change. Nothing is cut until you approve.</p>}
      {viewer === 'customer' && orderStatus === 'FABRIC_APPROVED' && <p className="mt-2 text-sm font-semibold text-emerald-700">You approved the fabric. Your tailor can begin cutting.</p>}

      <ol className="mt-4 space-y-5">{[...list].reverse().map((s) => (
        <li key={s.id} className="rounded-lg p-4 ring-1 ring-ink/10">
          <div className="flex flex-wrap items-center justify-between gap-2"><strong className="text-ink">Sample {s.revision}{s.revision > 1 ? ' (revised)' : ''}</strong><span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${TONE[s.status]}`}>{LABEL[s.status]}</span></div>
          <div className="mt-3 grid grid-cols-3 gap-2 sm:grid-cols-4">{s.photos.map((p, i) => <button key={p} type="button" onClick={() => setZoom(p)} aria-label={`Enlarge photo ${i + 1}`} className="aspect-square overflow-hidden rounded-lg bg-surface"><img src={p} alt={`Fabric sample photo ${i + 1}`} loading="lazy" className="h-full w-full object-cover transition hover:scale-105" /></button>)}</div>
          {s.note && <p className="mt-2 text-sm"><span className="font-semibold text-ink">Tailor’s note:</span> {s.note}</p>}
          {s.status === 'REJECTED' && s.decisionReason && <p className="mt-2 text-sm"><span className="font-semibold text-primary">Reason for rejection:</span> {s.decisionReason}</p>}
          <ul className="mt-3 space-y-0.5 text-xs">{s.events.map((e, i) => <li key={i}>{e.action === 'SUBMITTED' ? 'Sent' : e.action === 'APPROVED' ? 'Approved' : 'Rejected'} by {e.actorName} ({e.role.toLowerCase()}) · {when(e.at)}</li>)}</ul>
          {viewer === 'customer' && s.status === 'PENDING' && orderStatus === 'CUSTOMER_APPROVAL_PENDING' && (
            <div className="mt-4 flex flex-wrap gap-2"><Button onClick={() => setApproving(s)}>Approve fabric</Button><Button variant="ghost" onClick={() => setRejecting(s)}>Ask for a change</Button></div>)}
        </li>))}</ol>

      {tailorMayUpload && <UploadForm orderId={orderId} revision={list.length + 1} onSent={done} />}
      {viewer === 'tailor' && waiting && <p className="mt-3 text-sm">Waiting for the customer’s decision. You will be notified.</p>}
      {viewer === 'tailor' && orderStatus === 'FABRIC_APPROVED' && <p className="mt-3 rounded-lg bg-emerald-50 p-3 text-sm font-semibold text-ink">The customer approved the fabric. You can start cutting.</p>}

      <Lightbox url={zoom} onClose={() => setZoom(null)} />
      <ConfirmDialog open={!!approving} title="Approve this fabric?" message="Your tailor will start cutting and stitching with this fabric. You cannot change the fabric after approving." confirmLabel="Approve fabric" onConfirm={() => { const s = approving!; setApproving(null); void decide(s, true) }} onCancel={() => setApproving(null)} />
      <FormDialog open={!!rejecting} title="What should change?" submitLabel="Send to tailor" fields={[{ name: 'reason', label: 'Tell your tailor what is wrong with this sample', type: 'textarea', required: true, hint: 'For example: the colour is darker than I expected, or the texture is too thick.' }]}
        onSubmit={async (v) => { const s = rejecting!; await post(`/me/orders/${orderId}/samples/${s.id}/reject`, { reason: String(v.reason) }); toast.success('Sent back to your tailor for a revised sample.'); setRejecting(null); done() }} onCancel={() => setRejecting(null)} />
    </section>
  )
}
