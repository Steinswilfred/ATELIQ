import { useEffect, useState } from 'react'
import { get, post } from '../lib/api'

interface N { id: number; title: string; body?: string; read: boolean; createdAt: string }
interface Data { items: N[]; unread: number }

/** Polls every 30s. Replaced by WebSocket push in Phase 13. */
export function NotificationBell({ dark }: { dark?: boolean }) {
  const [data, setData] = useState<Data>({ items: [], unread: 0 })
  const [open, setOpen] = useState(false)
  const load = () => get<Data>('/me/notifications').then(setData).catch(() => undefined)
  useEffect(() => { void load(); const t = setInterval(load, 30000); return () => clearInterval(t) }, [])
  const readAll = async () => { await post('/me/notifications/read-all'); await load() }
  return (
    <div className="relative">
      <button aria-label={`Notifications, ${data.unread} unread`} aria-expanded={open} onClick={() => setOpen(!open)}
        className={`relative rounded-lg px-2 py-1 text-lg ${dark ? 'text-white' : 'text-ink'}`}>
        🔔{data.unread > 0 && <span className="absolute -right-1 -top-1 rounded-full bg-primary px-1.5 text-[10px] font-bold text-white">{data.unread}</span>}
      </button>
      {open && (
        <div className="absolute right-0 z-30 mt-2 w-80 max-w-[90vw] rounded-xl bg-canvas p-3 text-body shadow-xl ring-1 ring-ink/10">
          <div className="mb-2 flex items-center justify-between"><strong className="text-ink">Notifications</strong>
            {data.unread > 0 && <button onClick={readAll} className="text-xs font-semibold text-primary">Mark all read</button>}</div>
          {data.items.length === 0 ? <p className="py-6 text-center text-sm">You are all caught up.</p> : (
            <ul className="max-h-80 space-y-2 overflow-auto">
              {data.items.map((n) => (
                <li key={n.id} className={`rounded-lg p-2 text-sm ${n.read ? '' : 'bg-primary/10'}`}>
                  <div className="font-semibold text-ink">{n.title}</div>
                  {n.body && <div className="text-xs">{n.body}</div>}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
