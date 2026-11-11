import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios'
import type { AuthResponse } from './types'

/** Mirrors the backend ApiResponse envelope. */
export interface ApiEnvelope<T> {
  success: boolean
  data?: T
  error?: { code: string; message: string; details?: Record<string, string> }
}

export class ApiError extends Error {
  constructor(public code: string, message: string, public details?: Record<string, string>, public status?: number) {
    super(message)
  }
}

// withCredentials lets the browser send the HttpOnly refresh cookie to /auth/*.
export const http = axios.create({ baseURL: '/api/v1', timeout: 15000, withCredentials: true })

let accessToken: string | null = null
let onSessionLost: (() => void) | null = null
export const setAccessToken = (t: string | null) => { accessToken = t }
export const setSessionLostHandler = (fn: () => void) => { onSessionLost = fn }

http.interceptors.request.use((cfg) => {
  if (accessToken) cfg.headers.Authorization = `Bearer ${accessToken}`
  return cfg
})

let refreshing: Promise<AuthResponse> | null = null
/** One refresh at a time: concurrent 401s share the same call (the server rotates single-use tokens). */
export function refreshSession(): Promise<AuthResponse> {
  if (!refreshing) {
    refreshing = http.post<ApiEnvelope<AuthResponse>>('/auth/refresh')
      .then((r) => { setAccessToken(r.data.data!.accessToken); return r.data.data! })
      .finally(() => { refreshing = null })
  }
  return refreshing
}

const toApiError = (err: AxiosError<ApiEnvelope<unknown>>) => {
  const e = err.response?.data?.error
  return new ApiError(e?.code ?? 'NETWORK_ERROR', e?.message ?? 'Could not reach the server.', e?.details, err.response?.status)
}

http.interceptors.response.use(
  (r) => r,
  async (err: AxiosError<ApiEnvelope<unknown>>) => {
    const cfg = err.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined
    const isAuthCall = cfg?.url?.startsWith('/auth/')
    if (err.response?.status === 401 && cfg && !cfg._retried && !isAuthCall) {
      cfg._retried = true
      try {
        await refreshSession()
        return http(cfg)
      } catch {
        setAccessToken(null)
        onSessionLost?.()
      }
    }
    return Promise.reject(toApiError(err))
  },
)

export async function get<T>(url: string): Promise<T> {
  return (await http.get<ApiEnvelope<T>>(url)).data.data as T
}
export async function post<T = unknown>(url: string, body?: unknown): Promise<T> {
  return (await http.post<ApiEnvelope<T>>(url, body)).data.data as T
}
