package com.davidlang.vehicleexpensesautomated.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * OutlinedTextField where hardware ←/→ (and DPAD) move the caret instead of focus,
 * and **Home** / **End** jump to start / end of the field text.
 *
 * Soft ◀ ▶ appear **only when**:
 * - [showCaretButtons] is true (caller opts in for numeric fields),
 * - the field is focused (IME active / editing),
 * - not read-only,
 * - no hardware QWERTY keyboard (arrows available there),
 * - [KeyboardOptions.keyboardType] is a number-style pad (no caret keys on typical IME).
 *
 * Soft buttons are drawn **under** the field so they never steal the field's max width
 * (side-by-side icons crushed QF odo/cost/vol into ~0 width).
 * No soft Home/End keys — hardware only (QF NumericKeypad stays 4×4 without Home/End).
 */
@Composable
fun CaretEnabledOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    /**
     * Opt-in: allow soft ◀ ▶ when a number-style IME is up and there is no hardware keyboard.
     * Text keyboards / hardware keyboards do not get soft buttons.
     * Soft buttons are always under the field (never side-by-side) so tight max-width fields stay usable.
     */
    showCaretButtons: Boolean = false,
    /**
     * When non-null, external caret (e.g. custom NumericKeypad) is mirrored into the field.
     */
    caretIndex: Int? = null,
    onCaretIndexChange: ((Int) -> Unit)? = null,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
) {
    var fieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = value,
                selection = TextRange(value.length),
            ),
        )
    }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, caretIndex) {
        if (caretIndex != null) {
            val c = caretIndex.coerceIn(0, value.length)
            if (fieldValue.text != value ||
                fieldValue.selection.start != c ||
                fieldValue.selection.end != c
            ) {
                fieldValue = TextFieldValue(text = value, selection = TextRange(c))
            }
        } else if (fieldValue.text != value) {
            val sel = fieldValue.selection.end.coerceIn(0, value.length)
            fieldValue = TextFieldValue(text = value, selection = TextRange(sel))
        }
    }

    fun setCaret(index: Int) {
        val t = fieldValue.text
        val next = index.coerceIn(0, t.length)
        fieldValue = fieldValue.copy(selection = TextRange(next))
        onCaretIndexChange?.invoke(next)
    }

    fun nudgeCaret(delta: Int) {
        val t = fieldValue.text
        val start = fieldValue.selection.start
        val end = fieldValue.selection.end
        val caret = if (start == end) start else if (delta < 0) minOf(start, end) else maxOf(start, end)
        setCaret(caret + delta)
    }

    val configuration = LocalConfiguration.current
    val hasHardwareKeyboard =
        configuration.keyboard == Configuration.KEYBOARD_QWERTY
    val numberStyleIme = when (keyboardOptions.keyboardType) {
        KeyboardType.Number,
        KeyboardType.NumberPassword,
        KeyboardType.Decimal,
        KeyboardType.Phone,
        -> true
        else -> false
    }
    // Soft caret only when number pad is active (no arrow keys on typical IME) and no HW keys.
    val showSoftCaret = showCaretButtons &&
        focused &&
        !readOnly &&
        enabled &&
        numberStyleIme &&
        !hasHardwareKeyboard

    val fieldMod = Modifier
        .onFocusChanged { focused = it.isFocused }
        .focusProperties {
            left = FocusRequester.Cancel
            right = FocusRequester.Cancel
        }
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionLeft -> {
                    nudgeCaret(-1)
                    true
                }
                Key.DirectionRight -> {
                    nudgeCaret(1)
                    true
                }
                // Home / MoveHome → start of field content (collapse selection to 0).
                // Compose exposes Key.Home + Key.MoveHome (no Key.End; end is MoveEnd).
                Key.MoveHome, Key.Home -> {
                    setCaret(0)
                    true
                }
                // MoveEnd → end of field content.
                Key.MoveEnd -> {
                    setCaret(fieldValue.text.length)
                    true
                }
                else -> false
            }
        }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = fieldValue,
            onValueChange = { tv ->
                fieldValue = tv
                if (tv.text != value) onValueChange(tv.text)
                val caret = if (tv.selection.collapsed) {
                    tv.selection.start
                } else {
                    tv.selection.end
                }
                onCaretIndexChange?.invoke(caret.coerceIn(0, tv.text.length))
            },
            modifier = Modifier
                .fillMaxWidth()
                .then(fieldMod),
            label = label,
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            maxLines = maxLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            isError = isError,
            supportingText = supportingText,
        )
        if (showSoftCaret) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { nudgeCaret(-1) },
                    modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 40.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Move caret left",
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(
                    onClick = { nudgeCaret(1) },
                    modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 40.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Move caret right",
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}
