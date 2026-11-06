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
import com.davidlang.vehicleexpensesautomated.ui.util.S

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
        Text(S("Help"), style = MaterialTheme.typography.headlineMedium)
        Text(
            S("Short on-device guide so you can start immediately. For screenshots and every step, open the full illustrated manual (bundled in the app — works offline, no GitHub login)."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { UserManualDocs.openFullManual(context) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Text(S("Open full user manual"))
        }

        if (navController != null) {
            SectionTitle(S("Setup tips (animated)"))
            Text(
                S("Short step-by-step walkthroughs with screenshots (offline). Also offered on first run when you have no vehicles. ") +
                    S("Connect existing setup = join another device’s shared sheet + photo folder (not create a new blank sheet)."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    navController.navigate("tutorial/tutorial_add_vehicle")
                },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(S("Tutorial: Add a vehicle"))
            }
            Button(
                onClick = {
                    navController.navigate("tutorial/tutorial_setup_sync")
                },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(S("Tutorial: Connect existing setup"))
            }
            TextButton(
                onClick = { navController.navigate("onboarding") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(S("Show first-run welcome choices"))
            }
        }

        SectionTitle(S("Icons"))
        Bullet(S("☰ (top left) — open the menu"))
        Bullet(S("ⓘ (title bar) — page help for the current screen (stays while you stay on the page)"))
        Bullet(S("! (red, title bar) — last spreadsheet or photo sync failed; opens Syncing"))
        Bullet(S("?N (title bar) — open Import / Review questions when items need answers"))
        Bullet(S("White circle — shutter (capture dash, pump, or receipt)"))
        Bullet(S("Disk — save fill-up or expense"))
        Bullet(S("↕ on Fill — switch odometer mode ↔ pump (cost/volume) mode"))
        Bullet(S("↔ between cost and volume — swap the two values if OCR swapped them"))
        Bullet(S("🔍 on sync forms — browse Google Drive for a sheet or folder"))

        SectionTitle(S("1. Add a vehicle (do this first)"))
        Bullet(S("Menu → Manage Vehicles → Vehicle dropdown → Add New Vehicle"))
        Bullet(S("Take or pick a clear dashboard photo"))
        Bullet(S("Run Discovery, Select Odo, tap the seed, adjust, Save"))
        Bullet(S("Run Discovery, review landmarks (Edit OCR to fix misses), enter a Vehicle Name"))
        Bullet(S("Create Vehicle"))
        Text(
            S("Landmarks power auto vehicle match on Fill. No camera? Type odo/volume/cost by hand."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        SectionTitle(S("2. Log a fill-up"))
        Bullet(S("Open the app (Fill is home) — no need to pick the vehicle first"))
        Bullet(S("Aim at the odometer → shutter → confirm Odo (vehicle auto-detects from landmarks)"))
        Bullet(S("Tap ↕ for pump mode → aim at pump totals → shutter"))
        Bullet(S("Fix fields if needed (tap currency or G/L to change units)"))
        Bullet(S("Disk (Save). Blank fields are allowed as a partial fill. Works offline"))

        SectionTitle(S("3. Log an expense"))
        Bullet(S("Menu → New expense"))
        Bullet(S("Shutter or gallery for the receipt; fill vendor, amount, category, vehicle"))
        Bullet(S("Save. Open expenses from Reports → Expenses list to review or edit later"))

        SectionTitle(S("4. Reports"))
        Bullet(S("Menu → Reports — hub summary, then report cards"))
        Bullet(
            S("Time based reports — one plot with independent Y scales (mpg and G/mi on the left; \$ / trip miles / trip %% by type on the right), ") +
                S("optional Smooth bins"),
        )
        Bullet(S("Fill history lists fills only; Trip miles has the trip-start list (tap a row to edit)"))
        Bullet(S("Fuel History and edit screens can Fetch image from archive when any photo destination has the file"))

        SectionTitle(S("5. Backups & multi-device (optional)"))
        Text(
            S("Use your own accounts — not a shared app cloud. Sync is background; no network needed to add fills."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            S("Data (spreadsheet)"),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(top = 8.dp),
        )
        Bullet(S("Settings → Spreadsheet sync: Google Sheets, Excel, EtherCalc, or Other (Baserow, NocoDB, Airtable, PocketBase, Supabase, Firebase, Zoho Sheet, …)"))
        Text(
            S("Photos"),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(top = 8.dp),
        )
        Bullet(S("Settings → Photo backup: Google Drive, OneDrive, S3, or Other (WebDAV/SFTP/… via rclone)"))
        Text(
            S("Full provider list and setup: online manual or self-host index."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        TextButton(onClick = { SyncSetupDocs.open(context, SyncSetupDocs.index()) }) {
            Text(S("Self-host setup index"))
        }

        SectionTitle(S("If something fails"))
        Bullet(S("Red error under Spreadsheet sync / Photo backup in Settings"))
        Bullet(S("Red ! in the title bar → Settings"))
        Bullet(S("Yellow ? in the title bar → Import / Review questions"))
        Bullet(S("Same fill entered twice on two devices = two rows; delete the extra"))

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            S("Import Old Pictures and experiment screens are advanced tools and are not covered here. ") +
                S("Batch import references your existing dash/pump photo files in place (does not copy them). ") +
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
