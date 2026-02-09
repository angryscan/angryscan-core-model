package io.modelaudit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

/**
 * Resolves path to the unsafe PyTorch ZIP test asset.
 * Tries, in order: tests/assets/samples/pytorch/unsafe_zip_pytorch.pt,
 * tests/assets/samples/pytorch/malicious_eval.pt, or the same paths relative to repo root
 * when running from modelaudit-kotlin (parent dir).
 */
fun resolveUnsafePytorchPath(): File? {
    val candidates = listOf(
        File("tests/assets/samples/pytorch/unsafe_zip_pytorch.pt"),
        File("tests/assets/samples/pytorch/malicious_eval.pt"),
        File("../tests/assets/samples/pytorch/unsafe_zip_pytorch.pt"),
        File("../tests/assets/samples/pytorch/malicious_eval.pt"),
    )
    return candidates.firstOrNull { it.isFile }
}

class ModelAuditTest {

    @Test
    fun `scanToJson returns valid JSON for unsafe PyTorch zip`() {
        assumeTrue(resolveBundledBinary() != null) {
            "Bundled binary not in classpath. Run build_bundle.py then ./gradlew :modelaudit-kotlin:jar before test."
        }
        val path = resolveUnsafePytorchPath()
        assumeTrue(path != null) {
            "Unsafe PyTorch test asset not found. Add tests/assets/samples/pytorch/malicious_eval.pt " +
                "or unsafe_zip_pytorch.pt (run from repo root: ./gradlew :modelaudit-kotlin:test)"
        }

        val json = scanToJson(path!!.absolutePath)

        assertTrue(json.isNotBlank())
        assertTrue(json.trimStart().startsWith("{"))
        assertTrue(json.contains("results") || json.contains("issues") || json.contains("assets"))
    }

    @Test
    fun `scanToJson on unsafe PyTorch zip reports at least one issue`() {
        assumeTrue(resolveBundledBinary() != null) {
            "Bundled binary not in classpath. Run build_bundle.py then ./gradlew :modelaudit-kotlin:jar before test."
        }
        val path = resolveUnsafePytorchPath()
        assumeTrue(path != null) {
            "Unsafe PyTorch test asset not found. Run from repo root: ./gradlew :modelaudit-kotlin:test"
        }

        val json = scanToJson(path!!.absolutePath)

        assertTrue(
            json.contains("\"issues\"") && json.contains("CRITICAL") ||
                json.contains("\"level\"") && json.contains("critical", ignoreCase = true) ||
                json.contains("eval") || json.contains("malicious"),
            "Expected scan result to contain security issues (unsafe model). JSON snippet: ${json.take(500)}"
        )
    }

    @Test
    fun `scanFolder is equivalent to scanToJson for a file path`() {
        assumeTrue(resolveBundledBinary() != null) { "Bundled binary not in classpath." }
        val path = resolveUnsafePytorchPath()
        assumeTrue(path != null) { "Unsafe PyTorch test asset not found." }

        val fromScanFolder = scanFolder(path!!.absolutePath)
        val fromScanToJson = scanToJson(path.absolutePath)

        assertEquals(fromScanToJson, fromScanFolder)
    }

    @Test
    fun `scanToJson throws ModelAuditException when path does not exist`() {
        assumeTrue(resolveBundledBinary() != null) { "Bundled binary not in classpath; test skipped." }
        val badPath = File("/nonexistent/model_${System.nanoTime()}.pt").absolutePath

        val ex = assertThrows<ModelAuditException> {
            scanToJson(badPath)
        }

        val msg = ex.message ?: ""
        assertTrue(msg.contains("exit") || msg.contains("failed", ignoreCase = true))
    }
}
