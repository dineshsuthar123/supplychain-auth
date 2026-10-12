import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { Graph } from '../../lib/types';
import { CommandForm, Field, ErrorNotice } from '../../components/UI';
interface Observation {
  id: string;
  lot_id: string;
  quantity: string;
  reason: string;
  evidence: string;
  resolved: boolean;
  sequence: number;
}
function Resolution({ id }: { id: string }) {
  const [reason, setReason] = useState('');
  const [evidence, setEvidence] = useState('');
  return (
    <details>
      <summary>Resolve this reported evidence gap</summary>
      <CommandForm
        path={`/observations/${id}/resolve`}
        label="Append gap resolution"
        body={() => ({ reason, evidence })}
      >
        <Field label="Gap resolution reason">
          <textarea required value={reason} onChange={(e) => setReason(e.target.value)} />
        </Field>
        <Field label="Gap resolution evidence">
          <input required value={evidence} onChange={(e) => setEvidence(e.target.value)} />
        </Field>
        <p className="muted">
          Explain what the report actually meant and link accepted reconciliation / disposition
          records. This assertion resolves the evidence question; it does not silently alter
          inventory.
        </p>
      </CommandForm>
    </details>
  );
}
export default function Observations() {
  const [lot, setLot] = useState('');
  const [quantity, setQuantity] = useState('');
  const [reason, setReason] = useState('');
  const [evidence, setEvidence] = useState('');
  const graph = useQuery({
    queryKey: ['observation-lots'],
    queryFn: () => api.get<Graph>('/trace'),
  });
  const reports = useQuery({
    queryKey: ['observations'],
    queryFn: () => api.get<Observation[]>('/observations'),
  });
  return (
    <details className="projection-section">
      <summary>Reported unknown custody / evidence observations</summary>
      <p>
        Untracked stock is an explicit gap. A report does not commission inventory or prove a
        physical count. Open gaps block justified incident closure until a manager records an
        accountable explanation.
      </p>
      {reports.error && <ErrorNotice error={reports.error} />}
      <CommandForm
        path="/observations"
        label="Record unknown custody observation"
        body={() => ({ lotId: lot || graph.data?.lots[0]?.id, quantity, reason, evidence })}
      >
        <Field label="Reported lot">
          <select
            value={lot || graph.data?.lots[0]?.id || ''}
            onChange={(e) => setLot(e.target.value)}
            required
          >
            <option value="">Choose a lot</option>
            {graph.data?.lots.map((l) => (
              <option key={l.id} value={l.id}>
                {l.code} / {l.base_unit}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Reported unknown quantity">
          <input
            required
            inputMode="decimal"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
          />
        </Field>
        <Field label="Unknown custody report">
          <textarea required value={reason} onChange={(e) => setReason(e.target.value)} />
        </Field>
        <Field label="Observation evidence reference">
          <input required value={evidence} onChange={(e) => setEvidence(e.target.value)} />
        </Field>
      </CommandForm>
      {reports.data?.map((r) => (
        <article className="receipt-fact" key={r.id}>
          <p>
            <b>{graph.data?.lots.find((l) => l.id === r.lot_id)?.code ?? r.lot_id}</b> /{' '}
            {r.quantity} reported / sequence {r.sequence}
          </p>
          <p>{r.reason}</p>
          <small>
            {r.evidence} · {r.resolved ? 'resolved by a linked fact' : 'unresolved evidence gap'}
          </small>
          {!r.resolved && <Resolution id={r.id} />}
        </article>
      ))}
    </details>
  );
}
