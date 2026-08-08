#!/bin/sh
set -eu

repo_root=$(git rev-parse --show-toplevel)
hook_src="$repo_root/scripts/git/pre-commit-block-secrets"
hook_dir=$(git rev-parse --git-path hooks)
hook_dst="$hook_dir/pre-commit"

mkdir -p "$hook_dir"
cp "$hook_src" "$hook_dst"
chmod +x "$hook_dst"

echo "Installed pre-commit hook:"
echo "  $hook_dst"
