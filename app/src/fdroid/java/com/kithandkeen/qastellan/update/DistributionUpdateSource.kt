package com.kithandkeen.qastellan.update

import com.kithandkeen.qastellan.BuildConfig
import org.json.JSONObject

/**
 * F-Droid build: F-Droid installs updates itself, so the app only checks
 * F-Droid's public API and tells the user an update is waiting there.
 */
object DistributionUpdateSource : AppUpdateSource {
    override fun check(currentVersionCode: Int, currentVersionName: String, http: Http): AppUpdate {
        val resp = runCatching { http.get("https://f-droid.org/api/v1/packages/${BuildConfig.APPLICATION_ID}") }
            .getOrElse { return AppUpdate.Unknown }
        if (resp.code != 200) return AppUpdate.Unknown
        val json = runCatching { JSONObject(resp.body.decodeToString()) }.getOrElse { return AppUpdate.Unknown }
        val suggested = json.optInt("suggestedVersionCode", 0)
        return if (suggested > currentVersionCode) {
            AppUpdate.Available(suggested.toString(), "https://f-droid.org/packages/${BuildConfig.APPLICATION_ID}/")
        } else {
            AppUpdate.Current
        }
    }
}
