package com.davidlang.vehicleexpensesautomated.ui.onboarding
import com.davidlang.vehicleexpensesautomated.R
import com.davidlang.vehicleexpensesautomated.ui.util.AppStrings

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
    fun get(id: String): Tutorial? = all().find { it.id == id }

    /** Rebuilt on each call so an in-app language change is not stuck on the first locale. */
    fun all(): List<Tutorial> = listOf(
        Tutorial(
            id = TutorialIds.ADD_VEHICLE,
            title = AppStrings.get(R.string.onboarding_add_a_vehicle),
            endRoute = "managevehicles",
            endCtaLabel = AppStrings.get(R.string.tutorial_add_vehicle_cta),
            steps = listOf(
                TutorialStep(
                    title = AppStrings.get(R.string.onboarding_open_manage_vehicles),
                    body = AppStrings.get(R.string.onboarding_from_the_menu_choose_manage_vehicles_this) +
                        AppStrings.get(R.string.onboarding_the_app_can_recognize_later),
                    imageAsset = "tutorials/drawer.jpg",
                ),
                TutorialStep(
                    title = AppStrings.get(R.string.vehicle_add_new_vehicle),
                    body = AppStrings.get(R.string.onboarding_open_the_vehicle_dropdown_and_pick_add_new) +
                        AppStrings.get(R.string.onboarding_as_stand_alone_first_vehicle_or_an_additional_ve),
                    imageAsset = "tutorials/vehicle_manage.jpg",
                ),
                TutorialStep(
                    title = AppStrings.get(R.string.onboarding_dashboard_photo),
                    body = AppStrings.get(R.string.onboarding_take_or_pick_a_clear_photo_of_the_instrume) +
                        AppStrings.get(R.string.onboarding_view_of_the_odometer_help_discovery),
                    imageAsset = "tutorials/vehicle_dash.jpg",
                ),
                TutorialStep(
                    title = AppStrings.get(R.string.tutorial_add_vehicle_step4_title),
                    body = AppStrings.get(R.string.onboarding_run_discovery_select_odo_tap_the_seed_adju) +
                        AppStrings.get(R.string.onboarding_review_landmarks_edit_ocr_text_if_somethin),
                    imageAsset = "tutorials/vehicle_crops.jpg",
                ),
                TutorialStep(
                    title = AppStrings.get(R.string.onboarding_name_and_create),
                    body = AppStrings.get(R.string.onboarding_enter_a_vehicle_name_then_create_vehicle_a) +
                        AppStrings.get(R.string.onboarding_the_first_run_splash_will_not_appear_again),
                    imageAsset = "tutorials/vehicle_manage.jpg",
                ),
            ),
        ),
        Tutorial(
            id = TutorialIds.SETUP_SYNC,
            title = AppStrings.get(R.string.tutorial_setup_sync_title),
            endRoute = "syncing",
            endCtaLabel = AppStrings.get(R.string.tutorial_setup_sync_cta),
            steps = listOf(
                TutorialStep(
                    title = AppStrings.get(R.string.tutorial_setup_sync_step1_title),
                    body = AppStrings.get(R.string.onboarding_this_path_is_for_a_new_phone_or_tablet_joi) +
                        AppStrings.get(R.string.onboarding_another_device_already_has_vehicles_a_shar) +
                        AppStrings.get(R.string.onboarding_you_use_your_google_microsoft_other_accoun) +
                        AppStrings.get(R.string.onboarding_stand_alone_first_setup_no_other_device_yet_is_a),
                    imageAsset = "tutorials/drawer.jpg",
                ),
                TutorialStep(
                    title = AppStrings.get(R.string.tutorial_setup_sync_step2_title),
                    body = AppStrings.get(R.string.onboarding_from_the_menu_open_syncing_you_will_add_de) +
                        AppStrings.get(R.string.onboarding_sheet_and_photo_folder_the_other_device_already_),
                    imageAsset = "tutorials/sync_hub.jpg",
                ),
                TutorialStep(
                    title = AppStrings.get(R.string.tutorial_setup_sync_step3_title),
                    body = AppStrings.get(R.string.onboarding_add_a_spreadsheet_destination_pick_the_sam) +
                        AppStrings.get(R.string.onboarding_sign_in_paste_the_existing_sheet_url_from) +
                        AppStrings.get(R.string.onboarding_test_connection_then_sync_now_to_pull_vehi) +
                        AppStrings.get(R.string.onboarding_do_not_create_a_new_blank_spreadsheet_for_this_p),
                    imageAsset = "tutorials/sync_sheet.jpg",
                ),
                TutorialStep(
                    title = AppStrings.get(R.string.tutorial_setup_sync_step4_title),
                    body = AppStrings.get(R.string.onboarding_add_a_photo_destination_same_provider_as_t) +
                        AppStrings.get(R.string.onboarding_choose_the_same_photo_folder_already_used) +
                        AppStrings.get(R.string.onboarding_test_connection_sync_now_vehicle_reference) +
                        AppStrings.get(R.string.onboarding_fill_receipt_photos_are_on_demand_via_fetc) +
                        AppStrings.get(R.string.onboarding_do_not_create_a_new_empty_folder_for_this_path),
                    imageAsset = "tutorials/sync_photo.jpg",
                ),
                TutorialStep(
                    title = AppStrings.get(R.string.tutorial_setup_sync_step5_title),
                    body = AppStrings.get(R.string.onboarding_vehicles_and_data_should_appear_from_the_s) +
                        AppStrings.get(R.string.onboarding_a_local_dash_photo_open_manage_vehicles_an) +
                        AppStrings.get(R.string.onboarding_you_do_not_need_to_re_type_the_whole_fleet_from_),
                    imageAsset = "tutorials/sync_hub.jpg",
                ),
            ),
        ),
    )
}
