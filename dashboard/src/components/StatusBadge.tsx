import type { SagaStatus } from '../types';
import { tone } from '../format';

export function StatusBadge({ status }: { status: SagaStatus }) {
  return <span className={`badge ${tone(status)}`}>{status.replace('_', ' ')}</span>;
}
