import { useEffect, type ReactNode } from 'react';
import { applyTheme, defaultTokens } from './tokens';

export function ThemeProvider({ children }: { children: ReactNode }) {
  useEffect(() => {
    applyTheme(defaultTokens, document.documentElement.style);
    // Phase 3: fetch GET /api/v1/public/settings/theme and call applyTheme with the result.
  }, []);
  return <>{children}</>;
}
