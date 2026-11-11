export interface ThemeColors { primary: string; secondary: string; background: string; surface: string; accent: string; text: string }
export interface ThemeFonts { heading: string; body: string; baseSize: number }

const channels = (hex: string) => {
  const h = hex.replace('#', '')
  return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16)).join(' ')
}

function loadGoogleFont(name: string) {
  const id = `gf-${name.replace(/\s+/g, '-')}`
  if (document.getElementById(id)) return
  const link = document.createElement('link')
  link.id = id
  link.rel = 'stylesheet'
  link.href = `https://fonts.googleapis.com/css2?family=${encodeURIComponent(name).replace(/%20/g, '+')}:wght@400;500;600;700&display=swap`
  document.head.appendChild(link)
}

/** Applies admin-configured theme values to the document at runtime. */
export function applyTheme(colors?: Partial<ThemeColors>, fonts?: Partial<ThemeFonts>) {
  const root = document.documentElement.style
  if (colors) {
    for (const [k, v] of Object.entries(colors)) if (v && /^#[0-9a-fA-F]{6}$/.test(v)) root.setProperty(`--c-${k}`, channels(v))
  }
  if (fonts?.heading && /^[A-Za-z0-9 ]{2,40}$/.test(fonts.heading)) { loadGoogleFont(fonts.heading); root.setProperty('--font-heading', `'${fonts.heading}'`) }
  if (fonts?.body && /^[A-Za-z0-9 ]{2,40}$/.test(fonts.body)) { loadGoogleFont(fonts.body); root.setProperty('--font-body', `'${fonts.body}'`) }
  if (fonts?.baseSize) root.fontSize = `${fonts.baseSize}px`
}
