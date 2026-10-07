#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
attendance_sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
attendance_android_jar="${ATTENDANCE_ANDROID_JAR:-${attendance_sdk_root:+$attendance_sdk_root/platforms/android-36/android.jar}}"
attendance_java_bin="${ATTENDANCE_JAVA_HOME:-${JAVA_HOME:-}}"
if [[ -n "$attendance_java_bin" ]]; then attendance_java_bin="$attendance_java_bin/bin/"; fi
if [[ ! -d build/classes || ! -f "$attendance_android_jar" ]]; then echo 'Run a build and configure the SDK first.' >&2; exit 1; fi
mkdir -p build/test-classes
"${attendance_java_bin}javac" --release 8 -encoding UTF-8 -cp "build/classes:$attendance_android_jar" -d build/test-classes tests/ExportTest.java
"${attendance_java_bin}java" -cp build/classes:build/test-classes cn.local.attendance.ExportTest build/test-export.xlsx
python3 tests/verify_xlsx.py
python3 tests/verify_sqlite.py
