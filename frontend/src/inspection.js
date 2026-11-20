export function verdict(result) {
  switch (result.decision) {
    case 'MATCH': return { label: 'Fingerprint match', tone: 'success', icon: 'check' };
    case 'NO_MATCH': return { label: 'Fingerprint mismatch', tone: 'warning', icon: 'alert' };
    case 'UNKNOWN_PRODUCT': return { label: 'Unknown product', tone: 'neutral', icon: 'search' };
    case 'LEDGER_MISMATCH': return { label: 'Ledger hash mismatch', tone: 'warning', icon: 'alert' };
    default:
      if (result.verified === true) return verdict({ decision: 'MATCH' });
      if (result.verified === false && result.warning === 'No fingerprint on record for this product') {
        return verdict({ decision: 'UNKNOWN_PRODUCT' });
      }
      if (result.warning === 'Ledger hash mismatch') return verdict({ decision: 'LEDGER_MISMATCH' });
      if (result.verified === false && /^Similarity .* below verification threshold$/.test(result.warning)) {
        return verdict({ decision: 'NO_MATCH' });
      }
      return { label: result.verified === false ? 'Verification failed' : 'Outcome unavailable',
        tone: result.verified === false ? 'warning' : 'neutral', icon: 'alert' };
  }
}

export function serviceError(error, fallback) {
  const status = error.response?.status;
  const data = error.response?.data;
  if (!status || status >= 500) return { title: 'Service unavailable', detail: 'Try again when the inspection service is available.' };
  if (status === 413) return { title: fallback, detail: 'Use a JPEG or PNG image up to 10 MB.' };
  if (status === 403) return { title: 'Access denied', detail: 'Your current role does not allow this action.' };
  return { title: fallback, detail: data?.message || data?.error || 'Please try again.' };
}

export function ledgerLabel(status) {
  return { CONFIRMED: 'Confirmed', PENDING: 'Pending', FAILED: 'Delivery failed', SENT: 'Sent' }[status];
}
