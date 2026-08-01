package com.davidlang.vehicleexpensesautomated.ui.help

import com.davidlang.vehicleexpensesautomated.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.ui.util.SyncSetupDocs
import com.davidlang.vehicleexpensesautomated.ui.util.UserManualDocs

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
        Text(stringResource(R.string.help_short_on_device_guide_so_you_can_start_immediate),
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
            SectionTitle("Setup tips (animated)")
            Text(
                "Short step-by-step walkthroughs with screenshots (offline). Also offered on first run when you have no vehicles. " +
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

        SectionTitle("Icons")
        Bullet("☰ (top left) — open the menu")
        Bullet("ⓘ (title bar) — page help for the current screen (stays while you stay on the page)")
        Bullet("! (red, title bar) — last spreadsheet or photo sync failed; opens Syncing")
        Bullet("?N (title bar) — open Import / Review questions when items need answers")
        Bullet("White circle — shutter (capture dash, pump, or receipt)")
        Bullet("Disk — save fill-up or expense")
        Bullet("↕ on Quick Fill — switch odometer mode ↔ pump (cost/volume) mode")
        Bullet("↔ between cost and volume — swap the two values if OCR swapped them")
        Bullet("🔍 on sync forms — browse Google Drive for a sheet or folder")

        SectionTitle("1. Add a vehicle (do this first)")
        Bullet("Menu → Manage Vehicles → Vehicle dropdown → Add New Vehicle")
        Bullet("Take or pick a clear dashboard photo")
        Bullet("Odo Crop around the odometer digits; optional Ignore Crop for clutter")
        Bullet("Run Discovery, review landmarks (Edit OCR to fix misses), enter a Vehicle Name")
        Bullet("Create Vehicle")
        Text(stringResource(R.string.help_landmarks_power_auto_vehicle_match_on_quick_fill),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        SectionTitle("2. Log a fill-up")
        Bullet("Open the app (Quick Fill-up is home) — no need to pick the vehicle first")
        Bullet("Aim at the odometer → shutter → confirm Odo (vehicle auto-detects from landmarks)")
        Bullet("Tap ↕ for pump mode → aim at pump totals → shutter")
        Bullet("Fix fields if needed (tap currency or G/L to change units)")
        Bullet("Disk (Save). Blank fields are allowed as a partial fill. Works offline")

        SectionTitle("3. Log an expense")
        Bullet("Menu → New expense")
        Bullet("Shutter or gallery for the receipt; fill vendor, amount, category, vehicle")
        Bullet("Save. Open expenses from Reports → Expenses list to review or edit later")

        SectionTitle("4. Reports")
        Bullet("Menu → Reports — hub summary, then report cards")
        Bullet(
            "Time based reports — one plot with independent Y scales (mpg and G/mi on the left; \$ / trip miles / trip % by type on the right), " +
                stringResource(R.string.help_optional_smooth_bins),
        )
        Bullet("Fill history lists fills only; Trip miles has the trip-start list (tap a row to edit)")
        Bullet("Fuel History and edit screens can Fetch image from archive when any photo destination has the file")

        SectionTitle("5. Backups & multi-device (optional)")
        Text(stringResource(R.string.help_use_your_own_accounts_not_a_shared_app_cloud_syn),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.help_data_spreadsheet),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(top = 8.dp),
        )
        Bullet("Settings → Spreadsheet sync: Google Sheets, Excel, EtherCalc, or Other (Baserow, NocoDB, Airtable, PocketBase, Supabase, Firebase, Zoho Sheet, …)")
        Text(stringResource(R.string.help_photos),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(top = 8.dp),
        )
        Bullet("Settings → Photo backup: Google Drive, OneDrive, S3, or Other (WebDAV/SFTP/… via rclone)")
        Text(stringResource(R.string.help_full_provider_list_and_setup_online_manual_or_se),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        TextButton(onClick = { SyncSetupDocs.open(context, SyncSetupDocs.index()) }) {
            Text(stringResource(R.string.help_self_host_setup_index))
        }

        SectionTitle("If something fails")
        Bullet("Red error under Spreadsheet sync / Photo backup in Settings")
        Bullet("Red ! in the title bar → Settings")
        Bullet("Yellow ? in the title bar → Import / Review questions")
        Bullet("Same fill entered twice on two devices = two rows; delete the extra")

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Import Old Pictures and experiment screens are advanced tools and are not covered here. " +
                "Batch import references your existing dash/pump photo files in place (does not copy them). " +
                stringResource(R.string.help_do_not_delete_experiment_photos_or_pump_photos_w),
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
