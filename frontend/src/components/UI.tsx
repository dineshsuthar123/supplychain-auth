import { useRef, useState, useEffect, type ReactNode, type FormEvent } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { ApiError, api } from '../lib/api';
import type { Accepted } from '../lib/types';
export function Brand() {
  return (
    <span className="brand">
      <img src="/lotline.svg" alt="" width="30" height="30" />
      Lotline
    </span>
  );
}
export function Status({ value }: { value: string }) {
  return (
    <span className={`status status-${value.toLowerCase()}`}>
      {['HELD', 'ACTIVE', 'DISPUTED', 'UNRESOLVED'].includes(value)
        ? '◈ '
        : ['RECEIVED', 'RECOVERED', 'CLOSED', 'RECONCILED'].includes(value)
          ? '✓ '
          : '○ '}
      {value.replaceAll('_', ' ').toLowerCase()}
    </span>
  );
}
export function ErrorNotice({ error, retry }: { error: unknown; retry?: () => void }) {
  return (
    <div role="alert" className="error-notice">
      <b>
        {error instanceof ApiError
          ? error.code === 'OFFLINE'
            ? 'Offline'
            : error.code === 'STALE_PREVIEW'
              ? 'Scope changed'
              : error.code === 'BACKEND_WARMING'
                ? 'Backend warming'
                : error.status === 403
                  ? 'Outside your permission'
                  : error.code.replaceAll('_', ' ')
          : 'Could not complete this request'}
      </b>
      <p>{error instanceof Error ? error.message : String(error)}</p>
      {retry && (
        <button type="button" className="button secondary" onClick={retry}>
          Try again
        </button>
      )}
    </div>
  );
}
export function Loading({ label = 'Reading accepted records…' }: { label?: string }) {
  return (
    <div role="status" className="loading">
      <span className="loading-line" />
      {label}
    </div>
  );
}
export function Empty({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="empty-state">
      <span className="empty-mark">↳</span>
      <h2>{title}</h2>
      <p>{children}</p>
    </div>
  );
}
export function Modal({
  title,
  children,
  close,
}: {
  title: string;
  children: ReactNode;
  close: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    if (ref.current && !ref.current.open) ref.current.showModal();
  }, []);
  return (
    <dialog
      ref={ref}
      className="modal"
      aria-labelledby="dialog-heading"
      onCancel={(e) => {
        e.preventDefault();
        close();
      }}
    >
      <div className="modal-header">
        <h2 id="dialog-heading">{title}</h2>
        <button type="button" aria-label="Close dialog" className="icon-button" onClick={close}>
          ×
        </button>
      </div>
      {children}
    </dialog>
  );
}
export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
    </label>
  );
}
export function CommandForm({
  path,
  body,
  children,
  onAccepted,
  label = 'Record command',
  disabled = false,
}: {
  path: string;
  body: () => unknown;
  children: ReactNode;
  onAccepted?: (data: Accepted) => void;
  label?: string;
  disabled?: boolean;
}) {
  const query = useQueryClient();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [accepted, setAccepted] = useState(false);
  const last = useRef({ content: '', key: '' });
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setAccepted(false);
    const payload = body(),
      content = path + JSON.stringify(payload);
    if (last.current.content !== content) last.current = { content, key: crypto.randomUUID() };
    try {
      const result = await api.post<Accepted>(path, payload, last.current.key);
      setAccepted(true);
      await query.invalidateQueries();
      onAccepted?.(result);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };
  return (
    <form className="command-form" onSubmit={submit}>
      {children}
      {error !== null && <ErrorNotice error={error} />}
      <div className="form-footer">
        <button className="button" disabled={disabled || busy}>
          {busy ? 'Recording…' : label}
        </button>
        {accepted && (
          <span role="status" className="success-text">
            ✓ Accepted and recorded
          </span>
        )}
      </div>
      <small className="muted">
        Commands record your organization’s assertion. Use a reference that someone else can review.
      </small>
    </form>
  );
}
export const date = (value: string) =>
  new Date(value).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
