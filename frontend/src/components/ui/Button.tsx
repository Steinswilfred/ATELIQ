import { ButtonHTMLAttributes, forwardRef } from 'react'

type Variant = 'primary' | 'dark' | 'ghost' | 'danger'
interface Props extends ButtonHTMLAttributes<HTMLButtonElement> { variant?: Variant; loading?: boolean }

const styles: Record<Variant, string> = {
  primary: 'bg-primary text-white hover:brightness-95',
  dark: 'bg-ink text-white hover:bg-ink/85',
  ghost: 'bg-transparent text-ink ring-1 ring-ink/15 hover:bg-surface',
  danger: 'bg-red-700 text-white hover:bg-red-800',
}

export const Button = forwardRef<HTMLButtonElement, Props>(({ variant = 'primary', loading, className = '', children, disabled, ...rest }, ref) => (
  <button
    ref={ref}
    disabled={disabled || loading}
    className={`inline-flex items-center justify-center gap-2 rounded-lg px-5 py-2.5 text-sm font-semibold transition disabled:cursor-not-allowed disabled:opacity-50 ${styles[variant]} ${className}`}
    {...rest}
  >
    {loading && <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white" aria-hidden />}
    {children}
  </button>
))
Button.displayName = 'Button'
