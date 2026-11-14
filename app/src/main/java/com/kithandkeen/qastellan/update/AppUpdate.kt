package com.kithandkeen.qastellan.update

/** Result of the app-version check that runs on every start. */
sealed class AppUpdate {
    object Current : AppUpdate()
    data class Available(val version: String, val whereToGet: String) : AppUpdate()
    object Unknown : AppUpdate()
}

/** Each distribution (F-Droid, GitHub) has its own way to find the newest version. */
interface AppUpdateSource {
    fun check(currentVersionCode: Int, currentVersionName: String, http: Http): AppUpdate
}

/** Compares "1.2.10" style versions numerically; a leading "v" is ignored. */
fun isNewer(candidate: String, current: String): Boolean {
    fun parts(v: String) = v.trim().removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
    val a = parts(candidate)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
