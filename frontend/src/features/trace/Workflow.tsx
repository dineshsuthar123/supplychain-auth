import { useState } from 'react';
import { Field, CommandForm } from '../../components/UI';
import type { Graph, Workspace, Product, Accepted } from '../../lib/types';
import { useAuth } from '../../app/Auth';
import { sum, milli, decimal } from '../../lib/quantity';
import type { CommissionRequest, TransferDraftRequest } from '../../lib/api.generated';
export type WorkflowKind = 'commission' | 'operation' | 'dispatch' | 'disposition' | 'correction';
export default function Workflow({
  kind,
  graph,
  workspace,
  products,
  selected,
  done,
}: {
  kind: WorkflowKind;
  graph: Graph;
  workspace: Workspace;
  products: Product[];
  selected: string | null;
  done: (r: Accepted) => void;
}) {
  const { member } = useAuth();
  const ownSites = workspace.sites.filter(
    (s) => member?.role === 'OWNER' || s.organization_id === member?.organization_id,
  );
  const [site, setSite] = useState(ownSites[0]?.id ?? '');
  const [product, setProduct] = useState(products[0]?.id ?? '');
  const [lot, setLot] = useState(selected ?? graph.lots[0]?.id ?? '');
  const [code, setCode] = useState('');
  const [quantity, setQuantity] = useState('');
  const [destination, setDestination] = useState(
    workspace.sites.find((s) => s.id !== site)?.id ?? '',
  );
  const [reference, setReference] = useState('');
  const [reserve, setReserve] = useState(false);
  const [operation, setOperation] = useState('SPLIT');
  const [inputs, setInputs] = useState([{ lotId: lot, quantity: '' }]);
  const [outputs, setOutputs] = useState([
    { code: '', quantity: '' },
    { code: '', quantity: '' },
  ]);
  const [loss, setLoss] = useState('0');
  const [action, setAction] = useState('RECOVER');
  const [sourceStatus, setSourceStatus] = useState('HELD');
  const [reason, setReason] = useState('');
  const [evidence, setEvidence] = useState('');
  const [direction, setDirection] = useState('ADD');
  const [prior, setPrior] = useState('');
  const lots = graph.lots;
  const selectedLot = lots.find((l) => l.id === lot);
  const inSum = sum(inputs.map((i) => i.quantity));
  const outSum = sum(outputs.map((o) => o.quantity));
  const balance =
    milli(inSum) !== null && milli(outSum) !== null && milli(loss) !== null
      ? decimal(milli(inSum)! - milli(outSum)! - milli(loss)!)
      : 'Check entries';
  const selectSite = (
    <Field label="Authorized site">
      <select value={site} onChange={(e) => setSite(e.target.value)} required>
        <option value="">Select site</option>
        {ownSites.map((s) => (
          <option key={s.id} value={s.id}>
            {s.organization_name} / {s.name}
          </option>
        ))}
      </select>
    </Field>
  );
  const selectLot = (
    <Field label="Lot">
      <select value={lot} onChange={(e) => setLot(e.target.value)} required>
        {lots.map((l) => (
          <option key={l.id} value={l.id}>
            {l.code} · {l.product_name}
          </option>
        ))}
      </select>
    </Field>
  );
  const quantityField = (
    <Field
      label={`Exact quantity (${selectedLot?.base_unit ?? products.find((p) => p.id === product)?.base_unit ?? 'base unit'})`}
    >
      <input
        value={quantity}
        onChange={(e) => setQuantity(e.target.value)}
        inputMode="decimal"
        placeholder="e.g. 350"
        pattern="[0-9]+(\.[0-9]{1,3})?"
        required
      />
    </Field>
  );
  if (!ownSites.length)
    return (
      <p>Create your organization’s first site in the workspace menu before recording inventory.</p>
    );
  if (kind === 'commission')
    return (
      <>
        <p className="muted">
          Commission a newly identified lot at an authorized site. This creates stock and an
          accepted origin event.
        </p>
        <CommandForm
          path="/inventory/commission"
          label="Commission lot"
          body={() =>
            ({
              productId: product,
              siteId: site,
              code,
              quantity,
              issuedOn: new Date().toISOString().slice(0, 10),
              expiresOn: null,
            }) satisfies CommissionRequest
          }
          onAccepted={done}
        >
          <Field label="Product and base unit">
            <select required value={product} onChange={(e) => setProduct(e.target.value)}>
              <option value="">Choose a product</option>
              {products.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} / {p.base_unit}
                </option>
              ))}
            </select>
          </Field>
          {!products.length && <p>Create a product in workspace administration first.</p>}
          {selectSite}
          <Field label="External lot code">
            <input
              required
              value={code}
              maxLength={120}
              onChange={(e) => setCode(e.target.value)}
              placeholder="CF-2401"
            />
          </Field>
          {quantityField}
        </CommandForm>
      </>
    );
  if (kind === 'dispatch')
    return (
      <CommandForm
        path="/transfers"
        label={reserve ? 'Reserve stock and create draft' : 'Create handoff draft'}
        body={() =>
          ({
            sourceSiteId: site,
            destinationSiteId: destination,
            reference,
            lines: [{ lotId: lot, quantity }],
            reserve,
          }) satisfies TransferDraftRequest
        }
        onAccepted={done}
      >
        {selectLot}
        {selectSite}
        <Field label="Receiving partner / site">
          <select required value={destination} onChange={(e) => setDestination(e.target.value)}>
            {workspace.sites
              .filter((s) => s.id !== site)
              .map((s) => (
                <option key={s.id} value={s.id}>
                  {s.organization_name} / {s.name}
                </option>
              ))}
          </select>
        </Field>
        {quantityField}
        <Field label="Shipment reference">
          <input
            required
            value={reference}
            maxLength={160}
            onChange={(e) => setReference(e.target.value)}
            placeholder="Packing slip / dispatch register"
          />
        </Field>
        <label className="check-field">
          <input type="checkbox" checked={reserve} onChange={(e) => setReserve(e.target.checked)} />
          Reserve stock now; dispatch remains a separate command.
        </label>
      </CommandForm>
    );
  if (kind === 'operation')
    return (
      <CommandForm
        path="/inventory/operations"
        label="Consume inputs & create output lots"
        body={() => ({ kind: operation, siteId: site, inputs, outputs, declaredLoss: loss })}
        onAccepted={done}
      >
        <Field label="Operation">
          <select value={operation} onChange={(e) => setOperation(e.target.value)}>
            <option>SPLIT</option>
            <option>MERGE</option>
            <option>REPACKAGE</option>
          </select>
        </Field>
        {selectSite}
        <p className="muted">
          Same product and base unit only. Output lots are newly created. Mixed outputs inherit
          every input’s potential exposure.
        </p>
        <fieldset>
          <legend>Consumed inputs</legend>
          {inputs.map((input, i) => (
            <div className="form-row" key={i}>
              <Field label={`Input lot ${i + 1}`}>
                <select
                  required
                  value={input.lotId}
                  onChange={(e) =>
                    setInputs((v) =>
                      v.map((item, j) => (j === i ? { ...item, lotId: e.target.value } : item)),
                    )
                  }
                >
                  {lots.map((l) => (
                    <option key={l.id} value={l.id}>
                      {l.code}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label={`Input quantity ${i + 1}`}>
                <input
                  required
                  inputMode="decimal"
                  value={input.quantity}
                  onChange={(e) =>
                    setInputs((v) =>
                      v.map((item, j) => (j === i ? { ...item, quantity: e.target.value } : item)),
                    )
                  }
                />
              </Field>
              {inputs.length > 1 && (
                <button
                  type="button"
                  className="icon-button"
                  aria-label={`Remove input ${i + 1}`}
                  onClick={() => setInputs((v) => v.filter((_, j) => j !== i))}
                >
                  ×
                </button>
              )}
            </div>
          ))}
          <button
            type="button"
            className="text-button"
            onClick={() => setInputs((v) => [...v, { lotId: lot, quantity: '' }])}
          >
            + Add input
          </button>
        </fieldset>
        <fieldset>
          <legend>New output lots</legend>
          {outputs.map((output, i) => (
            <div className="form-row" key={i}>
              <Field label={`Output code ${i + 1}`}>
                <input
                  required
                  maxLength={120}
                  value={output.code}
                  onChange={(e) =>
                    setOutputs((v) =>
                      v.map((item, j) => (j === i ? { ...item, code: e.target.value } : item)),
                    )
                  }
                />
              </Field>
              <Field label={`Output quantity ${i + 1}`}>
                <input
                  required
                  inputMode="decimal"
                  value={output.quantity}
                  onChange={(e) =>
                    setOutputs((v) =>
                      v.map((item, j) => (j === i ? { ...item, quantity: e.target.value } : item)),
                    )
                  }
                />
              </Field>
              {outputs.length > 1 && (
                <button
                  type="button"
                  className="icon-button"
                  aria-label={`Remove output ${i + 1}`}
                  onClick={() => setOutputs((v) => v.filter((_, j) => j !== i))}
                >
                  ×
                </button>
              )}
            </div>
          ))}
          <button
            type="button"
            className="text-button"
            onClick={() => setOutputs((v) => [...v, { code: '', quantity: '' }])}
          >
            + Add output
          </button>
        </fieldset>
        <Field label="Declared loss">
          <input
            required
            inputMode="decimal"
            value={loss}
            onChange={(e) => setLoss(e.target.value)}
          />
        </Field>
        <div className="accounting-line">
          <span>
            {inSum} in − {outSum} out − {loss} loss
          </span>
          <b>{balance === '0' ? '✓ Balanced' : `${balance} remaining`}</b>
        </div>
      </CommandForm>
    );
  if (kind === 'correction')
    return (
      <CommandForm
        path="/inventory/corrections"
        label="Append linked correction"
        body={() => ({
          lotId: lot,
          siteId: site,
          correctsEventId: prior,
          direction,
          sourceStatus,
          quantity,
          reason,
          evidence,
        })}
        onAccepted={done}
      >
        {selectLot}
        {selectSite}
        <Field label="Prior accepted inventory event ID">
          <input required value={prior} onChange={(e) => setPrior(e.target.value)} />
        </Field>
        <Field label="Correction direction">
          <select value={direction} onChange={(e) => setDirection(e.target.value)}>
            <option>ADD</option>
            <option>REMOVE</option>
          </select>
        </Field>
        <Field label="Recorded stock status">
          <select value={sourceStatus} onChange={(e) => setSourceStatus(e.target.value)}>
            <option>AVAILABLE</option>
            <option>HELD</option>
          </select>
        </Field>
        {quantityField}
        <Field label="Correction reason">
          <textarea required value={reason} onChange={(e) => setReason(e.target.value)} />
        </Field>
        <Field label="Evidence reference">
          <input required value={evidence} onChange={(e) => setEvidence(e.target.value)} />
        </Field>
        <p className="muted">
          A late addition may reopen a closed incident and append a scope revision. Earlier
          sequences retain their original facts.
        </p>
      </CommandForm>
    );
  return (
    <CommandForm
      path="/inventory/dispositions"
      label="Record physical disposition"
      body={() => ({
        lotId: lot,
        siteId: site,
        kind: action,
        sourceStatus: action === 'HOLD' ? 'AVAILABLE' : sourceStatus,
        quantity,
        reason,
        evidence,
      })}
      onAccepted={done}
    >
      {selectLot}
      {selectSite}
      <Field label="Action">
        <select value={action} onChange={(e) => setAction(e.target.value)}>
          <option value="RECOVER">Recover held stock</option>
          <option value="DISPOSE">Dispose held / recovered stock</option>
          <option value="HOLD">Hold available stock</option>
          {(member?.role === 'OWNER' || member?.recall_manager) && (
            <option value="LOSS">Accept accountable loss</option>
          )}
        </select>
      </Field>
      {action === 'DISPOSE' && (
        <Field label="Consume from">
          <select value={sourceStatus} onChange={(e) => setSourceStatus(e.target.value)}>
            <option>HELD</option>
            <option>RECOVERED</option>
          </select>
        </Field>
      )}
      {quantityField}
      <Field label="Reason">
        <textarea required value={reason} onChange={(e) => setReason(e.target.value)} />
      </Field>
      <Field label="Evidence / register reference">
        <input required value={evidence} onChange={(e) => setEvidence(e.target.value)} />
      </Field>
    </CommandForm>
  );
}
