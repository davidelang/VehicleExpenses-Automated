package com.davidlang.vehicleexpensesautomated.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.davidlang.vehicleexpensesautomated.data.sync.ZohoSheetAuth
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Receives implicit OAuth redirect `vehicleexpenses://zoho/oauth#access_token=...`. */
@AndroidEntryPoint
class ZohoOAuthRedirectActivity : ComponentActivity() {

    @Inject lateinit var zohoAuth: ZohoSheetAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        zohoAuth.deliverRedirectUri(intent?.data)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        zohoAuth.deliverRedirectUri(intent.data)
        finish()
    }
}