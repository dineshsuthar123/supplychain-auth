import React from 'react';

const paths = {
  fingerprint: 'M3 15c-2-6 1-12 7-13 6-1 11 4 11 10 0 6-3 10-8 10-4 0-8-4-9-8m3 2c-2-4-1-9 3-10 4-1 7 2 7 6 0 3-1 6-4 6-2 0-4-3-4-6 0-1 1-3 2-3 2 0 3 2 3 4 0 1-.5 2-1 2-1 0-1.5-1-1.5-2',
  plus: 'M12 5v14M5 12h14',
  search: 'm20 20-4.4-4.4m2.4-5.1a7.5 7.5 0 1 1-15 0 7.5 7.5 0 0 1 15 0Z',
  check: 'm5 12 4.2 4.2L19 6.5',
  alert: 'M12 8v5m0 3v.1M12 3l10 18H2Z',
  logout: 'M10 5H5v14h5m4-4 5-3-5-3m5 3H10',
  camera: 'M3 6h4l2-3h6l2 3h4v14H3Zm13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
  scope: 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Zm-5 0a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
  arrow: 'M4 12h15m-6-6 6 6-6 6',
  retake: 'M5 8a8 8 0 1 1-1 8M5 3v5h5'
};

export function Icon({ name }) {
  return <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none"
    stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"><path d={paths[name]} /></svg>;
}

export function Brand() {
  return <span className="brand"><Icon name="fingerprint" />SupplyPrint</span>;
}

export function Button({ children, kind = 'primary', icon, className = '', ...props }) {
  return <button type="button" className={`button button--${kind} ${className}`} {...props}>
    {icon && <Icon name={icon} />}{children}
  </button>;
}

export function Status({ children, tone = 'neutral', icon }) {
  return <span className={`status status--${tone}`}>{icon ? <Icon name={icon} /> : <i aria-hidden="true" />}{children}</span>;
}

export function Data({ label, value, mono = false }) {
  if (value == null || value === '') return null;
  return <div className="data-row"><dt>{label}</dt><dd className={mono ? 'mono' : undefined}>{value}</dd></div>;
}

export function Time({ value }) {
  if (!value) return null;
  // Offset-free API timestamps are server wall time. Preserve the supplied clock.
  const text = typeof value === 'string' ? value.replace('T', ' ').replace(/(\.\d+)/, '') : value;
  return <time className="mono" dateTime={value}>{text}</time>;
}

export function Notice({ issue }) {
  if (!issue) return null;
  return <div className="notice" role="alert"><Icon name="alert" /><div><strong>{issue.title}</strong>
    {issue.detail && <p>{issue.detail}</p>}</div></div>;
}

export function Heading({ label, title, em, description, action }) {
  return <div className="page-heading"><div><p className="eyebrow ruled">{label}</p>
    <h1>{title}{em && <> <em>{em}</em></>}</h1>{description && <p className="lede">{description}</p>}</div>{action}</div>;
}
