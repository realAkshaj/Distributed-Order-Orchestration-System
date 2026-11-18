import type { Saga } from '../types';
import { clock, duration, money, productName } from '../format';
import { StatusBadge } from './StatusBadge';

interface Props {
  sagas: Saga[];
  selectedId: string | null;
  onSelect: (id: string) => void;
}

export function SagaTable({ sagas, selectedId, onSelect }: Props) {
  return (
    <div className="card table-card">
      <h2>Sagas</h2>
      <table>
        <thead>
          <tr>
            <th>Saga</th>
            <th>Status</th>
            <th>Order</th>
            <th>Time</th>
            <th>Took</th>
            <th>Reason</th>
          </tr>
        </thead>
        <tbody>
          {sagas.map((saga) => (
            <tr key={saga.id} className={saga.id === selectedId ? 'selected' : ''} onClick={() => onSelect(saga.id)}>
              <td className="mono">{saga.id.slice(0, 8)}</td>
              <td><StatusBadge status={saga.status} /></td>
              <td>{saga.payload.quantity} × {productName(saga.payload.productId)} · {money(saga.payload.totalAmount)}</td>
              <td className="mono">{clock(saga.createdAt)}</td>
              <td className="mono">{duration(saga.createdAt, saga.updatedAt)}</td>
              <td className="muted">{saga.failureReason ?? ''}</td>
            </tr>
          ))}
          {sagas.length === 0 && (
            <tr>
              <td colSpan={6} className="muted empty">No sagas yet — place an order.</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
