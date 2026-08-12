#!/usr/bin/env bash
set -euo pipefail

run_label="${C4_RUN_LABEL:?C4_RUN_LABEL is required}"
users="${C4_USERS:?C4_USERS is required}"
fixture_password="${C4_FIXTURE_PASSWORD:?C4_FIXTURE_PASSWORD is required}"
base_url="${C4_BASE_URL:-http://127.0.0.1:18080}"
db_container="${C4_DB_CONTAINER:-forenshield-c4-gatling-db}"
db_name="${C4_DB_NAME:-forenshield_c4_gatling}"
db_user="${C4_DB_USER:-forenshield}"

upper_run_label="$(printf '%s' "${run_label}" | tr '[:lower:]' '[:upper:]')"
case_key="C4-GATLING-${upper_run_label}"
investigator_login="c4-investigator-${run_label}"
reviewer_login="c4-reviewer-${run_label}"
password_hash="$(/usr/sbin/htpasswd -bnBC 10 '' "${fixture_password}" | tr -d ':\n')"

docker exec -i "${db_container}" psql -X -q -U "${db_user}" -d "${db_name}" \
  -v investigator_login="${investigator_login}" \
  -v investigator_email="${investigator_login}@example.invalid" \
  -v reviewer_login="${reviewer_login}" \
  -v reviewer_email="${reviewer_login}@example.invalid" \
  -v password_hash="${password_hash}" \
  -v case_key="${case_key}" \
  -v evidence_file="${run_label}.mp4" \
  -v evidence_hash='1111111111111111111111111111111111111111111111111111111111111111' \
  -v evidence_path="/isolated/c4/${run_label}.mp4" \
  < docs/performance/c4/scripts/seed-gatling-fixture.sql

analysis_result_id="$(docker exec "${db_container}" psql -X -Atq -U "${db_user}" -d "${db_name}" -c \
  "SELECT ar.analysis_result_id FROM analysis_results ar JOIN analysis_requests req ON req.analysis_request_id = ar.analysis_request_id JOIN evidences e ON e.evidence_id = req.evidence_id WHERE e.case_number = '${case_key}';")"

login_response="$(curl -sS -m 10 -H 'Content-Type: application/json' \
  -d "{\"loginId\":\"${reviewer_login}\",\"password\":\"${fixture_password}\"}" \
  "${base_url}/api/auth/login")"
auth_token="$(printf '%s' "${login_response}" | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')"
test -n "${auth_token}"
test "${auth_token}" != "${login_response}"

C4_BASE_URL="${base_url}" \
C4_AUTH_TOKEN="${auth_token}" \
C4_CASE_KEY="${case_key}" \
C4_USERS="${users}" \
CI=true \
  sh gradlew gatlingRun --simulation performance.c4.ConcurrentApprovalSimulation \
    --non-interactive --console=plain

docker exec "${db_container}" psql -X -Atq -U "${db_user}" -d "${db_name}" -c \
  "SELECT '${run_label}', ${users}, '${analysis_result_id}', COUNT(*), MIN(status) FROM report_issue_tasks WHERE analysis_result_id = ${analysis_result_id};"
docker exec "${db_container}" psql -X -Atq -U "${db_user}" -d "${db_name}" -c \
  "SELECT COUNT(*) FROM (SELECT analysis_result_id FROM report_issue_tasks GROUP BY analysis_result_id HAVING COUNT(*) > 1) duplicates;"
