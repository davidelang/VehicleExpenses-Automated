package com.davidlang.vehicleexpensesautomated.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class PageHelpSpec(
    val title: String,
    val bodyLines: List<String>,
)

/**
 * Current screen registers help for the TopAppBar Info action.
 * Screens call [ProvidePageHelp] / [rememberPageHelpController] from MainActivity.
 */
class PageHelpController {
    var current by mutableStateOf<PageHelpSpec?>(null)
        private set

    fun set(spec: PageHelpSpec?) {
        current = spec
    }

    fun clear() {
        current = null
    }
}

val LocalPageHelpController = compositionLocalOf<PageHelpController?> { null }

/** Register help while this composition is active; clears on leave. */
@Composable
fun RegisterPageHelp(title: String, vararg bodyLines: String) {
    val controller = LocalPageHelpController.current
    DisposableEffect(controller, title, bodyLines.toList()) {
        controller?.set(PageHelpSpec(title, bodyLines.toList()))
        onDispose { controller?.clear() }
    }
}

@Composable
fun rememberPageHelpController(): PageHelpController = remember { PageHelpController() }

/**
 * TopAppBar Info action: only visible when [PageHelpController.current] is non-null.
 */
@Composable
fun PageHelpTopBarAction(
    controller: PageHelpController,
) {
    val spec = controller.current ?: return
    var show by remember { mutableStateOf(false) }
    IconButton(
        onClick = { show = true },
        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
    ) {
        Icon(
            Icons.Filled.Info,
            contentDescription = "Page help",
            tint = MaterialTheme.colorScheme.primary,
        )
    }
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text(spec.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    spec.bodyLines.forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { show = false }) { Text("OK") }
            },
        )
    }
}
