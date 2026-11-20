import React, { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { useAuth } from './auth';
import OpticalBench, { usePhotoUrl } from './OpticalBench';
import { ledgerLabel, serviceError, verdict } from './inspection';
import { Button, Data, Heading, Notice, Status, Time } from './ui';

function Camera({ onPhoto, onClose }) {
  const video = useRef(null);
  const [ready, setReady] = useState(false);
  const [message, setMessage] = useState('');

  useEffect(() => {
    let stream;
    let closed = false;
    navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false })
      .then(source => {
        if (closed) source.getTracks().forEach(track => track.stop());
        else {
          stream = source;
          video.current.srcObject = source;
        }
      }).catch(() => {
        if (!closed) setMessage('Camera unavailable. You can upload a photograph instead.');
      });
    return () => {
      closed = true;
      stream?.getTracks().forEach(track => track.stop());
    };
  }, []);

  function takePhoto() {
    const source = video.current;
    if (!source.videoWidth || !source.videoHeight) return;
    const scale = Math.min(1, Math.sqrt(16000000 / (source.videoWidth * source.videoHeight)));
    const frame = document.createElement('canvas');
    frame.width = Math.floor(source.videoWidth * scale);
    frame.height = Math.floor(source.videoHeight * scale);
    frame.getContext('2d').drawImage(source, 0, 0, frame.width, frame.height);
    frame.toBlob(blob => {
      if (blob && video.current) onPhoto(new File([blob], 'camera-capture.jpg', { type: 'image/jpeg' }));
    }, 'image/jpeg', 0.92);
  }

  return <div className="camera">
    <video ref={video} autoPlay muted playsInline onLoadedData={() => setReady(true)} aria-label="Camera preview" />
    <div className="button-row">
      <Button kind="outline" disabled={!ready} onClick={takePhoto}>Take photo</Button>
      <Button kind="outline" onClick={onClose}>Close camera</Button>
    </div>
    {message && <p role="status">{message}</p>}
  </div>;
}

function Report({ record, verify, capture, reference, onAudit, onRetake, onInspect }) {
  const outcome = verify ? verdict(record) : { label: 'Reference enrolled', tone: 'success', icon: 'check' };
  const score = Number.isFinite(record.confidence) ? record.confidence : null;
  const ledger = record.blockchainConfirmed === true ? 'Confirmed'
    : record.verified === true && record.blockchainConfirmed === false ? 'Unconfirmed'
    : ledgerLabel(record.status);

  return <article className="result-grid" aria-label={verify ? 'Inspection result' : 'Enrollment result'}>
    <header className="report-summary">
      <p className="eyebrow">{verify ? 'Inspection report' : 'Enrollment record'}</p>
      <h2 className="product-title mono" tabIndex="-1" data-result-heading>{record.productId}</h2>
      <Status tone={outcome.tone} icon={outcome.icon}>{outcome.label}</Status>
    </header>
    <OpticalBench capture={capture} reference={reference} result={verify}
      captureLabel={verify ? 'Inspection capture' : 'Registered reference'} />
    <div className="report-details">
      {score != null && <div className="score">
        <span className="eyebrow">Cosine similarity</span>
        <p>{score.toFixed(3)} <small>/ 1.000</small></p>
        <div className="score-ticks" aria-hidden="true">
          <span style={{ width: Math.max(0, Math.min(1, score)) * 100 + '%' }} />
        </div>
      </div>}
      <dl>
        <Data label="Attempt ID" value={record.attemptId} mono />
        <Data label="Recorded" value={record.createdAt && <Time value={record.createdAt} />} />
        <Data label="Feature hash" value={record.featureHash} mono />
        <Data label="Ledger delivery" value={ledger} />
        <Data label="Transaction hash" value={record.transactionHash} mono />
        <Data label="Ledger block" value={record.blockNumber} mono />
      </dl>
      {record.warning && record.warning !== outcome.label && <p className="report-warning">{record.warning}</p>}
    </div>
    <div className="report-actions">
      <Button icon={verify ? 'arrow' : 'search'} onClick={verify ? onAudit : onInspect}>
        {verify ? 'View evidence record' : 'Verify this product'}
      </Button>
      <Button kind="outline" icon={verify ? 'retake' : 'arrow'} onClick={verify ? onRetake : onAudit}>
        {verify ? 'Retake photo' : 'View evidence record'}
      </Button>
    </div>
  </article>;
}

