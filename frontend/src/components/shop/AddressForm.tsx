import { useEffect, useRef, useState } from 'react'
import { ApiError, http, post } from '../../lib/api'
import { loadScript, MAPS_KEY } from '../../lib/loaders'
import type { AddressT } from '../../lib/orderTypes'
import { toast } from '../../store/toastStore'
import { Button } from '../ui/Button'

const input = 'w-full rounded-lg border border-ink/20 bg-canvas px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/30'
type Form = { label: string; fullName: string; phone: string; line1: string; line2: string; city: string; state: string; postalCode: string; latitude: number | null; longitude: number | null; isDefault: boolean }
const EMPTY: Form = { label: '', fullName: '', phone: '+91', line1: '', line2: '', city: '', state: '', postalCode: '', latitude: null, longitude: null, isDefault: false }

/* eslint-disable @typescript-eslint/no-explicit-any */
/** Optional Google Maps search and pin. Only active when VITE_GOOGLE_MAPS_API_KEY is set; manual entry always works. */
function MapPicker({ lat, lng, onPick }: { lat: number | null; lng: number | null; onPick: (p: Partial<Form>) => void }) {
  const mapEl = useRef<HTMLDivElement>(null); const searchEl = useRef<HTMLInputElement>(null)
  const [failed, setFailed] = useState(false)
  useEffect(() => {
    if (!MAPS_KEY) return
    let cancelled = false
    loadScript(`https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(MAPS_KEY)}&libraries=places`).then(() => {
      if (cancelled || !mapEl.current || !searchEl.current) return
      const g = (window as any).google
      const center = { lat: lat ?? 13.0827, lng: lng ?? 80.2707 }
      const map = new g.maps.Map(mapEl.current, { center, zoom: lat ? 16 : 11, streetViewControl: false, mapTypeControl: false })
      const marker = new g.maps.Marker({ map, position: center, draggable: true, visible: lat !== null })
      marker.addListener('dragend', () => { const p = marker.getPosition(); onPick({ latitude: Number(p.lat().toFixed(6)), longitude: Number(p.lng().toFixed(6)) }) })
      const ac = new g.maps.places.Autocomplete(searchEl.current, { componentRestrictions: { country: 'in' }, fields: ['address_components', 'geometry', 'name'] })
      ac.addListener('place_changed', () => {
        const place = ac.getPlace(); if (!place.geometry) return
        const part = (t: string) => place.address_components?.find((c: any) => c.types.includes(t))?.long_name ?? ''
        const pos = place.geometry.location
        map.setCenter(pos); map.setZoom(16); marker.setPosition(pos); marker.setVisible(true)
        onPick({ line1: [part('street_number'), part('route') || place.name].filter(Boolean).join(' ') || place.name || '', line2: part('sublocality_level_1'), city: part('locality') || part('administrative_area_level_2'),
          state: part('administrative_area_level_1'), postalCode: part('postal_code'), latitude: Number(pos.lat().toFixed(6)), longitude: Number(pos.lng().toFixed(6)) })
      })
    }).catch(() => setFailed(true))
    return () => { cancelled = true }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps
  if (!MAPS_KEY) return <p className="text-xs">Map search is switched off (no Google Maps key is configured). Enter the address below.</p>
  if (failed) return <p className="text-xs">The map could not be loaded. You can still enter the address below.</p>
  return <div><input ref={searchEl} placeholder="Search for your address" aria-label="Search for your address" className={input} /><div ref={mapEl} className="mt-2 h-48 w-full rounded-lg bg-surface" role="application" aria-label="Map: drag the pin to adjust" /></div>
}

export function AddressForm({ address, onSaved, onCancel }: { address?: AddressT; onSaved: (id?: number) => void; onCancel: () => void }) {
  const [f, setF] = useState<Form>(address ? { label: address.label ?? '', fullName: address.fullName, phone: address.phone, line1: address.line1, line2: address.line2 ?? '', city: address.city, state: address.state, postalCode: address.postalCode, latitude: address.latitude ?? null, longitude: address.longitude ?? null, isDefault: address.isDefault } : EMPTY)
  const [busy, setBusy] = useState(false)
  const set = (k: keyof Form, v: string | boolean) => setF((p) => ({ ...p, [k]: v }))
  const save = async () => {
    setBusy(true)
    try {
      const body = { ...f, label: f.label || null, line2: f.line2 || null }
      let id = address?.id
      if (address) await http.put(`/me/addresses/${address.id}`, body); else id = (await post<{ id: number }>('/me/addresses', body)).id
      toast.success('Address saved.'); onSaved(id)
    } catch (e) { const err = e as ApiError; toast.error(err.details ? Object.values(err.details)[0] : err.message) } finally { setBusy(false) }
  }
  const missing = !f.fullName || !f.phone || !f.line1 || !f.city || !f.state || !f.postalCode
  const text = (k: keyof Form, label: string, extra = '') => <label className={`block text-sm font-semibold text-ink ${extra}`}>{label}<input className={`${input} mt-1 font-normal`} value={String(f[k] ?? '')} onChange={(e) => set(k, e.target.value)} /></label>
  return (
    <div className="space-y-4">
      <MapPicker lat={f.latitude} lng={f.longitude} onPick={(p) => setF((x) => ({ ...x, ...p }))} />
      <div className="grid gap-4 sm:grid-cols-2">
        {text('fullName', 'Full name')}{text('phone', 'Phone (with country code)')}{text('line1', 'Address line 1', 'sm:col-span-2')}{text('line2', 'Address line 2 (optional)', 'sm:col-span-2')}
        {text('city', 'City')}{text('state', 'State')}{text('postalCode', 'PIN code')}{text('label', 'Label (Home, Office…)')}
      </div>
      <label className="flex items-center gap-2 text-sm font-semibold text-ink"><input type="checkbox" className="accent-primary" checked={f.isDefault} onChange={(e) => set('isDefault', e.target.checked)} />Make this my default address</label>
      <div className="flex justify-end gap-2"><Button variant="ghost" onClick={onCancel}>Cancel</Button><Button loading={busy} disabled={missing} onClick={save}>Save address</Button></div>
    </div>
  )
}
