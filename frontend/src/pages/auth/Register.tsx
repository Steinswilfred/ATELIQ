import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { AuthCard, Field, FormError, inputClass } from '../../components/auth/AuthCard'
import { Button } from '../../components/ui/Button'
import { ApiError, post } from '../../lib/api'
import { email, password, phone } from '../../lib/validation'

const schema = z.object({ fullName: z.string().min(2, 'Enter your full name.'), email, phone, password })
type Form = z.infer<typeof schema>

export default function Register() {
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<Form>({ resolver: zodResolver(schema) })
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)

  const onSubmit = async (v: Form) => {
    setError(null)
    try {
      await post('/auth/register/customer', v)
      navigate(`/verify-phone?email=${encodeURIComponent(v.email)}&sent=1`)
    } catch (e) {
      const err = e as ApiError
      setError(err.details ? Object.values(err.details)[0] : err.message)
    }
  }

  return (
    <AuthCard title="Create your account" subtitle="We will send a link to your email and a code to your phone.">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        <FormError message={error} />
        <Field label="Full name" error={errors.fullName?.message}><input autoComplete="name" className={inputClass} {...register('fullName')} /></Field>
        <Field label="Email" error={errors.email?.message}><input type="email" autoComplete="email" className={inputClass} {...register('email')} /></Field>
        <Field label="Phone" error={errors.phone?.message} hint="With country code, e.g. +919876543210"><input type="tel" autoComplete="tel" className={inputClass} {...register('phone')} /></Field>
        <Field label="Password" error={errors.password?.message} hint="8+ characters, with a letter and a number"><input type="password" autoComplete="new-password" className={inputClass} {...register('password')} /></Field>
        <Button type="submit" className="w-full" loading={isSubmitting}>Create account</Button>
      </form>
      <p className="mt-6 text-sm">Already registered? <Link to="/login" className="font-semibold text-primary">Sign in</Link> · Tailor? <Link to="/tailor/register" className="font-semibold text-primary">Apply here</Link></p>
    </AuthCard>
  )
}
