package com.davidlang.vehicleexpensesautomated.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.davidlang.vehicleexpensesautomated.TransferBlockingScreen
import com.davidlang.vehicleexpensesautomated.data.db.DatabaseHealthState
import com.davidlang.vehicleexpensesautomated.data.db.Exporting
import com.davidlang.vehicleexpensesautomated.data.db.Importing
import com.davidlang.vehicleexpensesautomated.data.sync.ZohoSheetAuth
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Receives implicit OAuth redirect `vehicleexpenses://zoho/oauth#access_token=...`. */
@AndroidEntryPoint
class ZohoOAuthRedirectActivity : ComponentActivity() {

    @Inject lateinit var zohoAuth: ZohoSheetAuth

    @Inject lateinit var dbState: DatabaseHealthState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RedirectGate() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    @Composable
    private fun RedirectGate() {
        val health by dbState.state.collectAsState()
        val progress by dbState.progress.collectAsState()
        var sawTransfer by remember { mutableStateOf(false) }
        if (health is Exporting || health is Importing) {
            sawTransfer = true
            TransferBlockingScreen(health, progress)
            return
        }
        LaunchedEffect(health, sawTransfer) {
            if (sawTransfer) {
                finish()
            } else {
                zohoAuth.deliverRedirectUri(intent?.data)
                finish()
            }
        }
    }
}
