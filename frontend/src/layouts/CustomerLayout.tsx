import { useState } from 'react'
import { Link, NavLink, Outlet } from 'react-router-dom'
import { customerNav } from '../config/navigation'
import { useSite } from '../store/siteStore'
import { useCart } from '../store/cartStore'
import { useAuth } from '../store/authStore'
import { NotificationBell } from '../components/NotificationBell'

export function CustomerLayout() {
  const [open, setOpen] = useState(false)
  const site = useSite()
  const cartCount = useCart((c) => c.count)
  const user = useAuth((x) => x.user)
  const link = ({ isActive }: { isActive: boolean }) =>
    `rounded-md px-3 py-2 text-sm font-semibold transition ${isActive ? 'text-primary' : 'text-ink hover:text-primary'}`
  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-30 border-b border-ink/10 bg-canvas/90 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4">
          <Link to="/" className="font-heading text-2xl font-bold text-ink">{site.logoUrl ? <img src={site.logoUrl} alt={site.name} className="h-8" /> : <>{site.name}<span className="text-primary">.</span></>}</Link>
          <nav className="hidden items-center gap-1 md:flex" aria-label="Main">
            {customerNav.map((n) => <NavLink key={n.path} to={n.path} className={link}>{n.label}</NavLink>)}
          </nav>
          <div className="hidden items-center gap-3 md:flex">
            {user ? <><NotificationBell /><Link to="/account" className="text-sm font-semibold text-ink hover:text-primary">{user.fullName.split(' ')[0]}</Link></> : <Link to="/login" className="text-sm font-semibold text-ink hover:text-primary">Sign in</Link>}
            <Link to="/cart" className="rounded-lg bg-ink px-4 py-2 text-sm font-semibold text-white" aria-label={`Cart, ${cartCount} item${cartCount === 1 ? '' : 's'}`}>Cart{cartCount > 0 && <span className="ml-2 rounded-full bg-primary px-2 py-0.5 text-xs">{cartCount}</span>}</Link>
          </div>
          <button className="md:hidden" aria-expanded={open} aria-label="Toggle menu" onClick={() => setOpen(!open)}>☰</button>
        </div>
        {open && (
          <nav className="flex flex-col border-t border-ink/10 px-4 py-2 md:hidden" aria-label="Mobile">
            {customerNav.map((n) => <NavLink key={n.path} to={n.path} className={link} onClick={() => setOpen(false)}>{n.label}</NavLink>)}
          </nav>
        )}
      </header>
      <main className="flex-1"><Outlet /></main>
      <footer className="bg-ink py-10 text-sm text-white/70">
        <div className="mx-auto flex max-w-7xl flex-col justify-between gap-4 px-4 sm:flex-row">
          <span>© {new Date().getFullYear()} {site.name}. {site.tagline || 'Made to measure.'}</span>
          <Link to="/tailor/register" className="hover:text-white">Become a tailor</Link>
        </div>
      </footer>
    </div>
  )
}
