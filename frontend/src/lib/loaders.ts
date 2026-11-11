const loading: Record<string, Promise<void>> = {}
/** Loads an external script once. Rejects if it cannot be fetched, so callers can show a clear message. */
export function loadScript(src: string): Promise<void> {
  if (!loading[src]) {
    loading[src] = new Promise<void>((resolve, reject) => {
      const s = document.createElement('script')
      s.src = src; s.async = true
      s.onload = () => resolve()
      s.onerror = () => { delete loading[src]; reject(new Error(`Could not load ${src}`)) }
      document.head.appendChild(s)
    })
  }
  return loading[src]
}
export const MAPS_KEY = import.meta.env.VITE_GOOGLE_MAPS_API_KEY as string | undefined
