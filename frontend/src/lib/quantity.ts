export function milli(value: string): bigint | null {
  if (!/^\d{1,17}(\.\d{1,3})?$/.test(value)) return null;
  const [whole, fraction = ''] = value.split('.');
  return BigInt(whole) * 1000n + BigInt(fraction.padEnd(3, '0'));
}
export function decimal(value: bigint): string {
  const sign = value < 0n ? '-' : '';
  const n = value < 0n ? -value : value;
  return sign + `${n / 1000n}.${(n % 1000n).toString().padStart(3, '0')}`.replace(/\.?0+$/, '');
}
export function sum(values: string[]): string {
  let n = 0n;
  for (const value of values) {
    const q = milli(value);
    if (q === null) return 'Check precision';
    n += q;
  }
  return decimal(n);
}
