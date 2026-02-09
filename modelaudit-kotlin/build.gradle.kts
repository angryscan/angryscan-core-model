plugins {
    id("org.jetbrains.kotlin.jvm")
    id("maven-publish")
}

tasks.test {
    useJUnitPlatform()
    testLogging.showStandardStreams = true
}

group = "io.github.gammmaaaa"
version = "0.2.24-test"

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

val buildBundleSync = tasks.register<Exec>("buildBundleSync") {
    group = "modelaudit"
    description = "Run uv sync --extra bundle from repo root"
    workingDir = repoRoot
    commandLine("uv", "sync", "--extra", "bundle")
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
    commandLine("uv", "run", "python", "scripts/build_bundle.py")
    inputs.files(
        rootProject.file("pyproject.toml"),
        rootProject.file("uv.lock"),
        rootProject.file("scripts/build_bundle.py"),
        rootProject.file("scripts/standalone_entry.py"),
    )
    outputs.dir(layout.projectDirectory.dir("src/main/resources/io/modelaudit/bins"))
}

val requireBundle = tasks.register("requireBundle") {
    doLast {
        val binsDir = layout.projectDirectory.dir("src/main/resources/io/modelaudit/bins")
        if (!binsDir.asFile.exists()) {
            throw GradleException(
                "Bundled binary missing. From repo root run: uv sync --extra bundle && uv run python scripts/build_bundle.py " +
                    "then rebuild. The JAR must include io/modelaudit/bins/<platform>/modelaudit."
            )
        }
        val hasBundle = binsDir.asFile.listFiles()?.any { platformDir ->
            platformDir.isDirectory && (platformDir.resolve("modelaudit").isFile || platformDir.resolve("modelaudit.exe").isFile)
        } ?: false
        if (!hasBundle) {
            throw GradleException(
                "No bundled binary under src/main/resources/io/modelaudit/bins/<platform>/. " +
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

// Publish (Maven Central and mavenLocal) only with bundle; no way to publish without it.
tasks.matching { it.name.startsWith("publish") }.configureEach {
    dependsOn(requireBundle)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "modelaudit-kotlin"
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
        maven {
            name = "Central"
            url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            credentials {
                username = project.findProperty("sonatypeUsername") as String? ?: ""
                password = project.findProperty("sonatypePassword") as String? ?: ""
            }
        }
    }
}
