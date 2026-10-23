<div align="center">

<h1>LocationSpoofer</h1>

<p>System-level Android location and wireless-environment simulation built on KernelSU + LSPosed</p>

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-purple.svg)](https://kotlinlang.org)
[![KernelSU](https://img.shields.io/badge/Root-KernelSU-orange.svg)](https://kernelsu.org)
[![LSPosed](https://img.shields.io/badge/LSPosed-API%20101%2B-purple.svg)](https://github.com/LSPosed/LSPosed)

[简体中文](README.md) | [English](README_EN.md)

</div>

---

> **Source code only — no prebuilt APK is provided.** Build it yourself, see [Build](#build).

---

## Overview

Modern Android risk-control and anti-cheat systems no longer rely solely on the `isFromMockProvider` / `isMock` flags. They cross-check the physical environment around the device:

* Wi-Fi access points and BSSID lists
* Cellular cell data (GSM / WCDMA / LTE / 5G NR)
* Nearby BLE beacons
* Low-level GNSS satellite distribution and ephemeris
* Accelerometer and step-counter interaction
* FFT / dispersion analysis over consecutive coordinate samples

This project obtains root via **KernelSU / APatch / Magisk** and uses **LSPosed (libxposed API 101+)** to inject into target processes at the Zygote stage. It intercepts location, satellite, cell, Wi-Fi, Bluetooth and sensor APIs and returns mutually consistent simulated data, alongside a complete toolchain for route planning, motion realism and environment collection.

---

## Features

### Location and coordinates

| Feature | Description |
|---|---|
| Static simulation | Pick a location on the map and lock the coordinate |
| Three map engines | Switch between AMap / Baidu / Google |
| Coordinate systems | WGS-84 / GCJ-02 / BD-09 conversion, per-package override |
| Position jitter | Ornstein-Uhlenbeck process emulating real GPS drift |
| Altitude | Base altitude ± adjustable range, terrain-shaped, grade capped at 8% |
| Accuracy | Accuracy fluctuates naturally within a realistic band |

### Route simulation

| Feature | Description |
|---|---|
| Route planning | Tap points on the map, undo, or restart selection |
| Real road matching | Fetch actual road geometry to avoid cutting through buildings |
| Traffic light dwell | Parse junction nodes, stop then resume |
| Run modes | Loop (closed forward / open back-and-forth), manual joystick |
| **Lap limit** | **Enter a lap count (0–999, 0 or blank = unlimited); stops at the end of the last lap** |
| **Estimate** | **Live total distance and estimated duration** |
| **Completion alert** | **Notification / sound / vibration, each individually toggleable** |
| Standby at start | Hold at the first point until the target app reports a stable GPS fix, then press "Start Running" |
| Route recording | Record while moving, thinned by distance and turn angle |
| Floating joystick | Adjust heading and speed above other apps |

### Motion realism

| Feature | Description |
|---|---|
| Speed fluctuation | Long-period drift plus short breathing wave, sharing one budget (0–30%) |
| Cadence fluctuation | Independent slider 0–30%, affects the step sensor only, never the route position |
| Accel / decel ramp | Smooth start and stop ramp, 0–10 seconds |
| Terrain effect | Slower uphill, faster downhill, sensitivity 0–100 |
| Master toggle | Turning it off keeps pace and cadence perfectly constant |
| Heading tracking | Derived from measured displacement; correct at U-turns, corners, start standby and stop |
| Orientation sensors | Intercepts rotation vector, orientation sensor and `getOrientation`, preserving real pitch and roll |
| Personal gait | Record a 20-second gait curve for accelerometer playback |
| Step simulation | Step counter, step detector and accelerometer, cadence adapts to speed |

### Radio environment

| Feature | Description |
|---|---|
| Wi-Fi | Scan list, connection state, BSSID / RSSI / channel |
| Cellular | 2G–5G NR cell info, signal strength, neighbour list |
| BLE | Beacon scan filtering and reconstruction |
| Field collection | Scan real Wi-Fi / cells / Bluetooth along the way |
| Data management | Browse, annotate, edit and delete individual records |
| Cloud import | WiGLE (Wi-Fi), OpenCellID (cells) |
| Spatial interpolation | Inverse distance weighting from nearby samples |
| Import / export | JSON bundle; locations, routes, environment, settings and keys are selectable |

### System-level simulation

| Feature | Description |
|---|---|
| Scoped mode | Applies only to apps selected in LSPosed |
| Global mode | Hooks system services, applies to all apps (vendor-specific adaptation required) |
| Per-app coordinates | Assign a coordinate system per package |
| Force location | Force-enable location for target apps |
| Adaptation status | Built-in per-vendor / per-device verification status |

### Anti-detection

| Feature | Description |
|---|---|
| Mock flags | `isMock()` / `isFromMockProvider()` and internal fields |
| AppOps | `OP_MOCK_LOCATION` queries return ignored |
| Settings | `mock_location` related keys rewritten |
| Stack scrubbing | Strip Xposed / LSPosed frames |
| ClassLoader isolation | Feature-class probes return `ClassNotFoundException` |
| MultiDex awareness | Dynamically capture location components in secondary dex |
| Satellite injection | Full GnssStatus / NMEA / GpsStatus spoofing |
| Emulator masking | Hide common emulator fingerprints |

### UI and tooling

| Feature | Description |
|---|---|
| Material UI | Jetpack Compose + M3, with frosted-glass and damped-drag components |
| Languages | Simplified Chinese / English / العربية |
| Background keep-alive | Foreground service and boot auto-start |
| Hook status page | Live view of whether each component is installed |
| Diagnostics | Root state, framework connection, config source |
| Data backup | One-tap export and merged import |

---

## Architecture

MVVM + Clean Architecture across six Gradle modules:

| Module | Type | Responsibility |
|---|---|---|
| `app` | App | Host shell: DI aggregation, packaging, bundles the `xposed` output for LSPosed |
| `app-ui` | Library | All Jetpack Compose UI and ViewModels |
| `service` | Library | Foreground service, floating joystick, boot receiver |
| `xposed` | Library | LSPosed module and all hook implementations |
| `core-data` | Library | Room database, repositories, config and root management |
| `core-geo` | JVM | Coordinates, route geometry, motion realism (no Android dependency) |

App and hooks communicate over the libxposed remote config channel: `ConfigManager` publishes JSON snapshots, the hook side decodes them on a background thread and atomically swaps an in-memory cache, so hook calls only read the cache.

**Multi-process consistency**: route position, heading, step count and speed are computed independently in the app process, each target process and `system_server`. All of them are deterministic functions, so every process computes bit-identical results for the same instant.

---

## Requirements

* **OS**: Android 8.0 (API 26) or newer
* **Root**: KernelSU / APatch / Magisk
* **Framework**: LSPosed (API 101+) or any framework compatible with libxposed API 101+

---

## Build

Requires **JDK 17** and the **Android SDK** (see `app/build.gradle.kts` for compileSdk).

```bash
git clone https://github.com/liming2500/LocationSpoofer.git
cd LocationSpoofer

# Point at your SDK (or use ANDROID_HOME)
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

./gradlew assembleScopedDebug
# Output: app/build/outputs/apk/scoped/debug/
```

**Global variant**: `./gradlew assembleGlobalDebug`.

**Windows note**: `gradle.properties` sets `android.overridePathCheck=true` so non-ASCII paths work. Some Gradle versions still fail unit tests under non-ASCII paths with `ClassNotFoundException`; use an ASCII-only path.

**Release**: no signing key ships with the repository. Configure `signingConfigs` in `app/build.gradle.kts`, then run `./gradlew assembleScopedRelease`.

---

## Usage

1. Grant root to this app in your root manager.
2. Enable the module in LSPosed and select the target apps.
3. Force-stop the target apps (or reboot) so they load the hooks.
4. Open the app, choose a location or plan a route, configure and start.

### Recommended route workflow

```
Plan route → tap "Start" → the app enters standby
         ↓
Open the target app, wait until it shows a stable GPS fix, start the run there
         ↓
Return here and tap "Start Running" → the route plays for the configured laps
         ↓
Lap target reached → notification / sound / vibration per your settings
```

The standby step matters: target apps validate GPS quality on the first fix, and a route that is already moving tends to be rejected.

### Motion realism settings

Under Settings → Motion Realism:

| Setting | Description |
|---|---|
| Randomness | Extra variation in stride, altitude and accelerometer waveform |
| Speed fluctuation | Slow pace drift on routes (0–30%) |
| Cadence fluctuation | Step-sensor cadence variation (0–30%), never affects position |
| Accel / decel ramp | Smooth start and stop ramp (0–10 s) |
| Terrain effect | Slope sensitivity (0–100), 0 disables it |
| Personal gait | Record your own gait curve |
| Completion alert | Notification / sound / vibration toggles |

These values are snapshotted when a session starts; changes take effect the next time you start.

---

## Known limitations

* Global mode depends on vendor internals; see `ADAPTATION_PROGRESS.md` for status.
* Map and location service behaviour may change with SDK updates.
* A few apps read GNSS data at the native layer, which Java-level hooks cannot cover.
* Unit tests may fail under non-ASCII project paths due to Gradle path handling; use an ASCII path.

---

## Disclaimer

This software is provided for study, research and lawful personal testing (for example developer location testing and device compatibility debugging).

**Do not use it for fake attendance, falsified assessments or activity records, exam cheating, commercial fraud, or any behaviour that violates applicable laws or service agreements.** You are solely responsible for any consequences, including account bans, data loss or legal disputes.

Root access and system-level hooks may affect system stability and app compatibility. Use a dedicated test device and back up important data.

---

## Changelog

### 3.0.7

**Fixed**
- Cadence reported wrong by 运动世界校园 (Sport World Campus) on an emulator while correct on a real device: emulators expose no hardware step counter, so `getSensorList(TYPE_STEP_COUNTER)` returns empty and some campus-running apps fall back to counting steps from the accelerometer themselves. The three axes used to peak at different phases (vertical 0.02, forward 0.30, lateral flipping every other step), so per-axis or any-axis peak counting turned each step into two or three — measured 354 spm with a 51 cm stride
- Vertical, forward and lateral now use a **same-phase, same-width** single-peak impact, so per-axis, union-of-axes and magnitude counting all give one peak per step; a unit test covers 25/50 Hz, 120–200 spm and three speed presets
- Lateral sway no longer flips sign every other step (that stepped the x axis from +A to -A each step, doubling delta/per-axis counters)

### 3.0.6

**Fixed**
- Cadence still near double the target, including with a fixed (preset) cadence:
  - Step accumulation now lives in a dedicated `StepClock` (core-geo, unit-tested): when the config's `start_timestamp` flips back and forth, elapsed time is no longer counted twice; the running total is monotonic, so no call order can make it jump
  - The step clock is frozen while standing still (standby before a run), so minutes of waiting are no longer converted into hundreds of steps the moment the run starts
  - The virtual step detector settles its increment once per dispatch instead of once per listener
  - Lateral and vertical impact now share the same phase and width: the magnitude signal has exactly one peak per step at low sample rates, so peak counting no longer drifts between high and low
  - Impact width widened to about 1/10 of a step, so 10–16 Hz sampling no longer misses the main peak
- Step settings were not forwarded when simulation started: the default-static and route-standby paths both fell back to the built-in defaults (auto cadence, 165 spm) and silently dropped the user's fixed cadence. Starting, re-picking a map point and environment refreshes now all keep the current session's cadence settings

### 3.0.5

**Fixed**
- Auto cadence reported as 300+: the synthesized accelerometer waveform had two near-equal peaks per step, so target apps that count steps by peak detection counted each step twice — 3 m/s (163 spm) showed up as 300+. The waveform now has a single peak per step, and a unit test samples it at 500 Hz to assert exactly one local maximum per step
- Accelerometer amplitude scaled linearly with speed (reaching 21 m/s² at 10 m/s); it is now capped to a physiological range

### 3.0.4

**Added**
- Route lap limit: enter a number (0–999, 0 or blank = unlimited); stops at the end of the last lap
- Estimated time and distance shown live, updating with lap count and speed
- Completion alert: notification / sound / vibration, each toggleable under Settings → Motion Realism
- Standby at start: hold at the first point until the target app shows a stable GPS fix, then press "Start Running"

**Improved**
- Orientation sensor coverage: rotation vector, orientation sensor and `getOrientation` all intercepted; heading follows motion while real pitch and roll are preserved
- Motion realism: accel / decel ramp, terrain-linked pace, short breathing wave
- Cadence fluctuation is now independent from the randomness level
- Route position fix: one-shot precomputed integration removes position jumps between processes
- Fixed "weak GPS signal" in route mode caused by double random-walk advancement
- Fixed spoofed satellite status throwing and swallowing the callback, which left apps without satellite data

**Fixed**
- Crash on the Motion Realism screen caused by an invalid format string
- Missing modulo guard for zero-length routes

**Build**
- Supports non-ASCII project paths (`android.overridePathCheck`)
- Removed prebuilt APKs from history; repository size 122 MB → about 3 MB

---

## License

Released under the [GNU General Public License v3.0](LICENSE).

```
Copyright (C) 2026 SuseOAA
```
