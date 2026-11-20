import React, { useCallback, useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { api, AuthProvider, useAuth } from './auth';
import CaptureFlow from './CaptureFlow';
import { ledgerLabel, serviceError, verdict } from './inspection';
import { Brand, Button, Data, Heading, Icon, Notice, Status, Time } from './ui';

const nav = [
  { id: 'overview', label: 'Overview' },
  { id: 'verify', label: 'Inspection desk' },
  { id: 'enroll', label: 'Enroll reference' },
  { id: 'audit', label: 'Evidence log' }
];

function permissions(role) {
  return {
    enroll: ['ADMIN', 'MANUFACTURER'].includes(role),
    verify: ['ADMIN', 'MANUFACTURER', 'VERIFIER'].includes(role),
    read: ['ADMIN', 'MANUFACTURER', 'VERIFIER', 'AUDITOR'].includes(role)
  };
}

const signupRoleLabels = {
  USER: 'Standard User',
  MANUFACTURER: 'Manufacturer',
  VERIFIER: 'Verifier'
};

function SignIn() {
  const { signIn, expired } = useAuth();
  const [mode, setMode] = useState('login');
  const [issue, setIssue] = useState(null);
  const [busy, setBusy] = useState(false);
  const [registrationRoles, setRegistrationRoles] = useState(null);
  const pending = useRef(false);
  const register = mode === 'register';

  useEffect(() => {
    if (!register) return;
    const controller = new AbortController();
    api.get('/auth/registration-options', { signal: controller.signal, timeout: 5000 }).then(({ data }) => {
      const serverRoles = Array.isArray(data?.roles) ? data.roles : [];
      setRegistrationRoles(Object.keys(signupRoleLabels).filter(role => role === 'USER' || serverRoles.includes(role)));
    }).catch(error => {
      if (!axios.isCancel(error)) setRegistrationRoles(['USER']);
    });
    return () => controller.abort();
  }, [register]);

  async function submit(event) {
    event.preventDefault();
    if (pending.current || (register && !registrationRoles)) return;
    pending.current = true;
    const fields = Object.fromEntries(new FormData(event.currentTarget));
    setBusy(true);
    setIssue(null);
    try {
      await signIn(mode, register ? { ...fields, displayName: fields.username } : fields);
    } catch (error) {
      setIssue(serviceError(error, register ? 'Account creation failed' : 'Sign-in failed'));
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }

  return <div className="signin">
    <header className="site-header"><div className="header-inner"><Brand /></div></header>
    <main className="signin-main">
      <section className="signin-intro">
        <p className="eyebrow ruled">Product authentication</p>
        <h1>Inspection <em>desk.</em></h1>
        <p className="lede">Enroll components, verify their visual fingerprints, and inspect the evidence.</p>
        <div className="signin-reticle" aria-hidden="true"><Icon name="scope" /></div>
      </section>
      <section className="signin-panel">
        <h2>{register ? 'Create an account' : 'Sign in'}</h2>
        {expired && <p role="status">Your session expired. Sign in to continue.</p>}
        <form onSubmit={submit} aria-busy={busy} key={mode}>
          <fieldset disabled={busy}>
            {register ? <>
              <label>Username<input name="username" required autoComplete="username" /></label>
              <label>Work email<input name="email" type="email" required autoComplete="email" /></label>
            </> : <label>Email or username<input name="emailOrUsername" required autoComplete="username" /></label>}
            <label>Password<input name="password" type="password" required
              autoComplete={register ? 'new-password' : 'current-password'} /></label>
            {register && (registrationRoles?.length > 1 ? (
              <label>Account role<select name="role" defaultValue="USER">
                {registrationRoles.map(role => <option value={role} key={role}>{signupRoleLabels[role]}</option>)}
              </select></label>
            ) : <p role="status">{registrationRoles
              ? 'New accounts start as Standard User. A workspace administrator assigns inspection or audit access.'
              : 'Checking signup options...'}</p>)}
            <Notice issue={issue} />
            <Button type="submit" icon="arrow" disabled={register && !registrationRoles}>{busy ? 'Please wait…' : register ? 'Create account' : 'Sign in'}</Button>
          </fieldset>
        </form>
        <button type="button" className="text-button" disabled={busy}
          onClick={() => { setMode(register ? 'login' : 'register'); setRegistrationRoles(null); setIssue(null); }}>
          {register ? 'Back to sign in' : 'Create an account'}
        </button>
      </section>
    </main>
  </div>;
}

function useBackend() {
  const [status, setStatus] = useState(null);
  const sequence = useRef(0);
  const probe = useCallback(async () => {
    const check = ++sequence.current;
    let next = 'Service unavailable';
    try {
      const { data } = await api.get('/actuator/health', { timeout: 5000, validateStatus: () => true });
      if (data?.status === 'UP') next = 'Service online';
      else if (data?.status === 'OUT_OF_SERVICE' || data?.status === 'MAINTENANCE') next = 'Service under maintenance';
    } catch {
      // The unavailable state covers health timeouts and network errors.
    }
    if (check === sequence.current) setStatus(next);
  }, []);

  useEffect(() => {
    probe();
    const timer = setInterval(probe, 10000);
    return () => { clearInterval(timer); sequence.current++; };
  }, [probe]);

  return { status, probe };
}

function Dashboard({ access, revision, navigate }) {
  const { request } = useAuth();
  const [data, setData] = useState(null);
  const [issue, setIssue] = useState(null);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    request({ url: '/api/dashboard', signal: controller.signal }).then(value => {
      setData(value);
      setIssue(null);
    }).catch(error => {
      if (!axios.isCancel(error)) setIssue(serviceError(error, 'Overview unavailable'));
    });
    return () => controller.abort();
  }, [request, revision, retry]);

  const metrics = [
    ['Enrolled products', data?.productsAttested],
    ['Enrollments today', data?.productsAttestedToday],
    ['Verifications today', data?.verificationsToday],
    ['Failed checks today', data?.failedVerificationsToday]
  ];
  return <>
    <Heading label="Workspace" title="Inspection" em="overview." description="Track registered components and recent checks."
      action={access.verify && <Button icon="plus" onClick={() => navigate('verify')}>New inspection</Button>} />
    <Notice issue={issue} />
    {issue && <Button kind="outline" onClick={() => setRetry(retry + 1)}>Retry overview</Button>}
    {!data && !issue && <p role="status">Loading overview…</p>}
    {data && <>
      <section className="metrics" aria-label="Workspace counts">{metrics.map(([label, value]) =>
        <div key={label}><span>{label}</span><strong>{Number.isFinite(value) ? value.toLocaleString() : '—'}</strong></div>
      )}</section>
      <section className="history">
        <div className="section-heading"><h2>Recent activity</h2><button className="text-button" onClick={() => navigate('audit')}>View evidence log</button></div>
        {data.recentActivity?.length ? <ul className="records">
          {data.recentActivity.map((item, index) => {
            const state = item.state === 'ATTESTED' ? { label: 'Reference enrolled', tone: 'success', icon: 'check' }
              : verdict({ decision: item.decision, verified: item.state === 'CLEARED' ? true : item.state === 'REVIEW' ? false : undefined });
            return <li key={item.productId + item.time + index}>
              <button className="record-link mono" onClick={() => navigate('audit', item.productId)}>{item.productId}</button>
              <Time value={item.time} /><Status tone={state.tone} icon={state.icon}>{state.label}</Status>
            </li>;
          })}
        </ul> : <p>No inspection activity recorded.</p>}
      </section>
    </>}
  </>;
}

