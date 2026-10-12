import { useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../app/Auth';
import { api, ApiError } from '../../lib/api';
import type { RecallActivationRequest } from '../../lib/api.generated';
import type { Graph, Recall, RecallDetail, Preview, Target, Accepted } from '../../lib/types';
import { CommandForm, Empty, ErrorNotice, Field, Loading, Status } from '../../components/UI';
function Recovery({ target }: { target: Target }) {
  const [quantity, setQuantity] = useState(target.quantity);
  const [reason, setReason] = useState('');
  const [evidence, setEvidence] = useState('');
  return (
    <details className="recovery-form">
      <summary>Record physical recovery · {target.code}</summary>
      <CommandForm
        path="/inventory/dispositions"
        label="Record recovery"
        body={() => ({
          lotId: target.lot_id,
          siteId: target.site_id,
          kind: 'RECOVER',
          sourceStatus: 'HELD',
          quantity,
          reason,
          evidence,
        })}
      >
        <Field label={`Recovery quantity · ${target.code}`}>
          <input
            required
            inputMode="decimal"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
          />
        </Field>
        <Field label={`Recovery reason · ${target.code}`}>
          <textarea required value={reason} onChange={(e) => setReason(e.target.value)} />
        </Field>
        <Field label={`Recovery evidence · ${target.code}`}>
          <input required value={evidence} onChange={(e) => setEvidence(e.target.value)} />
        </Field>
      </CommandForm>
    </details>
  );
}
function Decision({ id, kind }: { id: string; kind: 'close' | 'reopen' }) {
  const [reason, setReason] = useState('');
  return (
    <CommandForm
      path={`/recalls/${id}/${kind}`}
      label={kind === 'close' ? 'Record authorized closure' : 'Reopen incident'}
      body={() => ({ reason })}
    >
      <Field label={kind === 'close' ? 'Closure justification' : 'Reopening reason'}>
        <textarea
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          required
          placeholder="Describe the decision and its supporting records"
        />
      </Field>
    </CommandForm>
  );
}
function Review({
  graph,
  initialRoot,
  onActivated,
}: {
  graph: Graph;
  initialRoot: string | null;
  onActivated: (r: Accepted) => void;
}) {
  const [roots, setRoots] = useState<string[]>(
    initialRoot ? [initialRoot] : graph.lots[0] ? [graph.lots[0].id] : [],
  );
  const [title, setTitle] = useState('Packaging defect');
  const [reason, setReason] = useState('');
  const [preview, setPreview] = useState<Preview | null>(null);
  const [confirmed, setConfirmed] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const [key, setKey] = useState(crypto.randomUUID());
  const calculate = async () => {
    setBusy(true);
    setError(null);
    setConfirmed(false);
    try {
      setPreview(await api.post<Preview>('/recalls/preview', { roots }));
      setKey(crypto.randomUUID());
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };
  const activate = async () => {
    if (!preview) return;
    setBusy(true);
    setError(null);
    try {
      onActivated(
        await api.post<Accepted>(
          '/recalls',
          {
            roots,
            title,
            reason,
            previewSequence: preview.sequence,
            confirmed,
          } satisfies RecallActivationRequest,
          key,
        ),
      );
    } catch (e) {
      setError(e);
      if (e instanceof ApiError && e.code === 'STALE_PREVIEW') {
        setPreview(e.details as Preview);
        setConfirmed(false);
        setKey(crypto.randomUUID());
      }
    } finally {
      setBusy(false);
    }
  };
  return (
    <section className="recall-review">
      <p className="eyebrow">Scope review / deliberate containment</p>
      <h2>Follow the defect downstream.</h2>
      <p>
        Every mixed output is potentially affected in full. Current holdings and outstanding
        transfers count once; consumed parent stock stays historical.
      </p>
      <Field label="Root lots (select one or more)">
        <select
          multiple
          value={roots}
          onChange={(e) => {
            setRoots(Array.from(e.target.selectedOptions).map((o) => o.value));
            setPreview(null);
            setConfirmed(false);
          }}
          size={Math.min(5, Math.max(2, graph.lots.length))}
        >
          {graph.lots.map((l) => (
            <option key={l.id} value={l.id}>
              {l.code} · {l.product_name}
            </option>
          ))}
        </select>
      </Field>
      <div className="form-row">
        <Field label="Incident title">
          <input
            required
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            maxLength={160}
          />
        </Field>
        <Field label="Recall reason">
          <textarea
            required
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            maxLength={1000}
          />
        </Field>
      </div>
      <button
        className="button secondary"
        disabled={busy || roots.length === 0}
        onClick={calculate}
      >
        {busy ? 'Calculating…' : 'Preview affected scope →'}
      </button>
      {error !== null && <ErrorNotice error={error} />}{' '}
      {preview && (
        <div className="preview-surface">
          <div className="scope-heading">
            <h3>Visible scope · sequence {preview.sequence}</h3>
            <Status value={preview.complete ? 'COMPLETE' : 'INCOMPLETE'} />
          </div>
          <div className="scope-accounting">
            <p>
              <b>{preview.totals.recoverable}</b>
              <span>recoverable at current sites</span>
            </p>
            <p>
              <b>{preview.totals.inTransit}</b>
              <span>in transit</span>
            </p>
            <p className="incident-text">
              <b>{preview.totals.unresolved}</b>
              <span>unresolved custody</span>
            </p>
            <p>
              <b>{preview.totals.unaffected}</b>
              <span>known unrelated stock</span>
            </p>
          </div>
          <div
            className="table-scroll"
            tabIndex={0}
            role="region"
            aria-label="Preview of affected holdings"
          >
            <table>
              <caption>Affected current holdings and accountable terminals</caption>
              <thead>
                <tr>
                  <th>Lot / site</th>
                  <th>Status</th>
                  <th>Quantity</th>
                </tr>
              </thead>
              <tbody>
                {preview.exposure.map((h, i) => (
                  <tr key={i}>
                    <td>
                      <b>{h.code}</b>
                      <small>
                        {h.organization_name} / {h.site_name}
                      </small>
                    </td>
                    <td>
                      <Status value={h.status} />
                    </td>
                    <td>
                      {h.quantity} {h.base_unit}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {preview.gaps.map((g, i) => (
            <p className="hold-notice" key={i}>
              {g.quantity} unresolved · {g.message}
            </p>
          ))}
          <p className="muted">{preview.coverage}</p>
          <label className="check-field">
            <input
              type="checkbox"
              checked={confirmed}
              onChange={(e) => setConfirmed(e.target.checked)}
            />
            I reviewed this scope, its sequence and unresolved quantities. Activate holds and
            require partner responses.
          </label>
          <button
            className="button incident-button"
            disabled={!confirmed || !reason.trim() || !title.trim() || !preview.complete || busy}
            onClick={activate}
          >
            Activate recall & hold stock
          </button>
        </div>
      )}
    </section>
  );
}
export default function Incidents() {
  const cache = useQueryClient();
  const { id } = useParams();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { member } = useAuth();
  const manager = member?.role === 'OWNER' || member?.recall_manager;
  const [create, setCreate] = useState(!!params.get('root'));
  const [ack, setAck] = useState('');
  const queryKey = member?.workspace_id;
  const list = useQuery({
    queryKey: ['incidents', queryKey],
    queryFn: () => api.get<Recall[]>('/recalls'),
  });
  const graph = useQuery({
    queryKey: ['trace', queryKey, null],
    queryFn: () => api.get<Graph>('/trace'),
  });
  const detail = useQuery({
    queryKey: ['incident', id, queryKey],
    queryFn: () => api.get<RecallDetail>(`/recalls/${id}`),
    enabled: !!id,
  });
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">Incidents / contain the fallout</p>
          <h1>
            A clear scope.
            <br className="mobile-only" /> <em>An accountable response.</em>
          </h1>
        </div>
        {manager && (
          <button className="button incident-button" onClick={() => setCreate(!create)}>
            {create ? 'Close scope review' : '＋ Review a new recall'}
          </button>
        )}
      </div>
      {create && manager && graph.data && (
        <Review
          graph={graph.data}
          initialRoot={params.get('root')}
          onActivated={async (r) => {
            await cache.invalidateQueries();
            setCreate(false);
            navigate(`/app/incidents/${r.id}`);
          }}
        />
      )}
      {graph.error && create && <ErrorNotice error={graph.error} />}
      <div className="incident-layout">
        <section className="incident-list">
          {list.isPending && <Loading />}
          {list.error && <ErrorNotice error={list.error} retry={() => list.refetch()} />}{' '}
          {list.data?.length === 0 && (
            <Empty title="No incidents recorded.">
              Start with a root lot and preview the exact scope before activating a hold.
            </Empty>
          )}
          {list.data?.map((r) => (
            <Link
              key={r.id}
              to={`/app/incidents/${r.id}`}
              className={`incident-card ${r.id === id ? 'selected' : ''}`}
            >
              <div>
                <span className="eyebrow">INCIDENT / REV {r.version}</span>
                <Status value={r.status} />
              </div>
              <h2>{r.title}</h2>
              <p>{r.reason}</p>
              <small>Activated at sequence {r.activation_sequence} ↗</small>
            </Link>
          ))}
        </section>
        <section className="incident-workbench">
          {!id && (
            <Empty title="Containment begins with a known path.">
              Select an incident to review target responses, recovery and the quantity closure
              policy.
            </Empty>
          )}
          {detail.isPending && id && <Loading />}
          {detail.error && <ErrorNotice error={detail.error} retry={() => detail.refetch()} />}{' '}
          {detail.data && (
            <>
              <div className="panel-heading">
                <div>
                  <p className="eyebrow">Incident / revision {detail.data.recall.version}</p>
                  <h2>{detail.data.recall.title}</h2>
                </div>
                <Status value={detail.data.recall.status} />
              </div>
              <p>{detail.data.recall.reason}</p>
              <Link className="text-link" to={`/app/trace?incident=${id}`}>
                Focus this incident on the trace canvas ↗
              </Link>
              <div className="closure-policy">
                <p className="eyebrow">Visible closure policy</p>
                <p>{detail.data.policy}</p>
                {manager && (
                  <div className="accounting-line">
                    <b>
                      {detail.data.closure.unaccountedQuantity}{' '}
                      {detail.data.recall.base_unit ?? 'base units'} unaccounted
                    </b>
                    <span>
                      {detail.data.closure.unansweredOrganizations} organizations awaiting response
                      / {detail.data.closure.openEvidenceGaps} evidence gaps
                    </span>
                  </div>
                )}
              </div>
              <div
                className="table-scroll"
                tabIndex={0}
                role="region"
                aria-label="Incident response and recovery matrix"
              >
                <table>
                  <caption>
                    Target matrix · acknowledgment and physical accounting are separate
                  </caption>
                  <thead>
                    <tr>
                      <th>Lot / custodian</th>
                      <th>Target</th>
                      <th>Response</th>
                      <th>Accounted</th>
                      <th>Unresolved</th>
                    </tr>
                  </thead>
                  <tbody>
                    {detail.data.targets.map((t) => (
                      <tr key={t.id}>
                        <td>
                          <b>{t.code}</b>
                          <small>
                            {t.organization_name} / {t.site_name}
                          </small>
                        </td>
                        <td>
                          {t.quantity} {t.base_unit}
                        </td>
                        <td>{t.acknowledged ? '✓ Acknowledged' : 'Awaiting'}</td>
                        <td>{t.accounted}</td>
                        <td className={t.unresolved !== '0' ? 'incident-text' : ''}>
                          {t.unresolved}
                          {t.transfer_id && (
                            <Link to={`/app/handoffs/${t.transfer_id}`}>Review claims ↗</Link>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {member?.role !== 'AUDITOR' &&
                detail.data.targets.some(
                  (t) => t.organization_id === member?.organization_id && !t.acknowledged,
                ) && (
                  <CommandForm
                    path={`/recalls/${id}/acknowledge`}
                    label="Acknowledge notice for my organization"
                    body={() => ({ note: ack })}
                  >
                    <Field label="Acknowledgment note">
                      <textarea required value={ack} onChange={(e) => setAck(e.target.value)} />
                    </Field>
                    <p className="muted">
                      This response confirms receipt of the notice. It does not change recovery
                      totals.
                    </p>
                  </CommandForm>
                )}
              {detail.data.recall.status === 'ACTIVE' &&
                member?.role !== 'AUDITOR' &&
                detail.data.targets
                  .filter(
                    (t) =>
                      !t.transfer_id &&
                      t.quantity !== t.accounted &&
                      (t.organization_id === member?.organization_id || member?.role === 'OWNER'),
                  )
                  .map((t) => <Recovery key={t.id} target={t} />)}
              {manager && detail.data.recall.status === 'ACTIVE' && (
                <>
                  <div
                    className={`closure-state ${detail.data.closure.canClose ? 'ready' : 'blocked'}`}
                  >
                    {detail.data.closure.canClose
                      ? '✓ Responses and quantities satisfy the closure policy. Record your decision.'
                      : '◈ Closure stays blocked until responses and all affected quantities are accounted for.'}
                  </div>
                  <Decision id={id!} kind="close" />
                </>
              )}
              {manager && detail.data.recall.status === 'CLOSED' && (
                <Decision id={id!} kind="reopen" />
              )}
              <details className="revision-list">
                <summary>
                  Original incident and scope revisions ({detail.data.revisions.length})
                </summary>
                {detail.data.revisions.map((r) => (
                  <article key={r.revision}>
                    <b>
                      Revision {r.revision} / sequence {r.sequence}
                    </b>
                    <p>{r.reason}</p>
                  </article>
                ))}
              </details>
            </>
          )}
        </section>
      </div>
    </>
  );
}
