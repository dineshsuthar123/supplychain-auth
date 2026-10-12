import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api } from '../lib/api';
import type { Graph } from '../lib/types';
import { useAuth } from '../app/Auth';
import { Modal, Field, Loading, ErrorNotice } from './UI';
export default function CommandPalette({ close }: { close: () => void }) {
  const { member } = useAuth();
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const graph = useQuery({
    queryKey: ['palette', member?.workspace_id],
    queryFn: () => api.get<Graph>('/trace'),
  });
  const open = (url: string) => {
    navigate(url);
    close();
  };
  return (
    <Modal title="Find the thread" close={close}>
      <Field label="Search lots, products and workbench destinations">
        <input
          autoFocus
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Lot code, product, handoff…"
        />
      </Field>
      <div className="palette-results">
        {[
          ['Trace', '/app/trace'],
          ['Handoffs', '/app/handoffs'],
          ['Incidents', '/app/incidents'],
          ['Evidence', '/app/evidence'],
          ['Workspace', '/app/workspace'],
        ]
          .filter(([name]) => name.toLowerCase().includes(search.toLowerCase()))
          .map(([name, url]) => (
            <button className="button secondary" key={url} onClick={() => open(url)}>
              {name} ↗
            </button>
          ))}
      </div>
      {graph.isPending && <Loading />}
      {graph.error && <ErrorNotice error={graph.error} />}{' '}
      {graph.data && !graph.data.complete && (
        <p className="muted">
          Results are partial within the server’s graph bounds. No missing branch is treated as
          unaffected.
        </p>
      )}
      <div className="palette-lots">
        {graph.data?.lots
          .filter((l) => `${l.code} ${l.product_name}`.toLowerCase().includes(search.toLowerCase()))
          .slice(0, 30)
          .map((l) => (
            <button key={l.id} onClick={() => open(`/app/trace?lot=${l.id}`)}>
              <b>{l.code}</b>
              <small>
                {l.product_name} / {l.issuer_name}
              </small>
              <span>Inspect evidence ↗</span>
            </button>
          ))}
      </div>
      <p className="muted">
        Search is limited to your authorized network scope. Ctrl / ⌘ K opens this surface.
      </p>
    </Modal>
  );
}