function History({ attempts }) {
  if (!attempts) return <p>This service has no attempt history for the record.</p>;
  if (!attempts.length) return <p>No verification attempts recorded.</p>;
  return <ol className="attempts">
    {attempts.map(attempt => {
      const outcome = verdict(attempt);
      return <li key={attempt.attemptId}>
        <div className="attempt-head">
          <Status tone={outcome.tone} icon={outcome.icon}>{outcome.label}</Status><Time value={attempt.createdAt} />
        </div>
        <dl>
          <Data label="Attempt ID" value={attempt.attemptId} mono />
          <Data label="Cosine similarity" value={Number.isFinite(attempt.confidence) ? attempt.confidence.toFixed(3) : null} mono />
          <Data label="Ledger delivery" value={attempt.blockchainConfirmed === true ? 'Confirmed' : null} />
          <Data label="Transaction hash" value={attempt.transactionHash} mono />
          <Data label="Ledger block" value={attempt.blockNumber} mono />
        </dl>
        {attempt.warning && <p>{attempt.warning}</p>}
      </li>;
    })}
  </ol>;
}

function Audit({ productId }) {
  const { request } = useAuth();
  const [id, setId] = useState(productId || '');
  const [lookup, setLookup] = useState(productId ? { id: productId, page: 0 } : null);
  const [response, setResponse] = useState(null);
  const [issue, setIssue] = useState(null);
  const [loading, setLoading] = useState(Boolean(productId));

  useEffect(() => {
    if (!lookup) return;
    const { id, page } = lookup;
    const controller = new AbortController();
    setLoading(true);
    setIssue(null);
    setResponse(null);
    const pathSafe = /^[a-z0-9_.-]+$/i.test(id) && id !== '.' && id !== '..';
    const url = pathSafe ? '/api/verify/' + encodeURIComponent(id) + '/log' : '/api/verify/log';
    const params = pathSafe ? { page } : { productId: id, page };
    request({ url, params, signal: controller.signal }).then(data => {
      setResponse({ data, known: true });
    }).catch(error => {
      if (error.response?.status === 404) setResponse({ data: error.response.data, known: false });
      else if (!axios.isCancel(error)) setIssue(serviceError(error, 'Evidence lookup failed'));
    }).finally(() => {
      if (!controller.signal.aborted) setLoading(false);
    });
    return () => controller.abort();
  }, [lookup, request]);

  return <>
    <Heading label="Audit history" title="Evidence" em="log." description="Find enrollment evidence and verification attempts by product ID." />
    <form className="audit-search" onSubmit={event => { event.preventDefault(); if (id.trim()) setLookup({ id, page: 0 }); }}>
      <label>Product ID<input value={id} maxLength="255" className="mono" onChange={event => setId(event.target.value)} required /></label>
      <Button type="submit" icon="search" disabled={loading || !id.trim()}>{loading ? 'Searching…' : 'Find evidence'}</Button>
    </form>
    <Notice issue={issue} />
    {response && <div className="audit-result" aria-live="polite">
      <section className="evidence">
        <p className="eyebrow">{response.known ? 'Enrollment record' : 'Unknown product'}</p>
        <h2 className="product-title mono">{lookup.id}</h2>
        {response.known ? <dl>
          <Data label="Recorded" value={response.data.createdAt && <Time value={response.data.createdAt} />} />
          <Data label="Feature hash" value={response.data.featureHash} mono />
          <Data label="Ledger delivery" value={ledgerLabel(response.data.ledgerStatus)} />
          <Data label="Transaction hash" value={response.data.transactionHash === 'PENDING' ? null : response.data.transactionHash} mono />
          <Data label="Ledger block" value={response.data.blockNumber >= 0 ? response.data.blockNumber : null} mono />
        </dl> : <p>No fingerprint is registered for this product in your workspace.</p>}
      </section>
      <section className="history">
        <h2>Inspection history</h2>
        <History attempts={response.data.attempts} />
        {(lookup.page > 0 || response.data.hasMoreAttempts) && <div className="button-row">
          <Button kind="outline" disabled={lookup.page === 0} onClick={() => setLookup({ ...lookup, page: lookup.page - 1 })}>Newer attempts</Button>
          <Button kind="outline" disabled={!response.data.hasMoreAttempts} onClick={() => setLookup({ ...lookup, page: lookup.page + 1 })}>Older attempts</Button>
        </div>}
      </section>
    </div>}
  </>;
}

