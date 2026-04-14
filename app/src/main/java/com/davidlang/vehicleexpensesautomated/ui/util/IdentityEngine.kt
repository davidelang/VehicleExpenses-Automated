package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Bitmap
import android.graphics.RectF
import com.davidlang.vehicleexpensesautomated.data.model.Vehicle

/**
 * Common interface for all vehicle identity and matching algorithms.
 */
interface IdentityEngine {
    val name: String

    /**
     * Determines if the query image matches the reference image/vehicle.
     * @return AlignmentResult containing the confidence score and metadata.
     */
    suspend fun identify(
        reference: Bitmap,
        query: Bitmap,
        refOcr: OcrResult,
        queryOcr: OcrResult,
        odometerCrop: RectF?,
        otherTextCrop: RectF?,
        vehicle: Vehicle,
        veto: VetoResult,
        isHardcodedWinner: Boolean
    ): AlignmentResult
}

/**
 * Feature-based (ORB) spatial identity matching.
 */
class FeatureIdentityEngine : IdentityEngine {
    override val name: String = "feature"

    override suspend fun identify(
        reference: Bitmap, query: Bitmap, refOcr: OcrResult, queryOcr: OcrResult,
        odometerCrop: RectF?, otherTextCrop: RectF?, vehicle: Vehicle, veto: VetoResult, isHardcodedWinner: Boolean
    ): AlignmentResult {
        return ImageAlignmentUtils.alignImages(reference, query, refOcr.textBlocks, queryOcr.textBlocks, vehicle).copy(method = "feature")
    }
}

/**
 * Argument/Landmark overlap matching.
 */
class ArgIdentityEngine : IdentityEngine {
    override val name: String = "arg"

    override suspend fun identify(
        reference: Bitmap, query: Bitmap, refOcr: OcrResult, queryOcr: OcrResult,
        odometerCrop: RectF?, otherTextCrop: RectF?, vehicle: Vehicle, veto: VetoResult, isHardcodedWinner: Boolean
    ): AlignmentResult {
        val score = ImageAlignmentUtils.argMatch(refOcr.textBlocks, queryOcr.textBlocks, odometerCrop, otherTextCrop, refOcr.imageWidth, refOcr.imageHeight)
        return AlignmentResult(true, null, score, "ARG", method = "arg")
    }
}

/**
 * Raw text embedding overlap matching.
 */
class EmbeddingIdentityEngine : IdentityEngine {
    override val name: String = "embedding"

    override suspend fun identify(
        reference: Bitmap, query: Bitmap, refOcr: OcrResult, queryOcr: OcrResult,
        odometerCrop: RectF?, otherTextCrop: RectF?, vehicle: Vehicle, veto: VetoResult, isHardcodedWinner: Boolean
    ): AlignmentResult {
        val score = ImageAlignmentUtils.embeddingMatch(refOcr.textBlocks, queryOcr.textBlocks)
        return AlignmentResult(true, null, score, "Emb", method = "embedding")
    }
}

/**
 * Weighted consensus of feature, arg, and embedding scores.
 */
class ConsensusIdentityEngine : IdentityEngine {
    override val name: String = "consensus"

    override suspend fun identify(
        reference: Bitmap, query: Bitmap, refOcr: OcrResult, queryOcr: OcrResult,
        odometerCrop: RectF?, otherTextCrop: RectF?, vehicle: Vehicle, veto: VetoResult, isHardcodedWinner: Boolean
    ): AlignmentResult {
        val feat = ImageAlignmentUtils.alignImages(reference, query, refOcr.textBlocks, queryOcr.textBlocks, vehicle).confidence
        val arg = ImageAlignmentUtils.argMatch(refOcr.textBlocks, queryOcr.textBlocks, odometerCrop, otherTextCrop, refOcr.imageWidth, refOcr.imageHeight)
        val emb = ImageAlignmentUtils.embeddingMatch(refOcr.textBlocks, queryOcr.textBlocks)
        val consensusScore = (feat * 0.10f) + (emb * 0.45f) + (arg * 0.45f)
        
        return AlignmentResult(
            success = true,
            alignedImage = null,
            confidence = if (veto.isVetoed) -1f else consensusScore,
            message = if (veto.isVetoed) "VETO: ${veto.reasonWord}" else "OK",
            method = "consensus",
            wordVeto = veto.isVetoed,
            vetoReason = veto.reasonWord
        )
    }
}

/**
 * Hierarchical identity matching logic.
 */
class TieredIdentityEngine : IdentityEngine {
    override val name: String = "tiered"

    override suspend fun identify(
        reference: Bitmap, query: Bitmap, refOcr: OcrResult, queryOcr: OcrResult,
        odometerCrop: RectF?, otherTextCrop: RectF?, vehicle: Vehicle, veto: VetoResult, isHardcodedWinner: Boolean
    ): AlignmentResult {
        val results = mutableMapOf<String, AlignmentResult>()
        results["feature"] = ImageAlignmentUtils.alignImages(reference, query, refOcr.textBlocks, queryOcr.textBlocks, vehicle)
        results["arg"] = AlignmentResult(true, null, ImageAlignmentUtils.argMatch(refOcr.textBlocks, queryOcr.textBlocks, odometerCrop, otherTextCrop, refOcr.imageWidth, refOcr.imageHeight), "ARG", method = "arg")
        results["embedding"] = AlignmentResult(true, null, ImageAlignmentUtils.embeddingMatch(refOcr.textBlocks, queryOcr.textBlocks), "Emb", method = "embedding")
        
        return ImageAlignmentUtils.calculateTieredMatch(results, veto)
    }
}

/**
 * Hardcoded ground truth override for testing.
 */
class HardcodedIdentityEngine : IdentityEngine {
    override val name: String = "hardcoded"

    override suspend fun identify(
        reference: Bitmap, query: Bitmap, refOcr: OcrResult, queryOcr: OcrResult,
        odometerCrop: RectF?, otherTextCrop: RectF?, vehicle: Vehicle, veto: VetoResult, isHardcodedWinner: Boolean
    ): AlignmentResult {
        val conf = if (isHardcodedWinner) 1.0f else -1.0f
        val msg = if (isHardcodedWinner) "Hardcoded Winner" else "Hardcoded Loser"
        return AlignmentResult(true, null, conf, msg, method = "hardcoded")
    }
}

/**
 * Registry for managing active identity engines.
 */
object IdentityRegistry {
    private val engines = mutableListOf<IdentityEngine>()

    init {
        setupDefaultEngines()
    }

    fun register(engine: IdentityEngine) {
        if (engines.none { it.name == engine.name }) {
            engines.add(engine)
        }
    }

    fun getActiveEngines(): List<IdentityEngine> = engines

    private fun setupDefaultEngines() {
        register(FeatureIdentityEngine())
        register(ArgIdentityEngine())
        register(EmbeddingIdentityEngine())
        register(ConsensusIdentityEngine())
        register(TieredIdentityEngine())
        register(HardcodedIdentityEngine())
    }
}
