import { create } from 'zustand'
import { get } from '../lib/api'
import { applyTheme, ThemeColors, ThemeFonts } from '../lib/theme'

interface SiteState {
  name: string
  tagline: string
  logoUrl: string
  load: () => Promise<void>
}

type Settings = Record<string, unknown>

export const useSite = create<SiteState>((set) => ({
  name: 'Atelier',
  tagline: '',
  logoUrl: '',
  load: async () => {
    try {
      const s = await get<Settings>('/public/settings')
      applyTheme(s['theme.colors'] as Partial<ThemeColors>, s['theme.fonts'] as Partial<ThemeFonts>)
      const name = (s['site.name'] as string) || 'Atelier'
      document.title = name
      const fav = s['site.faviconUrl'] as string
      if (fav) {
        let link = document.querySelector<HTMLLinkElement>("link[rel='icon']")
        if (!link) { link = document.createElement('link'); link.rel = 'icon'; document.head.appendChild(link) }
        link.href = fav
      }
      set({ name, tagline: (s['site.tagline'] as string) ?? '', logoUrl: (s['site.logoUrl'] as string) ?? '' })
    } catch {
      /* keep built-in defaults if the API is unreachable */
    }
  },
}))
