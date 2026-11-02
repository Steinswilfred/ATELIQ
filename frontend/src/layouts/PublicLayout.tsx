import { useState } from 'react';
import { Link, NavLink, Outlet } from 'react-router-dom';
import { PageTransition } from './PageTransition';

const nav = [
  { to: '/men', label: 'Men' },
  { to: '/women', label: 'Women' },
  { to: '/kids', label: 'Kids' },
  { to: '/trending', label: 'Trending' },
];

export function PublicLayout() {
  const [open, setOpen] = useState(false);
  const link = ({ isActive }: { isActive: boolean }) =>
    `py-1 text-sm font-medium transition ${isActive ? 'text-primary' : 'text-ink hover:text-primary'}`;

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-40 border-b border-ink/10 bg-canvas/90 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4">
          <Link to="/" className="font-display text-2xl font-bold text-ink">Stitchline</Link>
          <nav className="hidden items-center gap-8 md:flex" aria-label="Main">
            {nav.map((n) => <NavLink key={n.to} to={n.to} className={link}>{n.label}</NavLink>)}
          </nav>
          <div className="hidden items-center gap-4 md:flex">
            <Link to="/login" className="text-sm font-medium text-ink hover:text-primary">Sign in</Link>
            <Link to="/cart" className="rounded-full bg-ink px-4 py-2 text-sm font-semibold text-white">Cart</Link>
          </div>
          <button className="md:hidden" onClick={() => setOpen(!open)} aria-expanded={open} aria-label="Menu">
            <span className="block h-0.5 w-6 bg-ink" /><span className="mt-1.5 block h-0.5 w-6 bg-ink" /><span className="mt-1.5 block h-0.5 w-6 bg-ink" />
          </button>
        </div>
        {open && (
          <nav className="border-t border-ink/10 px-4 py-3 md:hidden" aria-label="Mobile">
            <div className="flex flex-col gap-3">
              {nav.map((n) => <NavLink key={n.to} to={n.to} className={link} onClick={() => setOpen(false)}>{n.label}</NavLink>)}
              <Link to="/login" onClick={() => setOpen(false)} className="text-sm font-medium">Sign in</Link>
            </div>
          </nav>
        )}
      </header>
      <main className="flex-1"><PageTransition><Outlet /></PageTransition></main>
      <footer className="border-t border-ink/10 bg-surface">
        <div className="mx-auto flex max-w-6xl flex-col gap-2 px-4 py-8 text-sm sm:flex-row sm:justify-between">
          <p>Stitchline. Clothing made to your measurements.</p>
          <Link to="/tailor/register" className="font-medium text-ink underline underline-offset-2">Become a tailor</Link>
        </div>
      </footer>
    </div>
  );
}
