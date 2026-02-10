package io.modelaudit

import java.io.File

/**
 * Extracts the JSON object from raw process output that may contain log lines (e.g. WARNING, CRITICAL)
 * before or after the JSON. Returns the substring from the first `{` to the last `}` (inclusive).
 */
internal fun extractJsonFromOutput(raw: String): String {
    val trimmed = raw.trim()
    val start = trimmed.indexOf('{')
    if (start < 0) return trimmed
    val end = trimmed.lastIndexOf('}')
    if (end < start) return trimmed
    return trimmed.substring(start, end + 1)
}

/**
 * Runs ModelAudit scan on the given path (file or directory) and returns the result as JSON.
 * Uses the bundled executable from the JAR (Python + deps packaged via PyInstaller).
 *
 * @param path Absolute or relative path to a file or directory to scan.
 * @return JSON string of the scan result (same as `modelaudit scan <path> --format json`).
 * @throws ModelAuditException if the bundled binary is not in the JAR for this OS/arch, or if the process fails (exit code 2).
 */
fun scanToJson(path: String): String {
    val normalizedPath = File(path).absolutePath
    val executable: String = resolveBundledBinary()?.absolutePath
        ?: throw ModelAuditException(
            "Bundled modelaudit binary not found in JAR for this platform (${getBundledPlatformKey()}). " +
                "This JAR must be built with build_bundle.py so that io/modelaudit/bins/<platform>/ is included."
        )
    val processBuilder = ProcessBuilder(
        executable,
        "scan",
        normalizedPath,
        "--format", "json",
    ).redirectErrorStream(true)
    processBuilder.environment()["PYTHONIOENCODING"] = "utf-8"
    processBuilder.environment()["PYTHONUTF8"] = "1"

    val process = processBuilder.start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()

    when (exitCode) {
        0 -> return extractJsonFromOutput(output)
        1 -> return extractJsonFromOutput(output) // Security issues found; result is still valid JSON
        2 -> throw ModelAuditException("ModelAudit scan failed (exit $exitCode): $output")
        else -> throw ModelAuditException("ModelAudit exited with code $exitCode: $output")
    }
}

/**
 * Runs ModelAudit scan on the given folder (or file) and returns the result as JSON.
 * Convenience alias for [scanToJson]; supports both directories and single files.
 */
fun scanFolder(folderPath: String): String = scanToJson(folderPath)

/**
 * Exception thrown when the ModelAudit process fails (e.g. missing command, scan error).
 */
class ModelAuditException(message: String) : RuntimeException(message)
