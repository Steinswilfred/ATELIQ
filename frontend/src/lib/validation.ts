import { z } from 'zod'

// Same rules as the backend DTOs; the backend remains the authority.
export const password = z.string().regex(/^(?=.*[A-Za-z])(?=.*\d).{8,72}$/, 'Use 8 to 72 characters with at least one letter and one number.')
export const phone = z.string().regex(/^\+[1-9]\d{7,14}$/, 'Enter the phone number with country code, for example +919876543210.')
export const email = z.string().email('Enter a valid email address.')
