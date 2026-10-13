#!/usr/bin/env python3
"""Original engineering harness: checks startup/privileges/pins, never calls load or changes boot."""
import argparse
import json
import re
import shlex
import struct
import subprocess
import sys
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--adb', default='adb')
parser.add_argument('--serial')
parser.add_argument('--loader', required=True)
parser.add_argument('--module', required=True)
parser.add_argument('--proof-dir', required=True)
parser.add_argument('--output', required=True)
parser.add_argument('--dry-run', action='store_true')
args = parser.parse_args()
image = Path(args.loader).read_bytes()
if len(image) > 1024 * 1024 or image[:7] != b'\x7fELF\x02\x01\x01' or struct.unpack_from('<H', image, 18)[0] != 183:
    parser.error('An independently built Android ARM64 ELF64 loader of at most 1 MiB is required')
start, stride, count = struct.unpack_from('<Q', image, 32)[0], struct.unpack_from('<H', image, 54)[0], struct.unpack_from('<H', image, 56)[0]
if stride != 56 or not 1 <= count <= 128 or start + stride * count > len(image):
    parser.error('Malformed executable program headers')
interpreter = None
for index in range(count):
    header = start + stride * index
    if struct.unpack_from('<I', image, header)[0] == 3:
        offset, size = struct.unpack_from('<Q', image, header + 8)[0], struct.unpack_from('<Q', image, header + 32)[0]
        if not 1 <= size <= 4096 or offset + size > len(image) or image[offset + size - 1] != 0:
            parser.error('Invalid ELF interpreter')
        interpreter = image[offset:offset + size - 1].decode('ascii')
if interpreter != '/system/bin/linker64':
    parser.error('This probe supports only the current Android linker64 build')
root = Path(args.proof_dir)
files = {'loader': Path(args.loader), 'module.ko': Path(args.module), 'target.proof': root / 'target.proof',
         'Module.symvers': root / 'Module.symvers', 'kernel.config': root / 'kernel.config'}
if not all(path.is_file() for path in files.values()):
    parser.error('Missing probe input')
report = {'schema': 1, 'load_attempted': False, 'boot_modified': False,
          'interpreter': interpreter, 'early_init_available': 'not_verified_before_system_mount',
          'campaign_approval': False, 'device_tested': False}
if args.dry_run:
    report['stage'] = 'host_prepared_device_not_contacted'
else:
    adb = [args.adb] + (['-s', args.serial] if args.serial else [])
    def run(arguments):
        return subprocess.run(adb + arguments, check=True, capture_output=True, text=True, timeout=60).stdout.strip()
    remote = None
    try:
        run(['get-state'])
        proposed = run(['shell', 'mktemp', '-d', '/data/local/tmp/husky-probe.XXXXXX'])
        if not re.fullmatch(r'/data/local/tmp/husky-probe\.[A-Za-z0-9]+', proposed):
            raise RuntimeError('Unexpected private probe directory')
        remote = proposed
        for name, path in files.items():
            run(['push', str(path), remote + '/' + name])
        run(['shell', 'chmod', '755', remote + '/loader'])
        def execute(command):
            result = subprocess.run(adb + ['shell', 'su', '-c', shlex.join([remote + '/loader'] + command)],
                                    capture_output=True, text=True, timeout=60)
            if result.returncode not in (0, 2):
                raise RuntimeError('Probe execution failed with exit code ' + str(result.returncode))
            data = json.loads(result.stdout)
            if not isinstance(data, dict) or data.get('schema') != 1 or data.get('load_attempted') is not False:
                raise RuntimeError('Unexpected read-only probe result')
            return {'exit_code': result.returncode, 'report': data}
        report['doctor'] = execute(['doctor'])
        report['check_device'] = execute(['check-device', remote + '/module.ko', '--proof', remote + '/target.proof',
                                          '--symvers', remote + '/Module.symvers', '--config', remote + '/kernel.config'])
        report['device_tested'] = True
        report['stage'] = ('read_only_probe_complete_private_abi_root_boot_unverified'
                           if report['check_device']['exit_code'] == 0 else 'read_only_probe_rejected')
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        report['stage'] = 'read_only_probe_failed'
        report['error'] = str(error)
    finally:
        if remote is not None:
            try:
                run(['shell', 'rm', '-rf', remote])
                report['temporary_directory_removed'] = True
            except (OSError, subprocess.SubprocessError):
                report['temporary_directory_removed'] = False
                report['temporary_directory'] = remote
Path(args.output).write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps({'stage': report['stage'], 'load_attempted': False}))
if report['stage'] in ('read_only_probe_failed', 'read_only_probe_rejected') or report.get('temporary_directory_removed') is False:
    sys.exit(2)
