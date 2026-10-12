import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Brand, Status } from '../../components/UI';
import LotCharacter from '../../components/LotCharacter';
import CinemaScene from './CinemaScene';
import { useReducedMotion, useMotionMode, toggleMotion, motionReduced } from '../../lib/motion';

const chapters = [
  {
    name: 'Trace',
    verb: 'A beginning.',
    copy: 'One lot becomes many. Every contribution keeps its thread.',
    variant: 'origin' as const,
    role: 'LO / THE ORIGIN',
  },
  {
    name: 'Reconcile',
    verb: 'Two perspectives.',
    copy: 'What left and what arrived are two independent assertions.',
    variant: 'witness' as const,
    role: 'RO / THE WITNESS',
  },
  {
    name: 'Contain',
    verb: 'An accountable ending.',
    copy: 'Hold affected stock. Get a response. Account for every unit.',
    variant: 'sentinel' as const,
    role: 'CO / THE SENTINEL',
  },
];

export default function Story() {
  const root = useRef<HTMLElement>(null);
  const reduced = useReducedMotion();
  const motionMode = useMotionMode();
  const [chapter, setChapter] = useState(0);
  const [defect, setDefect] = useState(false);
  const [hold, setHold] = useState(false);
  const [intro, setIntro] = useState(
    () => !motionReduced() && !sessionStorage.getItem('lotline-film-seen'),
  );
  const [replay, setReplay] = useState(0);
  const activeChapter = hold ? 2 : defect ? 1 : chapter;
  useEffect(() => {
    if (!intro || reduced) return;
    sessionStorage.setItem('lotline-film-seen', 'seen');
    const timer = window.setTimeout(() => setIntro(false), 2850);
    const skip = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setIntro(false);
    };
    window.addEventListener('keydown', skip);
    return () => {
      window.clearTimeout(timer);
      window.removeEventListener('keydown', skip);
    };
  }, [intro, reduced, replay]);
  useEffect(() => {
    if (reduced) return;
    let cancelled = false;
    let context: gsap.Context | undefined;
    void Promise.all([import('gsap'), import('gsap/ScrollTrigger')])
      .then(([{ gsap }, { ScrollTrigger }]) => {
        if (cancelled) return;
        gsap.registerPlugin(ScrollTrigger);
        context = gsap.context(() => {
          gsap.fromTo(
            '.hero-thread-path',
            { strokeDashoffset: 1400 },
            { strokeDashoffset: 0, duration: 1.6, ease: 'power2.inOut' },
          );
          for (const item of gsap.utils.toArray<HTMLElement>('[data-reveal]')) {
            gsap.fromTo(
              item,
              { y: 38 },
              {
                y: 0,
                duration: 0.65,
                ease: 'power3.out',
                scrollTrigger: { trigger: item, start: 'top 94%', once: true },
              },
            );
          }
          gsap.to('.story-watermark', {
            xPercent: -12,
            ease: 'none',
            scrollTrigger: {
              trigger: '.cast-section',
              start: 'top bottom',
              end: 'bottom top',
              scrub: 0.5,
            },
          });
        }, root);
      })
      .catch(() => {
        /* Optional motion can fail to load without gating the complete static story. */
      });
    return () => {
      cancelled = true;
      context?.revert();
    };
  }, [reduced]);
  const follow = () => {
    setIntro(false);
    document
      .getElementById('coffee-case')
      ?.scrollIntoView({ behavior: reduced ? 'auto' : 'smooth', block: 'start' });
  };
  return (
    <main className="story-page cinema-page" ref={root}>
      <a className="skip-link" href="#story-content">
        Skip to story
      </a>
      {intro && !reduced && (
        <aside className="opening-film" key={replay} aria-label="Lotline opening film">
          <div className="film-grain" />
          <span className="film-kicker">LOTLINE PRESENTS / A CONNECTED STORY</span>
          <div className="film-type" aria-hidden="true">
            <span>EVERY LOT.</span>
            <span>A LIFE OF</span>
            <em>its own.</em>
          </div>
          <div className="film-character">
            <LotCharacter />
          </div>
          <svg
            className="film-thread"
            viewBox="0 0 1440 900"
            preserveAspectRatio="none"
            aria-hidden="true"
          >
            <path d="M-50 690C220 850 241 370 660 460S1033 859 1510 152" />
          </svg>
          <button className="film-skip" onClick={() => setIntro(false)}>
            Skip opening ↗ <span>ESC</span>
          </button>
          <span className="film-counter" aria-hidden="true">
            ORIGIN → CONTRIBUTION → CUSTODY
          </span>
        </aside>
      )}
      <header className="public-rail cinema-rail">
        <Link to="/" className="brand-link">
          <Brand />
        </Link>
        <a href="#coffee-case" className="rail-story-link">
          Explore the story <span>↘</span>
        </a>
        <div>
          <Link className="text-link" to="/login">
            Sign in
          </Link>
          <Link className="button small-button" to="/create">
            Start your network ↗
          </Link>
        </div>
      </header>
      <section className="cinematic-hero" id="story-content">
        <div className="hero-editorial">
          <p className="cinema-eyebrow">
            <span /> TRACEABILITY, WITH A PULSE.
          </p>
          <h1 className="hero-title">
            <span>Every lot.</span>
            <span>Many lives.</span>
            <em>One thread.</em>
          </h1>
          <p className="hero-positioning">Follow the lot. Contain the fallout.</p>
          <p className="cinema-description">
            A batch leaves your floor. Its story keeps moving. Follow every contribution, reconcile
            every handoff, and bring an incident to an accountable end.
          </p>
          <div className="hero-actions">
            <Link className="button cinema-primary" to="/create">
              Start a workspace <span>↗</span>
            </Link>
            <button
              className="film-play"
              onClick={() => {
                if (!reduced) {
                  setIntro(true);
                  setReplay((v) => v + 1);
                } else follow();
              }}
            >
              <span>▶</span>
              {reduced ? 'Explore the static story' : 'Replay opening film'}
            </button>
          </div>
          <p className="hero-footnote">
            For small manufacturers. And every pair of hands after them.
          </p>
        </div>
        <CinemaScene
          chapter={activeChapter}
          selectChapter={(value) => {
            setChapter(value);
            setDefect(false);
            setHold(false);
          }}
        />
        <div className="hero-bottom-line">
          <span>OPERATIONAL TRACEABILITY / CONNECTED PARTNERS</span>
          <button onClick={follow}>
            Follow a sample lot <span>↓</span>
          </button>
          <span>SCROLL TO UNRAVEL</span>
        </div>
        <svg
          className="hero-thread"
          viewBox="0 0 1440 160"
          preserveAspectRatio="none"
          aria-hidden="true"
        >
          <path className="hero-thread-path" d="M-50 80C250 190 280 -20 573 72S1070 145 1510 6" />
        </svg>
      </section>
      <section className="cast-section" aria-labelledby="cast-heading">
        <svg className="story-watermark" viewBox="0 0 1500 230" aria-hidden="true">
          <text
            x="0"
            y="200"
            fill="currentColor"
            fontFamily="DM Sans Variable, sans-serif"
            fontSize="240"
            fontWeight="750"
            letterSpacing="-16"
          >
            CONNECTED.
          </text>
        </svg>
        <div className="section-intro" data-reveal>
          <p className="eyebrow">01 / THE CAST OF A CONNECTED STORY</p>
          <h2 id="cast-heading">
            A lot of character.
            <br />
            <em>A clear chain of context.</em>
          </h2>
          <p>Three original paper characters. Three stages in a real operational story.</p>
        </div>
        <div className="cast-grid">
          {chapters.map((item, index) => (
            <button
              key={item.name}
              className={`cast-card ${chapter === index ? 'selected' : ''}`}
              onClick={() => {
                setChapter(index);
                setDefect(false);
                setHold(false);
              }}
              aria-pressed={chapter === index}
              data-reveal
            >
              <div className="cast-card-top">
                <span>ACT 0{index + 1}</span>
                <span>↗</span>
              </div>
              <div className="cast-portrait">
                <LotCharacter variant={item.variant} />
                <span className="cast-orbit" />
              </div>
              <p className="eyebrow">{item.role}</p>
              <h3>
                {item.name}
                <span>.</span>
              </h3>
              <p className="cast-verb">{item.verb}</p>
              <p>{item.copy}</p>
            </button>
          ))}
        </div>
        <div className="cast-footnote">
          <span>Brand characters explain the story. People assert the facts.</span>
          <a href="#coffee-case">Meet the coffee case ↘</a>
        </div>
      </section>
      <section className="coffee-cinema" id="coffee-case" aria-labelledby="case-heading">
        <div className="case-editorial" data-reveal>
          <p className="cinema-eyebrow">02 / COFFEE PACKAGING · SYNTHETIC WALKTHROUGH</p>
          <h2 id="case-heading">
            One defect.
            <br />
            <em>
              Every affected
              <br />
              branch.
            </em>
          </h2>
          <p>
            CF-2401 starts with 1,200 units. Three partners count 1,180 received. The missing 20
            stay visible. Another 200 have no known contribution from this lot.
          </p>
          <div className="case-steps">
            <button
              aria-pressed={defect}
              onClick={() => {
                setDefect(true);
                setHold(false);
              }}
            >
              <span aria-hidden="true">01</span>
              <span>Select defective CF-2401 ↗</span>
            </button>
            <div>
              <span>02</span>
              <span>
                Compare 500 sent / 480 received
                <br />
                <small>20 remain unresolved, never silently restored.</small>
              </span>
            </div>
            <button
              onClick={() => {
                setDefect(true);
                setHold(true);
              }}
            >
              <span aria-hidden="true">03</span>
              <span>Activate synthetic hold</span>
            </button>
          </div>
          <p className="case-disclosure">
            Fixture data. No workspace stock is changed by this walkthrough.
          </p>
        </div>
        <div
          className={`case-stage ${defect ? 'case-defect' : ''} ${hold ? 'case-held' : ''}`}
          data-reveal
        >
          <div className="case-slate">
            <span>THE COFFEE CASE / CF-2401</span>
            <span>
              {hold ? 'CONTAIN' : defect ? 'RECONCILE' : 'TRACE'} / 0{hold ? 3 : defect ? 2 : 1}
            </span>
          </div>
          <div className="case-diagram">
            <svg
              className="case-contributions"
              viewBox="0 0 660 420"
              preserveAspectRatio="none"
              aria-hidden="true"
            >
              <path d="M328 78V118Q328 139 219 139T111 174V225M328 118Q328 139 437 139T548 174V225" />
              <path d="M111 270V316M548 270V316" />
              <circle cx="328" cy="116" r="6" />
            </svg>
            <button
              className="case-lot case-root"
              onClick={() => {
                setDefect(true);
                setHold(false);
              }}
              aria-label="Inspect synthetic root lot CF-2401"
            >
              <small>ORIGIN / COMMISSIONED</small>
              <b>CF-2401</b>
              <span>1,200 units</span>
              {defect && <i>Packaging defect</i>}
            </button>
            <div className="case-lot case-a">
              <small>SPLIT / CONTRIBUTION</small>
              <b>CF-2401-A</b>
              <span>700 units</span>
            </div>
            <div className="case-lot case-b">
              <small>SPLIT / CONTRIBUTION</small>
              <b>CF-2401-B</b>
              <span>500 units</span>
            </div>
            <div className="case-claim claim-a">
              <span>↳ A1 / A2 · TWO PARTNERS</span>
              <b>350 + 350</b>
              <small>{hold ? 'Held at both sites' : 'Independent receipts'}</small>
            </div>
            <div className="case-claim claim-b">
              <span>↳ THIRD PARTNER</span>
              <b>
                480 <i>/ 500</i>
              </b>
              <small>{hold ? 'Held · 20 unresolved' : '20 unresolved'}</small>
            </div>
            <span className="diagram-label">consumed into</span>
          </div>
          <div className="case-safe">
            <span>○ CF-2402</span>
            <b>200 unrelated units</b>
            <small>Outside known scope</small>
          </div>
          <div className="case-accounting">
            <div>
              <span>{hold ? 'HELD' : 'RECEIVED'}</span>
              <b>1,180</b>
            </div>
            <div>
              <span>UNRESOLVED</span>
              <b>20</b>
            </div>
            <div>
              <span>UNAFFECTED</span>
              <b>200</b>
            </div>
          </div>
          {hold && (
            <div role="status" className="case-result">
              <Status value="HELD" />
              <p>
                1,180 received units held across three sites. Closure stays blocked by the 20
                unresolved units. An acknowledgment alone does not recover stock.
              </p>
            </div>
          )}
          <button
            className="case-reset"
            onClick={() => {
              setDefect(false);
              setHold(false);
            }}
          >
            Reset the case ↺
          </button>
        </div>
      </section>
      <section className="workbench-preview" data-reveal>
        <div className="section-intro">
          <p className="eyebrow">03 / THE THREAD BECOMES THE TOOL</p>
          <h2>
            A story you can
            <br />
            <em>actually act on.</em>
          </h2>
          <p>
            The workbench uses authenticated, accepted records. Stock, permissions and holds come
            from the server.
          </p>
          <Link to="/login" className="button">
            Open your workbench ↗
          </Link>
        </div>
        <div
          className="workbench-poster"
          aria-label="Illustrated example of Lotline workbench capabilities"
        >
          <div className="poster-rail">
            <Brand />
            <span>TRACE / HANDOFFS / INCIDENTS</span>
          </div>
          <div className="poster-field">
            <div>
              <small>GENEALOGY</small>
              <span className="poster-node">Origin lot</span>
              <span className="poster-route">contributed to →</span>
              <span className="poster-node">Current holdings</span>
            </div>
            <div>
              <small>CUSTODY</small>
              <p>
                Sent claim <b>≠</b> Received claim
              </p>
              <span>Independent actors. Explicit discrepancies.</span>
            </div>
            <div>
              <small>INCIDENT RESPONSE</small>
              <p>Hold → Respond → Recover</p>
              <span>Accountable loss. Justified closure.</span>
            </div>
          </div>
          <div className="poster-caption">ILLUSTRATED PREVIEW · SIGN IN FOR YOUR REAL NETWORK</div>
        </div>
      </section>
      <section className="cinema-ending" data-reveal>
        <p className="cinema-eyebrow">THE NEXT CHAPTER IS YOURS.</p>
        <h2>
          Follow the lot.
          <br />
          <em>Contain the fallout.</em>
        </h2>
        <div>
          <p>
            Connect your floor, warehouses and retail partners. Keep every contribution, every
            claim, and every accountable action in view.
          </p>
          <Link to="/create" className="button cinema-primary">
            Build your connected network <span>↗</span>
          </Link>
        </div>
        <svg viewBox="0 0 1300 260" preserveAspectRatio="none" aria-hidden="true">
          <path d="M-20 148C219 -78 478 333 702 170S1100 182 1320 2" />
        </svg>
      </section>
      <section className="cinema-honesty">
        <span className="eyebrow">AN HONEST RECORD HAS HONEST LIMITS.</span>
        <p>
          Lotline records partner assertions. It cannot physically stop a truck, independently
          verify a count, or discover events nobody recorded. An export hash verifies bytes, not
          physical authenticity.
        </p>
      </section>
      <footer className="cinema-footer">
        <Link to="/" className="footer-wordmark" aria-label="Lotline home">
          Lotline<span>↗</span>
        </Link>
        <div>
          <span>TRACE / RECONCILE / CONTAIN</span>
          <button onClick={toggleMotion}>
            {motionMode === 'reduced' ? 'Use device motion preference' : 'Reduce motion'}
          </button>
          <span>Follow the lot. Contain the fallout.</span>
        </div>
      </footer>
    </main>
  );
}
