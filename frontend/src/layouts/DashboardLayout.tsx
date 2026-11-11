import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { NotificationBell } from '../components/NotificationBell'
import type { NavItem } from '../config/navigation'
import { useAuth } from '../store/authStore'

/** Shared shell for the Tailor and Admin portals. Menu items are filtered by permission (a UI hint; the API enforces access). */
export function DashboardLayout({ title, items }: { title: string; items: (NavItem & { permission?: string })[] }) {
  const [open, setOpen] = useState(false)
  const { user, can, logout } = useAuth()
  const navigate = useNavigate()
  const visible = items.filter((n) => !n.permission || can(n.permission))
  const link = ({ isActive }: { isActive: boolean }) =>
    `block rounded-lg px-3 py-2 text-sm font-semibold transition ${isActive ? 'bg-primary text-white' : 'text-white/75 hover:bg-white/10 hover:text-white'}`
  const signOut = async () => { await logout(); navigate('/login') }
  return (
    <div className="min-h-screen bg-surface md:flex">
      <aside className={`${open ? 'block' : 'hidden'} w-full bg-ink p-4 md:block md:min-h-screen md:w-64`}>
        <div className="mb-6 font-heading text-xl font-bold text-white">{title}</div>
        <nav className="space-y-1" aria-label={title}>
          {visible.map((n) => <NavLink key={n.path} to={n.path} end={n.path.split('/').length <= 2} className={link} onClick={() => setOpen(false)}>{n.label}</NavLink>)}
        </nav>
      </aside>
      <div className="min-w-0 flex-1">
        <header className="flex h-14 items-center justify-between border-b border-ink/10 bg-canvas px-4">
          <button className="md:hidden" aria-expanded={open} aria-label="Toggle menu" onClick={() => setOpen(!open)}>☰</button>
          <span className="hidden text-sm md:block">{user?.fullName}</span>
          <div className="flex items-center gap-3"><NotificationBell /><button onClick={signOut} className="text-sm font-semibold text-ink hover:text-primary">Sign out</button></div>
        </header>
        <main className="p-4 md:p-8"><Outlet /></main>
      </div>
    </div>
  )
}
