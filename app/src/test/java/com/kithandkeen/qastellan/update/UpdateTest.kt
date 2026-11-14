package com.kithandkeen.qastellan.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class UpdateTest {
    private val keys: KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val publicB64 = Base64.getEncoder().encodeToString(keys.public.encoded)

    private fun sign(bytes: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run { initSign(keys.private); update(bytes); sign() }

    private class MemoryState : DefinitionState {
        override var currentVersion: String? = null
        override var manifestEtag: String? = null
    }

    /** Serves files from a map, like the GitHub release would. */
    private class FakeHttp(val files: MutableMap<String, ByteArray>) : Http {
        val requests = mutableListOf<String>()
        override fun get(url: String, etag: String?): Http.Response {
            val name = url.substringAfterLast('/')
            requests += name
            val body = files[name] ?: return Http.Response(404, ByteArray(0), null)
            val tag = "\"${body.contentHashCode()}\""
            return if (etag == tag) Http.Response(304, ByteArray(0), tag) else Http.Response(200, body, tag)
        }
    }

    private fun release(version: String, tamper: Boolean = false): MutableMap<String, ByteArray> {
        val names = "Qastellan.Test.EICAR\n".toByteArray()
        val manifest = """
            {"format": 1, "version": "$version", "files": {
              "names.txt": {"sha256": "${ManifestVerifier.sha256Hex(names)}", "size": ${names.size}, "records": 1},
              "../../evil.sh": {"sha256": "00", "size": 1, "records": 0}
            }}
        """.trimIndent().toByteArray()
        return mutableMapOf(
            "manifest.json" to manifest,
            "manifest.sig" to sign(manifest),
            "names.txt" to if (tamper) "tampered\n".toByteArray() else names,
        )
    }

    private fun updater(dir: File, http: Http, state: DefinitionState = MemoryState(), key: String = publicB64) =
        DefinitionUpdater("https://example.org/defs/", dir, state, ManifestVerifier(key), http)

    @Test
    fun `installs a correctly signed release, then reports up to date`() {
        val dir = createTempDir()
        val state = MemoryState()
        val http = FakeHttp(release("2026.11.15"))
        val u = updater(dir, http, state)

        assertEquals(DefinitionUpdater.Result.Updated("2026.11.15"), u.update(allowDownload = true))
        assertEquals("Qastellan.Test.EICAR\n", File(dir, "2026.11.15/names.txt").readText())
        assertFalse("unknown file names are never written", File(dir, "evil.sh").exists())

        http.requests.clear()
        assertEquals(DefinitionUpdater.Result.UpToDate, u.update(allowDownload = true))
        assertEquals(listOf("manifest.json"), http.requests) // a 304, nothing else downloaded
        dir.deleteRecursively()
    }

    @Test
    fun `startup check on mobile data only reports that an update is waiting`() {
        val dir = createTempDir()
        val result = updater(dir, FakeHttp(release("2026.11.16"))).update(allowDownload = false)
        assertEquals(DefinitionUpdater.Result.Waiting("2026.11.16"), result)
        assertTrue(dir.listFiles()!!.isEmpty())
        dir.deleteRecursively()
    }

    @Test
    fun `rejects a manifest signed with another key`() {
        val dir = createTempDir()
        val other = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val result = updater(dir, FakeHttp(release("1")), key = Base64.getEncoder().encodeToString(other.public.encoded))
            .update(allowDownload = true)
        assertTrue(result is DefinitionUpdater.Result.Failed)
        dir.deleteRecursively()
    }

    @Test
    fun `rejects a file that does not match the manifest and keeps the old set`() {
        val dir = createTempDir()
        val state = MemoryState()
        updater(dir, FakeHttp(release("1")), state).update(allowDownload = true)

        val result = updater(dir, FakeHttp(release("2", tamper = true)), state).update(allowDownload = true)
        assertTrue(result is DefinitionUpdater.Result.Failed)
        assertEquals("1", state.currentVersion)
        assertTrue(File(dir, "1/names.txt").exists())
        dir.deleteRecursively()
    }

    @Test
    fun `without a configured key every update fails safely`() {
        val dir = createTempDir()
        val result = updater(dir, FakeHttp(release("1")), key = "REPLACE_WITH_PUBLIC_KEY").update(allowDownload = true)
        assertTrue(result is DefinitionUpdater.Result.Failed)
        dir.deleteRecursively()
    }

    @Test
    fun `version comparison`() {
        assertTrue(isNewer("v0.2.0", "0.1.9"))
        assertTrue(isNewer("1.10.0", "1.9.3"))
        assertFalse(isNewer("1.0", "1.0.0"))
        assertFalse(isNewer("0.9", "1.0"))
    }
}
