import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { AuthCard, Field, FormError, inputClass } from '../../components/auth/AuthCard'
import { Button } from '../../components/ui/Button'
import { ApiError, post } from '../../lib/api'
import { email, password, phone } from '../../lib/validation'

const schema = z.object({
  fullName: z.string().min(2, 'Enter your full name.'), email, phone, password,
  address: z.string().min(5, 'Enter your address.'),
  experienceYears: z.coerce.number().int().min(0).max(80),
  previousExperience: z.string().max(4000).optional(),
  preferredCategory: z.string().max(80).optional(),
  skills: z.string().max(2000).optional(),
})
type Form = z.infer<typeof schema>

export default function RegisterTailor() {
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<Form>({ resolver: zodResolver(schema) })
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)

  const onSubmit = async (v: Form) => {
    setError(null)
    try {
      await post('/auth/register/tailor', v)
      navigate(`/verify-phone?email=${encodeURIComponent(v.email)}&sent=1`)
    } catch (e) {
      const err = e as ApiError
      setError(err.details ? Object.values(err.details)[0] : err.message)
    }
  }

  return (
    <AuthCard title="Apply as a tailor" subtitle="Verify your email and phone, then an administrator reviews your application. Portfolio and document uploads are added in a later phase.">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        <FormError message={error} />
        <Field label="Full name" error={errors.fullName?.message}><input className={inputClass} {...register('fullName')} /></Field>
        <Field label="Email" error={errors.email?.message}><input type="email" className={inputClass} {...register('email')} /></Field>
        <Field label="Phone" error={errors.phone?.message} hint="With country code, e.g. +919876543210"><input type="tel" className={inputClass} {...register('phone')} /></Field>
        <Field label="Password" error={errors.password?.message}><input type="password" autoComplete="new-password" className={inputClass} {...register('password')} /></Field>
        <Field label="Address" error={errors.address?.message}><textarea rows={2} className={inputClass} {...register('address')} /></Field>
        <Field label="Years of experience" error={errors.experienceYears?.message}><input type="number" min={0} className={inputClass} {...register('experienceYears')} /></Field>
        <Field label="Previous tailoring experience" error={errors.previousExperience?.message}><textarea rows={3} className={inputClass} {...register('previousExperience')} /></Field>
        <Field label="Preferred category" error={errors.preferredCategory?.message} hint="For example Beginner, Professional, Master Tailor. An admin assigns the final category."><input className={inputClass} {...register('preferredCategory')} /></Field>
        <Field label="Skills" error={errors.skills?.message}><textarea rows={2} className={inputClass} {...register('skills')} /></Field>
        <Button type="submit" className="w-full" loading={isSubmitting}>Submit application</Button>
      </form>
      <p className="mt-6 text-sm">Already applied? <Link to="/login" className="font-semibold text-primary">Sign in</Link> to see your status.</p>
    </AuthCard>
  )
}
