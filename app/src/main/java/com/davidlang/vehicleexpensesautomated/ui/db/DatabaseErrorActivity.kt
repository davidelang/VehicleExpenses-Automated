package com.davidlang.vehicleexpensesautomated.ui.db

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.davidlang.vehicleexpensesautomated.BuildConfig
import com.davidlang.vehicleexpensesautomated.MainActivity
import com.davidlang.vehicleexpensesautomated.VehicleExpensesApplication
import com.davidlang.vehicleexpensesautomated.data.db.DbState
import com.davidlang.vehicleexpensesautomated.data.db.Exporting
import com.davidlang.vehicleexpensesautomated.data.db.Failed
import com.davidlang.vehicleexpensesautomated.data.db.Importing
import com.davidlang.vehicleexpensesautomated.data.db.Ok
import com.davidlang.vehicleexpensesautomated.data.db.Unchecked
import com.davidlang.vehicleexpensesautomated.data.transfer.BackupContents
import com.davidlang.vehicleexpensesautomated.data.transfer.deleteRecursivelyOrThrow
import com.davidlang.vehicleexpensesautomated.ui.theme.VehicleExpensesAutomatedTheme
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import androidx.compose.ui.res.stringResource
import com.davidlang.vehicleexpensesautomated.R
import com.davidlang.vehicleexpensesautomated.ui.util.AppLanguage
import com.davidlang.vehicleexpensesautomated.ui.util.AppStrings

