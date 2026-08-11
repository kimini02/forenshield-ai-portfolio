import http from 'k6/http';
import { check } from 'k6';

export const options = {
  vus: 1,
  iterations: 100,
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'min', 'max'],
};

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:18080';
const token = __ENV.ACCESS_TOKEN;

export default function () {
  const response = http.get(`${baseUrl}/api/v1/evidences/stats`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { scenario: 'warm-cache' },
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
