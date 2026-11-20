import React, { useEffect, useRef, useState } from 'react';
import { Icon } from './ui';

const zoom = 2.4;
const clamp = (n, min = 0, max = 1) => Math.max(min, Math.min(max, n));

export function usePhotoUrl(file) {
  const [photo, setPhoto] = useState(null);
  useEffect(() => {
    if (!file) {
      setPhoto(null);
      return;
    }
    const url = URL.createObjectURL(file);
    setPhoto({ file, url });
    return () => URL.revokeObjectURL(url);
  }, [file]);
  return photo?.file === file ? photo?.url : null;
}

function Photo({ url, label, loupe, closeLoupe }) {
  const stage = useRef(null);
  const photo = useRef(null);
  const [point, setPoint] = useState({ x: 0.5, y: 0.5 });
  const [frame, setFrame] = useState(null);

  function measure() {
    if (!stage.current || !photo.current) return;
    const { width, height } = stage.current.getBoundingClientRect();
    const { naturalWidth, naturalHeight } = photo.current;
    if (!naturalWidth || !naturalHeight) return;
    const scale = Math.min(width / naturalWidth, height / naturalHeight);
    const w = naturalWidth * scale;
    const h = naturalHeight * scale;
    setFrame({ w, h, left: (width - w) / 2, top: (height - h) / 2, width, height });
  }

  useEffect(() => {
    let pendingFrame;
    const observer = new ResizeObserver(() => {
      cancelAnimationFrame(pendingFrame);
      pendingFrame = requestAnimationFrame(measure);
    });
    observer.observe(stage.current);
    return () => {
      cancelAnimationFrame(pendingFrame);
      observer.disconnect();
    };
  }, []);

  useEffect(() => {
    if (loupe) stage.current.focus();
  }, [loupe]);

  function move(event) {
    if (!loupe || !frame) return;
    const rect = stage.current.getBoundingClientRect();
    setPoint({
      x: clamp((event.clientX - rect.left - frame.left) / frame.w),
      y: clamp((event.clientY - rect.top - frame.top) / frame.h)
    });
  }

  function key(event) {
    if (!loupe) return;
    if (event.key === 'Escape') {
      event.preventDefault();
      closeLoupe();
      return;
    }
    const step = event.shiftKey ? 0.1 : 0.025;
    const delta = { ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, -step], ArrowDown: [0, step] }[event.key];
    if (!delta) return;
    event.preventDefault();
    setPoint(p => ({ x: clamp(p.x + delta[0]), y: clamp(p.y + delta[1]) }));
  }

  let lens;
  if (loupe && frame) {
    const radius = Math.min(72, frame.width / 4, frame.height / 3);
    const x = frame.left + point.x * frame.w;
    const y = frame.top + point.y * frame.h;
    lens = <span className="loupe" aria-hidden="true" style={{
      width: radius * 2, height: radius * 2,
      left: clamp(x - radius, 0, frame.width - radius * 2),
      top: clamp(y - radius, 0, frame.height - radius * 2),
      backgroundImage: `url("${url}")`,
      backgroundOrigin: 'border-box',
      backgroundSize: `${frame.w * zoom}px ${frame.h * zoom}px`,
      backgroundPosition: `${radius - point.x * frame.w * zoom}px ${radius - point.y * frame.h * zoom}px`
    }} />;
  }

  return <div ref={stage} className={`photo-stage ${loupe ? 'photo-stage--loupe' : ''}`}
    tabIndex={loupe ? 0 : undefined} role={loupe ? 'group' : undefined}
    aria-label={loupe ? label + ' loupe. Use arrow keys to move and Escape to close.' : undefined}
    onKeyDown={key} onPointerMove={move} onPointerDown={event => {
      if (!loupe) return;
      if (event.pointerType !== 'mouse') event.currentTarget.setPointerCapture(event.pointerId);
      move(event);
    }}>
    <img ref={photo} src={url} alt={label} onLoad={measure} draggable="false" />
    <span className="photo-tag" aria-hidden="true">{label}</span>
    {lens}
  </div>;
}

