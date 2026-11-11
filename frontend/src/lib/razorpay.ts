import { ApiError, post } from './api'
import { loadScript } from './loaders'
import type { RazorpayCheckoutT } from './orderTypes'
import { toast } from '../store/toastStore'

export type PayOutcome = 'SUCCESS' | 'PENDING' | 'FAILED' | 'DISMISSED'
declare global { interface Window { Razorpay?: new (o: Record<string, unknown>) => { open: () => void; on: (e: string, cb: (r: unknown) => void) => void } } }

/**
 * Opens Razorpay Checkout. Nothing here decides whether the payment worked: after the callback the SERVER verifies
 * the signature and asks Razorpay, and only its answer is returned. Card details never touch our code.
 */
export async function payWithRazorpay(c: RazorpayCheckoutT): Promise<PayOutcome> {
  try { await loadScript('https://checkout.razorpay.com/v1/checkout.js') }
  catch { toast.error('Could not load the payment window. Check your connection and try again.'); return 'DISMISSED' }
  return new Promise<PayOutcome>((resolve) => {
    const rzp = new window.Razorpay!({
      key: c.keyId, amount: c.amountPaise, currency: c.currency, name: c.merchantName, description: c.description, order_id: c.razorpayOrderId,
      prefill: { name: c.prefillName, email: c.prefillEmail, contact: c.prefillPhone }, theme: { color: '#E8505B' },
      handler: async (resp: { razorpay_order_id: string; razorpay_payment_id: string; razorpay_signature: string }) => {
        try {
          const r = await post<{ status: PayOutcome }>('/me/payments/razorpay/verify', { razorpayOrderId: resp.razorpay_order_id, razorpayPaymentId: resp.razorpay_payment_id, razorpaySignature: resp.razorpay_signature })
          resolve(r.status)
        } catch (e) {
          if (e instanceof ApiError && e.status === 400) { toast.error(e.message); resolve('FAILED') } else resolve('PENDING')   // network trouble: the webhook may still confirm
        }
      },
      modal: { ondismiss: () => resolve('DISMISSED') },
    })
    rzp.on('payment.failed', () => resolve('FAILED'))
    rzp.open()
  })
}