/** Full-screen database open or migration failure. No Hilt and no database access. */
class DatabaseErrorActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        // setApplicationLocales does not reach a ComponentActivity before API 33.
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as VehicleExpensesApplication
        setContent {
            val health by app.dbState.state.collectAsState()
            val progress by app.dbState.progress.collectAsState()
            VehicleExpensesAutomatedTheme {
                when (health) {
                    // Stay on this screen during a transfer so a lost wipe compare-and-set
                    // keeps its refusal. Do not swap to a screen that drops [notice].
                    is Failed, is Exporting, is Importing -> {
                        val failed = failedShown(health)
                        if (failed != null) {
                            DatabaseErrorScreen(
                                failed = failed,
                                transferLine = transferLine(health, progress),
                                cleanupDetail = if (cleanupFailure(failed)) {
                                    cleanupDetail(failed)
                                } else {
                                    null
                                },
                                wipeEnabled = health is Failed,
                                onEmail = { sendReport(failed) },
                                onQuit = { quit() },
                                onWipe = { wipe() },
                            )
                        }
                    }
                    Unchecked -> {
                        LaunchedEffect(Unit) { app.verifyDatabase() }
                    }
                    Ok -> {
                        LaunchedEffect(Unit) { openMain() }
                    }
                }
            }
        }
    }

    private fun openMain() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            ),
        )
        finish()
    }

    private fun quit() {
        finishAndRemoveTask()
        Process.killProcess(Process.myPid())
    }

    /**
     * Takes the import gate before any delete. A lost compare-and-set deletes nothing.
     * The first delete failure restores [Failed]. A later failure still restarts.
     * There is no Importing → Ok.
     */
    private fun wipe(): String? {
        val app = application as VehicleExpensesApplication
        val current = app.dbState.states.value
        if (current !is Failed) return REFUSAL
        val gate = Importing(prior = current, gen = app.dbState.nextGen())
        if (!app.casLogged(current, gate)) return REFUSAL
        var first = true
        fun step(block: () -> Unit): String? {
            try {
                block()
            // kept: failure stays on this call so the screen or caller can continue
            } catch (t: Throwable) {
                if (first) {
                    app.casLogged(gate, gate.prior)
                    Log.e(TAG, "wipe stopped; nothing removed", t)
                    return t.message ?: t.javaClass.simpleName
                }
                Log.e(TAG, "wipe delete failed after a partial delete", t)
            }
            first = false
            return null
        }
        val marker = File(noBackupFilesDir, "datatransfer/import-commit.json")
        step { deleteOne(marker) }?.let { return it }
        step {
            val internal = File(noBackupFilesDir, "datatransfer-staging")
            if (internal.exists()) deleteRecursivelyOrThrow(internal)
        }
        val externalParent = externalCacheDir?.parentFile
        if (externalParent != null) {
            step {
                val external = File(externalParent, "datatransfer-staging")
                if (external.exists()) deleteRecursivelyOrThrow(external)
            }
        }
        val databases = File(dataDir, "databases")
        val base = BackupContents.DB_FILE_NAME
        for (suffix in listOf("", "-wal", "-shm", "-journal")) {
            step { deleteOne(File(databases, base + suffix)) }
        }
        val restart = Intent.makeRestartActivityTask(ComponentName(this, MainActivity::class.java))
        startActivity(restart)
        Process.killProcess(Process.myPid())
        return null
    }

    private fun deleteOne(file: File) {
        if (!file.exists()) return
        try {
            Files.delete(file.toPath())
        // kept: failure stays on this call so the screen or caller can continue
        } catch (_: NoSuchFileException) {
        }
    }

    private fun sendReport(failed: Failed) {
        val report = bugReport(failed)
        val subject = getString(R.string.startup_vehicleexpenses_database_upgrade_failed) +
            "${schemaFrom(failed)} -> ${failed.toVersion}, ${BuildConfig.VERSION_NAME})"
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse(MAILTO)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, report)
        }
        try {
            startActivity(intent)
        // kept: failure stays on this call so the screen or caller can continue
        } catch (_: ActivityNotFoundException) {
            setContent {
                VehicleExpensesAutomatedTheme {
                    CopyReportScreen(report) { copyReport(report) }
                }
            }
        }
    }

    private fun copyReport(report: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.startup_bug_report), report))
    }

    /**
     * Exists checks only. Does not create parents, list children, or open the database.
     * Paths are the commit marker and the two staging dirs.
     */
    private fun cleanupDetail(failed: Failed): String {
        val error = failed.error
        val detail = error.message?.takeIf { it.isNotBlank() }
        val reason = if (detail == null) {
            error.javaClass.name
        } else {
            "${error.javaClass.simpleName}: $detail"
        }
        return reason + "\n" + cleanupDirsLine()
    }

    private fun cleanupDirsLine(): String {
        val marker = File(dataDir, "no_backup/datatransfer/import-commit.json")
        val internal = File(dataDir, "no_backup/datatransfer-staging")
        return place(getString(R.string.startup_commit_marker), marker) + "\n" +
            place(getString(R.string.startup_internal_staging), internal) + "\n" +
            externalStagingLine()
    }

    private fun externalStagingLine(): String {
        val parent = externalCacheDir?.parentFile ?: return getString(R.string.startup_external_staging_unavailable)
        return place(getString(R.string.startup_external_staging), File(parent, "datatransfer-staging"))
    }

    private fun place(label: String, file: File): String =
        "$label (${file.path}): ${if (file.exists()) "exists" else "absent"}"

    companion object {
        private const val TAG = "DatabaseHealth"
        private const val MAILTO = "mailto:david+vehicleexpenses@lang.hm"
        private const val REFUSAL = "An export or import is running; try again when it ends"
    }
}

