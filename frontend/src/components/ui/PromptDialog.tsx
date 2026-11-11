import { AnimatePresence, motion } from 'framer-motion'
import { useEffect, useState } from 'react'
import { Button } from './Button'

export interface PromptField { name: string; label: string; required?: boolean; multiline?: boolean; placeholder?: string }
interface Props {
  open: boolean
  title: string
  description?: string
  fields: PromptField[]
  submitLabel: string
  danger?: boolean
  onSubmit: (values: Record<string, string>) => Promise<void>
  onCancel: () => void
}

/** Confirmation dialog that collects text (a reason, a note, a category) before an action runs. */
export function PromptDialog({ open, title, description, fields, submitLabel, danger, onSubmit, onCancel }: Props) {
  const [values, setValues] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  useEffect(() => { if (open) setValues({}) }, [open])
  const missing = fields.some((f) => f.required && !(values[f.name] ?? '').trim())
  const submit = async () => { setBusy(true); try { await onSubmit(values) } finally { setBusy(false) } }
  const cls = 'w-full rounded-lg border border-ink/20 bg-canvas px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/30'
  return (
    <AnimatePresence>
      {open && (
        <motion.div className="fixed inset-0 z-40 grid place-items-center bg-ink/50 p-4" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
          onClick={onCancel} role="dialog" aria-modal="true" aria-label={title}>
          <motion.div className="w-full max-w-md rounded-2xl bg-canvas p-6 shadow-xl" initial={{ y: 16, opacity: 0 }} animate={{ y: 0, opacity: 1 }} exit={{ opacity: 0 }} onClick={(e) => e.stopPropagation()}>
            <h2 className="text-xl font-bold">{title}</h2>
            {description && <p className="mt-2 text-sm">{description}</p>}
            <div className="mt-4 space-y-3">
              {fields.map((f) => (
                <label key={f.name} className="block text-sm font-semibold text-ink">{f.label}
                  {f.multiline
                    ? <textarea rows={3} className={`${cls} mt-1 font-normal`} placeholder={f.placeholder} value={values[f.name] ?? ''} onChange={(e) => setValues({ ...values, [f.name]: e.target.value })} />
                    : <input className={`${cls} mt-1 font-normal`} placeholder={f.placeholder} value={values[f.name] ?? ''} onChange={(e) => setValues({ ...values, [f.name]: e.target.value })} />}
                </label>
              ))}
            </div>
            <div className="mt-6 flex justify-end gap-2">
              <Button variant="ghost" onClick={onCancel}>Cancel</Button>
              <Button variant={danger ? 'danger' : 'primary'} loading={busy} disabled={missing} onClick={submit}>{submitLabel}</Button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
