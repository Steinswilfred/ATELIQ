import { AnimatePresence, motion } from 'framer-motion'
import { useToasts } from '../../store/toastStore'

const tone = { success: 'bg-ink text-white', error: 'bg-primary text-white', info: 'bg-white text-ink ring-1 ring-ink/15' }

export function Toaster() {
  const { toasts, dismiss } = useToasts()
  return (
    <div className="pointer-events-none fixed bottom-4 right-4 z-50 flex w-80 max-w-[calc(100vw-2rem)] flex-col gap-2" role="status" aria-live="polite">
      <AnimatePresence>
        {toasts.map((t) => (
          <motion.div key={t.id} layout initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}
            className={`pointer-events-auto flex items-start justify-between gap-3 rounded-lg px-4 py-3 text-sm shadow-lg ${tone[t.kind]}`}>
            <span>{t.message}</span>
            <button onClick={() => dismiss(t.id)} aria-label="Dismiss" className="opacity-70 hover:opacity-100">×</button>
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  )
}
