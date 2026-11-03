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
import androidx.compose.ui.res.stringResource
import com.davidlang.vehicleexpensesautomated.R
import com.davidlang.vehicleexpensesautomated.ui.util.AppStrings

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
        Text(stringResource(R.string.startup_startup_failed), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.startup_the_app_could_not_finish_startup_nothing_w))
        Text(detail)
        Button(onClick = onQuit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.startup_quit))
        }
    }
}

private fun failureText(failure: Throwable?): String {
    if (failure == null) return AppStrings.get(R.string.startup_startup_failed_with_no_recorded_reason)
    val trace = StringWriter()
    failure.printStackTrace(PrintWriter(trace))
    var stack = trace.toString()
    if (stack.length > 50_000) {
        stack = stack.take(50_000) + AppStrings.get(R.string.startup_truncated)
    }
    return buildString {
        appendLine(AppStrings.get(R.string.startup_applicationid_1_s, BuildConfig.APPLICATION_ID))
        appendLine(AppStrings.get(R.string.startup_versionname_1_s, BuildConfig.VERSION_NAME))
        appendLine(AppStrings.get(R.string.startup_buildtype_1_s, BuildConfig.BUILD_TYPE))
        appendLine(AppStrings.get(R.string.startup_model_1_s_2_s, Build.MANUFACTURER, Build.MODEL))
        appendLine()
        append(stack)
    }
}
