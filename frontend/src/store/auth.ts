import { create } from 'zustand';

export type UserType = 'CUSTOMER' | 'TAILOR' | 'ADMIN' | 'SUPER_ADMIN';
export interface SessionUser {
  id: number;
  fullName: string;
  email: string;
  userType: UserType;
  permissions: string[];
}

/** Session state container. Login/refresh/logout are wired to the backend in Phase 2. */
interface AuthState {
  user: SessionUser | null;
  setUser: (u: SessionUser | null) => void;
  can: (permission: string) => boolean;
}

export const useAuth = create<AuthState>((set, get) => ({
  user: null,
  setUser: (user) => set({ user }),
  // UI convenience only. Real enforcement happens on the backend.
  can: (permission) => get().user?.permissions.includes(permission) ?? false,
}));
