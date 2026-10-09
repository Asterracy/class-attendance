#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

attendance_sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
attendance_tools="${ATTENDANCE_BUILD_TOOLS:-${attendance_sdk_root:+$attendance_sdk_root/build-tools/36.0.0}}"
attendance_android_jar="${ATTENDANCE_ANDROID_JAR:-${attendance_sdk_root:+$attendance_sdk_root/platforms/android-36/android.jar}}"
attendance_java_bin="${ATTENDANCE_JAVA_HOME:-${JAVA_HOME:-}}"
if [[ -n "$attendance_java_bin" ]]; then attendance_java_bin="$attendance_java_bin/bin/"; fi
if [[ -z "$attendance_tools" || ! -f "$attendance_android_jar" || ! -f "$attendance_tools/lib/d8.jar" ]]; then
    echo 'Set ANDROID_SDK_ROOT or ATTENDANCE_BUILD_TOOLS and ATTENDANCE_ANDROID_JAR to an installed official SDK.' >&2
    exit 1
fi

# Stage only private, validated roster assets; examples are never embedded.
mkdir -p build/generated build/classes build/dex build/assets dist
if [[ -f assets/roster.local.txt ]]; then
    python3 scripts/validate_roster.py assets/roster.local.txt
    cp assets/roster.local.txt build/assets/roster.txt
else
    : > build/assets/roster.txt
fi
if [[ "${ATTENDANCE_REQUIRE_COMPLETE_ROSTER:-0}" == 1 && ! -s build/assets/roster.txt ]]; then
    echo 'A complete private student-ID roster is required for this delivery.' >&2
    exit 1
fi
if [[ -f assets/names.txt ]]; then
    python3 scripts/validate_names.py assets/names.txt
    cp assets/names.txt build/assets/names.txt
else
    : > build/assets/names.txt
    echo 'Building with an empty legacy roster.'
fi
"$attendance_tools/aapt2" compile --dir res -o build/resources.zip
"$attendance_tools/aapt2" link -o build/base.apk --manifest AndroidManifest.xml -I "$attendance_android_jar" --java build/generated -A build/assets build/resources.zip
find src build/generated -name '*.java' > build/sources.txt
"${attendance_java_bin}javac" --release 8 -encoding UTF-8 -classpath "$attendance_android_jar" -d build/classes @build/sources.txt
"${attendance_java_bin}jar" cf build/classes.jar -C build/classes .
if [[ "${ATTENDANCE_COMPILE_ONLY:-0}" == 1 ]]; then
    echo 'Compilation verified. No installable or final APK generated.'
    exit 0
fi
"${attendance_java_bin}java" -cp "$attendance_tools/lib/d8.jar" com.android.tools.r8.D8 --release --min-api 23 --lib "$attendance_android_jar" --output build/dex build/classes.jar
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$attendance_tools/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk

if [[ "${ATTENDANCE_UNSIGNED_ONLY:-0}" == 1 ]]; then
    cp build/aligned.apk dist/class-attendance-unsigned.apk
    echo 'Built an unsigned APK for compilation checks; it is not installable.'
    exit 0
fi
attendance_keystore="${ATTENDANCE_KEYSTORE:-.private/signing/release.p12}"
attendance_alias="${ATTENDANCE_KEY_ALIAS:-attendance}"
if [[ ! -f "$attendance_keystore" ]]; then
    echo 'Create your own signing key as described in README.md, or set ATTENDANCE_KEYSTORE.' >&2
    exit 1
fi
attendance_sign_args=(--ks "$attendance_keystore" --ks-key-alias "$attendance_alias")
if [[ -n "${ATTENDANCE_STORE_PASSWORD:-}" ]]; then attendance_sign_args+=(--ks-pass env:ATTENDANCE_STORE_PASSWORD); fi
if [[ -n "${ATTENDANCE_KEY_PASSWORD:-}" ]]; then attendance_sign_args+=(--key-pass env:ATTENDANCE_KEY_PASSWORD); fi
"${attendance_java_bin}java" -jar "$attendance_tools/lib/apksigner.jar" sign "${attendance_sign_args[@]}" --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false --out dist/class-attendance.apk build/aligned.apk
"${attendance_java_bin}java" -jar "$attendance_tools/lib/apksigner.jar" verify --verbose --print-certs dist/class-attendance.apk > dist/signature-verification.txt
"$attendance_tools/zipalign" -c -p 4 dist/class-attendance.apk
python3 - <<'PY'
import hashlib
from pathlib import Path
p = Path('dist/class-attendance.apk')
print('APK bytes:', p.stat().st_size)
print('SHA256:', hashlib.sha256(p.read_bytes()).hexdigest())
PY
