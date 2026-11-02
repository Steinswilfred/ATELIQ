import axios, { AxiosError } from 'axios';

export interface ApiErrorBody {
  code: string;
  message: string;
  details?: { field: string; message: string }[];
}
export interface ApiEnvelope<T> {
  success: boolean;
  data: T | null;
  error: ApiErrorBody | null;
  timestamp: string;
}

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  timeout: 15000,
});

// Phase 2: attach the access token here and rotate it via the refresh endpoint on 401.

export function toApiError(err: unknown): ApiErrorBody {
  if (axios.isAxiosError(err)) {
    const body = (err as AxiosError<ApiEnvelope<unknown>>).response?.data?.error;
    if (body) return body;
    if (!err.response) return { code: 'NETWORK_ERROR', message: 'Cannot reach the server. Check your connection.' };
  }
  return { code: 'UNKNOWN', message: 'Something went wrong. Please try again.' };
}

export async function get<T>(url: string): Promise<T> {
  const res = await api.get<ApiEnvelope<T>>(url);
  return res.data.data as T;
}
