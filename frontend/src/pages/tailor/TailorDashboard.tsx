import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Skeleton } from '../../components/ui/Skeleton'
import { ApiError, get, http } from '../../lib/api'
import { money } from '../../lib/format'
import { useAsync } from '../../lib/useAsync'
import { toast } from '../../store/toastStore'

interface Dash { profile: { tailor: { fullName: string; categoryName: string; stitchingPrice: number; availability: string; maxActiveOrders: number }; availabilityNote?: string; availableAgainOn?: string }
  activeOrders: number; completedOrders: number; awaitingFabricApproval: number; ordersModuleReady: boolean }
const STATUSES = [['AVAILABLE', 'Available', 'You can receive new orders.'], ['BUSY', 'Busy', 'Pause new orders while you finish current work.'], ['ON_LEAVE', 'On leave', 'You are away. Set a return date below.']] as const

export default function TailorDashboard() {
  const { data, loading, error, reload } = useAsync(() => get<Dash>('/tailor/dashboard'), [])
  const [status, setStatus] = useState('AVAILABLE'); const [note, setNote] = useState(''); const [until, setUntil] = useState(''); const [busy, setBusy] = useState(false)
  useEffect(() => { if (!data) return; setStatus(data.profile.tailor.availability); setNote(data.profile.availabilityNote ?? ''); setUntil(data.profile.availableAgainOn ?? '') }, [data])

  const save = async () => {
    setBusy(true)
    try { await http.put('/tailor/availability', { status, note: note || null, availableAgainOn: until || null }); toast.success('Availability updated.'); reload() }
    catch (e) { toast.error((e as ApiError).message) } finally { setBusy(false) }
  }
  if (loading) return <div className="space-y-3"><Skeleton className="h-10 w-1/3" /><Skeleton className="h-40" /></div>
  if (error || !data) return <EmptyState title="Could not load your dashboard" hint={error?.message} />
  const t = data.profile.tailor
  const tiles: [string, number][] = [['Active orders', data.activeOrders], ['Awaiting fabric approval', data.awaitingFabricApproval], ['Completed orders', data.completedOrders]]

  return (
    <div className="max-w-3xl space-y-6">
      <div><h1 className="text-3xl font-bold">Welcome, {t.fullName.split(' ')[0]}</h1>
        <p className="mt-1 text-sm">Your category is <strong>{t.categoryName}</strong>, at {money(t.stitchingPrice)} per garment (set by the administrator). You can take up to {t.maxActiveOrders} active orders at once.</p></div>

      <section className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10"><h2 className="text-lg font-bold">Availability</h2>
        <div className="mt-3 grid gap-2 sm:grid-cols-3" role="radiogroup" aria-label="Availability">{STATUSES.map(([v, label, hint]) => (
          <button key={v} type="button" role="radio" aria-checked={status === v} onClick={() => setStatus(v)} className={`rounded-lg p-3 text-left ring-1 ${status === v ? 'bg-ink text-white ring-ink' : 'bg-canvas ring-ink/20 hover:ring-primary'}`}><div className="text-sm font-semibold">{label}</div><div className="text-xs opacity-80">{hint}</div></button>))}</div>
        <div className="mt-4 grid gap-4 sm:grid-cols-2">
          <label className="text-sm font-semibold text-ink">Back on (optional)<input type="date" value={until} onChange={(e) => setUntil(e.target.value)} className="mt-1 w-full rounded-lg border border-ink/20 px-3 py-2 text-sm font-normal" /></label>
          <label className="text-sm font-semibold text-ink">Note for the team (optional)<input value={note} maxLength={300} onChange={(e) => setNote(e.target.value)} className="mt-1 w-full rounded-lg border border-ink/20 px-3 py-2 text-sm font-normal" /></label></div>
        <Button className="mt-4" loading={busy} onClick={save}>Save availability</Button>
      </section>

      <section><h2 className="text-lg font-bold">Your orders</h2>
        <div className="mt-3 grid gap-4 sm:grid-cols-3">{tiles.map(([label, n]) => <div key={label} className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10"><div className="text-3xl font-bold text-ink">{data.ordersModuleReady ? n : '—'}</div><div className="mt-1 text-sm">{label}</div></div>)}</div>
        {!data.ordersModuleReady && <p className="mt-3 text-sm">Order figures will show once orders are live.</p>}
      </section>
      {data.ordersModuleReady && <p className="text-sm"><Link className="font-semibold text-primary" to="/tailor/available">See available orders →</Link> · <Link className="font-semibold text-primary" to="/tailor/assigned">Your assigned orders →</Link></p>}
      <p className="text-sm"><Link className="font-semibold text-primary" to="/tailor/profile">Edit your profile and password →</Link></p>
    </div>
  )
}
