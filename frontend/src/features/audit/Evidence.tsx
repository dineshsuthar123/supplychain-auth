import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../app/Auth';
import { api, download } from '../../lib/api';
import type { EventFact } from '../../lib/types';
import { CommandForm, Empty, ErrorNotice, Loading, date } from '../../components/UI';
import Observations from './Observations';
export default function Evidence() {
  const { member } = useAuth();
  const [params] = useSearchParams();
  const [cursor, setCursor] = useState(Number(params.get('after') ?? 0));
  const [error, setError] = useState<unknown>(null);
  const [exporting, setExporting] = useState(false);
  const [projection, setProjection] = useState<{
    matches: boolean;
    sequence: number;
    differences: unknown[];
  } | null>(null);
  const timeline = useQuery({
    queryKey: ['events', member?.workspace_id, cursor],
    queryFn: () =>
      api.get<{ sequence: number; events: EventFact[]; nextCursor: number; hasMore: boolean }>(
        `/events?after=${cursor}&limit=100`,
      ),
  });
  const exportPacket = async () => {
    setExporting(true);
    setError(null);
    try {
      download(await api.get<Blob>('/evidence/export'), 'lotline-evidence.zip');
    } catch (e) {
      setError(e);
    } finally {
      setExporting(false);
    }
  };
  const check = async () => {
    try {
      setProjection(await api.get('/evidence/projection-check'));
    } catch (e) {
      setError(e);
    }
  };
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">Evidence / accepted facts, retained</p>
          <h1>
            The story has <em>a record.</em>
          </h1>
        </div>
        <button className="button" onClick={exportPacket} disabled={exporting}>
          {exporting ? 'Building snapshot…' : 'Export complete evidence packet ↗'}
        </button>
      </div>
      <div className="evidence-intro">
        <p>
          Every command carries an actor, organization, server time and committed sequence. Late
          reports appear when recorded; earlier views keep what was known then.
        </p>
        <p>
          The packet includes JSON facts and journal, exposure and response CSVs, a printable HTML
          report, and a SHA-256 manifest. Verify changed bytes locally with{' '}
          <code>python scripts/verify-evidence.py lotline-evidence.zip</code>.
        </p>
      </div>
      {error !== null && <ErrorNotice error={error} />}{' '}
      {(member?.role === 'OWNER' || member?.recall_manager) && (
        <details className="projection-section">
          <summary>Check / rebuild the derived balance projection</summary>
          <p>
            Accepted journal entries are authoritative. This comparison rebuilds quantities in SQL
            and checks the current projection.
          </p>
          <button className="button secondary" onClick={check}>
            Compare journal with current balances
          </button>
          {projection && (
            <p role="status">
              {projection.matches
                ? '✓ Projection matches all accepted journal quantities.'
                : '◈ Projection differs. Review the discrepancy before rebuilding.'}{' '}
              Sequence {projection.sequence}.
            </p>
          )}
          {projection && !projection.matches && (
            <CommandForm
              path="/evidence/rebuild"
              label="Rebuild balances from accepted journal"
              body={() => ({})}
            >
              <p>
                Replace this workspace’s derived balance projection from accepted facts. The journal
                is preserved.
              </p>
            </CommandForm>
          )}
        </details>
      )}{' '}
      {(member?.role === 'OWNER' || member?.recall_manager) && <Observations />}
      {timeline.isPending && <Loading />}
      {timeline.error && (
        <ErrorNotice error={timeline.error} retry={() => timeline.refetch()} />
      )}{' '}
      {timeline.data?.events.length === 0 && (
        <Empty title="The record begins with your first accepted command.">
          Create a site, product and lot to start an operational timeline.
        </Empty>
      )}
      <ol className="evidence-timeline">
        {timeline.data?.events.map((e) => (
          <li key={e.id}>
            <Link
              className="timeline-sequence mono"
              to={`/app/trace?at=${e.sequence}`}
              aria-label={`Inspect historical sequence ${e.sequence}`}
            >
              {String(e.sequence).padStart(3, '0')} ↗
            </Link>
            <div className="timeline-fact">
              <p className="eyebrow">{e.type.split(':')[0].replaceAll('_', ' ')}</p>
              <h2>
                {e.actor_name} <span>/ {e.organization_name}</span>
              </h2>
              <div className="event-times">
                <span>Reported {date(e.occurred_at)}</span>
                <span>Recorded {date(e.recorded_at)}</span>
              </div>
              <details>
                <summary>Inspect command payload & identifiers</summary>
                <pre>{JSON.stringify(e.payload, null, 2)}</pre>
                <small>
                  Event {e.id}
                  <br />
                  Command {e.command_id}
                  <br />
                  Payload schema {e.schema_version}
                </small>
              </details>
            </div>
          </li>
        ))}
      </ol>
      <div className="pagination">
        <button className="button secondary" disabled={!cursor} onClick={() => setCursor(0)}>
          First events
        </button>
        <span>Snapshot sequence {timeline.data?.sequence ?? '…'}</span>
        <button
          className="button secondary"
          disabled={!timeline.data?.hasMore}
          onClick={() => setCursor(timeline.data!.nextCursor)}
        >
          Next events →
        </button>
      </div>
    </>
  );
}
