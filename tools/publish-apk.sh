#!/bin/bash
# Builds an app and puts its installable files in apps/<app>/apk/: the release and debug
# APKs, and version.json, which installed copies read to find out there's an update.
# Run it before every commit of an app, then commit the apk/ folder (APKs need `git add -f`).
#
# Usage: tools/publish-apk.sh fajr-alarm [accent-coach ...]
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
[ $# -gt 0 ] || { echo "Usage: $0 <app> [<app> ...]" >&2; exit 1; }

for app in "$@"; do
    dir="$root/apps/$app"
    [ -x "$dir/gradlew" ] || { echo "No Android app in $dir" >&2; exit 1; }
    (cd "$dir" && ./gradlew -q :core:test :app:assembleRelease :app:assembleDebug)

    mkdir -p "$dir/apk"
    cp "$dir/app/build/outputs/apk/release/app-release.apk" "$dir/apk/$app-release.apk"
    cp "$dir/app/build/outputs/apk/debug/app-debug.apk" "$dir/apk/$app-debug.apk"

    python3 - "$dir" "$app" <<'PY'
import hashlib, json, os, sys

dir, app = sys.argv[1], sys.argv[2]
built = json.load(open(f"{dir}/app/build/outputs/apk/release/output-metadata.json"))["elements"][0]
apk = f"{app}-release.apk"
data = open(f"{dir}/apk/{apk}", "rb").read()
version = {
    "versionCode": built["versionCode"],
    "versionName": built["versionName"],
    "apk": apk,
    "size": len(data),
    "sha256": hashlib.sha256(data).hexdigest(),
}
with open(f"{dir}/apk/version.json", "w") as f:
    json.dump(version, f, indent=2)
    f.write("\n")
print(f"{app}: version {version['versionName']}, {len(data) / 1e6:.1f} MB")
PY
done
