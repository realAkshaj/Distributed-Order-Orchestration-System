import type { SagaStatus } from './types';

export const PRODUCTS = [
  { id: '11111111-1111-1111-1111-111111111111', name: 'Laptop', price: 999 },
  { id: '22222222-2222-2222-2222-222222222222', name: 'Headphones', price: 79 },
  { id: '33333333-3333-3333-3333-333333333333', name: 'Limited Edition Console (20 in stock)', price: 499 },
];

export const productName = (id: string) =>
  PRODUCTS.find((p) => p.id === id)?.name.split(' (')[0] ?? id.slice(0, 8);

export const parseServerTime = (value: string) => new Date(value.endsWith('Z') ? value : `${value}Z`);

export const duration = (from: string, to: string) =>
  `${Math.max(0, parseServerTime(to).getTime() - parseServerTime(from).getTime())} ms`;

export const clock = (value: string) => {
  const d = parseServerTime(value);
  return `${d.toLocaleTimeString([], { hour12: false })}.${String(d.getMilliseconds()).padStart(3, '0')}`;
};

export const money = (value: number) =>
  value.toLocaleString(undefined, { style: 'currency', currency: 'USD' });

export const randomUuid = () => crypto.randomUUID();

export const IN_FLIGHT: SagaStatus[] = [
  'STARTED',
  'ORDER_PENDING',
  'PAYMENT_PENDING',
  'INVENTORY_PENDING',
  'NOTIFICATION_PENDING',
  'COMPENSATING',
];

export const tone = (status: SagaStatus): 'ok' | 'warn' | 'bad' | 'busy' => {
  if (status === 'COMPLETED') return 'ok';
  if (status === 'COMPENSATED') return 'warn';
  if (status === 'FAILED') return 'bad';
  return 'busy';
};
