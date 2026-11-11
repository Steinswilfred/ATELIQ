import { useEffect, useRef, useState } from 'react'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Skeleton } from '../../components/ui/Skeleton'
import { ApiEnvelope, ApiError, get, http, post } from '../../lib/api'
import { password as passwordRule } from '../../lib/validation'
import { useAsync } from '../../lib/useAsync'
import { toast } from '../../store/toastStore'

interface Detail { tailor: { fullName: string; email: string; phone?: string; categoryName: string; experienceYears: number }; bio?: string; skills?: string; address?: string; photoUrl?: string }
const input = 'w-full rounded-lg border border-ink/20 bg-canvas px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/30'

export default function TailorProfile() {
  const { data, loading, error, reload } = useAsync(() => get<Detail>('/tailor/profile'), [])
  const [bio, setBio] = useState(''); const [skills, setSkills] = useState(''); const [address, setAddress] = useState(''); const [photo, setPhoto] = useState('')
  const [busy, setBusy] = useState(false); const [uploading, setUploading] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)
  const [cur, setCur] = useState(''); const [next, setNext] = useState(''); const [pwBusy, setPwBusy] = useState(false)
  useEffect(() => { if (!data) return; setBio(data.bio ?? ''); setSkills(data.skills ?? ''); setAddress(data.address ?? ''); setPhoto(data.photoUrl ?? '') }, [data])

  const uploadPhoto = async (file?: File) => {
    if (!file) return
    const fd = new FormData(); fd.append('file', file); setUploading(true)
    try { const r = await http.post<ApiEnvelope<{ url: string }>>('/tailor/uploads/photo', fd, { headers: { 'Content-Type': 'multipart/form-data' } }); setPhoto(r.data.data!.url) }
    catch (e) { toast.error((e as ApiError).message) } finally { setUploading(false); if (fileRef.current) fileRef.current.value = '' }
  }
  const save = async () => {
    setBusy(true)
    try { await http.put('/tailor/profile', { bio: bio || null, skills: skills || null, address: address || null, photoUrl: photo || null }); toast.success('Profile saved.'); reload() }
    catch (e) { toast.error((e as ApiError).message) } finally { setBusy(false) }
  }
  const changePassword = async () => {
    const check = passwordRule.safeParse(next)
    if (!check.success) { toast.error(check.error.issues[0].message); return }
    setPwBusy(true)
    try { await post('/me/password', { currentPassword: cur, newPassword: next }); toast.success('Password changed. Other devices were signed out.'); setCur(''); setNext('') }
    catch (e) { toast.error((e as ApiError).message) } finally { setPwBusy(false) }
  }

  if (loading) return <div className="space-y-3"><Skeleton className="h-10 w-1/3" /><Skeleton className="h-64" /></div>
  if (error || !data) return <EmptyState title="Could not load your profile" hint={error?.message} />
  const t = data.tailor
  return (
    <div className="max-w-2xl space-y-6">
      <h1 className="text-3xl font-bold">Profile settings</h1>
      <section className="space-y-4 rounded-xl bg-canvas p-5 ring-1 ring-ink/10">
        <p className="text-sm">{t.fullName} · {t.email} · {t.phone}<br />Category: <strong>{t.categoryName}</strong> · {t.experienceYears} years of experience. Your category and price are set by the administrator.</p>
        <div className="flex items-center gap-3">{photo ? <img src={photo} alt="" className="h-16 w-16 rounded-full object-cover" /> : <div className="grid h-16 w-16 place-items-center rounded-full bg-surface text-xs">No photo</div>}
          <input ref={fileRef} type="file" accept="image/jpeg,image/png,image/webp,image/gif" className="sr-only" aria-label="Upload profile photo" onChange={(e) => void uploadPhoto(e.target.files?.[0])} />
          <Button type="button" variant="ghost" loading={uploading} onClick={() => fileRef.current?.click()}>{photo ? 'Replace photo' : 'Upload photo'}</Button></div>
        <label className="block text-sm font-semibold text-ink">About you<textarea rows={3} maxLength={2000} className={`${input} mt-1 font-normal`} value={bio} onChange={(e) => setBio(e.target.value)} /></label>
        <label className="block text-sm font-semibold text-ink">Skills<textarea rows={2} maxLength={2000} className={`${input} mt-1 font-normal`} value={skills} onChange={(e) => setSkills(e.target.value)} /></label>
        <label className="block text-sm font-semibold text-ink">Address<textarea rows={2} maxLength={500} className={`${input} mt-1 font-normal`} value={address} onChange={(e) => setAddress(e.target.value)} /></label>
        <Button loading={busy} onClick={save}>Save profile</Button>
      </section>
      <section className="space-y-4 rounded-xl bg-canvas p-5 ring-1 ring-ink/10"><h2 className="text-lg font-bold">Change password</h2>
        <label className="block text-sm font-semibold text-ink">Current password<input type="password" autoComplete="current-password" className={`${input} mt-1 font-normal`} value={cur} onChange={(e) => setCur(e.target.value)} /></label>
        <label className="block text-sm font-semibold text-ink">New password<input type="password" autoComplete="new-password" className={`${input} mt-1 font-normal`} value={next} onChange={(e) => setNext(e.target.value)} /><span className="text-xs font-normal">8+ characters, with a letter and a number</span></label>
        <Button loading={pwBusy} disabled={!cur || !next} onClick={changePassword}>Change password</Button></section>
    </div>
  )
}
