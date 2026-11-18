export type SagaStatus =
  | 'STARTED'
  | 'ORDER_PENDING'
  | 'PAYMENT_PENDING'
  | 'INVENTORY_PENDING'
  | 'NOTIFICATION_PENDING'
  | 'COMPLETED'
  | 'COMPENSATING'
  | 'COMPENSATED'
  | 'FAILED';

export interface Saga {
  id: string;
  orderId: string | null;
  status: SagaStatus;
  currentStep: string | null;
  payload: {
    customerId: string;
    productId: string;
    quantity: number;
    totalAmount: number;
    forceFailure: boolean;
  };
  failureReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SagaEvent {
  id: string;
  eventType: string;
  success: boolean;
  detail: string | null;
  occurredAt: string;
}

export interface Stats {
  counts: Record<SagaStatus, number>;
  avgCompletionMillis: number;
}

export interface OrderInput {
  customerId: string;
  productId: string;
  quantity: number;
  totalAmount: number;
}
