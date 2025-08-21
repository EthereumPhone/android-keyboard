package org.futo.inputmethod.latin.uix.theme.presets

import android.content.Context
import android.graphics.Color
import android.provider.Settings
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.toArgb
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.KeyboardColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption

private fun getSystemAccentColor(context: Context): androidx.compose.ui.graphics.Color {
    return try {
        val accentColor = Settings.Secure.getInt(context.contentResolver, "systemui_accent_color")
        androidx.compose.ui.graphics.Color(accentColor)
    } catch (e: Exception) {
        // Fallback to a default accent color if unable to get system accent
        androidx.compose.ui.graphics.Color(0xFF4CAF50) // Material Green
    }
}

private fun systemAccentColorScheme(context: Context): KeyboardColorScheme {
    val accentColor = getSystemAccentColor(context)
    val black = androidx.compose.ui.graphics.Color.Black
    val gray = androidx.compose.ui.graphics.Color(0xFF1A1A1A)
    val lightGray = androidx.compose.ui.graphics.Color(0xFF2A2A2A)
    
    val colorScheme = darkColorScheme(
        primary = accentColor,
        onPrimary = black,
        primaryContainer = accentColor.copy(alpha = 0.2f),
        onPrimaryContainer = accentColor,
        
        secondary = accentColor.copy(alpha = 0.8f),
        onSecondary = black,
        secondaryContainer = accentColor.copy(alpha = 0.15f),
        onSecondaryContainer = accentColor,
        
        tertiary = accentColor.copy(alpha = 0.6f),
        onTertiary = black,
        tertiaryContainer = accentColor.copy(alpha = 0.1f),
        onTertiaryContainer = accentColor,
        
        background = black,
        onBackground = accentColor,
        
        surface = black,
        onSurface = accentColor,
        surfaceVariant = gray,
        onSurfaceVariant = accentColor,
        
        surfaceTint = accentColor,
        inverseSurface = accentColor,
        inverseOnSurface = black,
        
        error = androidx.compose.ui.graphics.Color(0xFFCF6679),
        onError = black,
        errorContainer = androidx.compose.ui.graphics.Color(0xFF8B0020),
        onErrorContainer = androidx.compose.ui.graphics.Color(0xFFFFB4AB),
        
        outline = accentColor.copy(alpha = 0.3f),
        outlineVariant = accentColor.copy(alpha = 0.2f),
        scrim = black.copy(alpha = 0.8f)
    )
    
    return KeyboardColorScheme(
        base = colorScheme,
        extended = org.futo.inputmethod.latin.uix.ExtraColors(
            keyboardSurface = black,
            keyboardSurfaceDim = gray,
            keyboardContainer = gray,
            keyboardContainerVariant = lightGray,
            onKeyboardContainer = accentColor,
            keyboardPress = accentColor.copy(alpha = 0.3f),
            keyboardBackgroundGradient = null,
            primaryTransparent = accentColor.copy(alpha = 0.1f),
            onSurfaceTransparent = accentColor.copy(alpha = 0.1f),
            navigationBarColor = black
        )
    )
}

val SystemAccentTheme = ThemeOption(
    dynamic = true,
    key = "SystemAccentTheme",
    name = R.string.system_accent_theme,
    available = { true },
    obtainColors = { context -> systemAccentColorScheme(context) }
)
