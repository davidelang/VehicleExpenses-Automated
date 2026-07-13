package com.davidlang.vehicleexpensesautomated.data.sync

import org.json.JSONObject

/** Per-destination rclone settings stored in [PhotoDestination.configJson]. */
data class RcloneDestConfig(
    val remote: String,
    val pathPrefix: String,
    val confFileName: String = "rclone.conf",
) {
    fun toJson(): String = JSONObject().apply {
        put("remote", remote)
        put("pathPrefix", pathPrefix)
        put("confFileName", confFileName)
    }.toString()

    /** rclone fs string e.g. `mys3:VehicleExpenses/photos` */
    fun remoteFs(): String {
        val prefix = pathPrefix.trim().trim('/')
        return if (prefix.isBlank()) "${remote.trim()}:" else "${remote.trim()}:$prefix"
    }

    companion object {
        fun parse(json: String?): RcloneDestConfig? {
            if (json.isNullOrBlank()) return null
            return try {
                val obj = JSONObject(json)
                val remote = obj.optString("remote", "").trim()
                if (remote.isBlank()) return null
                RcloneDestConfig(
                    remote = remote,
                    pathPrefix = obj.optString("pathPrefix", "").trim(),
                    confFileName = obj.optString("confFileName", "rclone.conf").ifBlank { "rclone.conf" },
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}