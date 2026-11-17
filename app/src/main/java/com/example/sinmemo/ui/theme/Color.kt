package com.example.sinmemo.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// モックアップ（docs/mockup、モックアップ検証）の配色を Material 3 の ColorScheme に当てはめたもの（共通 基本設計 3 章・詳細設計 8.1）。

internal val LightColors = lightColorScheme(
    primary = Color(0xFF2550C8),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1B1C1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1C1F),
    onSurfaceVariant = Color(0xFF5A5E6B),
    outlineVariant = Color(0xFFE3E5EA),
    surfaceContainer = Color(0xFFF3F4F7),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    error = Color(0xFFB3261E),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB8FF),
    onPrimary = Color(0xFF0F1E46),
    background = Color(0xFF141518),
    onBackground = Color(0xFFE7E8EC),
    surface = Color(0xFF141518),
    onSurface = Color(0xFFE7E8EC),
    onSurfaceVariant = Color(0xFFA9ADB8),
    outlineVariant = Color(0xFF2E3138),
    surfaceContainer = Color(0xFF1E2024),
    surfaceContainerHigh = Color(0xFF272A31),
    error = Color(0xFFFF9A8F),
)
