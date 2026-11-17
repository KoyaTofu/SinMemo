package com.example.sinmemo.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.sinmemo.R
import com.example.sinmemo.data.settings.FontSizeSetting
import com.example.sinmemo.data.settings.ThemeMode

/** D-03 テーマ（設定 基本設計 2.2） */
@Composable
internal fun ThemeDialog(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ChoiceDialog(
        title = stringResource(R.string.theme),
        options = ThemeMode.entries,
        selected = selected,
        label = { themeLabel(it) },
        onSelect = onSelect,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** D-04 文字サイズ（設定 基本設計 2.2） */
@Composable
internal fun FontSizeDialog(
    selected: FontSizeSetting,
    onSelect: (FontSizeSetting) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ChoiceDialog(
        title = stringResource(R.string.font_size),
        options = FontSizeSetting.entries,
        selected = selected,
        label = { fontSizeLabel(it) },
        onSelect = onSelect,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** 選択肢をラジオボタンで並べ、「キャンセル」「OK」を持つダイアログ。選んだだけでは保存しない（設定 基本設計 決定事項 1） */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = option == selected,
                                onClick = { onSelect(option) },
                                role = Role.RadioButton,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 押せるのは行全体。ボタン自身は押せないようにして、読み上げが二重にならないようにする
                        RadioButton(selected = option == selected, onClick = null)
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
internal fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    },
)

@Composable
internal fun fontSizeLabel(size: FontSizeSetting): String = stringResource(
    when (size) {
        FontSizeSetting.SMALL -> R.string.font_size_small
        FontSizeSetting.MEDIUM -> R.string.font_size_medium
        FontSizeSetting.LARGE -> R.string.font_size_large
    },
)
