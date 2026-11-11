import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { Skeleton } from '../ui/Skeleton'
import { useAuth } from '../../store/authStore'
import type { UserType } from '../../lib/types'

interface Props { types?: UserType[]; permission?: string }

/** Client-side guard for navigation only; every API call is re-checked by the backend. */
export function ProtectedRoute({ types, permission }: Props) {
  const { user, status, can } = useAuth()
  const location = useLocation()
  if (status === 'loading') return <div className="mx-auto max-w-3xl space-y-3 p-8"><Skeleton className="h-8 w-1/3" /><Skeleton className="h-40" /></div>
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if ((types && !types.includes(user.userType)) || (permission && !can(permission)))
    return <Navigate to="/" replace />
  return <Outlet />
}
