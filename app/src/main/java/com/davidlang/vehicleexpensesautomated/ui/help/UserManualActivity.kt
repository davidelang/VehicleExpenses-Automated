package com.davidlang.vehicleexpensesautomated.ui.help

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.davidlang.vehicleexpensesautomated.ui.theme.VehicleExpensesAutomatedTheme

/**
 * Offline illustrated user manual (HTML + screenshots from assets).
 * No network and no GitHub login required.
 */
class UserManualActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VehicleExpensesAutomatedTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("User Manual") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Text("←")
                                }
                            },
                        )
                    },
                ) { padding ->
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        factory = { context ->
                            WebView(context).apply {
                                settings.apply {
                                    // Images load from file:///android_asset; JS not needed.
                                    javaScriptEnabled = false
                                    domStorageEnabled = false
                                    allowFileAccess = true
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                    ): Boolean {
                                        // Keep http(s) self-host / related links in the WebView when possible.
                                        return false
                                    }
                                }
                                loadUrl(ASSET_URL)
                            }
                        },
                    )
                }
            }
        }
    }

    companion object {
        const val ASSET_URL = "file:///android_asset/user-manual/index.html"
    }
}
