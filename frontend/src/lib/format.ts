export const money = (n: number) => `₹${Number(n).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
export const CM_PER_IN = 2.54
export const toDisplay = (cm: number, unit: 'CM' | 'IN') => (unit === 'IN' ? Math.round((cm / CM_PER_IN) * 10) / 10 : cm)
export const toCm = (v: number, unit: 'CM' | 'IN') => Math.round((unit === 'IN' ? v * CM_PER_IN : v) * 10) / 10
