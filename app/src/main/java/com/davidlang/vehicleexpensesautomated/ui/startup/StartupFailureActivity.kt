package com.davidlang.vehicleexpensesautomated.ui.startup

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

/** OpenCV, Paddle, or rclone failed during startup. No Paddle and no OpenCV. */
class StartupFailureActivity : ComponentActivity() {

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
        Text("Startup failed", style = MaterialTheme.typography.headlineMedium)
        Text("The app could not finish startup. Nothing was retried.")
        Text(detail)
        Button(onClick = onQuit, modifier = Modifier.fillMaxWidth()) {
            Text("Quit")
        }
    }
}

private fun failureText(failure: Throwable?): String {
    if (failure == null) return "Startup failed with no recorded reason."
    val trace = StringWriter()
    failure.printStackTrace(PrintWriter(trace))
    var stack = trace.toString()
    if (stack.length > 50_000) {
        stack = stack.take(50_000) + "\n... truncated"
    }
    return buildString {
        appendLine("applicationId: ${BuildConfig.APPLICATION_ID}")
        appendLine("versionName: ${BuildConfig.VERSION_NAME}")
        appendLine("buildType: ${BuildConfig.BUILD_TYPE}")
        appendLine("model: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine()
        append(stack)
    }
}
