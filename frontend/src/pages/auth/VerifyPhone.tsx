import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { AuthCard, Field, FormError, inputClass } from '../../components/auth/AuthCard'
import { Button } from '../../components/ui/Button'
import { ApiError, post } from '../../lib/api'
import { toast } from '../../store/toastStore'

export default function VerifyPhone() {
  const [params] = useSearchParams()
  const [email, setEmail] = useState(params.get('email') ?? '')
  const [otp, setOtp] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  const [busy, setBusy] = useState(false)

  const submit = async () => {
    setError(null); setBusy(true)
    try { await post('/auth/verify-phone', { email, otp }); setDone(true) }
    catch (e) { const err = e as ApiError; setError(err.details ? Object.values(err.details)[0] : err.message) }
    finally { setBusy(false) }
  }
  const resend = async () => {
    try { await post('/auth/resend-phone-otp', { email }); toast.success('If that account needs a code, it has been sent.') }
    catch (e) { toast.error((e as ApiError).message) }
  }

  if (done) return (
    <AuthCard title="Phone verified" subtitle="Thank you. If your email is also verified, you can sign in. Tailors will see their approval status after signing in.">
      <Link to="/login"><Button>Sign in</Button></Link>
    </AuthCard>
  )
  return (
    <AuthCard title="Enter your phone code" subtitle={params.get('sent') ? 'We sent a 6-digit code to your phone and a link to your email.' : 'Enter the 6-digit code sent to your phone.'}>
      <div className="space-y-4">
        <FormError message={error} />
        <Field label="Email"><input type="email" value={email} onChange={(e) => setEmail(e.target.value)} className={inputClass} /></Field>
        <Field label="6-digit code"><input inputMode="numeric" maxLength={6} value={otp} onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))} className={inputClass} autoComplete="one-time-code" /></Field>
        <div className="flex gap-3">
          <Button onClick={submit} loading={busy} disabled={otp.length !== 6 || !email}>Verify phone</Button>
          <Button variant="ghost" onClick={resend} disabled={!email}>Send a new code</Button>
        </div>
      </div>
    </AuthCard>
  )
}
