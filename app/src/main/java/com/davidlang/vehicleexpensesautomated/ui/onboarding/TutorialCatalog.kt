package com.davidlang.vehicleexpensesautomated.ui.onboarding

import android.content.Context
import androidx.annotation.StringRes
import com.davidlang.vehicleexpensesautomated.R

/** Stable tutorial IDs (T1). */
object TutorialIds {
    const val ADD_VEHICLE = "tutorial_add_vehicle"
    const val SETUP_SYNC = "tutorial_setup_sync"
}

data class TutorialStep(
    val title: String,
    val body: String,
    /** Asset path under `assets/tutorials/` or null for text-only. */
    val imageAsset: String? = null,
)

data class Tutorial(
    val id: String,
    val title: String,
    val steps: List<TutorialStep>,
    /** Nav route after Done (e.g. managevehicles, syncing). */
    val endRoute: String,
    val endCtaLabel: String,
)

private data class TutorialStepDef(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val imageAsset: String? = null,
)

private data class TutorialDef(
    val id: String,
    @StringRes val titleRes: Int,
    val steps: List<TutorialStepDef>,
    val endRoute: String,
    @StringRes val endCtaRes: Int,
)

/**
 * Tutorial copy is localized via string resources; images are shared under assets/tutorials/.
 * Resolve with [TutorialCatalog.get] / [TutorialCatalog.all] using a [Context].
 */
object TutorialCatalog {
    private val DEFS: List<TutorialDef> = listOf(
        TutorialDef(
            id = TutorialIds.ADD_VEHICLE,
            titleRes = R.string.tutorial_add_vehicle_title,
            endRoute = "managevehicles",
            endCtaRes = R.string.tutorial_add_vehicle_cta,
            steps = listOf(
                TutorialStepDef(
                    R.string.tutorial_add_vehicle_step1_title,
                    R.string.tutorial_add_vehicle_step1_body,
                    "tutorials/drawer.jpg",
                ),
                TutorialStepDef(
                    R.string.tutorial_add_vehicle_step2_title,
                    R.string.tutorial_add_vehicle_step2_body,
                    "tutorials/vehicle_manage.jpg",
                ),
                TutorialStepDef(
                    R.string.tutorial_add_vehicle_step3_title,
                    R.string.tutorial_add_vehicle_step3_body,
                    "tutorials/vehicle_dash.jpg",
                ),
                TutorialStepDef(
                    R.string.tutorial_add_vehicle_step4_title,
                    R.string.tutorial_add_vehicle_step4_body,
                    "tutorials/vehicle_crops.jpg",
                ),
                TutorialStepDef(
                    R.string.tutorial_add_vehicle_step5_title,
                    R.string.tutorial_add_vehicle_step5_body,
                    "tutorials/vehicle_manage.jpg",
                ),
            ),
        ),
        TutorialDef(
            id = TutorialIds.SETUP_SYNC,
            titleRes = R.string.tutorial_setup_sync_title,
            endRoute = "syncing",
            endCtaRes = R.string.tutorial_setup_sync_cta,
            steps = listOf(
                TutorialStepDef(
                    R.string.tutorial_setup_sync_step1_title,
                    R.string.tutorial_setup_sync_step1_body,
                    "tutorials/drawer.jpg",
                ),
                TutorialStepDef(
                    R.string.tutorial_setup_sync_step2_title,
                    R.string.tutorial_setup_sync_step2_body,
                    "tutorials/sync_hub.jpg",
                ),
                TutorialStepDef(
                    R.string.tutorial_setup_sync_step3_title,
                    R.string.tutorial_setup_sync_step3_body,
                    "tutorials/sync_sheet.jpg",
                ),
                TutorialStepDef(
                    R.string.tutorial_setup_sync_step4_title,
                    R.string.tutorial_setup_sync_step4_body,
                    "tutorials/sync_photo.jpg",
                ),
                TutorialStepDef(
                    R.string.tutorial_setup_sync_step5_title,
                    R.string.tutorial_setup_sync_step5_body,
                    "tutorials/sync_hub.jpg",
                ),
            ),
        ),
    )

    fun get(context: Context, id: String): Tutorial? =
        DEFS.find { it.id == id }?.let { it.resolve(context) }

    fun all(context: Context): List<Tutorial> = DEFS.map { it.resolve(context) }

    private fun TutorialDef.resolve(context: Context): Tutorial =
        Tutorial(
            id = id,
            title = context.getString(titleRes),
            endRoute = endRoute,
            endCtaLabel = context.getString(endCtaRes),
            steps = steps.map { s ->
                TutorialStep(
                    title = context.getString(s.titleRes),
                    body = context.getString(s.bodyRes),
                    imageAsset = s.imageAsset,
                )
            },
        )
}
