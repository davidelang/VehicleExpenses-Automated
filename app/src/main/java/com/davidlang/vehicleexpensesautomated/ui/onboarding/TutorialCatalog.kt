package com.davidlang.vehicleexpensesautomated.ui.onboarding

import com.davidlang.vehicleexpensesautomated.R

import androidx.compose.ui.res.stringResource

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

object TutorialCatalog {
    fun get(id: String): Tutorial? = all.find { it.id == id }

    val all: List<Tutorial> = listOf(
        Tutorial(
            id = TutorialIds.ADD_VEHICLE,
            title = stringResource(R.string.onboarding_add_a_vehicle),
            endRoute = "managevehicles",
            endCtaLabel = "Go to Manage Vehicles",
            steps = listOf(
                TutorialStep(
                    title = stringResource(R.string.onboarding_open_manage_vehicles),
                    body = "From the menu (☰), choose Manage Vehicles. This is where you create dashboards " +
                        stringResource(R.string.onboarding_the_app_can_recognize_later),
                    imageAsset = "tutorials/drawer.jpg",
                ),
                TutorialStep(
                    title = stringResource(R.string.vehicle_add_new_vehicle),
                    body = "Open the vehicle dropdown and pick Add New Vehicle. You are setting up this phone " +
                        stringResource(R.string.onboarding_as_stand_alone_first_vehicle_or_an_additional_ve),
                    imageAsset = "tutorials/vehicle_manage.jpg",
                ),
                TutorialStep(
                    title = stringResource(R.string.onboarding_dashboard_photo),
                    body = "Take or pick a clear photo of the instrument cluster. Good lighting and a square-on " +
                        stringResource(R.string.onboarding_view_of_the_odometer_help_discovery),
                    imageAsset = "tutorials/vehicle_dash.jpg",
                ),
                TutorialStep(
                    title = stringResource(R.string.onboarding_odo_crop_run_discovery),
                    body = "Draw Odo Crop around the odometer digits (optional Ignore Crop for clutter). " +
                        stringResource(R.string.onboarding_tap_run_discovery_and_review_landmarks_edit_ocr_),
                    imageAsset = "tutorials/vehicle_crops.jpg",
                ),
                TutorialStep(
                    title = stringResource(R.string.onboarding_name_and_create),
                    body = "Enter a Vehicle Name, then Create Vehicle. After at least one user vehicle exists, " +
                        stringResource(R.string.onboarding_the_first_run_splash_will_not_appear_again),
                    imageAsset = "tutorials/vehicle_manage.jpg",
                ),
            ),
        ),
        Tutorial(
            id = TutorialIds.SETUP_SYNC,
            title = stringResource(R.string.onboarding_connect_existing_setup),
            endRoute = "syncing",
            endCtaLabel = "Go to Syncing",
            steps = listOf(
                TutorialStep(
                    title = stringResource(R.string.onboarding_you_already_have_a_cluster),
                    body = "This path is for a **new phone or tablet** joining an **existing** Vehicle Expenses setup. " +
                        "Another device already has vehicles, a **shared spreadsheet**, and usually a **shared photo folder**. " +
                        "You use **your** Google / Microsoft / other account — not an app-hosted cloud. " +
                        stringResource(R.string.onboarding_stand_alone_first_setup_no_other_device_yet_is_a),
                    imageAsset = "tutorials/drawer.jpg",
                ),
                TutorialStep(
                    title = stringResource(R.string.onboarding_open_syncing_on_this_device),
                    body = "From the menu (☰), open **Syncing**. You will add destinations that point at the **same** " +
                        stringResource(R.string.onboarding_sheet_and_photo_folder_the_other_device_already_),
                    imageAsset = "tutorials/sync_hub.jpg",
                ),
                TutorialStep(
                    title = stringResource(R.string.onboarding_spreadsheet_open_the_existing_shared_file),
                    body = "Add a spreadsheet destination → pick the same provider as the other device (often Google Sheets) → " +
                        "sign in. Paste the **existing sheet URL** from the other phone (or Drive **browse to that file**). " +
                        "Test connection, then **Sync now** to pull vehicles and rows. " +
                        stringResource(R.string.onboarding_do_not_create_a_new_blank_spreadsheet_for_this_p),
                    imageAsset = "tutorials/sync_sheet.jpg",
                ),
                TutorialStep(
                    title = stringResource(R.string.onboarding_photos_same_existing_folder),
                    body = "Add a photo destination → same provider as the other device → sign in. " +
                        "Choose the **same photo folder** already used by the cluster (URL or browse). " +
                        "Test connection → Sync now. Vehicle reference images can download automatically; " +
                        "fill/receipt photos are on-demand via Fetch from archive. " +
                        stringResource(R.string.onboarding_do_not_create_a_new_empty_folder_for_this_path),
                    imageAsset = "tutorials/sync_photo.jpg",
                ),
                TutorialStep(
                    title = stringResource(R.string.onboarding_after_the_first_sync_now),
                    body = "Vehicles and data should appear from the shared sheet. Only then, if a vehicle is missing " +
                        "a local dash photo, open Manage Vehicles and capture or fetch the reference image. " +
                        stringResource(R.string.onboarding_you_do_not_need_to_re_type_the_whole_fleet_from_),
                    imageAsset = "tutorials/sync_hub.jpg",
                ),
            ),
        ),
    )
}
