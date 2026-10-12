import { useEffect, useRef, useState } from 'react';
import LotCharacter from '../../components/LotCharacter';
import { useReducedMotion } from '../../lib/motion';

export default function CinemaScene({
  chapter,
  selectChapter,
}: {
  chapter: number;
  selectChapter: (chapter: number) => void;
}) {
  const root = useRef<HTMLDivElement>(null);
  const reduced = useReducedMotion();
  const [angle, setAngle] = useState(0);
  useEffect(() => {
    const node = root.current;
    if (!node) return;
    let intersecting = true;
    const update = () =>
      (node.dataset.awake = String(
        intersecting && !reduced && document.visibilityState === 'visible',
      ));
    const observer = new IntersectionObserver(([entry]) => {
      intersecting = entry.isIntersecting;
      update();
    });
    observer.observe(node);
    document.addEventListener('visibilitychange', update);
    update();
    return () => {
      observer.disconnect();
      document.removeEventListener('visibilitychange', update);
    };
  }, [reduced]);
  return (
    <div
      className={`cinema-scene scene-chapter-${chapter}`}
      ref={root}
      onPointerMove={(event) => {
        if (reduced || event.pointerType !== 'mouse') return;
        const bounds = event.currentTarget.getBoundingClientRect();
        event.currentTarget.style.setProperty(
          '--look-x',
          `${((event.clientX - bounds.left) / bounds.width - 0.5) * 14}px`,
        );
        event.currentTarget.style.setProperty(
          '--look-y',
          `${((event.clientY - bounds.top) / bounds.height - 0.5) * 10}px`,
        );
      }}
      onPointerLeave={(event) => {
        event.currentTarget.style.setProperty('--look-x', '0px');
        event.currentTarget.style.setProperty('--look-y', '0px');
      }}
    >
      <div className="scene-caption">
        <span>SYNTHETIC CASE / LO · ORIGIN</span>
        <span>01 — 03</span>
      </div>
      <div className="scene-space" style={{ transform: `rotateY(${angle}deg)` }}>
        <div className="scene-orbit orbit-one" />
        <div className="scene-orbit orbit-two" />
        <div className="scene-halo" />
        <svg className="scene-thread" viewBox="0 0 620 600" aria-hidden="true">
          <path d="M-80 440C70 440 115 555 292 490S600 430 565 288S486 108 394 155S437 421 661 401" />
          <path
            className="scene-thread-second"
            d="M80 470C122 339 151 320 193 296S182 134 310 121S494 186 526 71"
          />
          <circle cx="95" cy="453" r="7" />
          <circle cx="572" cy="329" r="7" />
        </svg>
        <div className="scene-actor">
          <LotCharacter
            variant={chapter === 2 ? 'sentinel' : chapter === 1 ? 'witness' : 'origin'}
          />
        </div>
        <span className="scene-cross cross-one">+</span>
        <span className="scene-cross cross-two">+</span>
        <div className="scene-tag tag-origin">
          <i />
          <span>CF-2401</span>
          <small>1,200 units · one origin</small>
        </div>
        <div className="scene-tag tag-receipt">
          <span>{chapter === 2 ? 'DEMO HOLD' : chapter === 1 ? '480 / 500' : 'A1 + A2 + B'}</span>
          <small>
            {chapter === 2
              ? '20 still need resolution'
              : chapter === 1
                ? 'Both claims preserved'
                : 'Every contribution stays visible'}
          </small>
        </div>
        <span className="scene-coordinate">CF-2401 / A CONNECTED STORY</span>
      </div>
      <div className="scene-switches" role="group" aria-label="Story camera chapters">
        {['Trace', 'Reconcile', 'Contain'].map((name, index) => (
          <button
            key={name}
            aria-label={`${name} scene`}
            aria-pressed={chapter === index}
            onClick={() => selectChapter(index)}
          >
            {String(index + 1).padStart(2, '0')} / {name}
          </button>
        ))}
      </div>
      <div className="scene-controls">
        <span>Move your pointer. Follow the thread.</span>
        <button
          aria-label="Change camera angle"
          onClick={() => setAngle((a) => (a === 0 ? -16 : a === -16 ? 16 : 0))}
        >
          Orbit view ↻
        </button>
      </div>
    </div>
  );
}
