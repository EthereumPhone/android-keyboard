package org.futo.inputmethod.latin.uix.theme.presets

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.extendedDarkColorScheme
import org.futo.inputmethod.latin.uix.theme.ThemeOption
import org.futo.inputmethod.latin.uix.theme.selector.ThemePreview

private val darkScheme = extendedDarkColorScheme(
    primary= Color(0xFFE20000),
    onPrimary= Color.Black,
    primaryContainer= Color(0xFF474239),
    onPrimaryContainer= Color(0xFFFFF4E0),
    secondary= Color(0xFFFFF8F0),
    onSecondary= Color(0xFF6B6865),
    secondaryContainer= Color(0xFF3D3D3D),
    onSecondaryContainer= Color(0xFFD6D1CB),
    tertiary= Color(0xFFFFC294),
    onTertiary= Color(0xFF4D2D15),
    tertiaryContainer= Color(0xFF805322),
    onTertiaryContainer= Color(0xFFFFDBC7),
    error= Color(0xFFF2A7A9),
    onError= Color(0xFF783032),
    errorContainer= Color(0xFF801A20),
    onErrorContainer= Color(0xFFF2C2C5),
    outline= Color(0xFFB2B2B2),
    outlineVariant= Color(0xFF545859),
    surface= Color(0xFF2E2E2E),
    onSurface= Color(0xFFFFF2E5),
    onSurfaceVariant= Color(0xFFC7C7C7),
    surfaceContainerHighest= Color(0xFF4D4D4D),
    shadow= Color.Transparent,
    keyboardSurface= Color.Black,
    keyboardSurfaceDim= Color(0xFF24353B),
    keyboardContainer= Color.Transparent, // individual key color + spacebar
    keyboardContainerVariant= Color.Transparent,
    onKeyboardContainer= Color(0xFFE20000), // text color
    keyboardPress= Color.Transparent,
    keyboardFade0= Color(0xFF2E2E2E),
    keyboardFade1= Color(0xFF2E2E2E),
    primaryTransparent= Color(0xFFF0DFD3).copy(alpha = 0.3f),
    onSurfaceTransparent= Color(0xFFFFF0E5).copy(alpha = 0.1f),

    )

val dGen1RedTheme = ThemeOption(
    dynamic = false,
    key = "dGen1RedTheme",
    name = R.string.dgen_red_theme_name,
    available = { true }
) {
    darkScheme
}


@Composable
@Preview
private fun PreviewTheme() {
    ThemePreview(dGen1RedTheme)
}