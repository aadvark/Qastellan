package com.kithandkeen.qastellan.engine

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

class FileHashes(val md5: ByteArray, val sha1: ByteArray, val sha256: ByteArray) {
    fun sha256Hex(): String = sha256.toHex()
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/** Computes MD5, SHA-1 and SHA-256 in a single pass, so each file is read once. */
object Hasher {
    private const val BUFFER = 64 * 1024

    fun hash(input: InputStream): FileHashes {
        val md5 = MessageDigest.getInstance("MD5")
        val sha1 = MessageDigest.getInstance("SHA-1")
        val sha256 = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(BUFFER)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md5.update(buf, 0, n)
            sha1.update(buf, 0, n)
            sha256.update(buf, 0, n)
        }
        return FileHashes(md5.digest(), sha1.digest(), sha256.digest())
    }

    fun hash(file: File): FileHashes = file.inputStream().buffered(BUFFER).use(::hash)
}
