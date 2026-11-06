package com.davidlang.vehicleexpensesautomated.ui.components

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.davidlang.vehicleexpensesautomated.ui.util.S

/** Shared shutter / capture button visual states (Fill + Start trip). */
enum class CaptureButtonState {
    Live,
    Processing,
    Results,
    Restart,
}

/**
 * White circular shutter (Live), red cancel (Processing), the word Restart, or refresh (Results).
 * Matches Fill portrait control chrome.
 */
@Composable
fun RoundCaptureButton(
    viewState: CaptureButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescriptionLive: String = S("Shutter"),
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
                    CaptureButtonState.Restart -> Modifier
                        .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                },
            ),
    ) {
        when (viewState) {
            CaptureButtonState.Live -> Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = contentDescriptionLive }
                    .background(Color.White, CircleShape),
            )
            CaptureButtonState.Processing -> Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = S("Cancel processing"),
                tint = MaterialTheme.colorScheme.onError,
                modifier = Modifier.size(32.dp),
            )
            CaptureButtonState.Restart -> Text(
                text = S("Restart"),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
            CaptureButtonState.Results -> Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = S("Retry"),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .size(32.dp)
                    .scale(scaleX = -1f, scaleY = 1f),
            )
        }
    }
}

/** Disk save icon button matching QF control row size. */
@Composable
fun DiskSaveIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    contentDescription: String = S("Save"),
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
