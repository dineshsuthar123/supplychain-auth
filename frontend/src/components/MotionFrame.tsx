import { useEffect, useRef, type ReactNode } from 'react';
import { useLocation, useNavigationType } from 'react-router-dom';
import { useReducedMotion } from '../lib/motion';

export default function MotionFrame({ children }: { children: ReactNode }) {
  const location = useLocation();
  const navigation = useNavigationType();
  const previous = useRef(location.pathname);
  const surface = useRef<HTMLDivElement>(null);
  const curtain = useRef<HTMLDivElement>(null);
  const reduced = useReducedMotion();
  const workbench = location.pathname.startsWith('/app');
  const title = location.pathname.includes('handoffs')
    ? 'Handoffs'
    : location.pathname.includes('incidents')
      ? 'Incidents'
      : location.pathname.includes('evidence')
        ? 'Evidence'
        : location.pathname.includes('workspace')
          ? 'Your network'
          : workbench
            ? 'Trace'
            : location.pathname === '/'
              ? 'Follow the lot'
              : 'Pick up the thread';
  useEffect(() => {
    document.documentElement.dataset.motion = reduced ? 'reduced' : 'full';
    if (previous.current === location.pathname) return;
    previous.current = location.pathname;
    if (navigation !== 'POP') window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
    if (reduced || document.visibilityState === 'hidden') return;
    const animations: Animation[] = [];
    if (surface.current)
      animations.push(
        surface.current.animate(
          [
            { opacity: 0.7, transform: 'translateY(10px)' },
            { opacity: 1, transform: 'translateY(0)' },
          ],
          { duration: workbench ? 210 : 380, easing: 'cubic-bezier(.16,1,.3,1)' },
        ),
      );
    if (curtain.current)
      animations.push(
        curtain.current.animate(
          [
            { transform: 'translateX(-110%) skewX(-12deg)', opacity: 0, offset: 0 },
            { transform: 'translateX(-48%) skewX(-12deg)', opacity: 1, offset: 0.2 },
            { transform: 'translateX(110%) skewX(-12deg)', opacity: 0, offset: 1 },
          ],
          { duration: workbench ? 320 : 560, easing: 'cubic-bezier(.65,0,.35,1)' },
        ),
      );
    return () => animations.forEach((animation) => animation.cancel());
  }, [location.pathname, navigation, reduced, workbench]);
  return (
    <>
      <div className="route-surface" ref={surface}>
        {children}
      </div>
      <div className="thread-transition" ref={curtain} aria-hidden="true">
        <span>LOTLINE / {title}</span>
        <i />
        <i />
        <i />
      </div>
    </>
  );
}
