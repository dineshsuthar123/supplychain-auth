import { useRef, useState } from 'react';
import type { Graph, Lineage } from '../../lib/types';
import { layout } from './layout';
import { Status } from '../../components/UI';
export default function TraceCanvas({
  graph,
  selected,
  select,
  selectEdge,
  affected,
}: {
  graph: Graph;
  selected: string | null;
  select: (id: string) => void;
  selectEdge: (edge: Lineage) => void;
  affected: Set<string>;
}) {
  const positions = layout(graph.lots, graph.lineage);
  const width = Math.max(760, ...positions.map((p) => p.x + 244));
  const height = Math.max(450, ...positions.map((p) => p.y + 330));
  const [zoom, setZoom] = useState(1);
  const scroller = useRef<HTMLDivElement>(null);
  return (
    <section className="trace-field" aria-label="Lot genealogy and current holdings">
      <div className="canvas-caption">
        <span>
          LOT GENEALOGY <i>→</i> CURRENT CUSTODY
        </span>
        <span>
          {graph.lots.length} lots · {graph.lineage.length} contributions
        </span>
      </div>
      <div className="canvas-scroll" ref={scroller}>
        <div className="canvas-scaled" style={{ width: width * zoom, height: height * zoom }}>
          <div className="canvas-inner" style={{ width, height, transform: `scale(${zoom})` }}>
            <svg
              className="graph-lines"
              width={width}
              height={height}
              aria-label="Accepted contribution routes"
            >
              <defs>
                <marker id="arrow" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto">
                  <path d="M0,0 L6,3 L0,6" fill="none" stroke="currentColor" />
                </marker>
              </defs>
              {graph.lineage.map((e) => {
                const a = positions.find((p) => p.lot.id === e.input_lot_id),
                  b = positions.find((p) => p.lot.id === e.output_lot_id);
                if (!a || !b) return null;
                const tainted = affected.has(e.input_lot_id) && affected.has(e.output_lot_id);
                return (
                  <g
                    key={`${e.input_lot_id}-${e.output_lot_id}`}
                    className={tainted ? 'affected-route' : affected.size ? 'dimmed-route' : ''}
                    role="button"
                    tabIndex={0}
                    aria-label={`${a.lot.code} consumed into ${b.lot.code}, operation ${e.operation_id}`}
                    onClick={() => selectEdge(e)}
                    onKeyDown={(key) => {
                      if (key.key === 'Enter' || key.key === ' ') {
                        key.preventDefault();
                        selectEdge(e);
                      }
                    }}
                  >
                    <path
                      d={`M${a.x + 204},${a.y + 44} C${a.x + 235},${a.y + 44} ${b.x - 32},${b.y + 44} ${b.x},${b.y + 44}`}
                      style={{ stroke: 'transparent', strokeWidth: 32 }}
                    />
                    <path
                      d={`M${a.x + 204},${a.y + 44} C${a.x + 235},${a.y + 44} ${b.x - 32},${b.y + 44} ${b.x},${b.y + 44}`}
                      markerEnd="url(#arrow)"
                    />
                    <text x={(a.x + 204 + b.x) / 2} y={(a.y + b.y) / 2 + 31} textAnchor="middle">
                      consumed into
                    </text>
                  </g>
                );
              })}
            </svg>
            {positions.map(({ lot, x, y, depth }) => {
              const holdings = graph.holdings.filter(
                (h) => h.lot_id === lot.id && h.status !== 'CONSUMED',
              );
              const held = holdings.some((h) => h.status === 'HELD');
              const historical = holdings.length === 0;
              const dim = affected.size > 0 && !affected.has(lot.id);
              return (
                <div
                  key={lot.id}
                  className={`graph-cluster ${dim ? 'dimmed' : ''}`}
                  style={{ left: x, top: y }}
                >
                  <button
                    className={`lot-node ${selected === lot.id ? 'selected' : ''} ${held ? 'is-held' : ''}`}
                    onClick={() => select(lot.id)}
                    aria-pressed={selected === lot.id}
                  >
                    <span className="node-overline">
                      {depth === 0 ? 'COMMISSIONED LOT' : 'OUTPUT LOT'}
                      <span>{held ? '◈' : '↳'}</span>
                    </span>
                    <b>{lot.code}</b>
                    <span className="node-product">{lot.product_name}</span>
                    <span className="node-footer">
                      {historical
                        ? 'Consumed · historical input'
                        : held
                          ? 'Held · incident containment'
                          : 'Current stock tracked'}
                      <span>↗</span>
                    </span>
                  </button>
                  {holdings.slice(0, 3).map((h, i) => (
                    <button
                      className="holding-node"
                      key={`${h.site_id}-${h.status}-${h.transfer_id}`}
                      onClick={() => select(lot.id)}
                      aria-label={`${lot.code}, ${h.quantity} ${h.base_unit}, ${h.site_name}, ${h.status}`}
                    >
                      <span className="custody-stem">{i === 0 ? '└' : ' '}</span>
                      <span>
                        <small className="holding-edge-label">
                          {h.status === 'IN_TRANSIT'
                            ? 'dispatched'
                            : h.status === 'UNRESOLVED'
                              ? 'unresolved'
                              : 'holding at'}
                        </small>
                        <b>
                          {h.quantity} <small>{h.base_unit}</small>
                        </b>
                        <span>{h.site_name}</span>
                      </span>
                      <Status value={h.status} />
                    </button>
                  ))}
                  {holdings.length > 3 && (
                    <small className="muted">
                      + {holdings.length - 3} more holdings in evidence dock
                    </small>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      </div>
      <div className="canvas-controls">
        <div>
          <button
            className="icon-button"
            aria-label="Pan left"
            onClick={() => scroller.current?.scrollBy({ left: -180 })}
          >
            ←
          </button>
          <button
            className="icon-button"
            aria-label="Pan right"
            onClick={() => scroller.current?.scrollBy({ left: 180 })}
          >
            →
          </button>
          <button
            className="icon-button"
            aria-label="Pan up"
            onClick={() => scroller.current?.scrollBy({ top: -180 })}
          >
            ↑
          </button>
          <button
            className="icon-button"
            aria-label="Pan down"
            onClick={() => scroller.current?.scrollBy({ top: 180 })}
          >
            ↓
          </button>
          <span className="legend-line" />
          Contribution <span className="legend-holding">▱</span>Holding / custody
        </div>
        <div>
          <button
            className="icon-button"
            aria-label="Zoom out"
            onClick={() => setZoom((z) => Math.max(0.5, z - 0.15))}
          >
            −
          </button>
          <button
            className="text-button"
            onClick={() => {
              setZoom(Math.min(1, (scroller.current?.clientWidth ?? width) / width));
              if (scroller.current)
                scroller.current.scrollTo({ left: 0, top: 0, behavior: 'auto' });
            }}
          >
            Fit
          </button>
          <button
            className="icon-button"
            aria-label="Zoom in"
            onClick={() => setZoom((z) => Math.min(1.6, z + 0.15))}
          >
            +
          </button>
        </div>
      </div>
    </section>
  );
}
