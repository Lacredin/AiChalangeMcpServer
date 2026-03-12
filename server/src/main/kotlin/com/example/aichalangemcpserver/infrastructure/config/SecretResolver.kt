package com.example.aichalangemcpserver.infrastructure.config

import java.io.File

class SecretResolver(
    private val workingDir: File = File(System.getProperty("user.dir"))
) {
    fun get(vararg keys: String): String? {
        keys.forEach { key ->
            val fromEnv = System.getenv(key)
            if (!fromEnv.isNullOrBlank()) return fromEnv
        }

        val secretFile = discoverSecretFile() ?: return null
        val valuesByKey = secretFile.readLines()
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
            .map { line ->
                val idx = line.indexOf('=')
                line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }
            .toMap()

        return keys.firstNotNullOfOrNull { key -> valuesByKey[key]?.takeIf { it.isNotBlank() } }
    }

    private fun discoverSecretFile(): File? {
        var dir: File? = workingDir
        repeat(4) {
            val candidate = File(dir, ".secrets.env")
            if (candidate.exists()) return candidate
            dir = dir?.parentFile
        }
        return null
    }
}
