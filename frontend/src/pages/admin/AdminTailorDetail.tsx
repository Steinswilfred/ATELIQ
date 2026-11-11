import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Skeleton } from '../../components/ui/Skeleton'
import { StatusBadge } from '../../components/ui/StatusBadge'
import { ApiError, get, http } from '../../lib/api'
import { money } from '../../lib/format'
import { useAsync } from '../../lib/useAsync'
import { useAuth } from '../../store/authStore'
import { toast } from '../../store/toastStore'
import type { TailorCat } from './AdminTailorCategories'
import type { TailorRow } from './AdminTailors'

interface Detail { tailor: TailorRow; bio?: string; skills?: string; address?: string; photoUrl?: string; availabilityNote?: string; availableAgainOn?: string; applicationId?: number }
const input = 'w-full rounded-lg border border-ink/20 bg-canvas px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/30'

export default function AdminTailorDetail() {
  const { userId } = useParams()
  const can = useAuth((s) => s.can)
  const manage = can('TAILOR_CATEGORY_MANAGE')
  const { data, loading, error, reload } = useAsync(() => get<Detail>(`/admin/tailors/${userId}`), [userId])
  const cats = useAsync(() => get<TailorCat[]>('/admin/tailor-categories').catch(() => [] as TailorCat[]), [])
  const [categoryId, setCategoryId] = useState('')
  const [status, setStatus] = useState('AVAILABLE'); const [max, setMax] = useState('5'); const [note, setNote] = useState(''); const [until, setUntil] = useState('')
  const [busy, setBusy] = useState<string | null>(null)
  useEffect(() => { if (!data) return; setCategoryId(String(data.tailor.categoryId)); setStatus(data.tailor.availability); setMax(String(data.tailor.maxActiveOrders)); setNote(data.availabilityNote ?? ''); setUntil(data.availableAgainOn ?? '') }, [data])

  const run = async (key: string, fn: () => Promise<unknown>, ok: string) => { setBusy(key); try { await fn(); toast.success(ok); reload() } catch (e) { toast.error((e as ApiError).message) } finally { setBusy(null) } }
  if (loading) return <div className="space-y-3"><Skeleton className="h-10 w-1/3" /><Skeleton className="h-64" /></div>
  if (error || !data) return <EmptyState title="Could not load this tailor" hint={error?.message} />
  const t = data.tailor

  return (
    <div className="max-w-3xl space-y-6">
      <Link to="/admin/tailor-profiles" className="text-sm font-semibold text-primary">← All tailors</Link>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-center gap-4">{data.photoUrl && <img src={data.photoUrl} alt="" className="h-16 w-16 rounded-full object-cover" />}
          <div><h1 className="text-3xl font-bold">{t.fullName}</h1><p className="text-sm">{t.email} · {t.phone}</p><div className="mt-2"><StatusBadge status={t.accountStatus} /></div></div></div>
        <div className="flex gap-3 text-sm font-semibold"><Link className="text-primary" to={`/admin/users/${t.userId}`}>Account, sign-ins, block or suspend →</Link>{data.applicationId && <Link className="text-primary" to={`/admin/tailors/${data.applicationId}`}>Application →</Link>}</div>
      </div>

      <section className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10"><h2 className="text-lg font-bold">Category and price</h2>
        <p className="mt-1 text-sm">Currently <strong>{t.categoryName}</strong> at {money(t.stitchingPrice)} per garment.</p>
        {manage && <div className="mt-3 flex flex-wrap items-center gap-3"><select aria-label="Tailor category" value={categoryId} onChange={(e) => setCategoryId(e.target.value)} className={`${input} w-auto`}>{(cats.data ?? []).filter((c) => c.active || String(c.id) === categoryId).map((c) => <option key={c.id} value={c.id}>{c.name} · {money(c.stitchingPrice)}</option>)}</select>
          <Button loading={busy === 'cat'} disabled={categoryId === String(t.categoryId)} onClick={() => run('cat', () => http.put(`/admin/tailors/${t.userId}/category`, { categoryId: Number(categoryId) }), 'Category changed.')}>Change category</Button></div>}
      </section>

      <section className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10"><h2 className="text-lg font-bold">Availability</h2>
        <div className="mt-3 grid gap-4 sm:grid-cols-2">
          <label className="text-sm font-semibold text-ink">Status<select disabled={!manage} className={`${input} mt-1 font-normal`} value={status} onChange={(e) => setStatus(e.target.value)}><option value="AVAILABLE">Available</option><option value="BUSY">Busy</option><option value="ON_LEAVE">On leave</option></select></label>
          <label className="text-sm font-semibold text-ink">Maximum active orders<input disabled={!manage} type="number" min={1} max={50} className={`${input} mt-1 font-normal`} value={max} onChange={(e) => setMax(e.target.value)} /></label>
          <label className="text-sm font-semibold text-ink">Back on<input disabled={!manage} type="date" className={`${input} mt-1 font-normal`} value={until} onChange={(e) => setUntil(e.target.value)} /></label>
          <label className="text-sm font-semibold text-ink">Note<input disabled={!manage} className={`${input} mt-1 font-normal`} value={note} onChange={(e) => setNote(e.target.value)} /></label>
        </div>
        {manage && <Button className="mt-4" loading={busy === 'av'} onClick={() => run('av', () => http.put(`/admin/tailors/${t.userId}/availability`, { status, maxActiveOrders: Number(max), note: note || null, availableAgainOn: until || null }), 'Availability saved.')}>Save availability</Button>}
      </section>

      <section className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10"><h2 className="text-lg font-bold">Profile</h2>
        <dl className="mt-3 space-y-3 text-sm"><div><dt className="text-xs text-body/70">Experience</dt><dd>{t.experienceYears} years</dd></div><div><dt className="text-xs text-body/70">Skills</dt><dd className="whitespace-pre-wrap">{data.skills || '—'}</dd></div>
          <div><dt className="text-xs text-body/70">About</dt><dd className="whitespace-pre-wrap">{data.bio || '—'}</dd></div><div><dt className="text-xs text-body/70">Address</dt><dd>{data.address || '—'}</dd></div></dl>
      </section>

      <section className="rounded-xl bg-surface p-5"><h2 className="text-lg font-bold">Performance and earnings</h2>
        <p className="mt-1 text-sm">{t.ratingCount > 0 ? `Rating ${t.ratingAvg} from ${t.ratingCount} reviews.` : 'No ratings yet.'} Completed orders, delivery times and earnings appear here once orders and payouts are live.</p></section>
    </div>
  )
}
