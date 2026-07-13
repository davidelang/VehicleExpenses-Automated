package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages a per-destination rclone `onedrive` remote (user never types the remote name).
 * Tokens come from MSAL — never logged.
 */
@Singleton
class RcloneOneDriveSetup @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val runtime: RcloneRuntime,
    private val msAuth: MicrosoftOneDriveAuth,
) {
    fun managedRemoteName(destId: String): String = "od_${destId.take(8).lowercase()}"

    suspend fun applyAuthToDestination(
        destId: String,
        auth: MicrosoftAuthResult,
        pathPrefix: String = DEFAULT_PATH_PREFIX,
    ): RcloneDestConfig {
        val remoteName = managedRemoteName(destId)
        val config = RcloneDestConfig(remote = remoteName, pathPrefix = pathPrefix.trim())
        runtime.ensureConfigForDest(destId, config)
        val remotes = runtime.listRemotes(destId, config)
        val params = buildOneDriveParams(auth)
        if (remotes.any { it.equals(remoteName, ignoreCase = true) }) {
            runtime.updateRemote(destId, config, remoteName, params)
        } else {
            val step = runtime.createRemote(
                destId = destId,
                config = config,
                name = remoteName,
                type = "onedrive",
                parameters = params,
            )
            if (!step.complete) {
                throw RcloneException("OneDrive remote setup incomplete — try signing in again")
            }
        }
        return config
    }

    suspend fun refreshTokenIfPossible(destId: String, config: RcloneDestConfig, accountHint: String?) {
        val refreshed = msAuth.refreshSilent(accountHint) ?: return
        runtime.ensureConfigForDest(destId, config)
        runtime.updateRemote(
            destId = destId,
            config = config,
            name = config.remote,
            parameters = buildOneDriveParams(refreshed),
        )
    }

    private fun buildOneDriveParams(auth: MicrosoftAuthResult): Map<String, String> = buildMap {
        put("token", auth.tokenJsonForRclone)
        put("drive_id", auth.driveId)
        put("drive_type", auth.driveType)
        put("disable_site_permission", "true")
    }

    companion object {
        const val DEFAULT_PATH_PREFIX = "VehicleExpenses/photos"
        const val SESSION_EXPIRED_MESSAGE =
            "OneDrive session expired — open Photo Backup and Sign in with Microsoft again"
    }
}