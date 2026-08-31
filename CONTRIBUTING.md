# Contributing to PocketSQL

Thank you for your interest in contributing to **PocketSQL**! Contributions from the community help make PocketSQL a robust, fast, and feature-rich database engine for mobile developers worldwide.

---

## 🗺️ Table of Contents
- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
  - [Reporting Bugs](#reporting-bugs)
  - [Suggesting Enhancements](#suggesting-enhancements)
  - [Submitting Pull Requests](#submitting-pull-requests)
- [Local Development Setup](#local-development-setup)
- [Running Unit Tests](#running-unit-tests)
- [Pull Request Guidelines](#pull-request-guidelines)
- [Project Maintainer](#project-maintainer)

---

## Code of Conduct

This project and everyone participating in it is governed by the [PocketSQL Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to **[Prahlad Mahor](https://github.com/prahladmahor888)**.

---

## How Can I Contribute?

### Reporting Bugs

Before creating a bug report, please check existing issues to ensure the bug hasn't already been reported.

When creating a bug report, please include:
- A clear and descriptive title.
- Steps to reproduce the problem.
- Expected vs. actual behavior.
- The SQL query or command statement that caused the issue.
- Android OS version and device details (or emulator specifications).

### Suggesting Enhancements

Feature requests and enhancement ideas are welcome! When submitting an enhancement request:
- Use a clear and descriptive title.
- Provide a detailed explanation of the proposed feature and why it would be useful.
- Provide examples of SQL syntax or API responses if applicable.

### Submitting Pull Requests

1. **Fork the Repository**: Create your own fork of [PocketSQL](https://github.com/prahladmahor888/PocketSQL).
2. **Create a Feature Branch**: Branch off from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. **Make Your Changes**: Write clean, maintainable code following existing Java and Android conventions.
4. **Add Unit Tests**: Add test cases in `app/src/test/java/com/mysql/pocketsql/` covering your changes.
5. **Run Tests**: Ensure all unit tests pass before submitting.
6. **Commit & Push**:
   ```bash
   git commit -m "Add feature: detailed description of changes"
   git push origin feature/your-feature-name
   ```
7. **Open a Pull Request**: Submit a Pull Request targeting the `main` branch of `prahladmahor888/PocketSQL`.

---

## Local Development Setup

1. **Prerequisites**:
   - JDK 17 or higher
   - Android SDK (API 34 / Android 14)
   - Android Studio (recommended) or Gradle command line tools

2. **Clone the Repository**:
   ```bash
   git clone https://github.com/prahladmahor888/PocketSQL.git
   cd PocketSQL
   ```

3. **Build the Debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```

4. **Install on Connected Device / Emulator**:
   ```bash
   ./gradlew installDebug
   ```

---

## Running Unit Tests

PocketSQL includes a comprehensive JUnit test suite verifying SQL parsing, storage engine operations, alter table constraints, window functions, and encryption:

```bash
# Run all unit tests
./gradlew test

# Run specific test class
./gradlew :app:testDebugUnitTest --tests "com.mysql.pocketsql.SqlEngineTest"
```

All pull requests must pass `./gradlew test` with **0 failures**.

---

## Pull Request Guidelines

- Keep pull requests focused on a single feature or bug fix.
- Follow standard Java code style conventions (4-space indentation, clear variable names).
- Preserve existing comments and docstrings unless updating related logic.
- Ensure no breaking changes to existing SQL function signatures unless explicitly discussed in an issue.

---

## Project Maintainer

**Prahlad Mahor**
- **GitHub Profile**: [@prahladmahor888](https://github.com/prahladmahor888)
- **Repository**: [PocketSQL](https://github.com/prahladmahor888/PocketSQL)

Thank you for helping build PocketSQL! 🇮🇳
