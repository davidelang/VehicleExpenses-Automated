package com.davidlang.vehicleexpensesautomated.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.davidlang.vehicleexpensesautomated.ui.components.RegisterPageHelp
import com.davidlang.vehicleexpensesautomated.ui.util.UserManualDocs

/**
 * First-run splash when no user vehicles exist (S1–S6).
 * Offers add-vehicle tutorial, set-up-sync tutorial, skip, or open manual.
 */
@Composable
fun OnboardingSplashScreen(navController: NavHostController) {
    val context = LocalContext.current
    RegisterPageHelp(
        title = "Welcome",
        "No vehicles yet. Choose stand-alone (add a vehicle on this phone) or connect to an existing multi-device setup (same sheet + photo folder).",
        "You can skip and use Quick Fill manually; this screen returns on the next start until a vehicle exists.",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "Welcome to Vehicle Expenses",
            style = MaterialTheme.typography.headlineMedium,
            softWrap = true,
        )
        Text(
            "You don’t have a vehicle yet. How do you want to start?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            softWrap = true,
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                navController.navigate("tutorial/${TutorialIds.ADD_VEHICLE}")
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                Text("Add a vehicle", style = MaterialTheme.typography.titleMedium)
                Text(
                    "This phone is stand-alone / first vehicle",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Button(
            onClick = {
                navController.navigate("tutorial/${TutorialIds.SETUP_SYNC}")
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                Text("Connect existing setup", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Another device already has the app — join that sheet + photo folder",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = {
                navController.navigate("quickfill") {
                    popUpTo("onboarding") { inclusive = true }
                    launchSingleTop = true
                }
            },
        ) {
            Text("Skip for now")
        }
        Text(
            "You can enter fills by hand. This welcome screen returns next launch until you add a vehicle.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            softWrap = true,
        )
        OutlinedButton(
            onClick = { UserManualDocs.openFullManual(context) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open full user manual")
        }
    }
}
