import { create } from 'zustand'
import { get } from '../lib/api'

interface CartState { count: number; refresh: () => Promise<void>; reset: () => void }
export const useCart = create<CartState>((set) => ({
  count: 0,
  refresh: async () => { try { set({ count: (await get<{ count: number }>('/me/cart/count')).count }) } catch { /* not signed in as a customer */ } },
  reset: () => set({ count: 0 }),
}))
