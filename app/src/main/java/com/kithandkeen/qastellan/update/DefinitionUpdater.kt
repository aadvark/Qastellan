package com.kithandkeen.qastellan.update

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Small HTTP abstraction so the update logic can be tested without a network. */
interface Http {
    data class Response(val code: Int, val body: ByteArray, val etag: String?)

    fun get(url: String, etag: String? = null): Response
}

object UrlConnectionHttp : Http {
    private const val USER_AGENT = "Qastellan (+https://github.com/aadvark/Qastellan)"

    override fun get(url: String, etag: String?): Http.Response {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            etag?.let { setRequestProperty("If-None-Match", it) }
        }
        try {
            val code = conn.responseCode
            val body = if (code == 200) conn.inputStream.use { it.readBytes() } else ByteArray(0)
            return Http.Response(code, body, conn.getHeaderField("ETag"))
        } finally {
            conn.disconnect()
        }
    }
}

/** Where the installed definitions live, and which version is current. */
interface DefinitionState {
    var currentVersion: String?
    var manifestEtag: String?
}

/**
 * Checks for new definitions and installs them safely:
 * nothing replaces the current set until every file is downloaded and verified.
 */
class DefinitionUpdater(
    private val baseUrl: String,
    private val rootDir: File,
    private val state: DefinitionState,
    private val verifier: ManifestVerifier,
    private val http: Http = UrlConnectionHttp,
) {
    sealed class Result {
        object UpToDate : Result()
        data class Updated(val version: String) : Result()
        /** A new version exists but downloading now isn't allowed (for example on mobile data). */
        data class Waiting(val version: String) : Result()
        data class Failed(val reason: String) : Result()
    }

    fun currentDir(): File? = state.currentVersion?.let { File(rootDir, it) }?.takeIf { it.isDirectory }

    /** @param allowDownload false = only check (the startup check on mobile data with "Wi-Fi only" on). */
    fun update(allowDownload: Boolean): Result {
        val manifestResp = runCatching { http.get(baseUrl + "manifest.json", state.manifestEtag.takeIf { currentDir() != null }) }
            .getOrElse { return Result.Failed("network: ${it.message}") }
        if (manifestResp.code == 304) return Result.UpToDate
        if (manifestResp.code != 200) return Result.Failed("manifest HTTP ${manifestResp.code}")

        val sigResp = runCatching { http.get(baseUrl + "manifest.sig") }.getOrElse { return Result.Failed("network: ${it.message}") }
        if (sigResp.code != 200 || !verifier.isSigned(manifestResp.body, sigResp.body)) {
            return Result.Failed("manifest signature is not valid")
        }
        val manifest = runCatching { ManifestVerifier.parse(manifestResp.body.decodeToString()) }
            .getOrElse { return Result.Failed("manifest unreadable") }
        if (manifest.format != ManifestVerifier.SUPPORTED_FORMAT) return Result.Failed("unsupported format ${manifest.format}")
        if (!SAFE_VERSION.matches(manifest.version)) return Result.Failed("bad version name")

        if (manifest.version == state.currentVersion && currentDir() != null) {
            state.manifestEtag = manifestResp.etag
            return Result.UpToDate
        }
        if (!allowDownload) return Result.Waiting(manifest.version)

        val staging = File(rootDir, ".staging-${manifest.version}").apply { deleteRecursively(); mkdirs() }
        try {
            for (file in manifest.files) {
                val resp = http.get(baseUrl + file.name)
                if (resp.code != 200) return Result.Failed("${file.name}: HTTP ${resp.code}")
                if (resp.body.size.toLong() != file.size || ManifestVerifier.sha256Hex(resp.body) != file.sha256) {
                    return Result.Failed("${file.name} does not match the signed manifest")
                }
                File(staging, file.name).writeBytes(resp.body)
            }
            val target = File(rootDir, manifest.version)
            target.deleteRecursively()
            if (!staging.renameTo(target)) return Result.Failed("could not install files")

            val old = state.currentVersion
            state.currentVersion = manifest.version
            state.manifestEtag = manifestResp.etag
            if (old != null && old != manifest.version) File(rootDir, old).deleteRecursively()
            return Result.Updated(manifest.version)
        } catch (e: Exception) {
            return Result.Failed("download: ${e.message}")
        } finally {
            staging.deleteRecursively()
        }
    }

    companion object {
        private val SAFE_VERSION = Regex("^[0-9A-Za-z._-]{1,40}$")
    }
}
