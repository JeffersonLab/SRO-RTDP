#!/usr/bin/env python3
"""
Parse RTDP-style logs and plot.

X-axis is the shift in seconds from a given base timestamp.

Supports two formats:

Format Operator:
  [2025-09-26 02:35:21.943 UTC]  Incoming: [xxx Gbps], total [xxx MB] ; Outgoing: [xxx Gbps], total [xxx MB]

Format Sink:
  [2025-09-26 02:35:22.445 UTC]  Incoming: [xxx Gbps] (5.402 MB/s)  total=xxx MB

Usage:
   python3 plot.py --files \
    gpu_emu_k0_p55554_r55555_nvidarm.txt gpu_emu_k1_p55555_r55556_nvidarm.txt sink_ejfat-6.txt \
    --labels gpu_emu-0 gpu_emu-1 sink \
    --base-utc "2025-09-26 02:35:21 UTC" \
    --range 55 63 \
    --outdir output/
"""
import argparse
import re
from datetime import datetime, timezone
from pathlib import Path

import matplotlib.pyplot as plt
import matplotlib.ticker as mticker


FORMAT_OPERATOR = re.compile(
    r"^\[(?P<ts>[\d\-:.\s]+ UTC)\]\s*"
    r"Incoming:\s*\[(?P<in_gbps>[\d.]+)\s+Gbps\],\s*total\s*\[(?P<in_total>[\d.]+)\s+MB\]\s*;\s*"
    r"Outgoing:\s*\[(?P<out_gbps>[\d.]+)\s+Gbps\],\s*total\s*\[(?P<out_total>[\d.]+)\s+MB\]\s*$"
)

FORMAT_SINK = re.compile(
    r"^\[(?P<ts>[\d\-:.\s]+ UTC)\]\s*"
    r"Incoming:\s*\[(?P<in_gbps>[\d.]+)\s+Gbps\]\s*"
    r"\((?P<in_mbps>[\d.]+)\s+MB/s\)\s*"
    r"total=(?P<in_total>[\d.]+)\s+MB\s*$"
)


def parse_ts(ts_str: str) -> float:
    """Parse '2025-09-26 02:35:21.943 UTC' into seconds since epoch."""
    ts_str = ts_str.strip()
    assert ts_str.endswith("UTC")
    base = ts_str[:-3].strip()  # drop 'UTC'
    # Try with milliseconds or without
    for fmt in ("%Y-%m-%d %H:%M:%S.%f", "%Y-%m-%d %H:%M:%S"):
        try:
            dt = datetime.strptime(base, fmt).replace(tzinfo=timezone.utc)
            # Convert to milliseconds since epoch
            return dt.timestamp()
        except ValueError:
            continue
    raise ValueError(f"Unrecognized timestamp format: {ts_str}")


def parse_file(path: Path):
    """
    Returns dict with keys:
      'time'      -> list[datetime] (UTC-aware)
      'out_gbps'  -> list[float]
      'out_total' -> list[float] (MB)
      'in_gbps'   -> list[float]
      'in_total'  -> list[float] (MB)
    """
    times, in_gbps, out_gbps, in_total, out_total = [], [], [], [], []
    with path.open("r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            if line.startswith("SUB ") or line.startswith("PUB "):
                continue
            if "Waiting for data" in line:
                continue

            m = FORMAT_OPERATOR.match(line)
            if m:
                ts = parse_ts(m.group("ts"))
                times.append(ts)
                in_gbps.append(float(m.group("in_gbps")))
                out_gbps.append(float(m.group("out_gbps")))
                in_total.append(float(m.group("in_total")))
                out_total.append(float(m.group("out_total")))
                continue
            m = FORMAT_SINK.match(line)
            if m:
                ts = parse_ts(m.group("ts"))
                times.append(ts)
                in_gbps.append(float(m.group("in_gbps")))
                in_total.append(float(m.group("in_total")))
                continue
            # Unrecognized line; skip silently
    return {"time": times, "in_gbps": in_gbps, "out_gbps": out_gbps,
            "in_total": in_total, "out_total": out_total}


def main():
    ap = argparse.ArgumentParser(description="Parse RTDP logs and plot rates")
    ap.add_argument("--files", required=True, nargs="+", type=Path,
                    help="One or more log files")
    ap.add_argument("--labels", nargs="*", default=None,
                    help="Optional labels (same length/order as --files). Defaults to file stems.")
    ap.add_argument("--outdir", default=".", help="Directory to write PNG figures.")
    ap.add_argument("--range", nargs=2, type=int, metavar=("START", "END"),
                    help="Range of data points to plot [START END].")
    ap.add_argument("--base-utc", type=str, required=True,
                    help="Base timestamp (e.g. '2025-09-26 02:35:21 UTC') for x-axis alignment")
    args = ap.parse_args()

    base_ts = None
    if args.base_utc:
        base_ts = parse_ts(args.base_utc)
        print(f"Using base time {args.base_utc} → {base_ts} seconds since epoch")

    outdir = Path(args.outdir)
    outdir.mkdir(parents=True, exist_ok=True)

    if args.labels and len(args.labels) != len(args.files):
        raise SystemExit("--labels must match the number of --files")

    labels = args.labels or [p.stem for p in args.files]

    fig, ax = plt.subplots(figsize=(12, 4))

    for path, label in zip(args.files, labels):
        res = parse_file(path)
        if not res["time"]:
            print(f"No valid data found in {path}; skipping.")
            continue

        n_data = len(res["time"])
        print(f'Parsed {n_data} data points from {path}')

        if res["time"][0] < base_ts:
            raise ValueError(f"Wrong base time {args.base_time} for data in {path}")
        # Align times to base
        res["time"] = [t - base_ts for t in res["time"]]

        if args.range:
            start, end = args.range
            end = min(end, n_data)  # clamp to available data
            if start < 0 or start >= end:
                raise SystemExit(f"Invalid --range {args.range} for {n_data} data points in {path}")
            times = res["time"][start:end]
            in_gbps = res["in_gbps"][start:end]
            out_gbps = res["out_gbps"][start:end] if res["out_gbps"] else []
        else:
            times = res["time"]
            in_gbps = res["in_gbps"]
            out_gbps = res["out_gbps"]

        ax.plot(times, in_gbps,
                label=f"{label} In",
                linewidth=1.6,
                marker=".",
                markersize=3)
        if out_gbps:
            ax.plot(times, out_gbps,
                    label=f"{label} Out",
                    linewidth=1.6,
                    linestyle="--",
                    marker="*",
                    markersize=4)

    ax.set_title("Data Rate (Gbps)")
    ax.set_xlabel("Timestamp (seconds since base)")
    ax.set_ylabel("Throughput (Gbps)")
    ax.legend()
    ax.grid(True)

    ax.xaxis.set_major_formatter(mticker.ScalarFormatter())
    ax.ticklabel_format(style="plain", axis="x")

    if args.range:
        start, end = args.range
        outname = f"combined_data_rate_gbps_s{start}_e{end}.png"
    else:
        outname = "combined_data_rate_gbps.png"
    outpath = outdir / outname
    fig.savefig(outpath, dpi=150, bbox_inches="tight")
    print(f"Wrote: {outpath}")

if __name__ == "__main__":
    main()
