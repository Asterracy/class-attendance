# Dependency and license inventory

The application source, vector icon, and XLSX ZIP/XML writer in this repository were authored for this project. There are no bundled third-party source files or application libraries. The app calls Android platform APIs and Java standard APIs; it does not embed Apache POI, AndroidX, an OCR service, or an Excel library.

Build prerequisites are installed separately:

| Prerequisite | Purpose | License handling |
| --- | --- | --- |
| Android SDK Platform 36 and Build Tools 36.0.0 | Compile APIs, package resources, D8, alignment, APK signing | Install from Google and review the [Android SDK terms](https://developer.android.com/studio/terms); the SDK is not included or relicensed by this repository. Component open-source notices remain with the installed SDK. |
| Java Development Kit 17 or newer | Compile Java 8 bytecode; run SDK tools | Choose and install a JDK under its distribution's own license. No JDK binaries are redistributed here. |
| Python 3 | Local validation and read-only ZIP/XML/SQLite tests | Uses only the Python standard library. No Python runtime is redistributed here. |
| Git, Bash and a ZIP CLI | Source management, build shell and archive insertion | Installed separately; no tool binaries are redistributed here. |

The project license is MIT, with text in `LICENSE` and the [SPDX MIT reference](https://spdx.org/licenses/MIT.html). User rosters, attendance data, personalized APKs, and private keys are excluded from this public-source distribution. A source-code license does not grant permission to publish someone else's personal data.
