package com.davidlang.vehicleexpensesautomated.data.batch

import android.content.Context
import android.util.Log
import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Append-only Stage C answer journal (record only — no replay in this plan).
 * Path: files/stage_c_answer_journal.jsonl
 */
object StageCAnswerJournal {
    private const val TAG = "StageCAnswer"
    private const val FILE_NAME = "stage_c_answer_journal.jsonl"

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun append(
        context: Context,
        phase: Int,
        item: BatchPendingItem,
        action: PendingAnswerAction,
        result: PendingAnswerResult,
        entryBefore: FuelEntry? = null,
    ) {
        val photo = item.durablePhotoPath ?: item.photoPath
            ?: item.extra["photoPaths"]?.split('|')?.firstOrNull()
        val stem = photo?.let { photoStem(it) }.orEmpty()
        val payload = actionPayload(action)
        val fingerprint = JSONObject().apply {
            put("vehicleId", entryBefore?.vehicleId ?: item.suggestedVehicleId ?: 0)
            put("timestampMs", entryBefore?.timestamp ?: item.timestampMs ?: 0L)
            put("odoBefore", entryBefore?.odometer ?: item.extra["parsedOdo"]?.toIntOrNull() ?: 0)
            put("costBefore", entryBefore?.cost ?: item.extra["parsedCost"]?.toDoubleOrNull() ?: 0.0)
            put("volBefore", entryBefore?.gallons ?: item.extra["parsedVol"]?.toDoubleOrNull() ?: 0.0)
            put("syncId", entryBefore?.syncId ?: item.extra["syncId"].orEmpty())
            put("roomId", entryBefore?.id ?: item.fuelEntryId ?: 0L)
        }
        val line = JSONObject().apply {
            put("ts", System.currentTimeMillis())
            put("phase", phase)
            put("kind", item.kind.name)
            put("action", action::class.simpleName ?: "Unknown")
            put("photoStem", stem)
            put("entryFingerprint", fingerprint)
            put("payload", payload)
            put("result", if (result.success) "ok" else "fail")
            put("message", result.message)
        }.toString()

        try {
            FileOutputStream(file(context), true).use { fos ->
                fos.write((line + "\n").toByteArray(Charsets.UTF_8))
            }
            Log.i(TAG, line.take(500))
        } catch (e: Exception) {
            Log.w(TAG, "journal append failed: ${e.message}")
        }
    }

    private fun actionPayload(action: PendingAnswerAction): JSONObject {
        return when (action) {
            is PendingAnswerAction.Skip -> JSONObject().put("skip", true)
            is PendingAnswerAction.KeepBothNoMerge -> JSONObject().put("keepBoth", true)
            is PendingAnswerAction.RetryPump -> JSONObject().put("retry", true)
            is PendingAnswerAction.ManualEditFuelFields -> JSONObject().apply {
                put("odometer", action.odometer)
                put("cost", action.cost)
                put("volume", action.volume)
                put("entryId", action.entryId)
            }
            is PendingAnswerAction.ManualPumpEntry -> JSONObject().apply {
                put("cost", action.cost)
                put("volume", action.volume)
            }
            is PendingAnswerAction.ManualDashEntry -> JSONObject().apply {
                put("odometer", action.odometer)
                put("vehicleId", action.vehicleId)
            }
            is PendingAnswerAction.SaveOdoPeers -> JSONObject().apply {
                put("prevId", action.prevId)
                put("prevOdo", action.prevOdo)
                put("curId", action.curId)
                put("curOdo", action.curOdo)
                put("nextId", action.nextId)
                put("nextOdo", action.nextOdo)
            }
            is PendingAnswerAction.MarkAsGap -> JSONObject().put("entryId", action.entryId)
            is PendingAnswerAction.SetPartialFill -> JSONObject().apply {
                put("partial", action.partial)
                put("entryId", action.entryId)
            }
            is PendingAnswerAction.SetEconomyIgnored -> JSONObject().apply {
                put("ignored", action.ignored)
                put("entryId", action.entryId)
            }
            is PendingAnswerAction.AssignVehicle -> JSONObject().put("vehicleId", action.vehicleId)
            is PendingAnswerAction.AssignUnknownVehicle ->
                JSONObject().put("vehicleId", action.vehicleId)
            is PendingAnswerAction.ResolveConflictOdo ->
                JSONObject().put("chosenOdo", action.chosenOdo)
            is PendingAnswerAction.FlagPartial -> JSONObject().put("entryId", action.entryId)
        }
    }

    /** Copy journal to [dest] (e.g. cache or share). Returns dest or null. */
    fun exportCopy(context: Context, dest: File): File? {
        val src = file(context)
        if (!src.isFile) {
            dest.writeText("")
            return dest
        }
        return try {
            src.copyTo(dest, overwrite = true)
            dest
        } catch (e: Exception) {
            Log.w(TAG, "export failed: ${e.message}")
            null
        }
    }

    fun lineCount(context: Context): Int {
        val f = file(context)
        if (!f.isFile) return 0
        return try {
            f.useLines { it.count() }
        } catch (_: Exception) {
            0
        }
    }
}
