package com.davidlang.vehicleexpensesautomated.data.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** S3 provider presets for the simplified top-tier form (rclone `provider` field). */
enum class S3ProviderPreset(val rcloneValue: String, val label: String) {
    AWS("AWS", "AWS"),
    WASABI("Wasabi", "Wasabi"),
    CLOUDFLARE_R2("Cloudflare", "Cloudflare R2"),
    MINIO("Minio", "MinIO"),
    OTHER("Other", "Other S3-compatible"),
}

/**
 * Manages a per-destination rclone `s3` remote (user never picks type=s3 from Other).
 * Credentials live in app-private conf — never logged.
 */
@Singleton
class RcloneS3Setup @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val runtime: RcloneRuntime,
) {
    fun managedRemoteName(destId: String): String = "s3_${destId.take(8).lowercase()}"

    suspend fun applyCredentialsToDestination(
        destId: String,
        accessKeyId: String,
        secretAccessKey: String,
        region: String,
        endpoint: String,
        bucket: String,
        pathPrefix: String,
        providerPreset: S3ProviderPreset,
    ): RcloneDestConfig {
        val remoteName = managedRemoteName(destId)
        val config = RcloneDestConfig(
            remote = remoteName,
            pathPrefix = combineBucketAndPrefix(bucket, pathPrefix),
        )
        runtime.ensureConfigForDest(destId, config)
        val remotes = runtime.listRemotes(destId, config)
        val params = buildS3Params(
            accessKeyId = accessKeyId,
            secretAccessKey = secretAccessKey,
            region = region,
            endpoint = endpoint,
            providerPreset = providerPreset,
        )
        if (remotes.any { it.equals(remoteName, ignoreCase = true) }) {
            runtime.updateRemote(destId, config, remoteName, params)
        } else {
            val step = runtime.createRemote(
                destId = destId,
                config = config,
                name = remoteName,
                type = "s3",
                parameters = params,
            )
            if (!step.complete) {
                throw RcloneException("S3 remote setup incomplete — check keys, region, and endpoint")
            }
        }
        return config
    }

    fun splitBucketAndPrefix(fullPrefix: String): Pair<String, String> {
        val trimmed = fullPrefix.trim().trim('/')
        if (trimmed.isBlank()) return "" to DEFAULT_PATH_PREFIX
        val slash = trimmed.indexOf('/')
        return if (slash < 0) {
            trimmed to ""
        } else {
            trimmed.substring(0, slash) to trimmed.substring(slash + 1)
        }
    }

    private fun combineBucketAndPrefix(bucket: String, pathPrefix: String): String {
        val trimmedBucket = bucket.trim()
        val trimmedPrefix = pathPrefix.trim().trim('/')
        return when {
            trimmedBucket.isBlank() -> trimmedPrefix
            trimmedPrefix.isBlank() -> trimmedBucket
            else -> "$trimmedBucket/$trimmedPrefix"
        }
    }

    private fun buildS3Params(
        accessKeyId: String,
        secretAccessKey: String,
        region: String,
        endpoint: String,
        providerPreset: S3ProviderPreset,
    ): Map<String, String> = buildMap {
        put("provider", providerPreset.rcloneValue)
        put("access_key_id", accessKeyId.trim())
        put("secret_access_key", secretAccessKey.trim())
        val trimmedRegion = region.trim()
        if (trimmedRegion.isNotBlank()) put("region", trimmedRegion)
        val trimmedEndpoint = endpoint.trim()
        if (trimmedEndpoint.isNotBlank()) put("endpoint", trimmedEndpoint)
        put("no_check_bucket", "true")
    }

    companion object {
        const val DEFAULT_PATH_PREFIX = "VehicleExpenses/photos"
    }
}