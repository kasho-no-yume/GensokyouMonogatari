#!/usr/bin/env bash
# Linux/WSL/container counterpart of gradle_task.ps1.
# Keep this file ASCII-only.
set -uo pipefail

usage() {
  cat <<'EOF'
Usage: tools/gradle_task.sh [options] <task> [tasks/args...]

Options:
  --timeout <sec>   Maximum wait time (default: 1800)
  --no-wait         Start detached and print the log path
  --filter <regex>  Filter displayed log lines only
  --help            Show this help

Examples:
  bash tools/gradle_task.sh compileJava
  bash tools/gradle_task.sh --timeout 1200 compileJava --no-daemon --max-workers=1
  bash tools/gradle_task.sh runServer --no-wait
EOF
}

timeout_sec=1800
no_wait=0
filter_regex=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --help|-h)
      usage
      exit 0
      ;;
    --timeout)
      if [[ $# -lt 2 ]]; then
        echo "[gradle_task] --timeout requires a value" >&2
        exit 2
      fi
      timeout_sec="$2"
      shift 2
      ;;
    --no-wait)
      no_wait=1
      shift
      ;;
    --filter)
      if [[ $# -lt 2 ]]; then
        echo "[gradle_task] --filter requires a value" >&2
        exit 2
      fi
      filter_regex="$2"
      shift 2
      ;;
    --)
      shift
      break
      ;;
    -*)
      echo "[gradle_task] unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
    *)
      break
      ;;
  esac
done

if [[ $# -eq 0 ]]; then
  usage >&2
  exit 2
fi
if ! [[ "$timeout_sec" =~ ^[0-9]+$ ]]; then
  echo "[gradle_task] timeout must be an integer" >&2
  exit 2
fi

args=("$@")
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
gradlew="$project_dir/gradlew"
if [[ ! -x "$gradlew" ]]; then
  echo "[gradle_task] executable gradlew not found at $gradlew" >&2
  exit 1
fi

log_dir="$project_dir/build/agent-logs"
mkdir -p "$log_dir"
stamp="$(date +%Y%m%d-%H%M%S)"
safe_task="$(printf '%s' "${args[*]}" | tr -c '[:alnum:]._-' '_' | cut -c1-60)"
log_file="$log_dir/$stamp-$safe_task.log"
exit_file="$log_file.exit"
rm -f "$exit_file"

cmd=("$gradlew")
if [[ -n "${JAVA_HOME:-}" ]]; then
  cmd+=("-Dorg.gradle.java.home=$JAVA_HOME")
fi
cmd+=("${args[@]}" "--console=plain")

echo "[gradle_task] launching: gradlew ${args[*]}"
echo "[gradle_task] log: $log_file"

EXIT_FILE="$exit_file" nohup bash -c '
  set +e
  cd "$1" || exit 125
  shift
  "$@"
  code=$?
  printf "%s\n" "$code" > "$EXIT_FILE"
  exit "$code"
' _ "$project_dir" "${cmd[@]}" >"$log_file" 2>&1 &
pid=$!

if [[ "$no_wait" -eq 1 ]]; then
  echo "[gradle_task] detached pid: $pid"
  echo "[gradle_task] follow with: tail -f '$log_file'"
  exit 0
fi

show_lines() {
  local first="$1"
  local last="$2"
  if [[ "$last" -lt "$first" ]]; then
    return
  fi
  if [[ -n "$filter_regex" ]]; then
    sed -n "${first},${last}p" "$log_file" | grep -E -- "$filter_regex" || true
  else
    sed -n "${first},${last}p" "$log_file"
  fi
}

start_sec="$SECONDS"
last_line=0
timed_out=0
exit_code=""

while true; do
  if [[ -f "$exit_file" ]]; then
    sleep 0.2
    exit_code="$(tr -d '[:space:]' < "$exit_file")"
    break
  fi

  if [[ -f "$log_file" ]]; then
    total_lines="$(wc -l < "$log_file" | tr -d '[:space:]')"
    if [[ "$total_lines" =~ ^[0-9]+$ ]] && [[ "$total_lines" -gt "$last_line" ]]; then
      show_lines "$((last_line + 1))" "$total_lines"
      last_line="$total_lines"
    fi
  elif [[ -n "$filter_regex" ]]; then
    elapsed=$((SECONDS - start_sec))
    if [[ $((elapsed % 10)) -eq 0 ]]; then
      echo "[gradle_task] ...still running (${elapsed}s)"
    fi
  fi

  elapsed=$((SECONDS - start_sec))
  if [[ "$elapsed" -gt "$timeout_sec" ]]; then
    timed_out=1
    break
  fi
  sleep 1
done

if [[ -f "$log_file" ]]; then
  total_lines="$(wc -l < "$log_file" | tr -d '[:space:]')"
  if [[ "$total_lines" =~ ^[0-9]+$ ]] && [[ "$total_lines" -gt "$last_line" ]]; then
    show_lines "$((last_line + 1))" "$total_lines"
  fi
fi

elapsed=$((SECONDS - start_sec))
echo ""

if [[ "$timed_out" -eq 1 ]]; then
  echo "[gradle_task] TIMEOUT after ${timeout_sec}s. Build left running." >&2
  echo "[gradle_task] log: $log_file" >&2
  exit 124
fi

if ! [[ "$exit_code" =~ ^[0-9]+$ ]]; then
  echo "[gradle_task] missing or invalid exit code" >&2
  echo "[gradle_task] log: $log_file" >&2
  exit 1
fi

if [[ "$exit_code" -eq 0 ]]; then
  echo "[gradle_task] OK in ${elapsed}s"
else
  echo "[gradle_task] FAILED (exit $exit_code) after ${elapsed}s"
  echo "[gradle_task] log: $log_file"
fi
exit "$exit_code"
