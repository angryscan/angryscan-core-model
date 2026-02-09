package io.modelaudit

import java.io.File
import java.io.InputStream
import java.util.Locale

private const val RESOURCE_PREFIX = "io/modelaudit/bins"

/**
 * Resolves the platform key used for bundled binaries (e.g. "linux-x64", "macos-aarch64").
 */
fun getBundledPlatformKey(): String {
    val os = System.getProperty("os.name").lowercase(Locale.US)
    val arch = System.getProperty("os.arch").lowercase(Locale.US)
    val osKey = when {
        os.contains("linux") -> "linux"
        os.contains("mac") || os.contains("darwin") -> "macos"
        os.contains("windows") -> "windows"
        else -> return "unknown-unknown"
    }
    val archKey = when (arch) {
        "amd64", "x86_64", "x64" -> "x64"
        "aarch64", "arm64" -> "aarch64"
        "arm", "armv7l" -> "arm32"
        else -> arch
    }
    return "$osKey-$archKey"
}

/**
 * Executable name for the current platform (modelaudit or modelaudit.exe).
 */
fun getBundledExecutableName(): String =
    if (System.getProperty("os.name").lowercase(Locale.US).contains("windows")) "modelaudit.exe"
    else "modelaudit"

/**
 * Returns the path of the bundled binary for the current platform, or null if not in classpath.
 * The binary is extracted to a temp file (executable); caller is responsible for cleanup or reuse.
 */
fun resolveBundledBinary(): File? {
    val platformKey = getBundledPlatformKey()
    if (platformKey == "unknown-unknown") return null
    val execName = getBundledExecutableName()
    val resourcePath = "$RESOURCE_PREFIX/$platformKey/$execName"
    val stream =
        ModelAuditException::class.java.classLoader.getResourceAsStream(resourcePath) ?: return null
    val tempFile = File.createTempFile("modelaudit-", "-$execName").apply {
        deleteOnExit()
    }
    stream.use { input ->
        tempFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    tempFile.setExecutable(true, false)
    return tempFile
}
