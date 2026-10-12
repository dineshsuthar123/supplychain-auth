import { lazy, Suspense, useState, useEffect } from 'react';
import {
  Routes,
  Route,
  Navigate,
  Link,
  NavLink,
  Outlet,
  useNavigate,
  useLocation,
} from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from './Auth';
import { api, download } from '../lib/api';
import type { Notification } from '../lib/types';
import { Brand, ErrorNotice, Loading, date } from '../components/UI';
import Story from '../features/story/Story';
import MotionFrame from '../components/MotionFrame';
import { useMotionMode, toggleMotion } from '../lib/motion';
const AuthScreen = lazy(() => import('../features/identity/AuthScreen'));
const Handoffs = lazy(() => import('../features/custody/Handoffs'));
const Incidents = lazy(() => import('../features/recall/Incidents'));
const Administration = lazy(() => import('../features/workspace/Administration'));
const Evidence = lazy(() => import('../features/audit/Evidence'));
const CommandPalette = lazy(() => import('../components/CommandPalette'));
const Trace = lazy(() => import('../features/trace/Trace'));
function Shell() {
  const motionMode = useMotionMode();
  const { session, member, status, error, retry, logout, switchWorkspace } = useAuth();
  const query = useQueryClient();
  const navigate = useNavigate();
  const location = useLocation();
  const [menu, setMenu] = useState(false);
  const [palette, setPalette] = useState(false);
  useEffect(() => {
    const key = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        setPalette((p) => !p);
      }
    };
    window.addEventListener('keydown', key);
    return () => window.removeEventListener('keydown', key);
  }, []);
  const [inbox, setInbox] = useState(false);
  const [theme, setTheme] = useState(() => localStorage.getItem('lotline-theme') ?? 'light');
  const [exportError, setExportError] = useState<unknown>(null);
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem('lotline-theme', theme);
  }, [theme]);
  const notifications = useQuery({
    queryKey: ['inbox', member?.workspace_id],
    queryFn: () => api.get<Notification[]>('/inbox'),
    enabled: !!member,
    refetchInterval: false,
  });
  if (status === 'loading')
    return (
      <div className="auth-loading">
        <Brand />
        <Loading label="Restoring your secure session…" />
      </div>
    );
  if (status === 'unavailable')
    return (
      <div className="auth-loading">
        <Brand />
        <ErrorNotice error={new Error(error)} retry={retry} />
        <Link to="/">Return to the story</Link>
      </div>
    );
  if (!session || !member)
    return (
      <Navigate
        to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`}
        replace
      />
    );
  const exportPacket = async () => {
    setExportError(null);
    try {
      download(await api.get<Blob>('/evidence/export'), 'lotline-evidence.zip');
    } catch (e) {
      setExportError(e);
    }
  };
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main">
        Skip to workbench
      </a>
      <header className="command-rail">
        <Link to="/app/trace" className="brand-link">
          <Brand />
        </Link>
        <div className="workspace-switch">
          <button className="workspace-button" aria-expanded={menu} onClick={() => setMenu(!menu)}>
            {member.workspace_name}
            <span>⌄</span>
          </button>
          {menu && (
            <div className="workspace-menu">
              <p className="eyebrow">Workspace network</p>
              {session.memberships.map((m) => (
                <button
                  key={m.workspace_id}
                  onClick={async () => {
                    await switchWorkspace(m.workspace_id);
                    query.clear();
                    setMenu(false);
                    navigate('/app/trace');
                  }}
                >
                  {m.active ? '✓ ' : ''}
                  {m.workspace_name}
                </button>
              ))}
              <Link to="/app/workspace" onClick={() => setMenu(false)}>
                Organizations & permissions ↗
              </Link>
              <Link to="/app/evidence" onClick={() => setMenu(false)}>
                Evidence & projection check ↗
              </Link>
              <button onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')}>
                {theme === 'light' ? 'Switch to dusk appearance' : 'Switch to paper appearance'}
              </button>
              <button onClick={toggleMotion}>
                {motionMode === 'reduced'
                  ? 'Motion settings · use device preference'
                  : 'Motion settings · reduce animation'}
              </button>
              <button
                onClick={async () => {
                  try {
                    await logout();
                    query.clear();
                    navigate('/login');
                  } catch (e) {
                    setExportError(e);
                  }
                }}
              >
                Sign out · {session.user.name}
              </button>
            </div>
          )}
        </div>
        <nav className="main-nav" aria-label="Main navigation">
          <NavLink to="/app/trace">Trace</NavLink>
          <NavLink to="/app/handoffs">Handoffs</NavLink>
          <NavLink to="/app/incidents">Incidents</NavLink>
        </nav>
        <div className="rail-end">
          <button
            className="icon-button"
            aria-label="Search lots and commands"
            onClick={() => setPalette(true)}
          >
            ⌕
          </button>
          <button
            className="icon-button inbox-button"
            aria-label="Open inbox"
            aria-expanded={inbox}
            onClick={() => setInbox(!inbox)}
          >
            ↧{notifications.data?.some((n) => !n.read_at) && <span className="unread-dot" />}
          </button>
          <button
            className="avatar"
            title={`${session.user.name} · ${member.role.toLowerCase()}`}
            aria-label="Operator profile and workspace settings"
            aria-expanded={menu}
            onClick={() => setMenu(!menu)}
          >
            {session.user.name.slice(0, 1)}
          </button>
        </div>
      </header>
      {palette && (
        <Suspense fallback={<Loading />}>
          <CommandPalette close={() => setPalette(false)} />
        </Suspense>
      )}
      {inbox && (
        <aside className="inbox-panel" aria-label="Partner notifications">
          <div className="panel-heading">
            <h2>Your inbox</h2>
            <button
              className="icon-button"
              aria-label="Close inbox"
              onClick={() => setInbox(false)}
            >
              ×
            </button>
          </div>
          {notifications.error && <ErrorNotice error={notifications.error} />}
          <p className="muted">
            Notifications arrive after committed commands. Holds take effect immediately.
          </p>
          {notifications.data?.length === 0 && <p>No notifications yet.</p>}
          {notifications.data?.map((n) => (
            <button
              key={n.id}
              className={`notification ${n.read_at ? '' : 'unread'}`}
              onClick={async () => {
                await api.post(`/inbox/${n.id}/read`, undefined, crypto.randomUUID());
                await query.invalidateQueries({ queryKey: ['inbox'] });
              }}
            >
              <span>{n.message}</span>
              <small>
                {date(n.created_at)} · {n.read_at ? 'read' : 'mark read'}
              </small>
            </button>
          ))}
        </aside>
      )}
      {exportError !== null && <ErrorNotice error={exportError} />}
      <main id="main" className="app-main">
        <div className="context-line">
          <span>
            <i className="live-dot" />
            {member.organization_name}
            <span className="context-role">
              {' '}
              / {member.role.replaceAll('_', ' ').toLowerCase()}
            </span>
          </span>
          <button className="text-button" onClick={exportPacket}>
            Export evidence ↗
          </button>
        </div>
        <Suspense fallback={<Loading />}>
          <Outlet />
        </Suspense>
      </main>
      <footer className="app-footer">
        <span>Follow the lot. Contain the fallout.</span>
        <span>Connected records · partner assertions</span>
      </footer>
    </div>
  );
}
export default function App() {
  return (
    <MotionFrame>
      <Routes>
        <Route
          path="/"
          element={
            <Suspense fallback={<Loading />}>
              <Story />
            </Suspense>
          }
        />
        <Route path="/login" element={<AuthScreen mode="login" />} />
        <Route path="/create" element={<AuthScreen mode="create" />} />
        <Route path="/invite" element={<AuthScreen mode="invite" />} />
        <Route path="/app" element={<Shell />}>
          <Route index element={<Navigate to="trace" replace />} />
          <Route path="trace" element={<Trace />} />
          <Route path="handoffs" element={<Handoffs />} />
          <Route path="handoffs/:id" element={<Handoffs />} />
          <Route path="incidents" element={<Incidents />} />
          <Route path="incidents/:id" element={<Incidents />} />
          <Route path="workspace" element={<Administration />} />
          <Route path="evidence" element={<Evidence />} />
        </Route>
        <Route
          path="*"
          element={
            <div className="auth-loading">
              <Brand />
              <h1>This path is outside the network.</h1>
              <Link to="/app/trace">Open your trace workbench</Link>
            </div>
          }
        />
      </Routes>
    </MotionFrame>
  );
}
