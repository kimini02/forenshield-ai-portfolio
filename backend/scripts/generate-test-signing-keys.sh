#!/bin/sh
set -eu

dir="src/test/resources/crypto"
key="$dir/platform-signing-key.pem"
cert="$dir/platform-signing-cert.pem"

mkdir -p "$dir"

if [ -f "$key" ] && [ -f "$cert" ]; then
  echo "Test signing keys already exist in $dir"
  exit 0
fi

openssl genpkey -algorithm RSA -out "$key"
openssl req -new -x509 -key "$key" -out "$cert" -days 3650 \
  -subj "/CN=ForenShield Forensics CA" \
  -config "$(git rev-parse --show-toplevel)/scripts/openssl-test.cnf"

echo "Generated local-only test signing keys in $dir (gitignored)"
