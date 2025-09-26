#!/bin/bash
#
#!/bin/bash
#
# simple_operator_launcher.bash
#
# Launch or kill N components of either cpu_emu or gpu_emu with contiguous port pairs.
#
#   --components C     Number of instances to run (required with launch)
#   --mode M           0 => cpu_emu, 1 => gpu_emu (required with launch)
#   --end-port X       Highest port in the chain (required with launch)
#   --sub-ip IP        Passed to the binary as -i IP (default: 127.0.0.1)
#   --dry-run          Show what would be launched, but don’t run
#   --kill             Kill all cpu_emu/gpu_emu processes and exit
#   --help             Show usage
#
set -euo pipefail

usage() {
  cat <<EOF
Usage: $(basename "$0") [--kill] OR --components C --mode {0|1} --end-port X [--sub-ip IP] [--dry-run]

Required for launch:
  --components C    number of instances (>0)
  --mode M          0 for cpu_emu, 1 for gpu_emu
  --end-port X      highest port; instances use pairs (-p, -r) from X-C..X

Optional:
  --sub-ip IP       value for -i (default: 127.0.0.1)
  --dry-run         print commands instead of running them

Other:
  --kill            kill all cpu_emu and gpu_emu processes
  --help            show this help
EOF
}

# -------- arg parsing (long options) --------
COMPONENTS=""
MODE=""
END_PORT=""
SUB_IP="127.0.0.1"
DRY_RUN=0
DO_KILL=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --components) COMPONENTS="${2:-}"; shift 2 ;;
    --mode)       MODE="${2:-}"; shift 2 ;;
    --end-port)   END_PORT="${2:-}"; shift 2 ;;
    --sub-ip)     SUB_IP="${2:-}"; shift 2 ;;
    --dry-run)    DRY_RUN=1; shift ;;
    --kill)       DO_KILL=1; shift ;;
    --help|-h)    usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 1 ;;
  esac
done

# -------- kill mode --------
if [[ "$DO_KILL" -eq 1 ]]; then
  echo "Killing all cpu_emu and gpu_emu processes..."
  pkill -9 -x cpu_emu 2>/dev/null || true
  pkill -9 -x gpu_emu 2>/dev/null || true
  echo "Done."
  exit 0
fi


# -------- validation --------
re_num='^[0-9]+$'
if [[ -z "$COMPONENTS" || ! "$COMPONENTS" =~ $re_num || "$COMPONENTS" -lt 1 ]]; then
  echo "Error: --components must be a positive integer." >&2; usage; exit 1
fi
if [[ -z "$MODE" || ! "$MODE" =~ ^[01]$ ]]; then
  echo "Error: --mode must be 0 (cpu_emu) or 1 (gpu_emu)." >&2; usage; exit 1
fi
if [[ -z "$END_PORT" || ! "$END_PORT" =~ $re_num || "$END_PORT" -lt 2 ]]; then
  echo "Error: --end-port must be an integer >= 2." >&2; usage; exit 1
fi

# Compute starting pub port (p0) = X - C
START_P=$(( END_PORT - COMPONENTS ))
if [[ "$START_P" -lt 1 ]]; then
  echo "Error: computed start port ($START_P) < 1. Choose a larger --end-port." >&2
  exit 1
fi

# Choose binary
if [[ "$MODE" -eq 0 ]]; then
  BIN="./cpu_emu"
else
  BIN="./gpu_emu"
fi

# Ensure binary exists or is in PATH
if ! command -v "$BIN" >/dev/null 2>&1; then
  echo "Error: '$BIN' not found in PATH." >&2
  exit 1
fi

# Prepare logs directory
LOG_DIR="${LOG_DIR:-logs}"
mkdir -p "$LOG_DIR"

echo "Launcher summary:"
echo "  binary     : $BIN"
echo "  components : $COMPONENTS"
echo "  end-port   : $END_PORT"
echo "  sub-ip     : $SUB_IP"
echo "  ports      : p from $START_P..$((END_PORT-1)), r = p+1"
echo

# -------- launch loop --------
p="$START_P"
pids=()

for (( k=0; k<COMPONENTS; k++ )); do
  r=$(( p + 1 ))
  log="$LOG_DIR/${BIN}_k${k}_p${p}_r${r}_$(hostname -s).log"

  if [[ $k -eq 0 ]]; then
    cmd=( "$BIN" -p "$p" -r "$r" -i "$SUB_IP" )
  else
    cmd=( "$BIN" -p "$p" -r "$r" -i "127.0.0.1")
  fi
  # Add -f 1000000 to prevent cpu_emu exit after 100 events
  # -o 0.001 for 1 MB output
  if [[ "$MODE" -eq 0 ]]; then
    cmd+=( -f 1000000 -o 0.001 )
  fi
  printf "[%d/%d] %s\n" "$((k+1))" "$COMPONENTS" "${cmd[*]}  # log -> $log"

  if [[ "$DRY_RUN" -eq 0 ]]; then
    # Run detached, append logs, record PID
    nohup "${cmd[@]}" >>"$log" 2>&1 &
    pid=$!
    pids+=( "$pid" )
  fi

  p=$(( p + 1 ))
done

if [[ "$DRY_RUN" -eq 0 ]]; then
  echo
  echo "Launched ${#pids[@]} process(es). PIDs:"
  printf '  %s\n' "${pids[@]}" | tee "$LOG_DIR/${BIN}_pids.txt"
  echo "Logs in: $LOG_DIR/"
else
  echo
  echo "(dry-run) Nothing was executed."
fi
