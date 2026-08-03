package com.davidlang.vehicleexpensesautomated.ui.components

import com.davidlang.vehicleexpensesautomated.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared shutter / capture button visual states (Quick Fill + Start trip). */
enum class CaptureButtonState {
    Live,
    Processing,
    Results,
}

/**
 * White circular shutter (Live), red cancel (Processing), or refresh (Results).
 * Matches Quick Fill portrait control chrome.
 */
@Composable
fun RoundCaptureButton(
    viewState: CaptureButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescriptionLive: String = "Shutter",
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(64.dp)
            .then(
                when (viewState) {
                    CaptureButtonState.Live -> Modifier
                        .background(Color.White, CircleShape)
                        .border(4.dp, Color.Gray, CircleShape)
                    CaptureButtonState.Processing -> Modifier
                        .background(MaterialTheme.colorScheme.error, CircleShape)
                    CaptureButtonState.Results -> Modifier
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                },
            ),
    ) {
        when (viewState) {
            CaptureButtonState.Live -> Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White, CircleShape),
            )
            CaptureButtonState.Processing -> Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.ui_cancel_processing),
                tint = MaterialTheme.colorScheme.onError,
                modifier = Modifier.size(32.dp),
            )
            CaptureButtonState.Results -> Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.settings_retry),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

/** Disk save icon button matching QF control row size. */
@Composable
fun DiskSaveIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    contentDescription: String = "Save",
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(56.dp),
    ) {
        Icon(
            Icons.Filled.Save,
            contentDescription = contentDescription,
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            modifier = Modifier.size(32.dp),
        )
    }
}
