package com.starlink.scanner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = StarlinkCyan,
    secondary = StarlinkGreen,
    tertiary = StarlinkAmber,
    background = StarlinkDarkBg,
    surface = StarlinkCardBg
)

@Composable
fun StarlinkScannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
