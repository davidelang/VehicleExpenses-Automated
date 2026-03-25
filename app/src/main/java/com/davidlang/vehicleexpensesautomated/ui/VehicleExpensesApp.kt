package com.davidlang.vehicleexpensesautomated.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.davidlang.vehicleexpensesautomated.ui.fuel.QuickFillupScreen

@Composable
fun VehicleExpensesApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "quickfill",
        modifier = modifier
    ) {
        composable("quickfill") {
            QuickFillupScreen(navController = navController)
        }
    }
}
