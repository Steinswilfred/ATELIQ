import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Field, FormDialog, Values } from '../../components/ui/FormDialog'
import { Skeleton } from '../../components/ui/Skeleton'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { ApiError, get, post } from '../../lib/api'
import { money } from '../../lib/format'
import { useAsync } from '../../lib/useAsync'
import { toast } from '../../store/toastStore'
import type { AppRow } from './TailorApplications'

interface Detail {
  summary: AppRow; address: string; previousExperience?: string; skills?: string; approvedCategory?: string; rejectionReason?: string
  history: { decision: string; reviewer: string; reason?: string; notes?: string; at: string }[]
}
interface Cat { id: number; name: string; level: number; stitchingPrice: number; active: boolean }
type Kind = 'approve' | 'reject' | 'request-info' | 'notes'
const CONFIG: Record<Kind, { title: string; submit: string; ok: string }> = {
  approve: { title: 'Approve this tailor', submit: 'Approve', ok: 'Application approved.' },
  reject: { title: 'Reject this application', submit: 'Reject', ok: 'Application rejected.' },
  'request-info': { title: 'Request more information', submit: 'Send request', ok: 'Request sent.' },
  notes: { title: 'Add an internal note', submit: 'Save note', ok: 'Note saved.' },
}

export default function TailorApplicationDetail() {
  const { id } = useParams()
  const { data, loading, error, reload } = useAsync(() => get<Detail>(`/admin/tailor-applications/${id}`), [id])
  const cats = useAsync(() => get<Cat[]>('/admin/tailor-categories').catch(() => [] as Cat[]), [])
  const [kind, setKind] = useState<Kind | null>(null)

  const fieldsFor = (k: Kind): Field[] => {
    if (k === 'approve') return [
      { name: 'categoryId', label: 'Tailor category', type: 'select', required: true, hint: 'Sets this tailor’s stitching price and which orders they see. You can change it later.',
        options: [{ value: '', label: 'Choose a category…' }, ...(cats.data ?? []).filter((c) => c.active).map((c) => ({ value: String(c.id), label: `${c.name} · ${money(c.stitchingPrice)}` }))] },
      { name: 'notes', label: 'Internal note (optional)', type: 'textarea' }]
    if (k === 'reject') return [{ name: 'reason', label: 'Reason shown to the applicant', type: 'textarea', required: true }, { name: 'notes', label: 'Internal note (optional)', type: 'textarea' }]
    if (k === 'request-info') return [{ name: 'message', label: 'What do you need from the applicant?', type: 'textarea', required: true }]
    return [{ name: 'notes', label: 'Note (admins only)', type: 'textarea', required: true }]
  }
  const submit = async (v: Values) => {
    if (!kind) return
    try {
      const body = kind === 'approve' ? { categoryId: Number(v.categoryId), notes: v.notes || null } : v
      await post(`/admin/tailor-applications/${id}/${kind}`, body)
      toast.success(CONFIG[kind].ok); setKind(null); reload()
    } catch (e) { toast.error((e as ApiError).message) }
  }
  if (loading) return <div className="space-y-3"><Skeleton className="h-10 w-1/3" /><Skeleton className="h-64" /></div>
  if (error || !data) return <EmptyState title="Could not load this application" hint={error?.message} />
  const a = data.summary
  const pending = a.status === 'PENDING_APPROVAL'
  const Row = ({ k, v }: { k: string; v?: string | number }) => <div><dt className="text-xs text-body/70">{k}</dt><dd className="whitespace-pre-wrap text-sm">{v || '—'}</dd></div>

  return (
    <div className="space-y-6">
      <Link to="/admin/tailors/pending" className="text-sm font-semibold text-primary">← All applications</Link>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div><h1 className="text-3xl font-bold">{a.fullName}</h1><div className="mt-2 flex items-center gap-2"><StatusBadge status={a.status} /><span className="text-xs">Application #{a.id}</span></div></div>
        <div className="flex flex-wrap gap-2">
          <Button variant="ghost" onClick={() => setKind('notes')}>Add note</Button>
          {pending && <><Button variant="ghost" onClick={() => setKind('request-info')}>Request info</Button><Button variant="danger" onClick={() => setKind('reject')}>Reject</Button><Button onClick={() => setKind('approve')}>Approve</Button></>}
        </div>
      </div>
      {!pending && a.status.startsWith('PENDING_') && <p className="rounded-lg bg-amber-50 p-3 text-sm">The applicant has not finished verifying their email and phone, so this cannot be decided yet.</p>}
      {a.status === 'APPROVED' && <p className="text-sm"><Link className="font-semibold text-primary" to={`/admin/tailor-profiles/${a.userId}`}>Open this tailor’s profile →</Link></p>}
      <section className="grid gap-4 rounded-xl bg-canvas p-5 ring-1 ring-ink/10 md:grid-cols-2">
        <dl className="space-y-3"><Row k="Email" v={`${a.email} ${a.emailVerified ? '(verified)' : '(not verified)'}`} /><Row k="Phone" v={`${a.phone ?? ''} ${a.phoneVerified ? '(verified)' : '(not verified)'}`} /><Row k="Address" v={data.address} /><Row k="Years of experience" v={a.experienceYears} /></dl>
        <dl className="space-y-3"><Row k="Requested category" v={a.preferredCategory} /><Row k="Skills" v={data.skills} /><Row k="Previous experience" v={data.previousExperience} />
          {data.approvedCategory && <Row k="Assigned category" v={data.approvedCategory} />}{data.rejectionReason && <Row k="Rejection reason" v={data.rejectionReason} />}</dl>
        <p className="text-xs md:col-span-2">Portfolio photographs and identity documents will appear here once applicant uploads are added.</p>
      </section>
      <section className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10">
        <h2 className="text-lg font-bold">Review history</h2>
        {data.history.length === 0 ? <p className="mt-3 text-sm">No review activity yet.</p> : (
          <ul className="mt-3 divide-y divide-ink/10 text-sm">{data.history.map((h, i) => (
            <li key={i} className="py-2"><div className="flex justify-between"><strong>{h.decision.replace(/_/g, ' ').toLowerCase()} · {h.reviewer}</strong><time className="text-body/70">{new Date(h.at).toLocaleString()}</time></div>
              {h.reason && <div>Reason: {h.reason}</div>}{h.notes && <div>Note: {h.notes}</div>}</li>))}</ul>)}
      </section>
      <FormDialog open={!!kind} title={kind ? CONFIG[kind].title : ''} fields={kind ? fieldsFor(kind) : []} submitLabel={kind ? CONFIG[kind].submit : 'Save'} onSubmit={submit} onCancel={() => setKind(null)} />
    </div>
  )
}
