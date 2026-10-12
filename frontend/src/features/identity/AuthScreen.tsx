import { useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../app/Auth';
import { Brand, Field, ErrorNotice } from '../../components/UI';
export default function AuthScreen({ mode }: { mode: 'login' | 'create' | 'invite' }) {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const key = useRef(crypto.randomUUID());
  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const f = new FormData(e.currentTarget);
    const body = Object.fromEntries(f);
    try {
      await login(
        mode === 'login'
          ? '/auth/login'
          : mode === 'create'
            ? '/auth/register'
            : '/auth/accept-invitation',
        mode === 'invite' ? { ...body, token: params.get('token') ?? '' } : body,
        key.current,
      );
      const next = params.get('next');
      navigate(next?.startsWith('/app') ? next : '/app/trace');
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };
  return (
    <main className="auth-page">
      <Link to="/" className="brand-link">
        <Brand />
      </Link>
      <div className="auth-composition">
        <section className="auth-copy">
          <p className="eyebrow">A shared chain of custody</p>
          <h1>
            {mode === 'login' ? (
              <>
                Pick up
                <br />
                the thread.
              </>
            ) : mode === 'create' ? (
              <>
                Your network.
                <br />
                <em>Accounted for.</em>
              </>
            ) : (
              <>
                A handoff starts
                <br />
                with trust.
              </>
            )}
          </h1>
          <p>
            One workspace connects your manufacturer, warehouses and retail partners. Each
            organization operates its granted sites.
          </p>
          <div className="sharing-note">
            <span>↳</span>
            <p>
              Partners see their own holdings, handoffs and incident targets. Workspace owners and
              granted recall managers see the network needed to contain an incident.
            </p>
          </div>
        </section>
        <section className="auth-card">
          <p className="eyebrow">
            {mode === 'login'
              ? 'Operator access'
              : mode === 'create'
                ? 'Create a workspace'
                : 'Accept your invitation'}
          </p>
          <h2>
            {mode === 'login'
              ? 'Welcome back.'
              : mode === 'create'
                ? 'Begin with your organization.'
                : 'Join a cooperating network.'}
          </h2>
          <form onSubmit={submit} className="command-form">
            {mode !== 'login' && (
              <Field label="Your name">
                <input name="name" autoComplete="name" required maxLength={120} />
              </Field>
            )}
            <Field label="Work email">
              <input name="email" type="email" autoComplete="email" required />
            </Field>
            <Field
              label={
                mode === 'invite'
                  ? 'Password (use your existing account password if you have one)'
                  : 'Password'
              }
            >
              <input
                name="password"
                type="password"
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                required
                minLength={mode === 'login' ? 1 : 12}
                maxLength={72}
              />
            </Field>
            {mode !== 'login' && (
              <small className="muted">
                At least 12 characters. Refresh credentials stay in a secure browser cookie.
              </small>
            )}
            {mode === 'create' && (
              <>
                <Field label="Workspace name">
                  <input
                    name="workspaceName"
                    placeholder="e.g. Meridian coffee network"
                    required
                    maxLength={120}
                  />
                </Field>
                <Field label="Your organization">
                  <input
                    name="organizationName"
                    placeholder="e.g. Meridian Coffee Works"
                    required
                    maxLength={120}
                  />
                </Field>
              </>
            )}
            {error !== null && <ErrorNotice error={error} />}
            <button className="button" disabled={busy}>
              {busy
                ? 'Connecting…'
                : mode === 'login'
                  ? 'Sign in →'
                  : mode === 'create'
                    ? 'Create workspace →'
                    : 'Accept invitation →'}
            </button>
          </form>
          <p className="auth-alternative">
            {mode === 'login' ? (
              <>
                New network? <Link to="/create">Create your workspace</Link>
              </>
            ) : (
              <>
                Already have access? <Link to="/login">Sign in</Link>
              </>
            )}
          </p>
        </section>
      </div>
      <footer className="public-footer">
        <span>Lotline / operational traceability</span>
        <span>Follow the lot. Contain the fallout.</span>
      </footer>
    </main>
  );
}