function Compare({ reference, capture, wipe, setWipe, split }) {
  if (split) {
    return <div className="split">
      <Photo url={reference} label="Registered reference" />
      <Photo url={capture} label="Inspection capture" />
    </div>;
  }

  return <div className="wipe">
    <img src={capture} alt="Inspection capture" draggable="false" />
    <img src={reference} alt="Registered reference" draggable="false"
      style={{ clipPath: `inset(0 ${100 - wipe}% 0 0)` }} />
    <span className="photo-tag" aria-hidden="true">Registered reference</span>
    <span className="photo-tag photo-tag--right" aria-hidden="true">Inspection capture</span>
    <span className="wipe-line" aria-hidden="true" style={{ left: wipe + '%' }}><span>‹ ›</span></span>
    <input type="range" aria-label="Comparison wipe" aria-valuetext={wipe + '% reference visible'}
      min="0" max="100" value={wipe} onChange={event => setWipe(Number(event.target.value))} />
  </div>;
}

export default function OpticalBench({ capture, reference, captureLabel = 'Inspection capture', result = false }) {
  const [mode, setMode] = useState('Capture');
  const [loupe, setLoupe] = useState(false);
  const [split, setSplit] = useState(true);
  const [wipe, setWipe] = useState(50);
  const loupeButton = useRef(null);
  const view = reference ? mode : 'Capture';

  function show(next) {
    setMode(next);
    setLoupe(false);
    if (next === 'Compare') setSplit(true);
  }

  return <section className="bench" aria-label="Optical bench">
    <div className="bench-head">
      <span className="bench-label"><Icon name="scope" />Optical bench</span>
      {result && <div className="segments" role="group" aria-label="Photo view">
        {['Reference', 'Capture', 'Compare'].map(label =>
          <button key={label} type="button" disabled={!reference && label !== 'Capture'}
            aria-pressed={view === label} onClick={() => show(label)}>{label}</button>
        )}
      </div>}
    </div>
    {capture ? <div className="bench-image">
      {view === 'Compare' ? <Compare reference={reference} capture={capture} wipe={wipe} setWipe={setWipe}
        split={split} /> : <Photo key={view + (view === 'Reference' ? reference : capture)}
        url={view === 'Reference' ? reference : capture}
        label={view === 'Reference' ? 'Registered reference' : captureLabel}
        loupe={loupe} closeLoupe={() => { setLoupe(false); loupeButton.current.focus(); }} />}
      <span className="frame-corners" aria-hidden="true"><i/><i/><i/><i/></span>
    </div> : <div className="bench-empty">
      <div className="bench-target" aria-hidden="true"><Icon name="scope" /></div>
      <p>Photograph the component to begin</p>
      <small>Keep it centered, in focus, and evenly lit.</small>
    </div>}
    <div className="bench-foot">
      <span className="mono">{capture ? (view === 'Compare' ? (split ? 'Side by side' : wipe + '% reference')
        : loupe ? '2.4× magnification' : '1.0× overview') : 'Awaiting photograph'}</span>
      {capture && (view === 'Compare' ? <div className="segments" role="group" aria-label="Comparison layout">
        <button type="button" aria-pressed={split} onClick={() => setSplit(true)}>Side by side</button>
        <button type="button" aria-pressed={!split} onClick={() => setSplit(false)}>Wipe</button>
      </div> : <button ref={loupeButton} type="button" className="bench-tool" aria-pressed={loupe}
        onClick={() => setLoupe(!loupe)}>Loupe <span className="mono">2.4×</span></button>)}
    </div>
    {result && !reference && <p className="bench-note">Comparison is unavailable. The service stores fingerprints. Only the latest enrollment photo in this tab can be compared.</p>}
  </section>;
}
