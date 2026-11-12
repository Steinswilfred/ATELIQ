
import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios'
import type { AuthResponse } from './types'

/** Mirrors the backend ApiResponse envelope. */
export interface ApiEnvelope<T> {
  success: boolean
  data?: T
  error?: {
    code: string
    message: string
    details?: Record<string, string>
  }
}

export class ApiError extends Error {
  constructor(
    public code: string,
    message: string,
    public details?: Record<string, string>,
    public status?: number
  ) {
    super(message)
  }
}

const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL || '/api/v1'
).replace(/\/+$/, '')

/**
 * Extract backend origin.
 *
 * Production:
 * https://ateliq-2.onrender.com/api/v1
 * becomes:
 * https://ateliq-2.onrender.com
 *
 * Development:
 * /api/v1
 * remains relative and uses the Vite proxy.
 */
const MEDIA_BASE_URL = /^https?:\/\//i.test(API_BASE_URL)
  ? new URL(API_BASE_URL).origin
  : ''

/**
 * Convert backend-relative media URLs into absolute URLs.
 * Handles nested API response objects and arrays.
 */
export function normalizeMediaUrls(value: any): any {
  if (typeof value === 'string') {
    if (value.startsWith('/media/') && MEDIA_BASE_URL) {
      return `${MEDIA_BASE_URL}${value}`
    }

    return value
  }

  if (Array.isArray(value)) {
    return value.map(normalizeMediaUrls)
  }

  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([key, item]) => [
        key,
        normalizeMediaUrls(item),
      ])
    )
  }

  return value
}

// Helper for image and GLB URLs used outside API responses.
export function getMediaUrl(url?: string | null): string {
  if (!url) return ''

  if (/^(https?:|blob:|data:)/i.test(url)) {
    return url
  }

  if (url.startsWith('/media/') && MEDIA_BASE_URL) {
    return `${MEDIA_BASE_URL}${url}`
  }

  return url
}


export const resolveMediaUrl = (url?: string | null): string => {
  if (!url) return "";

  // Keep Supabase and other absolute URLs unchanged.
  if (/^https?:\/\//i.test(url)) {
    return url;
  }

  // Keep existing backend media URLs working during migration.
  const backendUrl = import.meta.env.VITE_API_URL;

  if (url.startsWith("/")) {
    return `${backendUrl}${url}`;
  }

  return `${backendUrl}/${url}`;
};


// withCredentials sends the HttpOnly refresh cookie.
export const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  withCredentials: true,
})

let accessToken: string | null = null
let onSessionLost: (() => void) | null = null

export const setAccessToken = (t: string | null) => {
  accessToken = t
}

export const setSessionLostHandler = (fn: () => void) => {
  onSessionLost = fn
}

// Attach access token to requests.
http.interceptors.request.use((cfg) => {
  if (accessToken) {
    cfg.headers.Authorization = `Bearer ${accessToken}`
  }

  return cfg
})

let refreshing: Promise<AuthResponse> | null = null

/**
 * One refresh at a time.
 * Concurrent 401 responses share the same refresh request.
 */
export function refreshSession(): Promise<AuthResponse> {
  if (!refreshing) {
    refreshing = http
      .post<ApiEnvelope<AuthResponse>>('/auth/refresh')
      .then((r) => {
        const auth = r.data.data

        if (!auth?.accessToken) {
          throw new Error('Refresh response did not contain an access token.')
        }

        setAccessToken(auth.accessToken)
        return auth
      })
      .finally(() => {
        refreshing = null
      })
  }

  return refreshing
}

const toApiError = (err: AxiosError<ApiEnvelope<unknown>>) => {
  const e = err.response?.data?.error

  return new ApiError(
    e?.code ?? 'NETWORK_ERROR',
    e?.message ?? 'Could not reach the server.',
    e?.details,
    err.response?.status
  )
}

// Normalize media URLs in all successful API responses.
http.interceptors.response.use(
  (response) => {
    if (
      response.data &&
      typeof response.data === 'object' &&
      !(response.data instanceof Blob)
    ) {
      response.data = normalizeMediaUrls(response.data)
    }

    return response
  },

  async (err: AxiosError<ApiEnvelope<unknown>>) => {
    const cfg = err.config as
      | (InternalAxiosRequestConfig & { _retried?: boolean })
      | undefined

    const isAuthCall = cfg?.url?.startsWith('/auth/')

    if (
      err.response?.status === 401 &&
      cfg &&
      !cfg._retried &&
      !isAuthCall
    ) {
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
  }
)

export async function get<T>(url: string): Promise<T> {
  return (await http.get<ApiEnvelope<T>>(url)).data.data as T
}

export async function post<T = unknown>(
  url: string,
  body?: unknown
): Promise<T> {
  return (await http.post<ApiEnvelope<T>>(url, body)).data.data as T
}
