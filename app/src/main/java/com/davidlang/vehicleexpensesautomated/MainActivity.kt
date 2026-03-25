package com.davidlang.vehicleexpensesautomated

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.davidlang.vehicleexpensesautomated.ui.fuel.QuickFillupScreen
import com.davidlang.vehicleexpensesautomated.ui.settings.SettingsScreen
import com.davidlang.vehicleexpensesautomated.ui.theme.VehicleExpensesAutomatedTheme
import dagger.hilt.android.AndroidEntryPoint
import android.widget.Toast
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Camera permission denied. Photo features will be disabled.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)

        setContent {
            VehicleExpensesAutomatedTheme {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Text("Menu", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                            NavigationDrawerItem(
                                label = { Text("Quick Fill-up") },
                                selected = false,
                                onClick = {
                                    navController.navigate("quickfill") { popUpTo("quickfill") { inclusive = true } }
                                    scope.launch { drawerState.close() }
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text("Preferences") },
                                selected = false,
                                onClick = {
                                    navController.navigate("settings")
                                    scope.launch { drawerState.close() }
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text("About") },
                                selected = false,
                                onClick = {
                                    // Future About screen
                                    scope.launch { drawerState.close() }
                                }
                            )
                        }
                    }
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Vehicle Expenses") },
                                navigationIcon = {
                                    IconButton(onClick = {
                                        scope.launch { drawerState.open() }
                                    }) {
                                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                                    }
                                }
                            )
                        }
                    ) { innerPadding ->
                        Surface(modifier = Modifier.fillMaxSize()) {
                            NavHost(
                                navController = navController,
                                startDestination = "quickfill",
                                modifier = Modifier.padding(innerPadding)
                            ) {
                                composable("quickfill") {
                                    QuickFillupScreen(navController = navController)
                                }
                                composable("settings") {
                                    SettingsScreen()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
