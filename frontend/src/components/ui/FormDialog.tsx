import { AnimatePresence, motion } from 'framer-motion'
import { useEffect, useState } from 'react'
import { ApiError } from '../../lib/api'
import { toast } from '../../store/toastStore'
import { Button } from './Button'
import { ImageUploader } from './ImageUploader'

export type Values = Record<string, string | boolean | string[]>
export interface Field {
  name: string; label: string; type?: 'text' | 'textarea' | 'number' | 'color' | 'checkbox' | 'image' | 'select' | 'multicheck'
  required?: boolean; hint?: string; step?: string
  options?: { value: string; label: string }[]
}
interface Props { open: boolean; title: string; fields: Field[]; initial?: Values; submitLabel?: string; onSubmit: (v: Values) => Promise<void>; onCancel: () => void }

const cls = 'w-full rounded-lg border border-ink/20 bg-canvas px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/30'

/** One form dialog for every admin create/edit screen. Errors from onSubmit are shown as a toast and the dialog stays open. */
export function FormDialog({ open, title, fields, initial, submitLabel = 'Save', onSubmit, onCancel }: Props) {
  const [v, setV] = useState<Values>({})
  const [busy, setBusy] = useState(false)
  useEffect(() => { if (open) setV(initial ?? {}) }, [open]) // eslint-disable-line react-hooks/exhaustive-deps
  const set = (k: string, x: string | boolean | string[]) => setV((p) => ({ ...p, [k]: x }))
  const missing = fields.some((f) => f.required && f.type !== 'checkbox' && !String(v[f.name] ?? '').trim())
  const submit = async () => {
    setBusy(true)
    try { await onSubmit(v) } catch (e) { const err = e as ApiError; toast.error(err.details ? Object.values(err.details)[0] : err.message) } finally { setBusy(false) }
  }
  return (
    <AnimatePresence>
      {open && (
        <motion.div className="fixed inset-0 z-40 grid place-items-center overflow-auto bg-ink/50 p-4" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onCancel} role="dialog" aria-modal="true" aria-label={title}>
          <motion.div className="w-full max-w-lg rounded-2xl bg-canvas p-6 shadow-xl" initial={{ y: 16, opacity: 0 }} animate={{ y: 0, opacity: 1 }} exit={{ opacity: 0 }} onClick={(e) => e.stopPropagation()}>
            <h2 className="text-xl font-bold">{title}</h2>
            <div className="mt-4 space-y-4">
              {fields.map((f) => {
                const val = v[f.name]
                return (
                  <div key={f.name}>
                    {f.type === 'checkbox' ? (
                      <label className="flex items-center gap-2 text-sm font-semibold text-ink"><input type="checkbox" className="accent-primary" checked={Boolean(val)} onChange={(e) => set(f.name, e.target.checked)} />{f.label}</label>
                    ) : f.type === 'multicheck' ? (
                      <fieldset><legend className="text-sm font-semibold text-ink">{f.label}</legend>
                        <div className="mt-1 grid max-h-40 gap-1 overflow-auto sm:grid-cols-2">{(f.options ?? []).length === 0 && <span className="text-xs">Nothing to choose from yet.</span>}
                          {(f.options ?? []).map((o) => { const cur = (val as string[]) ?? []; return (
                            <label key={o.value} className="flex items-center gap-2 text-sm"><input type="checkbox" className="accent-primary" checked={cur.includes(o.value)} onChange={() => set(f.name, cur.includes(o.value) ? cur.filter((x) => x !== o.value) : [...cur, o.value])} />{o.label}</label>) })}</div></fieldset>
                    ) : f.type === 'image' ? (
                      <div><span className="mb-1 block text-sm font-semibold text-ink">{f.label}</span><ImageUploader label={f.label} value={String(val ?? '')} onChange={(u) => set(f.name, u)} /></div>
                    ) : (
                      <label className="block text-sm font-semibold text-ink">{f.label}{f.required && ' *'}
                        {f.type === 'textarea' ? <textarea rows={3} className={`${cls} mt-1 font-normal`} value={String(val ?? '')} onChange={(e) => set(f.name, e.target.value)} />
                          : f.type === 'select' ? <select className={`${cls} mt-1 font-normal`} value={String(val ?? '')} onChange={(e) => set(f.name, e.target.value)}>{(f.options ?? []).map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}</select>
                          : f.type === 'color' ? <input type="color" className="mt-1 block h-10 w-16 rounded border border-ink/20" value={String(val ?? '#000000')} onChange={(e) => set(f.name, e.target.value.toUpperCase())} />
                          : <input type={f.type === 'number' ? 'number' : 'text'} step={f.step} className={`${cls} mt-1 font-normal`} value={String(val ?? '')} onChange={(e) => set(f.name, e.target.value)} />}
                      </label>)}
                    {f.hint && <p className="mt-1 text-xs">{f.hint}</p>}
                  </div>)
              })}
            </div>
            <div className="mt-6 flex justify-end gap-2"><Button variant="ghost" onClick={onCancel}>Cancel</Button><Button loading={busy} disabled={missing} onClick={submit}>{submitLabel}</Button></div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
