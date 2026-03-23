// (full file with updated NavHost — startDestination = "quickfill", plus new "reports/{vehicleId}" route)
package com.davidlang.vehicleexpensesautomated

// ... existing imports + new import for VehicleSummaryScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "quickfill") {
        composable("quickfill") { QuickFillupScreen(navController) }
        composable("reports/{vehicleId}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("vehicleId")?.toInt() ?: 0
            VehicleSummaryScreen(id, navController)
        }
        // other routes unchanged
    }
}
