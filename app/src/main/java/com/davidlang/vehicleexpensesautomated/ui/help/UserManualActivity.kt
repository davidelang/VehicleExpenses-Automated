package com.davidlang.vehicleexpensesautomated.ui.help

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.davidlang.vehicleexpensesautomated.ui.theme.VehicleExpensesAutomatedTheme
import com.davidlang.vehicleexpensesautomated.ui.util.AppLanguage
import com.davidlang.vehicleexpensesautomated.ui.util.ManualHtmlSegments
import com.davidlang.vehicleexpensesautomated.ui.util.S
import com.davidlang.vehicleexpensesautomated.ui.util.UserManualDocs

/**
 * Illustrated user manual. The WebView loads the bundled English HTML.
 */
class UserManualActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        // setApplicationLocales does not reach a ComponentActivity before API 33.
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VehicleExpensesAutomatedTheme {
                Scaffold(
                    topBar = {
                        val loc = AppLanguage.resolveActiveLocale(this@UserManualActivity)
                        val english = loc.bcp47 == "en" || loc.bcp47.startsWith("en-")
                        val showWeb = !english &&
                            loc.manualPathSegment.isNotEmpty() &&
                            ManualHtmlSegments.present.contains(loc.manualPathSegment)
                        TopAppBar(
                            title = { Text(S("User Manual")) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Text("←")
                                }
                            },
                            actions = {
                                if (showWeb) {
                                    TextButton(onClick = {
                                        openExternal(AppLanguage.onlineManualHtmlUrlFor(loc))
                                    }) {
                                        Text(S("This language on the web"))
                                    }
                                }
                                if (UserManualDocs.ONLINE_HTML_URL.isNotEmpty()) {
                                    TextButton(onClick = { openExternal(translateLatestEnglishUrl()) }) {
                                        Text(S("Translate the latest English"))
                                    }
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

    private fun openExternal(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun translateLatestEnglishUrl(): String {
        val loc = AppLanguage.resolveActiveLocale(this)
        val tl = Uri.encode(loc.prefTag)
        val page = Uri.encode(UserManualDocs.ONLINE_HTML_URL)
        return "https://translate.google.com/translate?sl=en&tl=$tl&u=$page"
    }

    companion object {
        const val ASSET_URL = "file:///android_asset/user-manual/index.html"
    }
}
