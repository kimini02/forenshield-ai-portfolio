import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const latency = new Trend('baseline_latency', true);
const failures = new Rate('baseline_failure');

export const options = {
  scenarios: {
    measured: {
      executor: 'shared-iterations',
      vus: Number(__ENV.VUS || 1),
      iterations: Number(__ENV.ITERATIONS || 15),
      maxDuration: __ENV.MAX_DURATION || '10m',
    },
  },
  summaryTrendStats: ['min', 'med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const response = http.get(`${__ENV.BASE_URL}${__ENV.PATH}`, {
    headers: { Authorization: `Bearer ${__ENV.TOKEN}` },
    timeout: __ENV.TIMEOUT || '180s',
  });
  const success = check(response, { 'status is 200': (result) => result.status === 200 });
  latency.add(response.timings.duration);
  failures.add(!success);
}

export function handleSummary(data) {
  return { [__ENV.SUMMARY_FILE || 'summary.json']: JSON.stringify(data, null, 2) };
}