function PendingAccess() {
  const { user, refreshSession } = useAuth();
  const [checking, setChecking] = useState(false);
  const [checked, setChecked] = useState(false);
  const [issue, setIssue] = useState(null);

  async function checkAccess() {
    setChecking(true);
    setIssue(null);
    setChecked(false);
    try {
      await refreshSession();
      setChecked(true);
    } catch (error) {
      if (!axios.isCancel(error)) setIssue(serviceError(error, 'Access check failed'));
    } finally {
      setChecking(false);
    }
  }

  return <>
    <Heading label="Workspace access" title="Access pending."
      description="A workspace administrator must assign your account an inspection or audit role."
      action={<Button kind="outline" onClick={checkAccess} disabled={checking}>
        {checking ? 'Checking access...' : 'Check access'}
      </Button>} />
    <p>Signed in as <strong>{user.username}</strong> (Standard User).</p>
    {checked && <p role="status">Your account is still awaiting a role assignment.</p>}
    <Notice issue={issue} />
  </>;
}

function Workspace() {
  const { user, logout } = useAuth();
  const access = permissions(user.role);
  const { status, probe } = useBackend();
  const [route, setRoute] = useState({ view: 'overview', key: 0 });
  const [revision, setRevision] = useState(0);
  const [reference, setReference] = useState(null);
  const main = useRef(null);
  const allowed = (route.view !== 'enroll' || access.enroll) && (route.view !== 'verify' || access.verify);
  const page = allowed ? route.view : 'overview';

  useEffect(() => {
    if (route.key) main.current.focus();
  }, [route.key]);

  const navigate = (view, productId) => setRoute(previous => ({ view, productId, key: previous.key + 1 }));
  const links = nav.filter(({ id }) => id === 'overview' || id === 'audit' || access[id]);

  return <>
    <a href="#workspace" className="skip-link">Skip to workspace</a>
    <header className="site-header">
      <div className="header-inner">
        <button type="button" className="brand-button" onClick={() => navigate('overview')}><Brand /></button>
        {access.read && <nav aria-label="Workspace">{links.map(({ id, label }) =>
          <button type="button" key={id} aria-current={page === id ? 'page' : undefined}
            onClick={() => navigate(id)}>{label}</button>)}</nav>}
        <div className="profile">
          <span className="profile-role mono">{user.role === 'AUDITOR' ? 'Auditor · read only' : user.role.toLowerCase()}</span>
          <Button kind="bare" icon="logout" aria-label="Sign out" title="Sign out" onClick={logout} />
        </div>
      </div>
    </header>
    <main className="workspace" ref={main} tabIndex="-1" id="workspace">
      {status && status !== 'Service online' && <div className="service-banner" role="status">
        <Status tone="warning" icon="alert">{status}</Status><Button kind="outline" onClick={probe}>Retry service</Button>
      </div>}
      {!access.read ? <PendingAccess />
        : page === 'overview' ? <Dashboard access={access} revision={revision} navigate={navigate} />
        : page === 'audit' ? <Audit key={route.key} productId={route.productId} />
        : <CaptureFlow key={route.key} verify={page === 'verify'} productId={route.productId}
          reference={reference} onEnroll={setReference} onRecorded={() => setRevision(n => n + 1)}
          onAudit={id => navigate('audit', id)} onInspect={id => navigate('verify', id)} />}
    </main>
  </>;
}

function Shell() {
  const { user } = useAuth();
  return user ? <Workspace key={user.tenantId + ':' + user.id} /> : <SignIn />;
}

export default function App() {
  return <AuthProvider><Shell /></AuthProvider>;
}
