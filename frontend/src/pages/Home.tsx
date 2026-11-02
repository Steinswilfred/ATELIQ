import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { get, toApiError } from '../lib/api';
import { Skeleton } from '../components/ui/Skeleton';

interface Ping { service: string; environment: string; time: string }

function ApiStatus() {
  const [state, setState] = useState<{ status: 'loading' | 'ok' | 'error'; data?: Ping; message?: string }>({ status: 'loading' });
  const load = useCallback(() => {
    setState({ status: 'loading' });
    get<Ping>('/system/ping')
      .then((data) => setState({ status: 'ok', data }))
      .catch((e) => setState({ status: 'error', message: toApiError(e).message }));
  }, []);
  useEffect(load, [load]);

  if (state.status === 'loading') return <Skeleton className="h-5 w-48" />;
  if (state.status === 'error')
    return (
      <p role="status" className="text-sm text-red-700">
        Backend unreachable: {state.message}{' '}
        <button onClick={load} className="font-semibold underline">Retry</button>
      </p>
    );
  return (
    <p role="status" className="text-sm">
      <span className="mr-2 inline-block h-2 w-2 rounded-full bg-green-600 align-middle" />
      Connected to {state.data?.service} ({state.data?.environment})
    </p>
  );
}

export function Home() {
  return (
    <section className="relative overflow-hidden">
      <div className="mx-auto max-w-6xl px-4 pb-32 pt-16 md:pt-24">
        <h1 className="max-w-3xl text-5xl font-bold leading-[1.05] md:text-7xl">
          Clothing cut to your measurements, stitched by a tailor you choose.
        </h1>
        <p className="mt-6 max-w-xl text-lg">
          Design a shirt, trousers or a suit down to the collar and cuff, pick the skill level of your tailor,
          and approve the fabric before a single cut is made.
        </p>
        <div className="mt-8 flex flex-wrap gap-3">
          <Link to="/men" className="rounded-full bg-primary px-6 py-3 text-sm font-semibold text-white hover:brightness-95">Browse collections</Link>
          <Link to="/tailor/register" className="rounded-full px-6 py-3 text-sm font-semibold text-ink ring-1 ring-ink/25 hover:bg-surface">Apply as a tailor</Link>
        </div>
        <div className="mt-10"><ApiStatus /></div>
      </div>
      {/* Tape-measure motif: the one decorative element, tied to the subject */}
      <div
        aria-hidden
        className="absolute inset-x-0 bottom-0 h-14 bg-primary-soft"
        style={{
          backgroundImage:
            'repeating-linear-gradient(90deg, rgb(var(--ink)) 0 1px, transparent 1px 12px), repeating-linear-gradient(90deg, rgb(var(--ink)) 0 2px, transparent 2px 60px)',
          backgroundSize: '100% 12px, 100% 26px',
          backgroundRepeat: 'repeat-x',
          backgroundPosition: 'top left, top left',
        }}
      />
    </section>
  );
}
