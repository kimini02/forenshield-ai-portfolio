$ErrorActionPreference = "Stop"

$repoRoot = git rev-parse --show-toplevel
if (-not $repoRoot) {
    throw "Run this script inside the git repository."
}

$hookSrc = Join-Path $repoRoot "scripts/git/pre-commit-block-secrets"
$gitDir = git rev-parse --git-path hooks
$hookDir = if ([System.IO.Path]::IsPathRooted($gitDir)) { $gitDir } else { Join-Path $repoRoot $gitDir }
$hookDst = Join-Path $hookDir "pre-commit"

New-Item -ItemType Directory -Force -Path $hookDir | Out-Null
Copy-Item -Path $hookSrc -Destination $hookDst -Force

Write-Host "Installed pre-commit hook:"
Write-Host "  $hookDst"
Write-Host ""
Write-Host "Git Bash commits will run the hook automatically."
Write-Host "If commit is blocked, remove secret files from staging before retrying."
