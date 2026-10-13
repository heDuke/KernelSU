#!/usr/bin/env python3
"""Read-only same-workload preset pilot. Never invokes load or modifies device state."""
import argparse
import hashlib
import json
import statistics
import subprocess
import time
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--loader', required=True)
parser.add_argument('--module', required=True)
parser.add_argument('--proof-dir', required=True)
parser.add_argument('--runs', type=int, default=30)
parser.add_argument('--output', required=True)
args = parser.parse_args()
if not 30 <= args.runs <= 1000:
    parser.error('Use 30-1000 samples; 200+ are needed before evaluating a percentile claim')
root = Path(args.proof_dir)
expected = None
results = []
for preset in ('balanced', 'lean', 'diagnostic'):
    timings, memory = [], []
    for _ in range(args.runs):
        command = [args.loader, 'check', args.module, '--proof', str(root / 'target.proof'),
                   '--symvers', str(root / 'Module.symvers'), '--config', str(root / 'kernel.config'),
                   '--preset', preset]
        started = time.perf_counter_ns()
        run = subprocess.run(command, text=True, capture_output=True, check=True, timeout=30)
        timings.append((time.perf_counter_ns() - started) // 1000)
        report = json.loads(run.stdout)
        if not report['success'] or report['stage'] != 'offline_checked':
            raise RuntimeError('Unexpected benchmark result')
        invariants = {key: report[key] for key in ('original_sha256', 'imports', 'private_imports', 'version_records')}
        if expected is None:
            expected = invariants
        if invariants != expected:
            raise RuntimeError('Preset changed verification results')
        if 'post_exec_peak_rss_kib' in report:
            memory.append(report['post_exec_peak_rss_kib'])
    timings.sort()
    results.append({'preset': preset, 'samples': args.runs, 'wall_median_us': statistics.median(timings),
                    'wall_p95_us': timings[(95 * len(timings) + 99) // 100 - 1],
                    'reported_post_exec_peak_rss_max_kib': max(memory) if memory else None})
output = {'schema': 1, 'phase': 'offline_check_same_inputs', 'device_tested': False,
          'load_attempted': False, 'comparison_to_production': False, 'invariants': expected,
          'loader_sha256': hashlib.sha256(Path(args.loader).read_bytes()).hexdigest(),
          'memory_scope': 'self VmHWM sampled before final JSON; lower bound on whole-process peak',
          'statistical_claim': 'host pilot only; no device budget, superiority or stability claim', 'results': results}
Path(args.output).write_text(json.dumps(output, indent=2) + '\n')
print(json.dumps({'presets': len(results), 'samples_per_preset': args.runs, 'checks_identical': True}))
