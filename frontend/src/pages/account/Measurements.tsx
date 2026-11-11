import { AnimatePresence, motion } from 'framer-motion'
import { useEffect, useState } from 'react'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EmptyState } from '../../components/ui/EmptyState'
import { Skeleton } from '../../components/ui/Skeleton'
import { ApiError, get, http, post } from '../../lib/api'
import { toCm, toDisplay } from '../../lib/format'
import type { MeasurementField, MeasurementProfile } from '../../lib/shopTypes'
import { useAsync } from '../../lib/useAsync'
import { toast } from '../../store/toastStore'

const inputCls = 'w-full rounded-lg border border-ink/20 bg-canvas px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/30'

function ProfileForm({ fields, profile, onClose, onSaved }: { fields: MeasurementField[]; profile?: MeasurementProfile; onClose: () => void; onSaved: () => void }) {
  const [name, setName] = useState(profile?.name ?? '')
  const [unit, setUnit] = useState<'CM' | 'IN'>(profile?.unit ?? 'CM')
  const [isDefault, setIsDefault] = useState(profile?.isDefault ?? false)
  const [values, setValues] = useState<Record<string, string>>({})
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  useEffect(() => { if (profile) setValues(Object.fromEntries(Object.entries(profile.measurements).map(([k, v]) => [k, String(toDisplay(v, profile.unit))]))) }, [profile])

  const switchUnit = (u: 'CM' | 'IN') => {   // convert what is typed so the numbers stay the same physical length
    if (u === unit) return
    setValues((v) => Object.fromEntries(Object.entries(v).map(([k, s]) => { const n = Number(s); return [k, s && !Number.isNaN(n) ? String(toDisplay(toCm(n, unit), u)) : s] })))
    setUnit(u)
  }
  const submit = async () => {
    const errs: Record<string, string> = {}
    const measurements: Record<string, number> = {}
    for (const f of fields) {
      const s = (values[f.code] ?? '').trim(); if (!s) continue
      const n = Number(s)
      if (Number.isNaN(n)) { errs[f.code] = 'Enter a number.'; continue }
      const cm = toCm(n, unit)
      if (cm < f.minCm || cm > f.maxCm) errs[f.code] = `Between ${toDisplay(f.minCm, unit)} and ${toDisplay(f.maxCm, unit)} ${unit.toLowerCase()}.`
      else measurements[f.code] = cm
    }
    if (!name.trim()) errs.name = 'Give this profile a name.'
    else if (Object.keys(measurements).length === 0 && Object.keys(errs).length === 0) errs.form = 'Enter at least one measurement.'
    setErrors(errs)
    if (Object.keys(errs).length) return
    setBusy(true)
    try {
      const body = { name: name.trim(), unit, measurements, isDefault }
      if (profile) await http.put(`/me/measurements/${profile.id}`, body); else await post('/me/measurements', body)
      toast.success('Measurements saved.'); onSaved()
    } catch (e) { toast.error((e as ApiError).message) } finally { setBusy(false) }
  }
  return (
    <motion.div className="fixed inset-0 z-40 grid place-items-center overflow-auto bg-ink/50 p-4" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose} role="dialog" aria-modal="true" aria-label="Measurement profile">
      <div className="my-8 w-full max-w-2xl rounded-2xl bg-canvas p-6 shadow-xl" onClick={(e) => e.stopPropagation()}>
        <h2 className="text-xl font-bold">{profile ? 'Edit measurements' : 'New measurement profile'}</h2>
        <p className="mt-1 text-xs">Use a soft tape measure over light clothing, and keep it snug but not tight. Fill in only what you know; tailors will confirm anything missing.</p>
        <div className="mt-4 grid gap-4 sm:grid-cols-2">
          <label className="text-sm font-semibold text-ink">Profile name<input className={`${inputCls} mt-1 font-normal`} value={name} onChange={(e) => setName(e.target.value)} placeholder="Me, Dad, Aarav (school)" />{errors.name && <span role="alert" className="text-xs text-primary">{errors.name}</span>}</label>
          <div><span className="text-sm font-semibold text-ink">Units</span><div className="mt-1 flex gap-2" role="group" aria-label="Units">{(['CM', 'IN'] as const).map((u) => <Button key={u} type="button" variant={unit === u ? 'dark' : 'ghost'} onClick={() => switchUnit(u)}>{u === 'CM' ? 'Centimetres' : 'Inches'}</Button>)}</div></div>
        </div>
        <div className="mt-5 grid gap-4 sm:grid-cols-2">{fields.map((f) => (
          <label key={f.code} className="block text-sm font-semibold text-ink">{f.label} ({unit.toLowerCase()})
            <input inputMode="decimal" className={`${inputCls} mt-1 font-normal`} value={values[f.code] ?? ''} onChange={(e) => setValues({ ...values, [f.code]: e.target.value })} />
            <span className="mt-1 block text-xs font-normal">{f.instruction}</span>{errors[f.code] && <span role="alert" className="text-xs font-semibold text-primary">{errors[f.code]}</span>}
          </label>))}</div>
        {errors.form && <p role="alert" className="mt-3 text-sm font-semibold text-primary">{errors.form}</p>}
        <label className="mt-5 flex items-center gap-2 text-sm font-semibold text-ink"><input type="checkbox" className="accent-primary" checked={isDefault} onChange={(e) => setIsDefault(e.target.checked)} />Use as my default profile</label>
        <div className="mt-6 flex justify-end gap-2"><Button variant="ghost" onClick={onClose}>Cancel</Button><Button loading={busy} onClick={submit}>Save measurements</Button></div>
      </div>
    </motion.div>
  )
}

