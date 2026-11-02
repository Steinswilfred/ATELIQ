import { AnimatePresence, motion } from 'framer-motion';
import { useEffect, useRef } from 'react';
import { Button } from './Button';

interface Props {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmDialog({ open, title, description, confirmLabel, onConfirm, onCancel }: Props) {
  const cancelRef = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (!open) return;
    cancelRef.current?.focus();
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onCancel();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onCancel]);

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          className="fixed inset-0 z-50 grid place-items-center bg-ink/50 p-4"
          initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
          onClick={onCancel}
        >
          <motion.div
            role="alertdialog" aria-modal="true" aria-labelledby="confirm-title"
            className="w-full max-w-md rounded-card bg-canvas p-6 shadow-xl"
            initial={{ scale: 0.96 }} animate={{ scale: 1 }} exit={{ scale: 0.96 }}
            onClick={(e) => e.stopPropagation()}
          >
            <h2 id="confirm-title" className="text-xl">{title}</h2>
            <p className="mt-2 text-sm">{description}</p>
            <div className="mt-6 flex justify-end gap-3">
              <Button ref={cancelRef} variant="ghost" onClick={onCancel}>Cancel</Button>
              <Button onClick={onConfirm}>{confirmLabel}</Button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
