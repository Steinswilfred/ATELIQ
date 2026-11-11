import type { DesignConfig } from './shopTypes'

export interface PriceLine { label: string; amount: number }
export interface CartItem { itemId: number; productId: number; name: string; slug: string; imageUrl?: string; quantity: number; configuration: DesignConfig; unitPrice: number; lineTotal: number; lines: PriceLine[]
  details: { fabric?: string; color?: string; selections: { group: string; option: string }[] }; available: boolean; problems: string[] }
export interface CartViewT { items: CartItem[]; garmentCount: number; subtotal: number; checkoutReady: boolean }
export interface AddressT { id: number; label?: string; fullName: string; phone: string; line1: string; line2?: string; city: string; state: string; postalCode: string; latitude?: number; longitude?: number; isDefault: boolean }
export interface Totals { subtotal: number; discount: number; tailorCharges: number; taxRatePercent: number; tax: number; delivery: number; total: number }
export interface Preview { items: CartItem[]; garments: number; totals: Totals; tailorCategory?: string; couponCode?: string; errors: string[]; canCheckout: boolean; codEnabled: boolean; onlineEnabled: boolean }
export interface RazorpayCheckoutT { keyId: string; razorpayOrderId: string; amountPaise: number; currency: string; merchantName: string; description: string; prefillName: string; prefillEmail: string; prefillPhone: string }
export interface CheckoutResultT { orderId: number; orderNumber: string; paymentMethod: 'RAZORPAY' | 'COD'; total: number; razorpay?: RazorpayCheckoutT }
export interface OrderSummaryT { id: number; orderNumber: string; status: string; paymentMethod: string; paymentStatus: string; total: number; garmentCount: number; firstItemName?: string; firstItemImage?: string; placedAt: string; customerName: string }
export interface OrderDetailT {
  id: number; orderNumber: string; status: string; paymentMethod: string; paymentStatus: string; tailorCategory: string; garmentCount: number; subtotal: number; discount: number; couponCode?: string
  tailorCharges: number; taxRatePercent: number; tax: number; deliveryCharge: number; total: number; currency: string; shippingAddress: Record<string, string>; customerName: string; customerPhone: string; customerEmail: string
  deliveryInstructions?: string; placedAt: string; invoiceNumber?: string; assignedTailor?: string
  items: { id: number; productId: number; name: string; slug: string; imageUrl?: string; quantity: number; unitPrice: number; lineTotal: number; sizeCode?: string; measurementProfile?: string; measurements?: Record<string, number>
    details?: { fabric?: string; color?: string; selections?: { group: string; option: string }[] } }[]
  timeline: { status: string; note?: string; at: string; photos?: string[] }[]
  payments: { id: number; method: string; status: string; amount: number; instrument?: string; failureReason?: string; paidAt?: string }[]
}
export const statusLabel = (s: string) => s.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())