export default function Measurements() {
  const profiles = useAsync(() => get<MeasurementProfile[]>('/me/measurements'), [])
  const fields = useAsync(() => get<MeasurementField[]>('/public/measurement-fields'), [])
  const [form, setForm] = useState<{ open: boolean; profile?: MeasurementProfile }>({ open: false })
  const [del, setDel] = useState<MeasurementProfile | null>(null)
  const remove = async () => { try { await http.delete(`/me/measurements/${del!.id}`); toast.success('Profile deleted.'); profiles.reload() } catch (e) { toast.error((e as ApiError).message) } setDel(null) }
  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3"><h1 className="text-3xl font-bold">Measurements</h1><Button onClick={() => setForm({ open: true })} disabled={!fields.data}>New profile</Button></div>
      <p className="mt-1 text-sm">Save several profiles (for you, family, different fits) and reuse them on any order.</p>
      <div className="mt-6">
        {profiles.error ? <EmptyState title="Could not load measurements" hint={profiles.error.message} /> : profiles.loading ? <Skeleton className="h-32" /> : profiles.data!.length === 0
          ? <EmptyState title="No measurement profiles yet" hint="Add one to order made-to-measure clothing in your exact fit." />
          : <ul className="space-y-3">{profiles.data!.map((m) => (
            <li key={m.id} className="rounded-xl bg-canvas p-5 ring-1 ring-ink/10">
              <div className="flex flex-wrap items-center justify-between gap-2"><h2 className="text-lg font-bold">{m.name}{m.isDefault && <span className="ml-2 rounded-full bg-primary/15 px-2 py-0.5 align-middle text-xs font-semibold text-primary">Default</span>}</h2>
                <div className="flex gap-3 text-sm font-semibold"><button className="text-primary" onClick={() => setForm({ open: true, profile: m })}>Edit</button><button className="text-ink/70 hover:text-primary" onClick={() => setDel(m)}>Delete</button></div></div>
              <dl className="mt-3 grid grid-cols-2 gap-x-6 gap-y-1 text-sm sm:grid-cols-3">{(fields.data ?? []).filter((f) => m.measurements[f.code] != null).map((f) => <div key={f.code} className="flex justify-between"><dt>{f.label}</dt><dd className="font-semibold text-ink">{toDisplay(m.measurements[f.code], m.unit)} {m.unit.toLowerCase()}</dd></div>)}</dl>
            </li>))}</ul>}
      </div>
      <AnimatePresence>{form.open && fields.data && <ProfileForm fields={fields.data} profile={form.profile} onClose={() => setForm({ open: false })} onSaved={() => { setForm({ open: false }); profiles.reload() }} />}</AnimatePresence>
      <ConfirmDialog open={!!del} danger title={`Delete “${del?.name}”?`} message="Designs already saved with this profile keep working only if you pick another profile before ordering." confirmLabel="Delete profile" onConfirm={remove} onCancel={() => setDel(null)} />
    </div>
  )
}
