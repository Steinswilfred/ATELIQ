import { useState } from 'react'
import { Link } from 'react-router-dom'
import { AuthCard, Field, inputClass } from '../../components/auth/AuthCard'
import { Button } from '../../components/ui/Button'
import { post } from '../../lib/api'

export default function ForgotPassword() {
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [busy, setBusy] = useState(false)
  const submit = async () => { setBusy(true); try { await post('/auth/forgot-password', { email }) } finally { setBusy(false); setSent(true) } }

  if (sent) return <AuthCard title="Check your email" subtitle="If an account exists for that address, we sent a link to reset the password. It expires in 1 hour."><Link to="/login"><Button variant="ghost">Back to sign in</Button></Link></AuthCard>
  return (
    <AuthCard title="Reset your password" subtitle="Enter your email and we will send you a reset link.">
      <div className="space-y-4">
        <Field label="Email"><input type="email" value={email} onChange={(e) => setEmail(e.target.value)} className={inputClass} /></Field>
        <Button onClick={submit} loading={busy} disabled={!email}>Send reset link</Button>
      </div>
    </AuthCard>
  )
}
