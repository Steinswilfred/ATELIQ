export interface AvailableOrderT { id: number; orderNumber: string; placedAt: string; garmentCount: number; stitchingCharge: number
  items: { name: string; imageUrl?: string; quantity: number; fabric?: string; color?: string; selections: { group: string; option: string }[]; sizeCode?: string; customMeasurements: boolean }[] }
export interface TailorOrderRowT { id: number; orderNumber: string; status: string; placedAt: string; garmentCount: number; customerFirstName: string; city?: string; firstItem?: string; stitchingCharge: number }
export interface TailorOrderDetailT { order: TailorOrderRowT; category: string
  items: { name: string; imageUrl?: string; quantity: number; sizeCode?: string; measurementProfile?: string; measurements?: Record<string, number>; details?: { fabric?: string; color?: string; selections?: { group: string; option: string }[] } }[]
  timeline: { status: string; note?: string; at: string; photos?: string[] }[] }
export interface AssignmentInfoT { status: string; currentTailorId?: number; currentTailorName?: string; canChange: boolean
  history: { tailorName: string; method: string; status: string; assignedBy?: string; reason?: string; assignedAt: string; releasedAt?: string; releaseReason?: string }[]
  tailors: { userId: number; name: string; category: string; availability: string; activeOrders: number; maxActiveOrders: number; inOrderCategory: boolean; eligible: boolean }[] }
