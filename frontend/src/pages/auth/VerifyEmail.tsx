import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { AuthCard, Field, FormError, inputClass } from '../../components/auth/AuthCard'
import { Button } from '../../components/ui/Button'
import { ApiError, post } from '../../lib/api'
import { toast } from '../../store/toastStore'

export default function VerifyEmail() {
  const [params] = useSearchParams()
  const token = params.get('token')
  const [state, setState] = useState<'idle' | 'working' | 'done' | 'failed'>(token ? 'working' : 'idle')
  const [error, setError] = useState<string | null>(null)
  const [email, setEmail] = useState('')

  useEffect(() => {
    if (!token) return
    post('/auth/verify-email', { token })
      .then(() => setState('done'))
      .catch((e: ApiError) => { setError(e.message); setState('failed') })
  }, [token])

  const resend = async () => {
    try { await post('/auth/resend-email', { email }); toast.success('If that account needs verification, a new link is on its way.') }
    catch (e) { toast.error((e as ApiError).message) }
  }

  if (state === 'working') return <AuthCard title="Verifying your email…"><p className="text-sm">One moment.</p></AuthCard>
  if (state === 'done') return (
    <AuthCard title="Email verified" subtitle="Your email address is confirmed. If you have not entered your phone code yet, do that next.">
      <div className="flex gap-3"><Link to="/verify-phone"><Button>Enter phone code</Button></Link><Link to="/login"><Button variant="ghost">Sign in</Button></Link></div>
    </AuthCard>
  )
  return (
    <AuthCard title="Verify your email" subtitle="Open the link we emailed you. Need a new one? Enter your email below.">
      <div className="space-y-4">
        <FormError message={error} />
        <Field label="Email"><input type="email" value={email} onChange={(e) => setEmail(e.target.value)} className={inputClass} /></Field>
        <Button onClick={resend} disabled={!email}>Send a new link</Button>
      </div>
    </AuthCard>
  )
}
