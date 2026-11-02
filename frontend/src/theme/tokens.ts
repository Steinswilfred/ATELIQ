export interface ThemeTokens {
  primary: string;
  primarySoft: string;
  ink: string;
  background: string;
  surface: string;
  text: string;
}

/** Fallback used until the public settings API (Phase 3) supplies admin-configured values. */
export const defaultTokens: ThemeTokens = {
  primary: '#F0616D',
  primarySoft: '#FDE6E8',
  ink: '#141114',
  background: '#FFFFFF',
  surface: '#F6F4F4',
  text: '#2B2628',
};

export function hexToRgbTriplet(hex: string): string | null {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex.trim());
  if (!m) return null;
  const n = parseInt(m[1], 16);
  return `${(n >> 16) & 255} ${(n >> 8) & 255} ${n & 255}`;
}

const cssVar: Record<keyof ThemeTokens, string> = {
  primary: '--primary',
  primarySoft: '--primary-soft',
  ink: '--ink',
  background: '--background',
  surface: '--surface',
  text: '--text',
};

/** Applies tokens to a style target. Invalid values are skipped so a bad admin entry cannot break the site. */
export function applyTheme(tokens: Partial<ThemeTokens>, target: { setProperty(k: string, v: string): void }) {
  (Object.keys(tokens) as (keyof ThemeTokens)[]).forEach((key) => {
    const raw = tokens[key];
    const triplet = raw ? hexToRgbTriplet(raw) : null;
    if (triplet) target.setProperty(cssVar[key], triplet);
  });
}
