export type UserType = 'CUSTOMER' | 'TAILOR' | 'ADMIN' | 'SUPER_ADMIN'
export interface UserSummary { id: number; fullName: string; email: string; userType: UserType; permissions: string[] }
export interface AuthResponse { accessToken: string; expiresInSeconds: number; user: UserSummary }
export interface PageResult<T> { items: T[]; total: number; page: number; size: number }
