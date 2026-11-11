import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { AuthCard, Field, FormError, inputClass } from '../../components/auth/AuthCard'
import { Button } from '../../components/ui/Button'
import { ApiError, post } from '../../lib/api'
import { password as passwordRule } from '../../lib/validation'

export default function ResetPassword() {
  const token = useSearchParams()[0].get('token') ?? ''
  const [pw, setPw] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  const [busy, setBusy] = useState(false)

  const submit = async () => {
    const check = passwordRule.safeParse(pw)
    if (!check.success) { setError(check.error.issues[0].message); return }
    setError(null); setBusy(true)
    try { await post('/auth/reset-password', { token, newPassword: pw }); setDone(true) }
    catch (e) { setError((e as ApiError).message) }
    finally { setBusy(false) }
  }

  if (done) return <AuthCard title="Password updated" subtitle="You have been signed out everywhere. Sign in with your new password."><Link to="/login"><Button>Sign in</Button></Link></AuthCard>
  return (
    <AuthCard title="Choose a new password">
      <div className="space-y-4">
        <FormError message={error} />
        <Field label="New password" hint="8+ characters, with a letter and a number"><input type="password" autoComplete="new-password" value={pw} onChange={(e) => setPw(e.target.value)} className={inputClass} /></Field>
        <Button onClick={submit} loading={busy} disabled={!token || !pw}>Update password</Button>
      </div>
    </AuthCard>
  )
}
