import { forwardRef, type ButtonHTMLAttributes } from 'react';

type Variant = 'primary' | 'dark' | 'ghost';
const styles: Record<Variant, string> = {
  primary: 'bg-primary text-white hover:brightness-95',
  dark: 'bg-ink text-white hover:bg-ink/85',
  ghost: 'bg-transparent text-ink ring-1 ring-ink/20 hover:bg-surface',
};

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  loading?: boolean;
}

export const Button = forwardRef<HTMLButtonElement, Props>(function Button(
  { variant = 'primary', loading, disabled, className = '', children, ...rest }, ref) {
  return (
    <button
      ref={ref}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={`inline-flex items-center justify-center gap-2 rounded-full px-5 py-2.5 text-sm font-semibold transition disabled:cursor-not-allowed disabled:opacity-50 ${styles[variant]} ${className}`}
      {...rest}
    >
      {loading && <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white" aria-hidden />}
      {children}
    </button>
  );
});
