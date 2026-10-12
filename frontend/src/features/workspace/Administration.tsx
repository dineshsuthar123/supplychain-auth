import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../app/Auth';
import { api } from '../../lib/api';
import type { Workspace, Product } from '../../lib/types';
import { CommandForm, ErrorNotice, Field, Loading, Status } from '../../components/UI';
function Permissions({
  workspace,
  memberId,
  initialRole,
  initialRecall,
  initialRead,
  siteIds,
  orgId,
}: {
  workspace: Workspace;
  memberId: string;
  initialRole: string;
  initialRecall: boolean;
  initialRead: boolean;
  siteIds: string[];
  orgId: string;
}) {
  const [role, setRole] = useState(initialRole);
  const [recallManager, setRecall] = useState(initialRecall);
  const [sites, setSites] = useState(siteIds);
  const [networkRead, setNetworkRead] = useState(initialRead);
  return (
    <details className="member-permissions">
      <summary>Adjust bounded permissions</summary>
      <CommandForm
        path="/workspace/permissions"
        label="Save permission grant"
        body={() => ({
          membershipId: memberId,
          role,
          recallManager: role === 'AUDITOR' ? false : recallManager,
          siteIds: sites,
          networkRead: role === 'AUDITOR' && networkRead,
        })}
      >
        <Field label="Membership role">
          <select value={role} onChange={(e) => setRole(e.target.value)}>
            <option>OPERATOR</option>
            <option>PARTNER_OPERATOR</option>
            <option>AUDITOR</option>
          </select>
        </Field>
        <Field label="Granted sites (empty grants all sites in own organization)">
          <select
            multiple
            value={sites}
            onChange={(e) => setSites(Array.from(e.target.selectedOptions).map((o) => o.value))}
          >
            {workspace.sites
              .filter((s) => s.organization_id === orgId)
              .map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
          </select>
        </Field>
        <label className="check-field">
          <input
            type="checkbox"
            disabled={role === 'AUDITOR'}
            checked={recallManager}
            onChange={(e) => setRecall(e.target.checked)}
          />
          Recall manager: can inspect the network and activate / close incidents.
        </label>
        {role === 'AUDITOR' && (
          <label className="check-field">
            <input
              type="checkbox"
              checked={networkRead}
              onChange={(e) => setNetworkRead(e.target.checked)}
            />
            Read the entire workspace network (auditor only; no mutation permission).
          </label>
        )}
      </CommandForm>
    </details>
  );
}
export default function Administration() {
  const { member } = useAuth();
  const workspace = useQuery({
    queryKey: ['workspace', member?.workspace_id],
    queryFn: () => api.get<Workspace>('/workspace'),
  });
  const products = useQuery({
    queryKey: ['products', member?.workspace_id],
    queryFn: () => api.get<Product[]>('/products'),
  });
  const [orgName, setOrgName] = useState('');
  const [siteName, setSiteName] = useState('');
  const [org, setOrg] = useState('');
  const [productName, setProductName] = useState('');
  const [unit, setUnit] = useState('unit');
  const [email, setEmail] = useState('');
  const [role, setRole] = useState('PARTNER_OPERATOR');
  const [recallManager, setRecall] = useState(false);
  const [siteIds, setSiteIds] = useState<string[]>([]);
  const [networkRead, setNetworkRead] = useState(false);
  const [invitePath, setInvitePath] = useState('');
  const [copied, setCopied] = useState(false);
  if (workspace.isPending) return <Loading />;
  if (workspace.error)
    return <ErrorNotice error={workspace.error} retry={() => workspace.refetch()} />;
  const w = workspace.data;
  const selectedOrg = org || w.organizations[0]?.id;
  const owner = member?.role === 'OWNER';
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">Workspace / a cooperating network</p>
          <h1>
            Known hands.
            <br className="mobile-only" /> <em>Bounded access.</em>
          </h1>
        </div>
        <Status value={owner ? 'OWNER' : 'READ_ONLY_ADMIN'} />
      </div>
      <div className="sharing-banner">
        <b>How this network shares information</b>
        <p>
          Partners operate their own granted sites and see their relevant lots, handoffs and recall
          targets. Membership shares a directory of organizations and sites. Owners and specifically
          granted recall managers can inspect the network for incident containment. Auditors cannot
          mutate operational stock.
        </p>
      </div>
      <div className="admin-grid">
        <section className="admin-section">
          <p className="eyebrow">01 / Organizations & sites</p>
          {w.organizations.map((o) => (
            <div className="organization-row" key={o.id}>
              <b>{o.name}</b>
              <div>
                {w.sites
                  .filter((s) => s.organization_id === o.id)
                  .map((s) => (
                    <span key={s.id}>{s.name}</span>
                  ))}
              </div>
            </div>
          ))}
          {owner && (
            <>
              <details open={!w.sites.length}>
                <summary>Create a site</summary>
                <CommandForm
                  path="/workspace/sites"
                  label="Create site"
                  body={() => ({ organizationId: selectedOrg, name: siteName })}
                >
                  <Field label="Site organization">
                    <select value={selectedOrg} onChange={(e) => setOrg(e.target.value)}>
                      {w.organizations.map((o) => (
                        <option key={o.id} value={o.id}>
                          {o.name}
                        </option>
                      ))}
                    </select>
                  </Field>
                  <Field label="Site name">
                    <input
                      required
                      value={siteName}
                      onChange={(e) => setSiteName(e.target.value)}
                      maxLength={160}
                    />
                  </Field>
                </CommandForm>
              </details>
              <details>
                <summary>Create a partner organization</summary>
                <CommandForm
                  path="/workspace/organizations"
                  label="Create organization"
                  body={() => ({ name: orgName })}
                  onAccepted={(r) => setOrg(r.id)}
                >
                  <Field label="Organization name">
                    <input
                      required
                      value={orgName}
                      onChange={(e) => setOrgName(e.target.value)}
                      maxLength={160}
                    />
                  </Field>
                </CommandForm>
              </details>
            </>
          )}
        </section>
        <section className="admin-section">
          <p className="eyebrow">02 / Product catalog</p>
          {products.error && <ErrorNotice error={products.error} />}{' '}
          {products.data?.map((p) => (
            <div className="product-row" key={p.id}>
              <b>{p.name}</b>
              <span className="mono">{p.base_unit}</span>
            </div>
          ))}
          {(owner || member?.role === 'OPERATOR') && (
            <details open={!products.data?.length}>
              <summary>Create a product</summary>
              <CommandForm
                path="/products"
                label="Create product"
                body={() => ({ name: productName, baseUnit: unit })}
              >
                <Field label="Product name">
                  <input
                    required
                    value={productName}
                    onChange={(e) => setProductName(e.target.value)}
                    maxLength={160}
                  />
                </Field>
                <Field label="Base unit">
                  <select value={unit} onChange={(e) => setUnit(e.target.value)}>
                    <option value="unit">unit · packaged pieces</option>
                    <option>kg</option>
                    <option>g</option>
                    <option>l</option>
                    <option>ml</option>
                  </select>
                </Field>
                <p className="muted">
                  Exact decimals, at most three places. Split / merge keeps the same product and
                  unit. Kilograms are never silently converted into pieces.
                </p>
              </CommandForm>
            </details>
          )}
        </section>
        {owner && (
          <>
            <section className="admin-section">
              <p className="eyebrow">03 / Invitations</p>
              <h2>Invite the next pair of hands.</h2>
              <CommandForm
                path="/workspace/invitations"
                label="Create expiring invitation"
                body={() => ({
                  organizationId: selectedOrg,
                  email,
                  role,
                  recallManager: role === 'AUDITOR' ? false : recallManager,
                  siteIds,
                  networkRead: role === 'AUDITOR' && networkRead,
                })}
                onAccepted={(r) => {
                  setInvitePath(r.invitationPath ?? '');
                  setCopied(false);
                }}
              >
                <Field label="Partner organization">
                  <select
                    required
                    value={selectedOrg}
                    onChange={(e) => {
                      setOrg(e.target.value);
                      setSiteIds([]);
                    }}
                  >
                    {w.organizations.map((o) => (
                      <option key={o.id} value={o.id}>
                        {o.name}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="Invitation email">
                  <input
                    type="email"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                  />
                </Field>
                <Field label="Invitation role">
                  <select value={role} onChange={(e) => setRole(e.target.value)}>
                    <option>PARTNER_OPERATOR</option>
                    <option>OPERATOR</option>
                    <option>AUDITOR</option>
                  </select>
                </Field>
                <Field label="Granted sites (empty means all own organization sites)">
                  <select
                    multiple
                    value={siteIds}
                    onChange={(e) =>
                      setSiteIds(Array.from(e.target.selectedOptions).map((o) => o.value))
                    }
                  >
                    {w.sites
                      .filter((s) => s.organization_id === selectedOrg)
                      .map((s) => (
                        <option key={s.id} value={s.id}>
                          {s.name}
                        </option>
                      ))}
                  </select>
                </Field>
                <label className="check-field">
                  <input
                    type="checkbox"
                    checked={recallManager}
                    disabled={role === 'AUDITOR'}
                    onChange={(e) => setRecall(e.target.checked)}
                  />
                  Grant recall management and network visibility
                </label>
                {role === 'AUDITOR' && (
                  <label className="check-field">
                    <input
                      type="checkbox"
                      checked={networkRead}
                      onChange={(e) => setNetworkRead(e.target.checked)}
                    />
                    Grant read-only access across the workspace network
                  </label>
                )}
              </CommandForm>
              {invitePath && (
                <div className="invite-copy">
                  <Field label="Invitation link">
                    <input readOnly value={window.location.origin + invitePath} />
                  </Field>
                  <button
                    className="button secondary"
                    onClick={async () => {
                      try {
                        await navigator.clipboard.writeText(window.location.origin + invitePath);
                        setCopied(true);
                      } catch {
                        setCopied(false);
                      }
                    }}
                  >
                    {copied ? 'Copied ✓' : 'Copy link'}
                  </button>
                  <small>
                    Single use · bound to email, organization and role · expires in seven days
                  </small>
                </div>
              )}
              {w.invitations.map((i) => (
                <p key={i.id} className="invitation-row">
                  {i.email}
                  <small>
                    {i.role.toLowerCase()} ·{' '}
                    {i.used_at
                      ? 'accepted'
                      : `expires ${new Date(i.expires_at).toLocaleDateString()}`}
                  </small>
                </p>
              ))}
            </section>
            <section className="admin-section">
              <p className="eyebrow">04 / Operators & permission grants</p>
              {w.members.map((m) => (
                <div className="member-row" key={m.id}>
                  <div>
                    <b>{m.name}</b>
                    <span>{m.email}</span>
                    <small>{w.organizations.find((o) => o.id === m.organization_id)?.name}</small>
                  </div>
                  <Status value={m.role} />
                  {m.recall_manager && <small>Recall manager / full incident graph</small>}
                  {m.role !== 'OWNER' && (
                    <Permissions
                      workspace={w}
                      memberId={m.id}
                      initialRole={m.role}
                      initialRecall={m.recall_manager}
                      initialRead={m.network_read}
                      siteIds={m.site_ids}
                      orgId={m.organization_id}
                    />
                  )}
                </div>
              ))}
            </section>
          </>
        )}
      </div>
    </>
  );
}
