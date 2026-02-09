# ModelAudit Kotlin library

Thin Kotlin/JVM wrapper that runs [ModelAudit](https://github.com/promptfoo/modelaudit) on a path and returns the result as JSON. Uses a **bundled** executable (Python + deps packaged inside the JAR via PyInstaller); no Python or modelaudit on the host required.

## Requirements

- JVM 11+ (or the JVM used to run Gradle; no fixed toolchain is set).
- The published JAR always includes the bundle; building and publishing require the bundle to be built first (see Build).

## Usage

```kotlin
import io.modelaudit.scanFolder
import io.modelaudit.scanToJson

fun main() {
    val json = scanToJson("/path/to/models")
    // or
    val json2 = scanFolder("/path/to/models")
}
```

- **`scanToJson(path): String`** — Runs scan and returns JSON using the bundled binary from the JAR.
- **`scanFolder(folderPath): String`** — Same, convenience for folder paths.
- Throws `ModelAuditException` if the process fails, exits with code 2, or the bundle for the current OS/arch is missing.

## Tests

Tests check that the library runs a scan and returns valid JSON; one test uses the unsafe PyTorch asset and asserts that the result contains security issues.

**Requirements:** The bundled binary must be present in the JAR (build it first; see below). Test asset: `tests/assets/samples/pytorch/malicious_eval.pt`. From **repo root** (after building the bundle):

```bash
./gradlew :modelaudit-kotlin:test
```

If the bundle or test asset is missing, dependent tests are skipped.

## Build

From repo root. The bundle must already exist (see below); otherwise the build fails with instructions.

```bash
./gradlew :modelaudit-kotlin:jar
```

Artifact: `modelaudit-kotlin/build/libs/modelaudit-kotlin-<version>.jar`.

### Building the bundle (required before first jar/test/publish)

From repo root, in a terminal where [uv](https://docs.astral.sh/uv/) is on PATH:

```bash
./gradlew :modelaudit-kotlin:buildBundle
```

Or: `uv sync --extra bundle && uv run python scripts/build_bundle.py`. The binary is placed in `modelaudit-kotlin/src/main/resources/io/modelaudit/bins/<os>-<arch>/`. After that, `jar`, `test`, and `publish` only check that the bundle exists (they do not run uv). For multiple platforms, run `buildBundle` on each target OS/arch (or use CI).

## Automatic multi-platform builds (CI)

Workflow [`.github/workflows/build-kotlin-bundle.yml`](../.github/workflows/build-kotlin-bundle.yml) builds standalone binaries on **Linux (x64), macOS (ARM64), and Windows (x64)** and merges them into a single JAR.

- **Triggers:** push to `main` (when Kotlin/Python bundle-related files change) or manual `workflow_dispatch`.
- **Jobs:** one job per OS (ubuntu-22.04, macos-14, windows-latest) runs `uv sync --extra bundle && uv run python scripts/build_bundle.py` and uploads the `bins/<platform>/` artifact; a final job downloads all artifacts, merges them into `modelaudit-kotlin/.../resources/io/modelaudit/bins/`, and runs `./gradlew :modelaudit-kotlin:jar`.
- **Output:** artifact `modelaudit-kotlin-jar` contains the JAR with all bundled platforms. Download it from the workflow run.

## Publish to a Maven repository

**Local (for development):**

```bash
./gradlew :modelaudit-kotlin:publishToMavenLocal
```

Artifacts go to `~/.m2/repository/io/github/gammmaaaa/modelaudit-kotlin/<version>/`.

**Remote (Maven Central):** One command uploads to OSSRH Staging and closes/releases to Maven Central (no manual Portal step). From **repo root**:

1. **Portal User Token** — https://central.sonatype.com/usertoken → Generate User Token → copy username and password once.
2. **Signing (GPG)** — Maven Central requires signed artifacts. Configure in `~/.gradle/gradle.properties` (or repo root `gradle.properties`, do not commit secrets):

```properties
sonatypeUsername=TOKEN_USERNAME_FROM_PORTAL
sonatypePassword=TOKEN_PASSWORD_FROM_PORTAL
signing.keyId=YOUR_GPG_KEY_ID
signing.password=YOUR_GPG_KEY_PASSPHRASE
signing.secretKeyRingFile=/path/to/secring.gpg
```

(Or use GPG agent and set only `signing.keyId` and `signing.password`; see [Central publishing](https://central.sonatype.org/publish/publish-gradle/).)
3. **Publish** (bundle is built automatically if needed):

```bash
./gradlew publishToMavenCentral
```

This builds the bundle (if needed), uploads the JAR to OSSRH Staging, closes the staging repository, and releases it to Maven Central. No curl or manual Publish in the Portal.

## Add to your project

After publishing (local or remote):

```kotlin
repositories { mavenLocal() }  // or mavenCentral() if published there
dependencies { implementation("io.github.gammmaaaa:modelaudit-kotlin:0.2.24") }
```

Or use a composite build / `includeBuild` pointing at this repo.
