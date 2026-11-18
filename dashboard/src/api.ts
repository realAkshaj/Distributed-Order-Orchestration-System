import type { OrderInput, Saga, SagaEvent, Stats } from './types';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`/api${path}`, init);
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
  return res.json() as Promise<T>;
}

export const api = {
  stats: () => request<Stats>('/sagas/stats'),
  sagas: () => request<Saga[]>('/sagas?limit=100'),
  events: (id: string) => request<SagaEvent[]>(`/sagas/${id}/events`),
  placeOrder: (order: OrderInput, forcePaymentFailure: boolean) =>
    request<{ sagaId: string }>('/orders', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Force-Payment-Failure': String(forcePaymentFailure),
      },
      body: JSON.stringify(order),
    }),
};
