import { AnimatePresence, motion } from 'framer-motion';
import { useToast } from '../../store/toast';

const tone = { success: 'bg-ink text-white', error: 'bg-red-600 text-white', info: 'bg-surface text-ink ring-1 ring-ink/10' };

export function Toaster() {
  const { toasts, dismiss } = useToast();
  return (
    <div className="pointer-events-none fixed inset-x-0 bottom-4 z-50 flex flex-col items-center gap-2 px-4" aria-live="polite">
      <AnimatePresence>
        {toasts.map((t) => (
          <motion.div
            key={t.id}
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            className={`pointer-events-auto flex items-center gap-3 rounded-full px-5 py-2.5 text-sm shadow-lg ${tone[t.kind]}`}
          >
            {t.message}
            <button onClick={() => dismiss(t.id)} aria-label="Dismiss" className="opacity-70 hover:opacity-100">×</button>
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  );
}
