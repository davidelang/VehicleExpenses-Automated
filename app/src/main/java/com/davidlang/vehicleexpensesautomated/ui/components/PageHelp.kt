package com.davidlang.vehicleexpensesautomated.ui.components

import com.davidlang.vehicleexpensesautomated.R

import androidx.compose.ui.res.stringResource

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
 *
 * Registration uses a generation token so [clearIf] does not wipe a newer screen’s
 * help when an older composition disposes after a navigation race (H1).
 */
class PageHelpController {
    var current by mutableStateOf<PageHelpSpec?>(null)
        private set

    private var generation: Long = 0L
    private var ownerId: Long = 0L

    /** Registers [spec] and returns an owner id for [clearIf]. */
    fun set(spec: PageHelpSpec?): Long {
        generation += 1L
        ownerId = generation
        current = spec
        return ownerId
    }

    /** Clears only if [ownerId] still owns the registration. */
    fun clearIf(ownerId: Long) {
        if (this.ownerId == ownerId) {
            current = null
            this.ownerId = 0L
        }
    }

    /** Unconditional clear (tests / rare full reset). Prefer [clearIf]. */
    fun clear() {
        current = null
        ownerId = 0L
    }
}

val LocalPageHelpController = compositionLocalOf<PageHelpController?> { null }

/** Register help while this composition is active; clears on leave only if still owner. */
@Composable
fun RegisterPageHelp(title: String, vararg bodyLines: String) {
    val controller = LocalPageHelpController.current
    val body = bodyLines.toList()
    DisposableEffect(controller, title, body) {
        val id = controller?.set(PageHelpSpec(title, body)) ?: 0L
        onDispose { controller?.clearIf(id) }
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
            contentDescription = stringResource(R.string.ui_page_help),
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
                TextButton(onClick = { show = false }) { Text(stringResource(R.string.settings_ok)) }
            },
        )
    }
}
