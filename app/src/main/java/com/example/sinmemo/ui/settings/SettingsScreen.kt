package com.example.sinmemo.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sinmemo.BuildConfig
import com.example.sinmemo.R
import com.example.sinmemo.data.settings.FontSizeSetting
import com.example.sinmemo.data.settings.ThemeMode
import com.example.sinmemo.data.settings.UserSettings
import com.example.sinmemo.ui.theme.SinMemoTheme

/** S-03 設定。ViewModel から状態を取り出して [SettingsContent] に渡す（設定 詳細設計 7 章）。 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        uiState = uiState,
        // ビルド時に決まる versionName（1.0.0。リリース 基本設計 3 章・6.5）
        versionName = BuildConfig.VERSION_NAME,
        onBack = onBack,
        onThemeClick = viewModel::onThemeClick,
        onFontSizeClick = viewModel::onFontSizeClick,
        onThemeSelect = viewModel::onThemeSelect,
        onFontSizeSelect = viewModel::onFontSizeSelect,
        onDialogConfirm = viewModel::onDialogConfirm,
        onDialogDismiss = viewModel::onDialogDismiss,
    )
}

/** 状態だけで S-03 を描く。プレビューと UI テストではこちらを使う。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    versionName: String,
    onBack: () -> Unit,
    onThemeClick: () -> Unit,
    onFontSizeClick: () -> Unit,
    onThemeSelect: (ThemeMode) -> Unit,
    onFontSizeSelect: (FontSizeSetting) -> Unit,
    onDialogConfirm: () -> Unit,
    onDialogDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SettingItem(
                name = stringResource(R.string.theme),
                value = themeLabel(uiState.settings.themeMode),
                onClick = onThemeClick,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingItem(
                name = stringResource(R.string.font_size),
                value = fontSizeLabel(uiState.settings.fontSize),
                onClick = onFontSizeClick,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // バージョンは表示のみ（押せない）
            SettingItem(name = stringResource(R.string.version), value = versionName, onClick = null)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    when (val dialog = uiState.dialog) {
        is SettingsDialog.Theme -> ThemeDialog(
            selected = dialog.selected,
            onSelect = onThemeSelect,
            onConfirm = onDialogConfirm,
            onDismiss = onDialogDismiss,
        )
        is SettingsDialog.FontSize -> FontSizeDialog(
            selected = dialog.selected,
            onSelect = onFontSizeSelect,
            onConfirm = onDialogConfirm,
            onDismiss = onDialogDismiss,
        )
        null -> Unit
    }
}

/** 1 行目が項目名、2 行目が今の値（薄い色）。[onClick] が null なら押せない。 */
@Composable
private fun SettingItem(name: String, value: String, onClick: (() -> Unit)?) {
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = { Text(value) },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.background,
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    SinMemoTheme {
        SettingsContent(SettingsUiState(), "1.0.0", {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsThemeDialogDarkPreview() {
    SinMemoTheme(darkTheme = true) {
        SettingsContent(
            uiState = SettingsUiState(
                settings = UserSettings(themeMode = ThemeMode.DARK, fontSize = FontSizeSetting.LARGE),
                dialog = SettingsDialog.Theme(selected = ThemeMode.SYSTEM),
            ),
            versionName = "1.0.0",
            onBack = {}, onThemeClick = {}, onFontSizeClick = {}, onThemeSelect = {},
            onFontSizeSelect = {}, onDialogConfirm = {}, onDialogDismiss = {},
        )
    }
}
