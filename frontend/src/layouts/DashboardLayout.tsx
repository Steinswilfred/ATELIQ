import { useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { PageTransition } from './PageTransition';

export interface DashboardNavItem { to: string; label: string }

/** Shared shell for the Tailor and Admin portals. Nav items are passed in; permission filtering arrives in Phase 3. */
export function DashboardLayout({ title, items }: { title: string; items: DashboardNavItem[] }) {
  const [open, setOpen] = useState(false);
  const link = ({ isActive }: { isActive: boolean }) =>
    `block rounded-lg px-3 py-2 text-sm font-medium transition ${isActive ? 'bg-primary text-white' : 'text-white/80 hover:bg-white/10'}`;

  return (
    <div className="min-h-screen bg-surface md:flex">
      <aside className={`${open ? 'block' : 'hidden'} bg-ink p-4 md:block md:w-64 md:shrink-0`}>
        <p className="mb-6 px-3 font-display text-xl font-bold text-white">{title}</p>
        <nav className="flex flex-col gap-1" aria-label={title}>
          {items.map((i) => <NavLink key={i.to} to={i.to} end className={link} onClick={() => setOpen(false)}>{i.label}</NavLink>)}
        </nav>
      </aside>
      <div className="flex-1">
        <div className="flex h-14 items-center justify-between border-b border-ink/10 bg-canvas px-4 md:hidden">
          <span className="font-display font-bold text-ink">{title}</span>
          <button onClick={() => setOpen(!open)} aria-expanded={open} className="text-sm font-medium">Menu</button>
        </div>
        <main className="p-4 md:p-8"><PageTransition><Outlet /></PageTransition></main>
      </div>
    </div>
  );
}
