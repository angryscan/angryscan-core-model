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
version = "0.1.1"

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

// Resolve full path to "uv" so bundle tasks work when Gradle runs from IDE (no Homebrew in PATH)
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
    val searchDirs = listOf(
        "/opt/homebrew/bin",
        "/usr/local/bin",
        System.getenv("HOME")?.let { "$it/.local/bin" },
        System.getenv("HOME")?.let { "$it/.cargo/bin" },
    ).filterNotNull() + (System.getenv("PATH")?.split(pathSeparator)?.filter { it.isNotBlank() } ?: emptyList())
    for (dir in searchDirs.distinct()) {
        val candidate = project.file(dir).resolve("uv")
        if (candidate.isFile && candidate.canExecute()) return candidate.absolutePath
    }
    throw GradleException(
        "uv executable not found. Searched: /opt/homebrew/bin, /usr/local/bin, \$HOME/.local/bin, \$HOME/.cargo/bin, and PATH. " +
            "Install with: brew install uv (or set uvPath=/path/to/uv or UV_PATH env var)."
    )
}

val uvExecutable = project.provider { findUvExecutable() }

val buildBundleSync = tasks.register<Exec>("buildBundleSync") {
    group = "modelaudit"
    description = "Run uv sync --extra bundle from repo root"
    workingDir = repoRoot
    commandLine(uvExecutable, "sync", "--extra", "bundle")
    inputs.files(
        rootProject.file("pyproject.toml"),
        rootProject.file("uv.lock"),
    )
    outputs.dir(rootProject.file(".venv"))
}

val buildBundle = tasks.register<Exec>("buildBundle") {
    group = "modelaudit"
    description = "Build PyInstaller bundle (uv sync --extra bundle + scripts/build_bundle.py) from repo root"
    dependsOn(buildBundleSync)
    workingDir = repoRoot
    commandLine(uvExecutable, "run", "python", "scripts/build_bundle.py")
    inputs.files(
        rootProject.file("pyproject.toml"),
        rootProject.file("uv.lock"),
        rootProject.file("scripts/build_bundle.py"),
        rootProject.file("scripts/standalone_entry.py"),
    )
    // Do not declare outputs.dir() on src/main/resources/... — Gradle disallows task outputs inside source sets
}

val skipBundleBuild = project.hasProperty("skipBundleBuild")

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

// Sign for Maven Central — required for Sonatype/Maven Central
signing {
    sign(publishing.publications["maven"])
}

// Fail early if signing keys are missing (Gradle reads gradle.properties from project root or ~/.gradle/, NOT from .gradle/)
tasks.matching { it.name.startsWith("publish") && it.project == project }.configureEach {
    doFirst {
        if (!project.hasProperty("signing.keyId") || !project.hasProperty("signing.password") ||
            (!project.hasProperty("signing.secretKeyRingFile") && !project.hasProperty("signing.key"))) {
            throw GradleException(
                "Signing is required for Maven Central. Add to gradle.properties in project root or ~/.gradle/gradle.properties:\n" +
                    "  signing.keyId=<your-key-id>\n" +
                    "  signing.password=<key-passphrase>\n" +
                    "  signing.secretKeyRingFile=<path-to-exported-secret-key>\n" +
                    "Note: Gradle does NOT read .gradle/gradle.properties. secretKeyRingFile must be the keyring file (e.g. from: gpg --export-secret-keys KEY_ID > secring.gpg), not the .rev file."
            )
        }
    }
}
