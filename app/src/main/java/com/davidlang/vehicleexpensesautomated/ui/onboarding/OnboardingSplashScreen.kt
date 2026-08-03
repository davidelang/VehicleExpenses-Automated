package com.davidlang.vehicleexpensesautomated.ui.onboarding

import com.davidlang.vehicleexpensesautomated.R

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
import androidx.compose.ui.res.stringResource
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
        title = stringResource(R.string.nav_welcome),
        stringResource(R.string.onboarding_no_vehicles_yet_choose_stand_alone_add_a_vehicle),
        stringResource(R.string.onboarding_you_can_skip_and_use_quick_fill_manually_this_sc),
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
        Text(stringResource(R.string.onboarding_welcome_to_vehicle_expenses),
            style = MaterialTheme.typography.headlineMedium,
            softWrap = true,
        )
        Text(stringResource(R.string.onboarding_you_don_t_have_a_vehicle_yet_how_do_you_want_to_),
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
                Text(stringResource(R.string.onboarding_add_a_vehicle), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.onboarding_this_phone_is_stand_alone_first_vehicle),
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
                Text(stringResource(R.string.onboarding_connect_existing_setup), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.onboarding_another_device_already_has_the_app_join_that_she),
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
            Text(stringResource(R.string.onboarding_skip_for_now))
        }
        Text(stringResource(R.string.onboarding_you_can_enter_fills_by_hand_this_welcome_screen_),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            softWrap = true,
        )
        OutlinedButton(
            onClick = { UserManualDocs.openFullManual(context) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.help_open_full_user_manual))
        }
    }
}
