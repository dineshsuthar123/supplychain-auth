import { useState } from 'react';
import { Link, useSearchParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../app/Auth';
import { api } from '../../lib/api';
import type { Graph, Workspace, Product, RecallDetail, Lineage } from '../../lib/types';
import { Empty, ErrorNotice, Loading, Status, Modal, date } from '../../components/UI';
import TraceCanvas from './TraceCanvas';
import Workflow, { type WorkflowKind } from './Workflow';
export default function Trace() {
  const { member } = useAuth();
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const selected = params.get('lot');
  const selectedOperation = params.get('operation');
  const chooseEdge = (edge: Lineage) =>
    setParams((p) => {
      p.set('lot', edge.output_lot_id);
      p.set('operation', edge.operation_id);
      return p;
    });
  const at = params.get('at');
  const incident = params.get('incident');
  const [mode, setMode] = useState<'canvas' | 'table'>('canvas');
  const [search, setSearch] = useState('');
  const [workflow, setWorkflow] = useState<WorkflowKind | null>(null);
  const graph = useQuery({
    queryKey: ['trace', member?.workspace_id, at],
    queryFn: () => api.get<Graph>(`/trace${at ? `?sequence=${at}` : ''}`),
  });
  const workspace = useQuery({
    queryKey: ['workspace', member?.workspace_id],
    queryFn: () => api.get<Workspace>('/workspace'),
  });
  const products = useQuery({
    queryKey: ['products', member?.workspace_id],
    queryFn: () => api.get<Product[]>('/products'),
  });
  const lens = useQuery({
    queryKey: ['incident', incident, member?.workspace_id],
    queryFn: () => api.get<RecallDetail>(`/recalls/${incident}`),
    enabled: !!incident,
  });
  const choose = (id: string) =>
    setParams((p) => {
      p.set('lot', id);
      p.delete('operation');
      return p;
    });
  const setAt = (seq: string) =>
    setParams((p) => {
      if (seq === 'live') p.delete('at');
      else p.set('at', seq);
      return p;
    });
  if (graph.isPending) return <Loading />;
  if (graph.error) return <ErrorNotice error={graph.error} retry={() => graph.refetch()} />;
  const data = graph.data;
  const edge = data.lineage.find(
    (e) => e.operation_id === selectedOperation && e.output_lot_id === selected,
  );
  const lot = data.lots.find((l) => l.id === selected) ?? data.lots[0];
  const filtered = {
    ...data,
    lots: search
      ? data.lots.filter((l) =>
          `${l.code} ${l.product_name}`.toLowerCase().includes(search.toLowerCase()),
        )
      : data.lots,
  };
  const affected = new Set(lens.data?.targets.map((t) => t.lot_id) ?? []);
  const historical = data.historical;
  const mutable = !historical && member?.role !== 'AUDITOR';
  const manufacturer = member?.role === 'OWNER' || member?.role === 'OPERATOR';
  const holdings = data.holdings.filter((h) => h.lot_id === lot?.id);
  const selectedEvents = data.events.filter(
    (e) =>
      JSON.stringify(e.payload).includes(lot?.id ?? 'nonexistent') ||
      JSON.stringify(e.payload).includes(lot?.code ?? 'nonexistent'),
  );
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">Trace / the connected lot</p>
          <h1>
            Every branch has <em>a bearing.</em>
          </h1>
        </div>
        <button
          className="button"
          onClick={() => setWorkflow('commission')}
          disabled={!mutable || !manufacturer}
        >
          + Commission lot
        </button>
      </div>
      {historical && (
        <div className="historical-banner" role="status">
          <b>Historical view · sequence {data.sequence}</b>
          <span>What the system knew at this committed point. Mutations are disabled.</span>
          <button className="text-button" onClick={() => setAt('live')}>
            Return to live ↗
          </button>
        </div>
      )}
      {(workspace.error || products.error) && (
        <ErrorNotice error={workspace.error ?? products.error} />
      )}{' '}
      {!data.complete && (
        <div className="error-notice" role="alert">
          Partial graph: the server’s traversal / holding bounds were reached. This view cannot
          establish a complete recall scope.
        </div>
      )}
      {incident && (
        <div className="incident-lens">
          <span>◈ Incident lens · {lens.data?.recall.title ?? 'Loading incident'}</span>
          <button
            className="text-button"
            onClick={() =>
              setParams((p) => {
                p.delete('incident');
                return p;
              })
            }
          >
            Clear lens ×
          </button>
        </div>
      )}
      <div className="workbench-toolbar">
        <label className="search-field">
          <span className="sr-only">Search lot codes or products</span>
          <span aria-hidden="true">⌕</span>
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Find a lot or product…"
          />
        </label>
        <div className="segmented" aria-label="Trace view">
          <button className={mode === 'canvas' ? 'active' : ''} onClick={() => setMode('canvas')}>
            Canvas
          </button>
          <button className={mode === 'table' ? 'active' : ''} onClick={() => setMode('table')}>
            Accessible list
          </button>
        </div>
        <span className="sequence-label">
          SEQ {data.sequence} / {historical ? 'HISTORICAL' : 'LIVE'}
        </span>
      </div>
      {!data.lots.length ? (
        <Empty title="Your first lot starts the thread.">
          Create a product and site in <Link to="/app/workspace">workspace administration</Link>,
          then commission stock here. Handoffs and incidents will build from accepted events.
        </Empty>
      ) : (
        <div className="workbench">
          <div className="graph-area">
            {mode === 'canvas' ? (
              <TraceCanvas
                graph={filtered}
                selected={lot?.id ?? null}
                select={choose}
                selectEdge={chooseEdge}
                affected={affected}
              />
            ) : (
              <div
                className="table-scroll"
                tabIndex={0}
                role="region"
                aria-label="Accessible lot holdings"
              >
                <table>
                  <caption>Lots and accepted contribution relationships</caption>
                  <thead>
                    <tr>
                      <th>Lot code</th>
                      <th>Product / unit</th>
                      <th>Issuer</th>
                      <th>Current holdings</th>
                      <th>Evidence</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filtered.lots.map((l) => (
                      <tr key={l.id}>
                        <td>
                          <b>{l.code}</b>
                        </td>
                        <td>
                          {l.product_name}
                          <small>{l.base_unit}</small>
                        </td>
                        <td>{l.issuer_name}</td>
                        <td>
                          {data.holdings
                            .filter((h) => h.lot_id === l.id && h.status !== 'CONSUMED')
                            .map((h) => (
                              <span
                                className="table-holding"
                                key={`${h.site_id}-${h.status}-${h.transfer_id}`}
                              >
                                {h.quantity} {h.base_unit} · {h.site_name} ·{' '}
                                {h.status.toLowerCase()}
                              </span>
                            ))}
                        </td>
                        <td>
                          <button className="text-button" onClick={() => choose(l.id)}>
                            Select ↗
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <section
                  className="contribution-list"
                  aria-label="Accessible contribution relationships"
                >
                  <h3>Accepted contributions</h3>
                  {data.lineage.map((e) => (
                    <button
                      className="text-button"
                      key={`${e.input_lot_id}-${e.output_lot_id}`}
                      onClick={() => chooseEdge(e)}
                    >
                      {data.lots.find((l) => l.id === e.input_lot_id)?.code} consumed into{' '}
                      {data.lots.find((l) => l.id === e.output_lot_id)?.code} / sequence{' '}
                      {e.sequence} ↗
                    </button>
                  ))}
                </section>
              </div>
            )}
          </div>
          <aside className="evidence-dock" aria-label="Selected lot evidence" tabIndex={0}>
            <div className="dock-heading">
              <span className="eyebrow">Evidence dock</span>
              <span>↗</span>
            </div>
            {lot && (
              <>
                <p className="dock-code">{lot.code}</p>
                <p>{lot.product_name}</p>
                <dl className="facts">
                  <div>
                    <dt>Asserted origin</dt>
                    <dd>{lot.issuer_name}</dd>
                  </div>
                  <div>
                    <dt>Issue / expiry</dt>
                    <dd>
                      {lot.issued_on} / {lot.expires_on ?? 'not set'}
                    </dd>
                  </div>
                  <div>
                    <dt>Identity</dt>
                    <dd className="mono small">{lot.id}</dd>
                  </div>
                </dl>
                <h3>Current custody</h3>
                {edge && (
                  <div className="selected-contribution">
                    <b>
                      {data.lots.find((l) => l.id === edge.input_lot_id)?.code} →{' '}
                      {data.lots.find((l) => l.id === edge.output_lot_id)?.code}
                    </b>
                    <p>
                      Accepted contribution · consumed into a newly identified output. This is
                      genealogy, distinct from a location handoff.
                    </p>
                    <small>
                      Operation {edge.operation_id}
                      <br />
                      Committed sequence {edge.sequence}
                    </small>
                    <Link to={`/app/evidence?after=${Math.max(0, edge.sequence - 1)}`}>
                      Inspect the accepted assertion ↗
                    </Link>
                  </div>
                )}
                {holdings
                  .filter((h) => h.status !== 'CONSUMED')
                  .map((h) => (
                    <div className="dock-holding" key={`${h.site_id}-${h.status}-${h.transfer_id}`}>
                      <div>
                        <b>
                          {h.quantity} <small>{h.base_unit}</small>
                        </b>
                        <Status value={h.status} />
                      </div>
                      <p>
                        {h.organization_name}
                        <br />
                        {h.site_name}
                      </p>
                      {h.transfer_id && (
                        <Link to={`/app/handoffs/${h.transfer_id}`}>Review shipment claims ↗</Link>
                      )}
                    </div>
                  ))}
                {holdings.every((h) => h.status === 'CONSUMED') && (
                  <p className="muted">
                    Input stock was consumed. Its historical volume contributes to output lots and
                    is excluded from current exposure.
                  </p>
                )}
                <h3>Permitted next action</h3>
                <div className="dock-actions">
                  <button
                    className="button secondary"
                    disabled={!mutable}
                    onClick={() => setWorkflow('dispatch')}
                  >
                    Create handoff ↗
                  </button>
                  {manufacturer && (
                    <button
                      className="button secondary"
                      disabled={!mutable}
                      onClick={() => setWorkflow('operation')}
                    >
                      Split / merge / repackage
                    </button>
                  )}
                  <button
                    className="button secondary"
                    disabled={!mutable}
                    onClick={() => setWorkflow('disposition')}
                  >
                    Hold / recover / dispose
                  </button>
                  {(member?.role === 'OWNER' || member?.recall_manager) && (
                    <>
                      <Link
                        className={`button incident-button ${historical ? 'disabled' : ''}`}
                        aria-disabled={historical}
                        tabIndex={historical ? -1 : 0}
                        to={historical ? '#' : `/app/incidents?root=${lot.id}`}
                      >
                        Review recall scope ↗
                      </Link>
                      <button
                        className="text-button"
                        disabled={!mutable}
                        onClick={() => setWorkflow('correction')}
                      >
                        Append linked correction
                      </button>
                    </>
                  )}
                </div>
                <h3>Recent assertions</h3>
                {selectedEvents
                  .slice(-3)
                  .reverse()
                  .map((e) => (
                    <div className="dock-event" key={e.id}>
                      <b>{e.type.split(':')[0].replaceAll('_', ' ').toLowerCase()}</b>
                      <small>
                        {e.actor_name} / {e.organization_name}
                      </small>
                      <small>
                        Reported {date(e.occurred_at)}
                        <br />
                        Recorded {date(e.recorded_at)} · seq {e.sequence}
                      </small>
                      <code>{e.id}</code>
                    </div>
                  ))}
                {!selectedEvents.length && (
                  <p className="muted">See the full evidence timeline for earlier assertions.</p>
                )}
              </>
            )}
          </aside>
        </div>
      )}
      <section className="event-ribbon" aria-label="Historical event ribbon">
        <div className="ribbon-control">
          <div>
            <p className="eyebrow">The accepted sequence</p>
            <label htmlFor="time-scrubber">
              {historical
                ? `Sequence ${data.sequence} · read only`
                : 'Live · what the network knows now'}
            </label>
          </div>
          <input
            id="time-scrubber"
            type="range"
            min="0"
            max={workspace.data?.workspace.sequence ?? data.sequence}
            value={data.sequence}
            onChange={(e) => setAt(e.target.value)}
            aria-label="Historical sequence"
          />
          <button className="text-button" onClick={() => setAt('live')}>
            Live ↗
          </button>
          <Link to="/app/evidence">Full timeline ↗</Link>
        </div>
        <div className="ribbon-events">
          {data.events.slice(-12).map((e) => (
            <button key={e.id} onClick={() => setAt(String(e.sequence))}>
              <span className="mono">{String(e.sequence).padStart(3, '0')}</span>
              <b>{e.type.split(':')[0].replaceAll('_', ' ').toLowerCase()}</b>
              <small>
                {e.actor_name} · {date(e.recorded_at)}
              </small>
            </button>
          ))}
        </div>
      </section>
      {workflow && (!workspace.data || !products.data) && (
        <Modal title="Loading workflow context" close={() => setWorkflow(null)}>
          <Loading />
          {(workspace.error || products.error) && (
            <ErrorNotice error={workspace.error ?? products.error} />
          )}
        </Modal>
      )}
      {workflow && workspace.data && products.data && (
        <Modal
          title={
            {
              commission: 'Commission a lot',
              operation: 'Quantity operation',
              dispatch: 'Prepare a handoff',
              disposition: 'Physical stock accounting',
              correction: 'Linked evidence correction',
            }[workflow]
          }
          close={() => setWorkflow(null)}
        >
          <Workflow
            kind={workflow}
            graph={data}
            workspace={workspace.data}
            products={products.data}
            selected={lot?.id ?? null}
            done={(r) => {
              setWorkflow(null);
              if (workflow === 'dispatch') navigate(`/app/handoffs/${r.id}`);
              else if (workflow === 'commission') choose(r.id);
            }}
          />
        </Modal>
      )}
    </>
  );
}
