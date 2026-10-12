import { describe, it, expect } from 'vitest';
import { decimal, milli, sum } from '../src/lib/quantity';
describe('quantity feedback preserves the API decimal contract', () => {
  it('handles values beyond floating point precision without rounding', () => {
    expect(sum(['99999999999999999.999', '0.001'])).toBe('100000000000000000');
    expect(milli('1.0001')).toBeNull();
    expect(sum(['0.1', '0.2'])).toBe('0.3');
    expect(decimal(-20n)).toBe('-0.02');
  });
});
