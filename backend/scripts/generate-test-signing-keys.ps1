$ErrorActionPreference = "Stop"

$dir = Join-Path (git rev-parse --show-toplevel) "src/test/resources/crypto"
$key = Join-Path $dir "platform-signing-key.pem"
$cert = Join-Path $dir "platform-signing-cert.pem"

New-Item -ItemType Directory -Force -Path $dir | Out-Null

if ((Test-Path $key) -and (Test-Path $cert)) {
    Write-Host "Test signing keys already exist in $dir"
    exit 0
}

& openssl genpkey -algorithm RSA -out $key
$opensslConfig = Join-Path (git rev-parse --show-toplevel) "scripts/openssl-test.cnf"
$env:OPENSSL_CONF = $opensslConfig
& openssl req -new -x509 -key $key -out $cert -days 3650 -subj "/CN=ForenShield Forensics CA" -config $opensslConfig

Write-Host "Generated local-only test signing keys in $dir (gitignored)"
