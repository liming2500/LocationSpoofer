[简体中文](CONTRIBUTING.md) | [English](CONTRIBUTING_EN.md)

---

# Contributing to LocationSpoofer

First off, thank you for considering contributing to LocationSpoofer! 🎉

Contributions from the community help make LocationSpoofer more stable, reliable, and effective.

---

## 📑 Table of Contents

1. [Code of Conduct](#code-of-conduct)
2. [How Can I Contribute?](#how-can-i-contribute)
   * [Reporting Bugs](#reporting-bugs)
   * [Suggesting Enhancements](#suggesting-enhancements)
   * [Pull Requests](#pull-requests)
3. [Development Setup](#development-setup)
4. [Architecture & Guidelines](#architecture--guidelines)
5. [Commit Message Conventions](#commit-message-conventions)

---

## Code of Conduct

This project and everyone participating in it is governed by the [LocationSpoofer Code of Conduct](CODE_OF_CONDUCT_EN.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to the project maintainers.

---

## How Can I Contribute?

### Reporting Bugs

Before creating a bug report, please:
* Check the [existing Issues](https://github.com/your-username/LocationSpoofer/issues) to ensure the problem hasn't already been reported.
* Ensure you are running a supported environment (**Android 8.0+**, **KernelSU / APatch / Magisk**, **LSPosed API 101+**).

When filing a bug report via the **Bug Report Template**, please provide:
* **Device & Environment**: Android OS version, ROM/device model, Root solution (KernelSU/APatch/Magisk version), LSPosed/libxposed version.
* **Target Application**: App name and version code/name where the issue occurs.
* **Steps to Reproduce**: Detailed step-by-step description.
* **Logs & Behavior**: Logcat snippets or LSPosed module logs (especially crash traces or unexpected fallback coordinates).

### Suggesting Enhancements

Feature requests are welcome! When opening an issue via the **Feature Request Template**, please explain:
* The problem or limitation you are experiencing.
* The proposed solution or behavior.
* Potential edge cases or considerations.

### Pull Requests

1. **Fork the repository** and create your branch from `main`:
   ```bash
   git checkout -b feat/your-feature-name
   ```
2. **Make your changes** following our code style and architecture.
3. **Verify the build**:
   ```bash
   ./gradlew assembleDebug
   ```
4. **Commit your changes** using clear commit messages (see [Commit Message Conventions](#commit-message-conventions)).
5. **Push to your fork** and submit a Pull Request targeting the `main` branch.
6. Complete the PR template checklist and describe your changes clearly.

---

## Development Setup

### Prerequisites
* **Android Studio**: Android Studio Hedgehog / Iguana / Jellyfish or newer.
* **JDK**: OpenJDK 17 or OpenJDK 21.
* **Android SDK**: Build Tools `34.0.0`+, compileSdk `34`.
* **Testing Device**: A rooted device with **KernelSU / APatch / Magisk** and **LSPosed (API 101+)** installed.

### Building
```bash
# Clone the repository
git clone https://github.com/your-username/LocationSpoofer.git

# Open directory
cd LocationSpoofer

# Build debug APK
./gradlew assembleDebug

# Install directly to connected device
./gradlew installDebug
```

---

## Architecture & Guidelines

LocationSpoofer is structured using **MVVM + Clean Architecture**:

* **Language**: 100% Kotlin with Coroutines and StateFlow.
* **UI**: Jetpack Compose and Material Design 3. Maintain modular, decoupled Composable components.
* **Dependency Injection**: Koin (`appModule`).
* **Database**: Room Database with spatial index optimizations.
* **Xposed Hook Layer**:
  * Located in `com.suseoaa.locationspoofer.xposed`.
  * Adheres to **LSPosed API 101+ / libxposed (Service mode)** specifications.
  * Zero-IO on high-frequency hook threads: Read configuration from volatile in-memory cache updated by background daemon thread.
  * MultiDex safety: Dynamic ClassLoader hooking locked to the host package via `/proc/self/cmdline`.
  * Maintain clean stack traces and avoid leaving observable inspection points.

---

## Commit Message Conventions

We follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

```
<type>(<scope>): <subject>
```

### Allowed Types:
* `feat`: A new feature
* `fix`: A bug fix
* `docs`: Documentation only changes
* `style`: Formatting, missing semi-colons, whitespace, etc. (no code change)
* `refactor`: A code change that neither fixes a bug nor adds a feature
* `perf`: A code change that improves performance
* `test`: Adding missing tests or correcting existing tests
* `chore`: Build process, dependencies, or auxiliary tool changes

### Examples:
```
feat(hook): add support for dynamic MultiDex location listener hooking
fix(coords): resolve coordinate shift on Baidu Map rendering layer
docs: update README with API 101+ specifications
```

---

Thank you for contributing to LocationSpoofer!
