import { useRef, useState } from 'react'
import { ApiError, http, ApiEnvelope } from '../../lib/api'
import { toast } from '../../store/toastStore'
import { Button } from './Button'

/** Uploads to the backend, which checks the real file type. Calls onChange with the stored URL. */
export function ImageUploader({ value, onChange, label = 'Image' }: { value?: string; onChange: (url: string) => void; label?: string }) {
  const ref = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const pick = async (file?: File) => {
    if (!file) return
    if (file.size > 5 * 1024 * 1024) { toast.error('The image is larger than 5 MB.'); return }
    const fd = new FormData(); fd.append('file', file)
    setBusy(true)
    try {
      const r = await http.post<ApiEnvelope<{ url: string }>>('/admin/uploads/images', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
      onChange(r.data.data!.url)
    } catch (e) { toast.error((e as ApiError).message) } finally { setBusy(false); if (ref.current) ref.current.value = '' }
  }
  return (
    <div className="flex items-center gap-3">
      {value ? <img src={value} alt="" className="h-16 w-16 rounded-lg object-cover ring-1 ring-ink/10" /> : <div className="grid h-16 w-16 place-items-center rounded-lg bg-surface text-xs">None</div>}
      <input ref={ref} type="file" accept="image/jpeg,image/png,image/webp,image/gif" className="sr-only" aria-label={label} onChange={(e) => void pick(e.target.files?.[0])} />
      <Button type="button" variant="ghost" loading={busy} onClick={() => ref.current?.click()}>{value ? 'Replace' : 'Upload'}</Button>
      {value && <button type="button" className="text-sm font-semibold text-primary" onClick={() => onChange('')}>Remove</button>}
    </div>
  )
}
