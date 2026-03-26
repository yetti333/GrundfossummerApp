package com.example.grundfos_summer_app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Immutable
data class GrundfosColors(
    val red: Color = GrundfosRed,
    val black: Color = GrundfosBlack,
    val darkGrey: Color = GrundfosDarkGrey,
    val ledGreen: Color = LedGreen,
    val ledYellow: Color = LedYellow,
    val ledRed: Color = LedRed,
    val uiGrey1: Color = UiGrey1,
    val uiGrey2: Color = UiGrey2,
    val uiGrey3: Color = UiGrey3,
    val textWhite: Color = TextWhite,
    val textGrey: Color = TextGrey
)

val LocalGrundfosColors = staticCompositionLocalOf { GrundfosColors() }

object GrundfosTheme {
    val colors: GrundfosColors
        @Composable
        @ReadOnlyComposable
        get() = LocalGrundfosColors.current
}

val GrundfosColorScheme = darkColorScheme(
    primary = GrundfosRed,
    onPrimary = TextWhite,
    background = GrundfosBlack,
    onBackground = TextWhite,
    surface = GrundfosDarkGrey,
    onSurface = TextWhite,
    secondary = UiGrey2,
    onSecondary = TextWhite,
    error = LedRed,
    onError = TextWhite
)

private val LightColorScheme = lightColorScheme(
    primary = GrundfosRed,
    secondary = UiGrey3,
    tertiary = LedGreen,
    background = Color.White,
    surface = Color(0xFFF5F5F5),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = GrundfosBlack,
    onSurface = GrundfosBlack
)

@Composable
fun GrundfosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) GrundfosColorScheme else LightColorScheme
    val grundfosColors = GrundfosColors()

    CompositionLocalProvider(
        LocalGrundfosColors provides grundfosColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}

@Composable
fun GrundfossummerAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Jen přesměrujeme na hlavní GrundfosTheme
    GrundfosTheme(darkTheme = darkTheme, content = content)
}

@Composable
fun GrundfosControllerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    GrundfosTheme(darkTheme = darkTheme, content = content)
}
