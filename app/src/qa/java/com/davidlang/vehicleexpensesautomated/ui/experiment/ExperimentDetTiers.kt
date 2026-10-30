package com.davidlang.vehicleexpensesautomated.ui.experiment

import android.content.Context
import android.util.Log
import com.baidu.paddle.lite.MobileConfig
import com.baidu.paddle.lite.PaddlePredictor
import com.baidu.paddle.lite.PowerMode
import com.davidlang.vehicleexpensesautomated.ui.util.NativePaddleEngine

/**
 * Replace **det tiers only** with an experiment nb under `exp_det_ab/` (scheduled:
 * `product_det` only). Rec models stay production. Call [restoreProductionDetTiers]
 * after the experiment column.
 *
 * @param assetBase name without arch/`.nb`, e.g. `product_det`
 */
fun NativePaddleEngine.Companion.loadExperimentDetTiers(context: Context, assetBase: String) {
    val arch = modelArchForPrimaryAbi()
    val asset = "paddle/exp_det_ab/${assetBase}_$arch.nb"
    val detPath = materializeModel(context, asset, "Missing experiment model $asset")
    Log.i("PaddleLite", "loadExperimentDetTiers base=$assetBase arch=$arch → $detPath")
    for ((scale, p) in sharedTiers.toList()) {
        releasePredictor(p, "swap_det_tier_$scale")
        sharedTiers.remove(scale)
    }
    val config = MobileConfig()
    config.setThreads(4)
    config.setPowerMode(PowerMode.LITE_POWER_NO_BIND)
    TIER_SCALES.forEach { scale ->
        config.setModelFromFile(detPath)
        val p = PaddlePredictor.createPaddlePredictor(config)
            ?: throw IllegalStateException("createPaddlePredictor exp det null scale=$scale")
        p.getInput(0).resize(longArrayOf(1, 1, scale.toLong(), scale.toLong()))
        sharedTiers[scale] = p
        sharedTiersInt8.getOrPut(scale) { ByteArray(1 * scale * scale) }
    }
    activeProductPathId = "exp_det_$assetBase"
    // activeProductDir left as production rec pack
}

/** Reload production det tiers (keeps current [activeProductDir] rec pack). */
fun NativePaddleEngine.Companion.restoreProductionDetTiers(context: Context) {
    val arch = modelArchForPrimaryAbi()
    val prodDir = activeProductDir
    val prodAsset = "paddle/$prodDir/det_$arch.nb"
    val detPath = materializeModel(context, prodAsset, "Missing production model $prodAsset")
    Log.i("PaddleLite", "restoreProductionDetTiers dir=$prodDir → $detPath")
    for ((scale, p) in sharedTiers.toList()) {
        releasePredictor(p, "restore_det_tier_$scale")
        sharedTiers.remove(scale)
    }
    val config = MobileConfig()
    config.setThreads(4)
    config.setPowerMode(PowerMode.LITE_POWER_NO_BIND)
    TIER_SCALES.forEach { scale ->
        config.setModelFromFile(detPath)
        val p = PaddlePredictor.createPaddlePredictor(config)
            ?: throw IllegalStateException("createPaddlePredictor prod det null scale=$scale")
        p.getInput(0).resize(longArrayOf(1, 1, scale.toLong(), scale.toLong()))
        sharedTiers[scale] = p
        sharedTiersInt8.getOrPut(scale) { ByteArray(1 * scale * scale) }
    }
    activeProductPathId = productPathIdForDir(prodDir)
}
