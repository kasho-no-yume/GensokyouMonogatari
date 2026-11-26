#!/usr/bin/env bash
# Use an existing CLI, or the user-local Node/OpenSpec installation for this workspace.
set -euo pipefail

if command -v openspec >/dev/null 2>&1 && command -v node >/dev/null 2>&1; then
  exec openspec "$@"
fi

tool_root="${XDG_DATA_HOME:-$HOME/.local/share}/gensokyou-tools"
cli_path="$tool_root/openspec/node_modules/@fission-ai/openspec/bin/openspec.js"
node_paths=("$tool_root"/node-*-linux-*/bin/node)
for node_path in "${node_paths[@]}"; do
  if [[ -x "$node_path" && -f "$cli_path" ]]; then
    exec "$node_path" "$cli_path" "$@"
  fi
done

echo "OpenSpec is not installed. Install Node >=22.20 and @fission-ai/openspec." >&2
exit 127
