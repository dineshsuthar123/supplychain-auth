import { verdict } from './inspection';

it.each([
  [{ decision: 'MATCH', verified: true, blockchainConfirmed: false }, 'Fingerprint match', 'success'],
  [{ decision: 'NO_MATCH', verified: false, confidence: 0.61 }, 'Fingerprint mismatch', 'warning'],
  [{ decision: 'LEDGER_MISMATCH', verified: false, confidence: 0.99 }, 'Ledger hash mismatch', 'warning'],
  [{ decision: 'UNKNOWN_PRODUCT', verified: false, confidence: null }, 'Unknown product', 'neutral'],
  [{ verified: false, confidence: null }, 'Verification failed', 'warning'],
  [{ verified: false, warning: 'No fingerprint on record for this product' }, 'Unknown product', 'neutral'],
  [{ verified: true, blockchainConfirmed: false }, 'Fingerprint match', 'success'],
  [{}, 'Outcome unavailable', 'neutral']
])('maps %j using the recorded decision', (result, label, tone) => {
  expect(verdict(result)).toMatchObject({ label, tone });
});
