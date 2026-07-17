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
        Text("Help", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Short on-device guide so you can start immediately. For screenshots and every step, open the full illustrated manual (bundled in the app — works offline, no GitHub login).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { UserManualDocs.openFullManual(context) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Text("Open full user manual")
        }

        SectionTitle("Icons")
        Bullet("☰ (top left) — open the menu")
        Bullet("! (red, title bar) — last spreadsheet or photo sync failed; open Settings")
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
        Text(
            "Landmarks power auto vehicle match on Quick Fill. No camera? Type odo/volume/cost by hand.",
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
        Bullet("Menu → New Expense Entry")
        Bullet("Shutter or gallery for the receipt; fill vendor, amount, category, vehicle")
        Bullet("Save. Use Expense List to review or edit later")

        SectionTitle("4. Reports")
        Bullet("Menu → Reports & Charts for summaries, last full fills, expenses, and history")

        SectionTitle("5. Backups & multi-device (optional)")
        Text(
            "Use your own accounts — not a shared app cloud. Sync is background; no network needed to add fills.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Data (spreadsheet)",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(top = 8.dp),
        )
        Bullet("Settings → Spreadsheet sync: Google Sheets, Excel, EtherCalc, or Other (Baserow, NocoDB, Airtable, PocketBase, Supabase, Firebase, Zoho Sheet, …)")
        Text(
            "Photos",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(top = 8.dp),
        )
        Bullet("Settings → Photo backup: Google Drive, OneDrive, S3, or Other (WebDAV/SFTP/… via rclone)")
        Text(
            "Full provider list and setup: online manual or self-host index.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        TextButton(onClick = { SyncSetupDocs.open(context, SyncSetupDocs.index()) }) {
            Text("Self-host setup index")
        }

        SectionTitle("If something fails")
        Bullet("Red error under Spreadsheet sync / Photo backup in Settings")
        Bullet("Red ! in the title bar → Settings")
        Bullet("Same fill entered twice on two devices = two rows; delete the extra")

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Import Old Pictures and experiment screens are advanced tools and are not covered here.",
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
