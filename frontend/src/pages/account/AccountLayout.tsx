import { NavLink, Outlet } from 'react-router-dom'

const tabs = [['/account', 'Overview', true], ['/account/orders', 'Orders', false], ['/account/designs', 'Saved designs', false], ['/account/measurements', 'Measurements', false], ['/account/addresses', 'Addresses', false]] as const

export default function AccountLayout() {
  return (
    <div className="mx-auto max-w-5xl px-4 py-10">
      <nav className="mb-8 flex gap-2 overflow-x-auto border-b border-ink/10 pb-3" aria-label="Account">
        {tabs.map(([to, label, end]) => <NavLink key={to} to={to} end={end} className={({ isActive }) => `whitespace-nowrap rounded-lg px-4 py-2 text-sm font-semibold ${isActive ? 'bg-ink text-white' : 'text-ink hover:bg-surface'}`}>{label}</NavLink>)}
      </nav>
      <Outlet />
    </div>
  )
}
