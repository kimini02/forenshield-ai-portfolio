import http from 'k6/http';
import { check } from 'k6';

const vus = Number(__ENV.VUS || 2);

export const options = {
  scenarios: {
    distributed_cold_miss: {
      executor: 'per-vu-iterations',
      vus,
      iterations: 1,
      maxDuration: '30s',
    },
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'min', 'max'],
};

const backends = [
  __ENV.BASE_URL_A || 'http://127.0.0.1:18080',
  __ENV.BASE_URL_B || 'http://127.0.0.1:18081',
];
const token = __ENV.ACCESS_TOKEN;

export default function () {
  const baseUrl = backends[(__VU - 1) % backends.length];
  const response = http.get(`${baseUrl}/api/v1/evidences/stats`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { scenario: 'two-jvm-distributed-cold-miss' },
  });

  check(response, {
    'status is 200': (res) => res.status === 200,
    'body has expected totals': (res) => {
      const body = res.json();
      return body.totalAnalysisCount === 90000
        && body.deepfakeDetectedCount === 5000
        && body.completedCount === 7500
        && body.inProgressCount === 15000;
    },
  });
}
