import { create } from 'zustand'
import { post, refreshSession, setAccessToken, setSessionLostHandler } from '../lib/api'
import type { AuthResponse, UserSummary } from '../lib/types'

type Status = 'loading' | 'authed' | 'anon'
interface AuthState {
  user: UserSummary | null
  status: Status
  bootstrap: () => Promise<void>
  login: (email: string, password: string) => Promise<UserSummary>
  logout: () => Promise<void>
  /** UI hint only. The backend authorises every request independently. */
  can: (permission: string) => boolean
}

export const useAuth = create<AuthState>((set, get) => ({
  user: null,
  status: 'loading',
  bootstrap: async () => {
    try {
      const r = await refreshSession()
      set({ user: r.user, status: 'authed' })
    } catch {
      set({ user: null, status: 'anon' })
    }
  },
  login: async (email, password) => {
    const r = await post<AuthResponse>('/auth/login', { email, password })
    setAccessToken(r.accessToken)
    set({ user: r.user, status: 'authed' })
    return r.user
  },
  logout: async () => {
    try { await post('/auth/logout') } finally {
      setAccessToken(null)
      set({ user: null, status: 'anon' })
    }
  },
  can: (p) => get().user?.permissions.includes(p) ?? false,
}))

setSessionLostHandler(() => useAuth.setState({ user: null, status: 'anon' }))

export const homePathFor = (u: UserSummary) =>
  u.userType === 'TAILOR' ? '/tailor' : u.userType === 'CUSTOMER' ? '/account' : '/admin'
