import { forwardRef, useId, type InputHTMLAttributes } from 'react';

interface Props extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
}

/** Works with react-hook-form's register(). Error text is announced via aria-describedby. */
export const Input = forwardRef<HTMLInputElement, Props>(function Input({ label, error, className = '', ...rest }, ref) {
  const id = useId();
  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1 block text-sm font-medium text-ink">{label}</label>
      <input
        id={id}
        ref={ref}
        aria-invalid={!!error}
        aria-describedby={error ? `${id}-err` : undefined}
        className={`w-full rounded-lg border bg-canvas px-3 py-2.5 text-sm outline-none transition focus:border-primary ${error ? 'border-red-500' : 'border-ink/20'}`}
        {...rest}
      />
      {error && <p id={`${id}-err`} className="mt-1 text-sm text-red-600">{error}</p>}
    </div>
  );
});
