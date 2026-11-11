import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { AuthCard, Field, FormError, inputClass } from '../../components/auth/AuthCard'
import { Button } from '../../components/ui/Button'
import { ApiError } from '../../lib/api'
import { homePathFor, useAuth } from '../../store/authStore'

const schema = z.object({ email: z.string().email('Enter a valid email address.'), password: z.string().min(1, 'Enter your password.') })
type Form = z.infer<typeof schema>

export default function Login() {
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<Form>({ resolver: zodResolver(schema) })
  const login = useAuth((s) => s.login)
  const navigate = useNavigate()
  const from = (useLocation().state as { from?: string } | null)?.from
  const [error, setError] = useState<ApiError | null>(null)

  const onSubmit = async (v: Form) => {
    setError(null)
    try {
      const user = await login(v.email, v.password)
      navigate(from ?? homePathFor(user), { replace: true })
    } catch (e) {
      setError(e as ApiError)
    }
  }

  return (
    <AuthCard title="Sign in" subtitle="Customers, tailors and admins sign in here.">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        <FormError message={error?.message} />
        {error?.code === 'EMAIL_NOT_VERIFIED' && <p className="text-sm"><Link className="font-semibold text-primary" to="/verify-email">Resend the verification email</Link></p>}
        {error?.code === 'PHONE_NOT_VERIFIED' && <p className="text-sm"><Link className="font-semibold text-primary" to="/verify-phone">Enter your phone code</Link></p>}
        <Field label="Email" error={errors.email?.message}><input type="email" autoComplete="email" className={inputClass} {...register('email')} /></Field>
        <Field label="Password" error={errors.password?.message}><input type="password" autoComplete="current-password" className={inputClass} {...register('password')} /></Field>
        <Button type="submit" className="w-full" loading={isSubmitting}>Sign in</Button>
      </form>
      <div className="mt-6 flex justify-between text-sm">
        <Link to="/forgot-password" className="font-semibold text-primary">Forgot password?</Link>
        <span>New here? <Link to="/register" className="font-semibold text-primary">Create an account</Link></span>
      </div>
    </AuthCard>
  )
}