@Composable
private fun DatabaseErrorScreen(
    failed: Failed,
    transferLine: String?,
    cleanupDetail: String?,
    wipeEnabled: Boolean,
    onEmail: () -> Unit,
    onQuit: () -> Unit,
    onWipe: () -> String?,
) {
    val from = schemaFrom(failed)
    val export = exportCommand()
    var confirm by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.startup_your_data_couldn_t_be_upgraded), style = MaterialTheme.typography.headlineMedium)
        if (cleanupDetail != null) {
            Text(cleanupDetail)
        } else {
            Text(
                stringResource(R.string.startup_the_database_couldn_t_be_opened_or_upgrade, from, failed.toVersion) +
                    stringResource(R.string.startup_nothing_has_been_deleted),
            )
        }
        transferLine?.let { Text(it) }
        Text(export)
        notice?.let { Text(it) }
        Button(onClick = onEmail, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.startup_email_bug_report))
        }
        Button(
            onClick = { confirm = true },
            enabled = wipeEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.startup_wipe_data_and_continue))
        }
        TextButton(onClick = onQuit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.startup_quit))
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            text = {
                Text(
                    stringResource(R.string.startup_this_permanently_deletes_all_vehicles_fuel) +
                        stringResource(R.string.startup_export_first_with_1_s_this_cannot_be_undon, export),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.settings_cancel)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirm = false
                        notice = onWipe()
                    },
                ) { Text(stringResource(R.string.startup_delete_everything)) }
            },
        )
    }
}

@Composable
private fun CopyReportScreen(report: String, onCopy: () -> Unit) {
    var copied by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.startup_no_email_app_found), style = MaterialTheme.typography.headlineMedium)
        Text(report)
        Button(
            onClick = {
                onCopy()
                copied = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (copied) stringResource(R.string.settings_copied) else stringResource(R.string.settings_copy))
        }
    }
}

private fun schemaFrom(failed: Failed): String = failed.fromVersion?.toString() ?: "none"

private fun failedShown(health: DbState): Failed? = when (health) {
    is Failed -> health
    is Exporting -> health.prior as? Failed
    is Importing -> health.prior as? Failed
    else -> null
}

private fun transferLine(health: DbState, progress: String): String? {
    val title = when (health) {
        is Importing -> AppStrings.get(R.string.startup_importing_data)
        is Exporting -> AppStrings.get(R.string.startup_exporting_data)
        else -> return null
    }
    return if (progress.isBlank()) title else "$title\n$progress"
}

/** Startup cleanup records [Failed] with no schema version and one of these reasons. */
private fun cleanupFailure(failed: Failed): Boolean {
    if (failed.fromVersion != null) return false
    val message = failed.error.message ?: return false
    return message.contains(AppStrings.get(R.string.startup_marker_delete_failed)) ||
        message.contains(AppStrings.get(R.string.startup_stale_transfer_staging_couldn_t_be_removed))
}

private fun exportCommand(): String =
    "adb exec-out content read --uri content://${BuildConfig.APPLICATION_ID}.datatransfer/export > backup.tar"

private fun bugReport(failed: Failed): String {
    val trace = StringWriter()
    failed.error.printStackTrace(PrintWriter(trace))
    var stack = trace.toString()
    if (stack.length > 50_000) {
        stack = stack.take(50_000) + AppStrings.get(R.string.startup_truncated)
    }
    return buildString {
        appendLine(AppStrings.get(R.string.startup_applicationid_1_s, BuildConfig.APPLICATION_ID))
        appendLine(AppStrings.get(R.string.startup_versionname_1_s, BuildConfig.VERSION_NAME))
        appendLine(AppStrings.get(R.string.startup_versioncode_1_s, BuildConfig.VERSION_CODE))
        appendLine(AppStrings.get(R.string.startup_buildtype_1_s, BuildConfig.BUILD_TYPE))
        appendLine(AppStrings.get(R.string.startup_test_build_1_s, BuildConfig.TEST_BUILD))
        appendLine(AppStrings.get(R.string.startup_fromschema_1_s, schemaFrom(failed)))
        appendLine(AppStrings.get(R.string.startup_toschema_1_s, failed.toVersion))
        appendLine(AppStrings.get(R.string.startup_manufacturer_1_s, Build.MANUFACTURER))
        appendLine(AppStrings.get(R.string.startup_model_1_s, Build.MODEL))
        appendLine(AppStrings.get(R.string.startup_release_1_s, Build.VERSION.RELEASE))
        appendLine(AppStrings.get(R.string.startup_sdkint_1_s, Build.VERSION.SDK_INT))
        appendLine()
        append(stack)
    }
}
