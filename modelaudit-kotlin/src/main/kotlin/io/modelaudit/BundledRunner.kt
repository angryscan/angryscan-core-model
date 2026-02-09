package io.modelaudit

import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream

private const val RESOURCE_PREFIX = "io/modelaudit/bins"
private const val BUNDLE_ZIP = "modelaudit.zip"
private const val BUNDLE_DIR_NAME = "modelaudit"

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
 * The bundle is a zip (onedir output); it is extracted to a temp dir and the exe path is returned.
 */
fun resolveBundledBinary(): File? {
    val platformKey = getBundledPlatformKey()
    if (platformKey == "unknown-unknown") return null
    val execName = getBundledExecutableName()
    val resourcePath = "$RESOURCE_PREFIX/$platformKey/$BUNDLE_ZIP"
    val stream =
        ModelAuditException::class.java.classLoader.getResourceAsStream(resourcePath) ?: return null
    val extractDir = File(System.getProperty("java.io.tmpdir"), "modelaudit-$platformKey")
    val exeFile = File(extractDir, "$BUNDLE_DIR_NAME/$execName")
    if (exeFile.isFile) return exeFile.also { it.setExecutable(true, false) }
    extractDir.mkdirs()
    stream.use { unzip(it, extractDir) }
    if (!exeFile.isFile) return null
    exeFile.setExecutable(true, false)
    return exeFile
}

private fun unzip(input: InputStream, destDir: File) {
    ZipInputStream(input).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            val file = File(destDir, entry.name)
            if (entry.isDirectory) {
                file.mkdirs()
            } else {
                file.parentFile?.mkdirs()
                file.outputStream().use { zis.copyTo(it) }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }
}
