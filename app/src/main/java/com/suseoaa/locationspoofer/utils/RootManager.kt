package com.suseoaa.locationspoofer.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class RootManager {

    suspend fun checkRootAccess(): Boolean = withContext(Dispatchers.IO) {
        val hasRoot = executeCommand("id").contains("uid=0(root)")
        if (hasRoot) {
            applyRootBackgroundExemptions()
        }
        hasRoot
    }

    suspend fun applyRootBackgroundExemptions(packageName: String = "com.suseoaa.locationspoofer"): Boolean =
        withContext(Dispatchers.IO) {
            val cmds = """
            chmod 777 /data/local/tmp 2>/dev/null || true
            chmod 755 /data/local 2>/dev/null || true
            dumpsys deviceidle whitelist +$packageName 2>/dev/null || true
            cmd appops set $packageName RUN_IN_BACKGROUND allow 2>/dev/null || true
            cmd appops set $packageName RUN_ANY_IN_BACKGROUND allow 2>/dev/null || true
            cmd appops set $packageName WAKE_LOCK allow 2>/dev/null || true
            cmd appops set $packageName AUTO_REVOKE_PERMISSIONS_IF_UNUSED ignore 2>/dev/null || true
            am set-standby-bucket $packageName active 2>/dev/null || true
        """.trimIndent()
            val result = executeCommand(cmds)
            result != "ERROR"
        }

    suspend fun grantMockLocation(): Boolean = withContext(Dispatchers.IO) {
        val result =
            executeCommand("appops set com.suseoaa.locationspoofer android:mock_location allow")
        result != "ERROR"
    }

    suspend fun revokeMockLocation(): Boolean = withContext(Dispatchers.IO) {
        val result =
            executeCommand("appops set com.suseoaa.locationspoofer android:mock_location default")
        result != "ERROR"
    }

    fun executeCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            output.ifEmpty { "SUCCESS" }
        } catch (e: Exception) {
            "ERROR"
        }
    }

    fun executeCommandWithInput(command: String, input: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            process.outputStream.bufferedWriter().use { writer ->
                writer.write(input)
                writer.flush()
            }
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            output.ifEmpty { "SUCCESS" }
        } catch (e: Exception) {
            "ERROR"
        }
    }
}
