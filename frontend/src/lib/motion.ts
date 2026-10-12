import { useSyncExternalStore } from 'react';

const preference = '(prefers-reduced-motion: reduce)';
function subscribe(callback: () => void) {
  const media = window.matchMedia(preference);
  media.addEventListener('change', callback);
  window.addEventListener('lotline-motion', callback);
  window.addEventListener('storage', callback);
  return () => {
    media.removeEventListener('change', callback);
    window.removeEventListener('lotline-motion', callback);
    window.removeEventListener('storage', callback);
  };
}
export function motionReduced() {
  return (
    window.matchMedia(preference).matches || localStorage.getItem('lotline-motion') === 'reduced'
  );
}
export function useReducedMotion() {
  return useSyncExternalStore(subscribe, motionReduced, () => true);
}
function mode() {
  return localStorage.getItem('lotline-motion') === 'reduced' ? 'reduced' : 'device';
}
export function useMotionMode() {
  return useSyncExternalStore(subscribe, mode, () => 'device');
}
export function toggleMotion() {
  const manual = localStorage.getItem('lotline-motion') === 'reduced';
  localStorage.setItem('lotline-motion', manual ? 'auto' : 'reduced');
  window.dispatchEvent(new Event('lotline-motion'));
}