export default function CaptureFlow({ verify, productId, reference, onEnroll, onAudit, onInspect, onRecorded }) {
  const { request } = useAuth();
  const [id, setId] = useState(productId || '');
  const [metadata, setMetadata] = useState('');
  const [file, setFile] = useState(null);
  const [record, setRecord] = useState(null);
  const [issue, setIssue] = useState(null);
  const [busy, setBusy] = useState(false);
  const [camera, setCamera] = useState(false);
  const [preparing, setPreparing] = useState(false);
  const pending = useRef(false);
  const pickVersion = useRef(0);
  const formStart = useRef(null);
  const report = useRef(null);
  const focusDraft = useRef(false);
  const captureUrl = usePhotoUrl(file);
  const referenceFile = verify && record && record.productId === reference?.productId &&
    record.decision !== 'UNKNOWN_PRODUCT' ? reference.file : null;
  const referenceUrl = usePhotoUrl(referenceFile);

  useEffect(() => {
    if (record) report.current?.querySelector('[data-result-heading]').focus();
    else if (focusDraft.current) {
      formStart.current?.focus();
      focusDraft.current = false;
    }
  }, [record]);

  async function selectPhoto(candidate) {
    if (!candidate) return;
    const version = ++pickVersion.current;
    setIssue(null);
    setPreparing(true);
    try {
      if (!candidate.size || candidate.size > 10 * 1024 * 1024 ||
          (candidate.type && !['image/jpeg', 'image/png'].includes(candidate.type))) {
        throw new Error('Choose a JPEG or PNG photo up to 10 MB.');
      }
      const url = URL.createObjectURL(candidate);
      try {
        const image = new Image();
        image.src = url;
        await image.decode();
      } catch {
        throw new Error('This photograph could not be opened. Choose another JPEG or PNG.');
      } finally {
        URL.revokeObjectURL(url);
      }
      if (version !== pickVersion.current) return;
      setFile(candidate);
      setCamera(false);
    } catch (error) {
      if (version === pickVersion.current) setIssue({ title: 'Photo unavailable', detail: error.message });
    } finally {
      if (version === pickVersion.current) setPreparing(false);
    }
  }

  async function submit(event) {
    event.preventDefault();
    const selectedId = id;
    if (pending.current || preparing || !file || !selectedId.trim()) return;
    pending.current = true;
    setBusy(true);
    setIssue(null);
    setCamera(false);
    const form = new FormData();
    form.append('productId', selectedId);
    form.append('image', file);
    if (!verify && metadata.trim()) form.append('metadata', metadata);
    try {
      const data = await request({ method: 'post', url: `/api/${verify ? 'verify' : 'enroll'}/image`, data: form });
      setRecord({ ...data, productId: data.productId || selectedId });
      if (!verify) onEnroll({ productId: selectedId, file });
      onRecorded();
    } catch (error) {
      if (!axios.isCancel(error)) setIssue(serviceError(error, 'Upload failed'));
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }

  function retake() {
    focusDraft.current = true;
    setId(record.productId);
    setRecord(null);
    setFile(null);
    setIssue(null);
  }

  function openCamera() {
    if (navigator.mediaDevices?.getUserMedia) setCamera(true);
    else document.getElementById('native-camera').click();
  }

  function upload(event) {
    selectPhoto(event.target.files[0]);
    event.target.value = '';
  }

  return <div ref={report}>
    <Heading label={verify ? 'Product authentication' : 'Reference library'}
      title={verify ? 'Inspection' : 'Enroll a'} em={verify ? 'desk.' : 'reference.'}
      description={verify ? 'Verify a physical capture against its registered fingerprint.'
        : 'Create a visual fingerprint for a physical component.'} />
    {record ? <Report record={record} verify={verify} capture={captureUrl} reference={referenceUrl}
      onAudit={() => onAudit(record.productId)} onInspect={() => onInspect(record.productId)} onRetake={retake} />
      : <div className="draft-grid">
        <OpticalBench capture={captureUrl} captureLabel={verify ? 'Inspection capture' : 'Enrollment preview'} />
        <form className="draft-form" onSubmit={submit} aria-busy={busy}>
          <h2>{verify ? 'New inspection' : 'New enrollment'}</h2>
          <fieldset disabled={busy}>
            <label>Product ID<input ref={formStart} name="productId" required maxLength="255" value={id}
              className="mono" onChange={event => setId(event.target.value)} autoComplete="off" /></label>
            <div className="photo-pick">
              <p className="field-label">Component photograph</p>
              <div className="button-row">
                <label className="button button--outline file-button">
                  <input aria-label="Upload photograph" type="file" accept="image/jpeg,image/png" onChange={upload} />
                  {file ? 'Replace photo' : 'Upload photo'}
                </label>
                <Button kind="outline" icon="camera" onClick={openCamera}>Use camera</Button>
                <input id="native-camera" type="file" accept="image/jpeg,image/png" capture="environment"
                  hidden onChange={upload} />
              </div>
              <small aria-live="polite">{preparing ? 'Opening photo…' : file?.name || 'JPEG or PNG, up to 10 MB'}</small>
            </div>
            {camera && <Camera onPhoto={selectPhoto} onClose={() => setCamera(false)} />}
            {!verify && <p className="photo-policy">The service saves the fingerprint. Your latest enrollment photo
              stays available for comparison in this tab until you reload or sign out.</p>}
            {!verify && <label>Metadata <span className="muted">(optional)</span>
              <textarea name="metadata" rows="3" value={metadata} onChange={event => setMetadata(event.target.value)} />
            </label>}
            <Notice issue={issue} />
            <Button type="submit" icon={verify ? 'search' : 'plus'} disabled={!file || !id.trim() || preparing}>
              {busy ? 'Processing photo…' : verify ? 'Run verification' : 'Enroll reference'}
            </Button>
          </fieldset>
        </form>
      </div>}
  </div>;
}
