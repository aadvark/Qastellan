package com.kithandkeen.qastellan.update

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** One file listed in manifest.json. */
data class ManifestFile(val name: String, val sha256: String, val size: Long)

data class Manifest(val format: Int, val version: String, val files: List<ManifestFile>)

/**
 * Checks that a definitions manifest was signed by Qastellan's pipeline and
 * that every downloaded file matches the hash the manifest lists for it.
 */
class ManifestVerifier(publicKeyBase64: String) {
    /** Null until a real key is configured; then every manifest is rejected (fails safe). */
    private val publicKey = runCatching {
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64.trim())))
    }.getOrNull()

    val hasKey: Boolean get() = publicKey != null

    fun isSigned(manifestBytes: ByteArray, signature: ByteArray): Boolean = publicKey != null && try {
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(manifestBytes)
            verify(signature)
        }
    } catch (e: Exception) {
        false
    }

    companion object {
        const val SUPPORTED_FORMAT = 1

        /** Allowed file names; anything else in a manifest is ignored, never written to disk. */
        val KNOWN_FILES = setOf("md5.qhs", "sha1.qhs", "sha256.qhs", "names.txt", "packages.txt", "trackers.txt")

        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        /** Minimal parser for the manifest the pipeline writes (avoids a JSON library). */
        fun parse(json: String): Manifest {
            val obj = org.json.JSONObject(json)
            val filesObj = obj.getJSONObject("files")
            val files = filesObj.keys().asSequence()
                .filter { it in KNOWN_FILES }
                .map { name ->
                    val f = filesObj.getJSONObject(name)
                    ManifestFile(name, f.getString("sha256").lowercase(), f.getLong("size"))
                }
                .toList()
            return Manifest(obj.getInt("format"), obj.getString("version"), files)
        }
    }
}
