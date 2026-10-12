import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../app/Auth';
import { api } from '../../lib/api';
import type { Transfer, TransferDetail, Discrepancy } from '../../lib/types';
import { CommandForm, Empty, ErrorNotice, Field, Loading, Status, date } from '../../components/UI';
function Receive({ detail }: { detail: TransferDetail }) {
  const [lines, setLines] = useState(
    detail.lines.map((l) => ({
      lotId: l.lot_id,
      quantity: l.quantity,
      condition: 'GOOD',
      reason: '',
    })),
  );
  return (
    <section className="receipt-surface">
      <p className="eyebrow">Receiver’s independent assertion</p>
      <h2>Count what arrived.</h2>
      <p>
        Confirm the physical count and condition. A short receipt leaves the remainder unresolved;
        it never becomes source stock again.
      </p>
      {detail.lines.some((l) => l.held) && (
        <div className="hold-notice">
          ◈ An incident hold applies. Accepted quantities will enter held stock so you can contain
          the shipment.
        </div>
      )}
      <CommandForm
        path={`/transfers/${detail.transfer.id}/receive`}
        label="Accept actual received stock"
        body={() => ({ version: detail.transfer.version, lines })}
      >
        {detail.lines.map((l, i) => (
          <fieldset key={l.lot_id}>
            <legend>
              {l.code} / {l.base_unit} · dispatched {l.quantity}
            </legend>
            <Field label={`Received quantity · ${l.code}`}>
              <input
                inputMode="decimal"
                required
                value={lines[i].quantity}
                onChange={(e) =>
                  setLines((v) =>
                    v.map((item, j) => (i === j ? { ...item, quantity: e.target.value } : item)),
                  )
                }
              />
            </Field>
            <Field label={`Condition · ${l.code}`}>
              <select
                value={lines[i].condition}
                onChange={(e) =>
                  setLines((v) =>
                    v.map((item, j) => (i === j ? { ...item, condition: e.target.value } : item)),
                  )
                }
              >
                <option value="GOOD">Good</option>
                <option value="DAMAGED">Damaged — hold</option>
                <option value="HELD">Hold for inspection</option>
              </select>
            </Field>
            <Field label={`Exception reason · ${l.code}`}>
              <textarea
                value={lines[i].reason}
                onChange={(e) =>
                  setLines((v) =>
                    v.map((item, j) => (i === j ? { ...item, reason: e.target.value } : item)),
                  )
                }
                placeholder="Required for a short receipt or a condition exception"
              />
            </Field>
          </fieldset>
        ))}
      </CommandForm>
    </section>
  );
}
function Resolution({ detail, d }: { detail: TransferDetail; d: Discrepancy }) {
  const { member } = useAuth();
  const manager = member?.role === 'OWNER' || member?.recall_manager;
  const isReceiver = member?.organization_id === detail.transfer.destination_organization_id;
  const isSender = member?.organization_id === detail.transfer.source_organization_id;
  const [disposition, setDisposition] = useState(
    isReceiver ? 'LATER_RECEIVED' : isSender ? 'RETURNED' : 'ACCOUNTABLE_LOSS',
  );
  const [quantity, setQuantity] = useState(d.unresolved_quantity);
  const [reason, setReason] = useState('');
  const [evidence, setEvidence] = useState('');
  return (
    <section className="resolution-surface">
      <h3>Account for the unresolved remainder</h3>
      <CommandForm
        path={`/transfers/${detail.transfer.id}/resolve`}
        label="Record accountable resolution"
        body={() => ({ discrepancyId: d.id, disposition, quantity, reason, evidence })}
      >
        <Field label="Disposition">
          <select value={disposition} onChange={(e) => setDisposition(e.target.value)}>
            {isReceiver && (
              <option value="LATER_RECEIVED">Later received · receiver asserts</option>
            )}
            {isSender && <option value="RETURNED">Returned · sender asserts</option>}
            {manager && (
              <option value="ACCOUNTABLE_LOSS">Accountable loss · manager accepts</option>
            )}
          </select>
        </Field>
        <Field label="Resolution quantity">
          <input
            required
            inputMode="decimal"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
          />
        </Field>
        <Field label="Resolution reason">
          <textarea required value={reason} onChange={(e) => setReason(e.target.value)} />
        </Field>
        <Field label="Resolution evidence reference">
          <input required value={evidence} onChange={(e) => setEvidence(e.target.value)} />
        </Field>
      </CommandForm>
    </section>
  );
}
function Cancel({ detail }: { detail: TransferDetail }) {
  const [reason, setReason] = useState('');
  return (
    <details className="cancel-details">
      <summary>Cancellation / reconciliation</summary>
      <p>
        A dispatched cancellation records unresolved custody until a separate authorized resolution
        accounts for the stock.
      </p>
      <CommandForm
        path={`/transfers/${detail.transfer.id}/cancel`}
        label="Record cancellation"
        body={() => ({ reason, version: detail.transfer.version })}
      >
        <Field label="Cancellation reason">
          <textarea required value={reason} onChange={(e) => setReason(e.target.value)} />
        </Field>
      </CommandForm>
    </details>
  );
}
export default function Handoffs() {
  const { id } = useParams();
  const { member } = useAuth();
  const list = useQuery({
    queryKey: ['handoffs', member?.workspace_id],
    queryFn: () => api.get<Transfer[]>('/transfers'),
  });
  const detail = useQuery({
    queryKey: ['handoff', id, member?.workspace_id],
    queryFn: () => api.get<TransferDetail>(`/transfers/${id}`),
    enabled: !!id,
  });
  const [filter, setFilter] = useState('all');
  const selected = detail.data;
  const t = selected?.transfer;
  const sender =
    t && (member?.role === 'OWNER' || member?.organization_id === t.source_organization_id);
  const receiver = t && member?.organization_id === t.destination_organization_id;
  const mutable = member?.role !== 'AUDITOR';
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">Handoffs / two sides of the same shipment</p>
          <h1>
            The sent count.
            <br className="mobile-only" /> <em>The received count.</em>
          </h1>
        </div>
        <Link className="button secondary" to="/app/trace">
          Prepare a handoff ↗
        </Link>
      </div>
      <div className="handoff-layout">
        <section className="handoff-list">
          <div className="segmented">
            <button className={filter === 'all' ? 'active' : ''} onClick={() => setFilter('all')}>
              All
            </button>
            <button
              className={filter === 'incoming' ? 'active' : ''}
              onClick={() => setFilter('incoming')}
            >
              Incoming
            </button>
            <button
              className={filter === 'outgoing' ? 'active' : ''}
              onClick={() => setFilter('outgoing')}
            >
              Outgoing
            </button>
          </div>
          {list.isPending && <Loading />}
          {list.error && <ErrorNotice error={list.error} retry={() => list.refetch()} />}
          <div className="shipment-list">
            {list.data
              ?.filter(
                (t) =>
                  filter === 'all' ||
                  (filter === 'incoming'
                    ? t.destination_organization_id === member?.organization_id
                    : t.source_organization_id === member?.organization_id),
              )
              .map((t) => (
                <Link
                  to={`/app/handoffs/${t.id}`}
                  key={t.id}
                  className={`shipment-card ${t.id === id ? 'selected' : ''}`}
                >
                  <div>
                    <span className="eyebrow">
                      {t.destination_organization_id === member?.organization_id
                        ? 'INCOMING'
                        : 'OUTGOING'}
                    </span>
                    <Status value={t.status} />
                  </div>
                  <h2>{t.reference}</h2>
                  <p>
                    {t.source_organization} / {t.source_site}
                    <span className="handoff-arrow">↳</span>
                    {t.destination_organization} / {t.destination_site}
                  </p>
                </Link>
              ))}
          </div>
          {list.data?.length === 0 && (
            <Empty title="No handoffs yet.">
              Select current stock in Trace and create a draft. Dispatch and receipt are separate
              assertions.
            </Empty>
          )}
        </section>
        <section className="shipment-evidence">
          {!id && (
            <Empty title="Select a handoff.">
              The evidence stays with the shipment: sender claims, receiver counts and any
              unresolved remainder.
            </Empty>
          )}
          {detail.isPending && id && <Loading />}
          {detail.error && <ErrorNotice error={detail.error} retry={() => detail.refetch()} />}{' '}
          {selected && t && (
            <>
              <div className="panel-heading">
                <div>
                  <p className="eyebrow">Shipment evidence / version {t.version}</p>
                  <h2>{t.reference}</h2>
                </div>
                <Status value={t.status} />
              </div>
              <div
                className="claim-table table-scroll"
                tabIndex={0}
                role="region"
                aria-label="Sender and receiver quantity claims"
              >
                <table>
                  <caption>Both organization claims remain in the record</caption>
                  <thead>
                    <tr>
                      <th>Lot</th>
                      <th>Sender dispatched</th>
                      <th>Receiver accepted</th>
                      <th>Unresolved</th>
                    </tr>
                  </thead>
                  <tbody>
                    {selected.lines.map((l) => {
                      const receipt = selected.receipts.find((r) => r.lot_id === l.lot_id),
                        d = selected.discrepancies.find((d) => d.lot_id === l.lot_id);
                      return (
                        <tr key={l.lot_id}>
                          <td>
                            <b>{l.code}</b>
                            <small>{l.base_unit}</small>
                          </td>
                          <td>{l.quantity}</td>
                          <td>{receipt?.received_quantity ?? 'Awaiting assertion'}</td>
                          <td className={d && d.unresolved_quantity !== '0' ? 'incident-text' : ''}>
                            {d?.unresolved_quantity ?? '—'}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
              {t.status === 'DRAFT' && sender && mutable && (
                <CommandForm
                  path={`/transfers/${id}/dispatch`}
                  label="Confirm dispatch"
                  body={() => ({ version: t.version })}
                >
                  <p>
                    Dispatch consumes available / reserved source stock and creates transfer-bound
                    in-transit quantities. The receiver must then confirm arrival.
                  </p>
                </CommandForm>
              )}
              {t.status === 'DISPATCHED' && receiver && mutable && (
                <Receive key={`${id}-${t.version}`} detail={selected} />
              )}{' '}
              {t.status === 'DISPATCHED' && !receiver && (
                <div className="waiting-note">
                  ↳ Awaiting the destination organization’s receipt. A sender cannot assert it on
                  their behalf.
                </div>
              )}
              {selected.receipts.map((r) => (
                <article className="receipt-fact" key={r.id}>
                  <span className="eyebrow">Receiver claim</span>
                  <p>
                    <b>{r.received_quantity}</b> accepted · {r.condition.toLowerCase()} ·{' '}
                    {r.actor_name}
                  </p>
                  <p>{r.reason}</p>
                  <small>
                    Reported {date(r.occurred_at)} / recorded {date(r.recorded_at)}
                  </small>
                </article>
              ))}
              {selected.discrepancies
                .filter((d) => d.unresolved_quantity !== '0')
                .map((d) =>
                  mutable ? (
                    <Resolution key={d.id} detail={selected} d={d} />
                  ) : (
                    <p key={d.id}>Unresolved: {d.unresolved_quantity}. Read-only membership.</p>
                  ),
                )}
              {selected.resolutions.map((r, i) => (
                <p className="accepted-resolution" key={i}>
                  ✓ {String(r.disposition).replaceAll('_', ' ').toLowerCase()} ·{' '}
                  {String(r.quantity)} · {String(r.reason)}
                  <small>{String(r.evidence)}</small>
                </p>
              ))}
              {sender && mutable && ['DRAFT', 'DISPATCHED'].includes(t.status) && (
                <Cancel detail={selected} />
              )}
              <Link to="/app/evidence" className="text-link">
                Inspect the accepted timeline ↗
              </Link>
            </>
          )}
        </section>
      </div>
    </>
  );
}
