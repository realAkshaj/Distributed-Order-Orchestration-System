import http from 'k6/http';
import { check } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const RATE = Number(__ENV.RATE || 200);
const PRODUCTS = ['11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222'];

export const options = {
  scenarios: {
    orders: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: __ENV.DURATION || '1m',
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<320'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const res = http.post(
    `${BASE_URL}/orders`,
    JSON.stringify({
      customerId: uuidv4(),
      productId: PRODUCTS[Math.floor(Math.random() * PRODUCTS.length)],
      quantity: 1 + Math.floor(Math.random() * 3),
      totalAmount: (Math.random() * 200 + 10).toFixed(2),
    }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  check(res, { accepted: (r) => r.status === 202 });
}
