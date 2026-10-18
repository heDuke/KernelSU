#!/usr/bin/env bash
# Build only the pinned 4K engineering candidate; never flash or load it.
set -euo pipefail
if [ "$#" -ne 5 ]; then
  echo "Usage: $0 SOURCE PREPARED_KDIR CLANG_BIN OFFICIAL_IMAGE OUTPUT_DIR" >&2
  exit 2
fi
source_dir=$(realpath "$1")
prepared_dir=$(realpath "$2")
clang_dir=$(realpath "$3")
official_image=$(realpath "$4")
output_dir=$(realpath -m "$5")
repo_dir=$(cd "$(dirname "$0")/.." && pwd)
target_release=6.1.172-android14-11-g3fc92ed75e44-ab16169542
[ "$(git -C "$source_dir" rev-parse HEAD)" = 3fc92ed75e44bfb2b12bc625fad7a054b939f8a6 ]
git -C "$source_dir" diff --quiet HEAD
"$clang_dir/clang" --version | head -n 1 | grep -q '10087095.*r487747c'
python3 - "$repo_dir" "$prepared_dir" "$official_image" "$target_release" <<'PY'
import sys
from pathlib import Path
sys.path.insert(0, str(Path(sys.argv[1]) / "scripts"))
import audit_canary_lkm as audit
out, image, release = Path(sys.argv[2]), Path(sys.argv[3]), sys.argv[4]
for path, expected in [(out / '.config', audit.CONFIG_SHA256), (out / 'vmlinux', audit.VMLINUX_SHA256),
                       (out / 'Module.symvers', audit.SYMVERS_SHA256), (image, audit.IMAGE_SHA256)]:
    audit.require(audit.digest(path) == expected, 'unverified target input: ' + str(path))
audit.require((out / 'include/generated/utsrelease.h').read_text().strip() ==
              '#define UTS_RELEASE "' + release + '"', 'prepared release mismatch')
PY
mkdir -p "$output_dir" "$prepared_dir/security/selinux"
printf '# Relocated official prepared output.\ninclude %s/Makefile\n' "$source_dir" > "$prepared_dir/Makefile"
cc -O2 -I"$source_dir/include/uapi" -I"$source_dir/include" -I"$source_dir/security/selinux/include" \
  "$source_dir/scripts/selinux/genheaders/genheaders.c" -o "$prepared_dir/genheaders"
"$prepared_dir/genheaders" "$prepared_dir/security/selinux/flask.h" "$prepared_dir/security/selinux/av_permissions.h"
export PATH="$clang_dir:$PATH" ARCH=arm64 CROSS_COMPILE=aarch64-linux-gnu-
# Private references are relocated by ksuinit; the audit below must resolve every
# reference against this exact official vmlinux. Keep all generated CRCs.
make -C "$prepared_dir" M="$repo_dir/kernel" -j4 LLVM=1 CC=clang CONFIG_KSU=m \
  KSU_KMI=android14-6.1 KERNELRELEASE="$target_release" KBUILD_MODPOST_WARN=1 \
  KSU_PACKAGE_NAME=me.weishu.kernelsu.husky.fork KSU_EXPECTED_SIZE=875 \
  KSU_EXPECTED_HASH=b37ef4db671d6d1dd44d1b42efdd63effece3265649734a9d665ff5939c59553 modules
module="$output_dir/android14-6.1_4k_ZP11.260918.007_candidate.ko"
cp "$repo_dir/kernel/kernelsu.ko" "$module"
llvm-strip -d "$module"
python3 "$repo_dir/scripts/audit_canary_lkm.py" --module "$module" \
  --vmlinux "$prepared_dir/vmlinux" --symvers "$prepared_dir/Module.symvers" \
  --image "$official_image" --config "$prepared_dir/.config" > "$output_dir/audit.json"
