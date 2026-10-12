import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Brand, Status } from '../../components/UI';
export default function Story() {
  const stage = useRef<HTMLDivElement>(null);
  const timeline = useRef<gsap.core.Timeline | null>(null);
  const [chapter, setChapter] = useState(0);
  const [demo, setDemo] = useState(false);
  const [defect, setDefect] = useState(false);
  const [hold, setHold] = useState(false);
  useEffect(() => {
    const media = window.matchMedia('(prefers-reduced-motion: reduce)');
    // The complete SVG is visible before the optional animation library loads.
    if (media.matches) return;
    let cancelled = false;
    let context: gsap.Context | undefined;
    void import('gsap').then(({ gsap }) => {
      if (cancelled) return;
      context = gsap.context(() => {
        const play = !media.matches && !sessionStorage.getItem('lotline-opening');
        sessionStorage.setItem('lotline-opening', 'seen');
        const routes = gsap.utils.toArray<SVGPathElement>('.ribbon-route');
        routes.forEach((p) => {
          const length = p.getTotalLength();
          gsap.set(p, { strokeDasharray: length, strokeDashoffset: length });
        });
        timeline.current = gsap
          .timeline({ paused: true })
          .to('.ribbon-route.root', { strokeDashoffset: 0, duration: 0.6 })
          .to('.ribbon-route.branch', { strokeDashoffset: 0, duration: 0.8 }, 0.6)
          .from('.stage-output', { opacity: 0, y: 12, duration: 0.4 }, 0.9)
          .to('.ribbon-route.receipt', { strokeDashoffset: 0, duration: 0.8 }, 1.4)
          .from('.stage-receiver', { opacity: 0, duration: 0.4 }, 1.8)
          .fromTo(
            '.incident-signal',
            { opacity: 0, scale: 0.7 },
            { opacity: 1, scale: 1, duration: 0.7 },
            2.2,
          )
          .to('.stage-root,.stage-output', { borderColor: '#ac593b', duration: 0.7 }, 2.9);
        if (play) timeline.current.play();
        else timeline.current.progress(1);
      }, stage);
    });
    return () => {
      cancelled = true;
      context?.revert();
    };
  }, []);
  const replay = () => {
    setDefect(false);
    setHold(false);
    if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) timeline.current?.restart();
  };
  const activeChapter = demo ? (hold ? 2 : defect ? 1 : 0) : chapter;
  return (
    <main className="story-page">
      <a className="skip-link" href="#story-content">
        Skip to story
      </a>
      <header className="public-rail">
        <Link to="/" className="brand-link">
          <Brand />
        </Link>
        <span className="public-positioning">Operational traceability / connected partners</span>
        <div>
          <Link className="text-link" to="/login">
            Sign in
          </Link>
          <Link className="button small-button" to="/create">
            Start your network ↗
          </Link>
        </div>
      </header>
      <section className="hero" id="story-content">
        <div className="hero-copy">
          <p className="eyebrow">
            <span className="live-dot" />A continuous chain of context
          </p>
          <h1>
            One batch.
            <br />
            Many hands.
            <br />
            <em>
              Every step
              <br className="desktop-only" /> accounted for.
            </em>
          </h1>
          <p className="hero-positioning">Follow the lot. Contain the fallout.</p>
          <p className="hero-description">
            From the packing floor to the partner shelf, trace the lot’s contribution, reconcile
            what arrived, and turn an incident into accountable action.
          </p>
          <div className="hero-actions">
            <Link className="button" to="/create">
              Start a workspace <span>↗</span>
            </Link>
            <button
              className="text-button"
              onClick={() => {
                setDemo(true);
                setDefect(false);
                setHold(false);
                document.getElementById('ribbon-stage')?.scrollIntoView({
                  behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches
                    ? 'auto'
                    : 'smooth',
                  block: 'center',
                });
              }}
            >
              Follow a sample lot →
            </button>
          </div>
          <p className="hero-footnote">
            Built for small manufacturers and the partners who move their stock.
          </p>
        </div>
        <div
          className={`hero-stage chapter-${activeChapter} ${defect ? 'defect-active' : ''} ${hold ? 'demo-held' : ''}`}
          id="ribbon-stage"
          ref={stage}
        >
          <div className="stage-top">
            <span className="eyebrow">
              {demo ? 'SYNTHETIC WALKTHROUGH' : 'COFFEE CASE / SYNTHETIC'}
            </span>
            <button className="text-button" onClick={() => timeline.current?.progress(1)}>
              Skip motion ↗
            </button>
          </div>
          <div className="ribbon-world">
            <svg
              viewBox="0 0 650 550"
              preserveAspectRatio="xMidYMid meet"
              aria-hidden="true"
              className="ribbon-svg"
            >
              <path className="ribbon-route root" d="M105 15 C105 40 105 60 105 95" />
              <path
                className="ribbon-route branch affected-path"
                d="M230 130 C315 130 285 270 370 270"
              />
              <path
                className="ribbon-route branch affected-path"
                d="M230 130 C310 130 285 420 370 420"
              />
              <path
                className="ribbon-route receipt affected-path"
                d="M505 270 C560 270 590 220 620 220"
              />
              <path
                className="ribbon-route receipt affected-path"
                d="M505 420 C560 420 590 365 620 365"
              />
              <path
                className="ribbon-route unaffected-path"
                d="M125 505 C260 505 270 505 335 505"
              />
              <circle className="incident-signal" cx="287" cy="182" r="13" />
              <path d="M287 176v8m0 4v1" stroke="#fff4df" strokeWidth="2" />
            </svg>
            <svg viewBox="0 0 360 650" aria-hidden="true" className="ribbon-svg mobile-ribbon">
              <path className="ribbon-route root" d="M100 4V32" />
              <path
                className="ribbon-route branch affected-path"
                d="M232 105C302 105 55 265 110 265"
              />
              <path
                className="ribbon-route branch affected-path"
                d="M232 105C330 105 55 450 110 450"
              />
              <path
                className="ribbon-route receipt affected-path"
                d="M275 280C315 280 295 370 320 370"
              />
              <path
                className="ribbon-route receipt affected-path"
                d="M275 465C315 465 295 555 320 555"
              />
              <path className="ribbon-route unaffected-path" d="M140 610H270" />
            </svg>
            <button
              className="story-lot stage-root"
              onClick={() => {
                setDemo(true);
                setDefect(true);
              }}
              aria-pressed={defect}
            >
              <span className="eyebrow">MANUFACTURER / ROOT LOT</span>
              <b>CF-2401</b>
              <span>
                1,200 <small>units commissioned</small>
              </span>
              <i>{defect ? '◈ Packaging defect reported' : 'Select to follow the lot ↗'}</i>
            </button>
            <div className="story-lot stage-output output-a">
              <span className="eyebrow">SPLIT / CONTRIBUTION</span>
              <b>CF-2401-A</b>
              <span>
                700 <small>units</small>
              </span>
              <i>{hold ? '◈ Held across two sites' : 'A1 / 350 + A2 / 350'}</i>
            </div>
            <div className="story-lot stage-output output-b">
              <span className="eyebrow">SPLIT / CONTRIBUTION</span>
              <b>CF-2401-B</b>
              <span>
                500 <small>units dispatched</small>
              </span>
              <i>{defect ? '480 received / 20 unresolved' : 'Separate sender & receiver claims'}</i>
            </div>
            <div className="stage-receiver receiver-a">
              <span>✓</span>
              <p>
                Two partners
                <br />
                <b>700 received</b>
              </p>
            </div>
            <div className="stage-receiver receiver-b">
              <span>{hold ? '◈' : '↳'}</span>
              <p>
                Third partner
                <br />
                <b>480 received</b>
              </p>
            </div>
            <div className="unrelated-seal">
              <span>○ CF-2402</span>
              <b>200 unrelated units</b>
              <small>Known lineage stays outside scope</small>
            </div>
            <span className="route-label label-a">split into</span>
            <span className="route-label label-b">received / unresolved</span>
          </div>
          <div className="stage-bottom">
            <div>
              <p className="eyebrow">
                {hold
                  ? 'CONTAIN / HELD STOCK'
                  : defect
                    ? 'RECONCILE / A VISIBLE GAP'
                    : 'TRACE / KNOWN CONTRIBUTIONS'}
              </p>
              <p>
                {hold
                  ? '1,180 units held. 20 still require accountable resolution.'
                  : defect
                    ? 'A 500-unit claim becomes 480 received + 20 unresolved.'
                    : 'Current holdings count once. Historical parents stay in the record.'}
              </p>
            </div>
            <span className="stage-coordinate">
              {String(activeChapter + 1).padStart(2, '0')} / 03
            </span>
          </div>
        </div>
      </section>
      <section className="chapter-rail" aria-label="Trace, reconcile, contain story chapters">
        {[
          ['Trace', 'Know where the batch contributed.'],
          ['Reconcile', 'Keep both sides of every handoff.'],
          ['Contain', 'Make every affected unit accountable.'],
        ].map(([title, copy], i) => (
          <button
            className={chapter === i ? 'active' : ''}
            key={title}
            onClick={() => {
              setChapter(i);
              setDemo(false);
            }}
          >
            <span className="chapter-number">0{i + 1}</span>
            <div>
              <h2>{title}</h2>
              <p>{copy}</p>
            </div>
            <span>↗</span>
          </button>
        ))}
      </section>
      <section className="public-demo">
        <div>
          <p className="eyebrow">A synthetic case / coffee packaging</p>
          <h2>
            A defect has a path.
            <br />
            <em>So does containment.</em>
          </h2>
          <p>
            Use the sample ribbon to inspect the affected root, compare dispatch and receipt, and
            activate a demo hold. Sample outputs are fixture data. Your authenticated workspace
            always uses server records.
          </p>
        </div>
        <div className="demo-controls">
          <ol>
            <li>
              <span>01</span>
              <button
                className="button secondary"
                onClick={() => {
                  setDemo(true);
                  setDefect(true);
                  setHold(false);
                }}
              >
                Select defective CF-2401 ↗
              </button>
            </li>
            <li>
              <span>02</span>
              <p>
                <b>500 sent → 480 received</b>
                <br />
                20 units have unresolved custody.
              </p>
            </li>
            <li>
              <span>03</span>
              <button
                className="button incident-button"
                disabled={!defect}
                onClick={() => {
                  setDemo(true);
                  setHold(true);
                }}
              >
                Activate synthetic hold
              </button>
            </li>
          </ol>
          {hold && (
            <div role="status" className="demo-result">
              <Status value="HELD" />
              <p>
                1,180 received units are affected across three sites. The unrelated 200 remain
                outside known scope. Closure stays blocked by the 20 unresolved units.
              </p>
            </div>
          )}
          <button className="text-button" onClick={replay}>
            Replay the opening ribbon ↻
          </button>
        </div>
      </section>
      <section className="public-limits">
        <span className="eyebrow">A useful record has honest limits.</span>
        <p>
          Lotline enforces connected inventory and recall workflows. It records what each partner
          asserts. It cannot physically stop a truck, independently verify a count, or discover
          events nobody recorded.
        </p>
        <Link to="/create" className="button">
          Build your connected network ↗
        </Link>
      </section>
      <footer className="public-footer">
        <Brand />
        <span>Follow the lot. Contain the fallout.</span>
        <span>Trace / Reconcile / Contain</span>
      </footer>
    </main>
  );
}
