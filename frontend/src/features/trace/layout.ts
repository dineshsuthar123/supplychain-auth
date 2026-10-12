import type { Lot, Lineage } from '../../lib/types';
export function layout(lots: Lot[], edges: Lineage[]) {
  const depth = new Map(lots.map((l) => [l.id, 0]));
  let changed = true,
    passes = 0;
  while (changed && passes++ < lots.length) {
    changed = false;
    for (const e of edges) {
      const next = (depth.get(e.input_lot_id) ?? 0) + 1;
      if (next > (depth.get(e.output_lot_id) ?? 0)) {
        depth.set(e.output_lot_id, Math.min(next, lots.length));
        changed = true;
      }
    }
  }
  const rows = new Map<number, number>();
  return lots.map((l) => {
    const d = depth.get(l.id) ?? 0;
    const row = rows.get(d) ?? 0;
    rows.set(d, row + 1);
    return { lot: l, x: 36 + d * 300, y: 64 + row * 300, depth: d };
  });
}
