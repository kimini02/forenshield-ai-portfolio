import http from 'k6/http';
import { check } from 'k6';

const tokens = (__ENV.ACCESS_TOKENS || '').split(',').filter(Boolean);

export const options = {
  scenarios: {
    distinct_key_cold_miss: {
      executor: 'per-vu-iterations',
      vus: tokens.length,
      iterations: 1,
      maxDuration: '30s',
    },
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'min', 'max'],
};

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:18080';

export default function () {
  const response = http.get(`${baseUrl}/api/v1/evidences/stats`, {
    headers: { Authorization: `Bearer ${tokens[__VU - 1]}` },
    tags: { scenario: 'distinct-key-cold-miss' },
  });

  check(response, {
    'status is 200': (res) => res.status === 200,
    'body has expected totals': (res) => {
      const body = res.json();
      return body.totalAnalysisCount === 3000
        && body.deepfakeDetectedCount === 167
        && body.completedCount === 250
        && body.inProgressCount === 500;
    },
  });
}
