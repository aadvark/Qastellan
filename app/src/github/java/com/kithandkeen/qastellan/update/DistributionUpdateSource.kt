package com.kithandkeen.qastellan.update

import com.kithandkeen.qastellan.BuildConfig
import org.json.JSONObject

/** GitHub build: asks GitHub for the latest release (free, no server of our own). */
object DistributionUpdateSource : AppUpdateSource {
    override fun check(currentVersionCode: Int, currentVersionName: String, http: Http): AppUpdate {
        val resp = runCatching { http.get("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest") }
            .getOrElse { return AppUpdate.Unknown }
        if (resp.code != 200) return AppUpdate.Unknown
        val json = runCatching { JSONObject(resp.body.decodeToString()) }.getOrElse { return AppUpdate.Unknown }
        val tag = json.optString("tag_name")
        if (tag.isBlank() || tag == "definitions") return AppUpdate.Unknown
        return if (isNewer(tag, currentVersionName)) {
            AppUpdate.Available(tag.removePrefix("v"), json.optString("html_url"))
        } else {
            AppUpdate.Current
        }
    }
}
