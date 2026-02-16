plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.dokka") version "1.9.20"
    id("maven-publish")
    id("signing")
}

tasks.test {
    useJUnitPlatform()
    testLogging.showStandardStreams = true
}

group = "io.github.gammmaaaa"
version = "0.3.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Use the same JVM as Gradle (no fixed toolchain) so the project opens without requiring JDK 11/17.
// To pin a version, add: kotlin { jvmToolchain(11) } and install that JDK or enable toolchain auto-download.

val repoRoot = rootProject.layout.projectDirectory.asFile

val skipBundleBuild = project.hasProperty("skipBundleBuild")

// Resolve full path to "uv" so bundle tasks work when Gradle runs from IDE (no Homebrew in PATH).
// Only resolve when not skipping bundle build (e.g. CI merge-and-jar has no uv).
fun findUvExecutable(): String {
    project.findProperty("uvPath")?.toString()?.let { path ->
        val f = project.file(path)
        if (f.isFile && f.canExecute()) return f.absolutePath
    }
    System.getenv("UV_PATH")?.let { path ->
        val f = project.file(path)
        if (f.isFile && f.canExecute()) return f.absolutePath
    }
    val pathSeparator = System.getProperty("path.separator", ":")
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    val exeNames = if (isWindows) listOf("uv.exe", "uv") else listOf("uv")
    val searchDirs = listOf(
        "/opt/homebrew/bin",
        "/usr/local/bin",
        System.getenv("HOME")?.let { "$it/.local/bin" },
        System.getenv("HOME")?.let { "$it/.cargo/bin" },
    ).filterNotNull() + (System.getenv("PATH")?.split(pathSeparator)?.filter { it.isNotBlank() } ?: emptyList())
    for (dir in searchDirs.distinct()) {
        for (name in exeNames) {
            val candidate = project.file(dir).resolve(name)
            if (candidate.isFile && candidate.canExecute()) return candidate.absolutePath
        }
    }
    throw GradleException(
        "uv executable not found. Searched: /opt/homebrew/bin, /usr/local/bin, \$HOME/.local/bin, \$HOME/.cargo/bin, and PATH. " +
            "Install with: brew install uv (or set uvPath=/path/to/uv or UV_PATH env var)."
    )
}

val uvPath: String = if (skipBundleBuild) "" else findUvExecutable()

// Separate venv for bundle so we don't pull dev deps (mypy -> librt needs MSVC on Windows). Use Python 3.12 for wheels.
val bundleVenvDir = rootProject.file(".venv-bundle")

val buildBundleSync = tasks.register<Exec>("buildBundleSync") {
    group = "modelaudit"
    description = "Run uv sync --extra bundle --no-default-groups into .venv-bundle (no mypy/librt)"
    workingDir = repoRoot
    doFirst {
        environment("UV_PROJECT_ENVIRONMENT", bundleVenvDir.absolutePath)
    }
    commandLine(
        uvPath,
        "sync",
        "--extra", "bundle",
        "--no-default-groups",
        "--python", "3.12",
    )
    inputs.files(
        rootProject.file("pyproject.toml"),
        rootProject.file("uv.lock"),
    )
    outputs.dir(bundleVenvDir)
}

val buildBundle = tasks.register<Exec>("buildBundle") {
    group = "modelaudit"
    description = "Build PyInstaller bundle from .venv-bundle"
    dependsOn(buildBundleSync)
    workingDir = repoRoot
    doFirst {
        environment("UV_PROJECT_ENVIRONMENT", bundleVenvDir.absolutePath)
    }
    commandLine(uvPath, "run", "python", "scripts/build_bundle.py")
    inputs.files(
        rootProject.file("pyproject.toml"),
        rootProject.file("uv.lock"),
        rootProject.file("scripts/build_bundle.py"),
        rootProject.file("scripts/standalone_entry.py"),
    )
    // Do not declare outputs.dir() on src/main/resources/... — Gradle disallows task outputs inside source sets
}

val requireBundle = tasks.register("requireBundle") {
    if (!skipBundleBuild) dependsOn(buildBundle)
    doLast {
        val binsDir = layout.projectDirectory.dir("src/main/resources/io/modelaudit/bins")
        if (!binsDir.asFile.exists()) {
            throw GradleException(
                "Bundled binary missing. From repo root run: uv sync --extra bundle && uv run python scripts/build_bundle.py " +
                    "then rebuild. The JAR must include io/modelaudit/bins/<platform>/modelaudit.zip."
            )
        }
        val hasBundle = binsDir.asFile.listFiles()?.any { platformDir ->
            platformDir.isDirectory && platformDir.resolve("modelaudit.zip").isFile
        } ?: false
        if (!hasBundle) {
            throw GradleException(
                "No bundled binary under src/main/resources/io/modelaudit/bins/<platform>/modelaudit.zip. " +
                    "From repo root run: uv sync --extra bundle && uv run python scripts/build_bundle.py then rebuild."
            )
        }
    }
}

tasks.named<Jar>("jar") {
    dependsOn(requireBundle)
    manifest {
        attributes("Automatic-Module-Name" to "io.modelaudit.kotlin")
    }
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    dependsOn(tasks.named("dokkaJavadoc"))
    from(layout.buildDirectory.dir("dokka/javadoc"))
}

// Publish (Maven Central and mavenLocal) only with bundle; no way to publish without it.
tasks.matching { it.name.startsWith("publish") }.configureEach {
    dependsOn(requireBundle)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "modelaudit-kotlin"
            artifact(sourcesJar.get())
            artifact(javadocJar.get())
            pom {
                name.set("ModelAudit Kotlin")
                description.set("Kotlin/JVM wrapper for ModelAudit — scan ML model files for security issues and get JSON results.")
                url.set("https://github.com/promptfoo/modelaudit")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        name.set("ModelAudit Contributors")
                    }
                }
                scm {
                    url.set("https://github.com/promptfoo/modelaudit")
                    connection.set("scm:git:git://github.com/promptfoo/modelaudit.git")
                }
            }
        }
    }
    repositories {
        mavenLocal()
        // Sonatype/Central is configured via root's nexusPublishPlugin; use :modelaudit-kotlin:publishToSonatype + closeAndReleaseSonatypeStagingRepository
    }
}

// Sign only when -PsignForPublish (publishToMavenLocal runs without signing)
if (project.hasProperty("signForPublish")) {
    signing {
        sign(publishing.publications["maven"])
    }
}

// Require signing keys only for publish tasks other than publishToMavenLocal
tasks.matching { it.name.startsWith("publish") && it.name != "publishToMavenLocal" && it.project == project }.configureEach {
    doFirst {
        if (!project.hasProperty("signing.keyId") || !project.hasProperty("signing.password") ||
            (!project.hasProperty("signing.secretKeyRingFile") && !project.hasProperty("signing.key"))) {
            throw GradleException(
                "Signing is required for Maven Central. Add to gradle.properties or use -PsignForPublish with signing.* properties.\n" +
                    "For mavenLocal only, use: ./gradlew publishToMavenLocal (no signing)."
            )
        }
    }
}
