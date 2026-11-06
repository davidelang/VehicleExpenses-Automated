package com.davidlang.vehicleexpensesautomated.ui.startup

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.davidlang.vehicleexpensesautomated.BuildConfig
import com.davidlang.vehicleexpensesautomated.VehicleExpensesApplication
import com.davidlang.vehicleexpensesautomated.ui.theme.VehicleExpensesAutomatedTheme
import java.io.PrintWriter
import java.io.StringWriter
import com.davidlang.vehicleexpensesautomated.ui.util.AppLanguage
import com.davidlang.vehicleexpensesautomated.ui.util.S

/** OpenCV, Paddle, or rclone failed during startup. No Paddle and no OpenCV. */
class StartupFailureActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        // setApplicationLocales does not reach a ComponentActivity before API 33.
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as VehicleExpensesApplication
        val failure = app.startupFailure
        setContent {
            VehicleExpensesAutomatedTheme {
                StartupFailureScreen(
                    detail = failureText(failure),
                    onQuit = { quit() },
                )
            }
        }
    }

    private fun quit() {
        finishAndRemoveTask()
        Process.killProcess(Process.myPid())
    }
}

@Composable
private fun StartupFailureScreen(detail: String, onQuit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(S("Startup failed"), style = MaterialTheme.typography.headlineMedium)
        Text(S("The app could not finish startup. Nothing was retried."))
        Text(detail)
        Button(onClick = onQuit, modifier = Modifier.fillMaxWidth()) {
            Text(S("Quit"))
        }
    }
}

private fun failureText(failure: Throwable?): String {
    if (failure == null) return S("Startup failed with no recorded reason.")
    val trace = StringWriter()
    failure.printStackTrace(PrintWriter(trace))
    var stack = trace.toString()
    if (stack.length > 50_000) {
        stack = stack.take(50_000) + S("\n... truncated")
    }
    return buildString {
        appendLine(S("applicationId: %1\$s", BuildConfig.APPLICATION_ID))
        appendLine(S("versionName: %1\$s", BuildConfig.VERSION_NAME))
        appendLine(S("buildType: %1\$s", BuildConfig.BUILD_TYPE))
        appendLine(S("model: %1\$s %2\$s", Build.MANUFACTURER, Build.MODEL))
        appendLine()
        append(stack)
    }
}
