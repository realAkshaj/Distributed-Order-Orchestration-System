import http from 'k6/http';
import { check } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  vus: 20,
  duration: '1m',
  thresholds: { http_req_failed: ['rate<0.01'] },
};

export default function () {
  const forceFailure = Math.random() < 0.3;
  const res = http.post(
    `${BASE_URL}/orders`,
    JSON.stringify({
      customerId: uuidv4(),
      productId: '11111111-1111-1111-1111-111111111111',
      quantity: 1,
      totalAmount: '50.00',
    }),
    { headers: { 'Content-Type': 'application/json', 'X-Force-Payment-Failure': String(forceFailure) } },
  );
  check(res, { accepted: (r) => r.status === 202 });
}
