import { api } from '../api';
import type { Saga, SagaEvent } from '../types';
import { clock, money, productName } from '../format';
import { usePolling } from '../usePolling';
import { StatusBadge } from './StatusBadge';

type StepState = 'done' | 'failed' | 'pending' | 'idle';

const STEPS = [
  { label: 'Order', ok: 'ORDER_CREATED', fail: 'ORDER_CREATION_FAILED', command: 'ORDER_CREATE' },
  { label: 'Payment', ok: 'PAYMENT_CHARGED', fail: 'PAYMENT_CHARGE_FAILED', command: 'PAYMENT_CHARGE' },
  { label: 'Inventory', ok: 'INVENTORY_RESERVED', fail: 'INVENTORY_RESERVATION_FAILED', command: 'INVENTORY_RESERVE' },
  { label: 'Notification', ok: 'NOTIFICATION_SENT', fail: 'NOTIFICATION_SENT', command: 'NOTIFICATION_SEND' },
];

const COMPENSATIONS = [
  { label: 'Refund payment', ok: 'PAYMENT_REFUNDED', command: 'PAYMENT_REFUND' },
  { label: 'Cancel order', ok: 'ORDER_CANCELLED', command: 'ORDER_CANCEL' },
];

function stepState(events: SagaEvent[], saga: Saga, okType: string, failType: string, command: string): StepState {
  const reply = events.find((e) => e.eventType === okType || e.eventType === failType);
  if (reply) return reply.success ? 'done' : 'failed';
  return saga.currentStep === command ? 'pending' : 'idle';
}

export function SagaDetail({ saga }: { saga: Saga }) {
  const { data: events } = usePolling(() => api.events(saga.id), 1500, saga.id);
  const log = events ?? [];
  const compensating = log.some((e) => !e.success && e.eventType !== 'NOTIFICATION_SENT');
  const visibleCompensations = COMPENSATIONS.filter(
    (c) => log.some((e) => e.eventType === c.ok) || saga.currentStep === c.command,
  );

  return (
    <div className="card detail">
      <div className="detail-head">
        <h2>Saga <span className="mono">{saga.id.slice(0, 8)}</span></h2>
        <StatusBadge status={saga.status} />
      </div>
      <p className="muted">
        {saga.payload.quantity} × {productName(saga.payload.productId)} · {money(saga.payload.totalAmount)}
        {saga.payload.forceFailure && ' · forced payment failure'}
      </p>

      <div className="pipeline">
        {STEPS.map((step) => {
          const state = stepState(log, saga, step.ok, step.fail, step.command);
          return (
            <div key={step.label} className={`step ${state}`}>
              <span className="dot" />
              {step.label}
            </div>
          );
        })}
      </div>

      {(compensating || visibleCompensations.length > 0) && (
        <div className="pipeline compensation">
          {visibleCompensations.map((step) => {
            const state: StepState = log.some((e) => e.eventType === step.ok) ? 'done' : 'pending';
            return (
              <div key={step.label} className={`step ${state}`}>
                <span className="dot" />
                {step.label}
              </div>
            );
          })}
        </div>
      )}

      {saga.failureReason && <p className="error">{saga.failureReason}</p>}

      <h3>Timeline</h3>
      <ol className="timeline">
        {log.map((event) => (
          <li key={event.id} className={event.success ? 'ok' : 'bad'}>
            <span className="mono">{clock(event.occurredAt)}</span>
            <strong>{event.eventType}</strong>
            {event.detail && <span className="muted">{event.detail}</span>}
          </li>
        ))}
      </ol>
    </div>
  );
}
