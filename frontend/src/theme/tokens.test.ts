import { describe, expect, it } from 'vitest';
import { applyTheme, hexToRgbTriplet } from './tokens';

describe('theme tokens', () => {
  it('converts hex to an rgb triplet', () => {
    expect(hexToRgbTriplet('#F0616D')).toBe('240 97 109');
  });
  it('rejects invalid colors', () => {
    expect(hexToRgbTriplet('red')).toBeNull();
    expect(hexToRgbTriplet('#12')).toBeNull();
  });
  it('skips invalid values and applies valid ones', () => {
    const set: Record<string, string> = {};
    applyTheme({ primary: '#000000', ink: 'nope' }, { setProperty: (k, v) => (set[k] = v) });
    expect(set).toEqual({ '--primary': '0 0 0' });
  });
});
