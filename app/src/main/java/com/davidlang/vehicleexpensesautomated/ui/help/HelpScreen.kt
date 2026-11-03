package com.davidlang.vehicleexpensesautomated.ui.help

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.ui.util.SyncSetupDocs
import com.davidlang.vehicleexpensesautomated.ui.util.UserManualDocs
import androidx.compose.ui.res.stringResource
import com.davidlang.vehicleexpensesautomated.R

@Composable
fun HelpScreen(navController: NavHostController? = null) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.nav_help), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.help_short_on_device_guide_so_you_can_start_immediate),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { UserManualDocs.openFullManual(context) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Text(stringResource(R.string.help_open_full_user_manual))
        }

        if (navController != null) {
            SectionTitle(stringResource(R.string.help_setup_tips_animated))
            Text(
                stringResource(R.string.help_short_step_by_step_walkthroughs_with_scree) +
                    stringResource(R.string.help_connect_existing_setup_join_another_device_s_sha),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    navController.navigate("tutorial/tutorial_add_vehicle")
                },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.settings_tutorial_add_a_vehicle))
            }
            Button(
                onClick = {
                    navController.navigate("tutorial/tutorial_setup_sync")
                },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.settings_tutorial_connect_existing_setup))
            }
            TextButton(
                onClick = { navController.navigate("onboarding") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.help_show_first_run_welcome_choices))
            }
        }

        SectionTitle(stringResource(R.string.help_icons))
        Bullet(stringResource(R.string.help_top_left_open_the_menu))
        Bullet(stringResource(R.string.help_title_bar_page_help_for_the_current_screen))
        Bullet(stringResource(R.string.help_red_title_bar_last_spreadsheet_or_photo_sy))
        Bullet(stringResource(R.string.help_n_title_bar_open_import_review_questions_w))
        Bullet(stringResource(R.string.help_white_circle_shutter_capture_dash_pump_or))
        Bullet(stringResource(R.string.help_disk_save_fill_up_or_expense))
        Bullet(stringResource(R.string.help_on_fill_switch_odometer_mode_pump_cost_vol))
        Bullet(stringResource(R.string.help_between_cost_and_volume_swap_the_two_value))
        Bullet(stringResource(R.string.help_on_sync_forms_browse_google_drive_for_a_sh))

        SectionTitle(stringResource(R.string.help_s_1_add_a_vehicle_do_this_first))
        Bullet(stringResource(R.string.help_menu_manage_vehicles_vehicle_dropdown_add))
        Bullet(stringResource(R.string.help_take_or_pick_a_clear_dashboard_photo))
        Bullet(stringResource(R.string.help_run_discovery_select_odo_tap_the_seed_adju))
        Bullet(stringResource(R.string.help_run_discovery_review_landmarks_edit_ocr_to))
        Bullet(stringResource(R.string.help_create_vehicle))
        Text(
            stringResource(R.string.help_landmarks_power_auto_vehicle_match_on_fill),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        SectionTitle(stringResource(R.string.help_s_2_log_a_fill_up))
        Bullet(stringResource(R.string.help_open_the_app_fill_is_home_no_need_to_pick))
        Bullet(stringResource(R.string.help_aim_at_the_odometer_shutter_confirm_odo_ve))
        Bullet(stringResource(R.string.help_tap_for_pump_mode_aim_at_pump_totals_shutt))
        Bullet(stringResource(R.string.help_fix_fields_if_needed_tap_currency_or_g_l_t))
        Bullet(stringResource(R.string.help_disk_save_blank_fields_are_allowed_as_a_pa))

        SectionTitle(stringResource(R.string.help_s_3_log_an_expense))
        Bullet(stringResource(R.string.help_menu_new_expense))
        Bullet(stringResource(R.string.help_shutter_or_gallery_for_the_receipt_fill_ve))
        Bullet(stringResource(R.string.help_save_open_expenses_from_reports_expenses_l))

        SectionTitle(stringResource(R.string.help_s_4_reports))
        Bullet(stringResource(R.string.help_menu_reports_hub_summary_then_report_cards))
        Bullet(
            stringResource(R.string.help_time_based_reports_one_plot_with_independe) +
                stringResource(R.string.help_optional_smooth_bins),
        )
        Bullet(stringResource(R.string.help_fill_history_lists_fills_only_trip_miles_h))
        Bullet(stringResource(R.string.help_fuel_history_and_edit_screens_can_fetch_im))

        SectionTitle(stringResource(R.string.help_s_5_backups_multi_device_optional))
        Text(
            stringResource(R.string.help_use_your_own_accounts_not_a_shared_app_cloud_syn),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.help_data_spreadsheet),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(top = 8.dp),
        )
        Bullet(stringResource(R.string.help_settings_spreadsheet_sync_google_sheets_ex))
        Text(
            stringResource(R.string.help_photos),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(top = 8.dp),
        )
        Bullet(stringResource(R.string.help_settings_photo_backup_google_drive_onedriv))
        Text(
            stringResource(R.string.help_full_provider_list_and_setup_online_manual_or_se),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        TextButton(onClick = { SyncSetupDocs.open(context, SyncSetupDocs.index()) }) {
            Text(stringResource(R.string.help_self_host_setup_index))
        }

        SectionTitle(stringResource(R.string.help_if_something_fails))
        Bullet(stringResource(R.string.help_red_error_under_spreadsheet_sync_photo_bac))
        Bullet(stringResource(R.string.help_red_in_the_title_bar_settings))
        Bullet(stringResource(R.string.help_yellow_in_the_title_bar_import_review_ques))
        Bullet(stringResource(R.string.help_same_fill_entered_twice_on_two_devices_two))

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.help_import_old_pictures_and_experiment_screens) +
                stringResource(R.string.help_batch_import_references_your_existing_dash) +
                "Do not delete experiment_photos or pump_photos while fuel rows still point at those files.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(text, style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun Bullet(text: String) {
    Text("• $text", style = MaterialTheme.typography.bodyMedium)
}
