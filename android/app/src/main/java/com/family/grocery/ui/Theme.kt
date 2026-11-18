package com.family.grocery.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.family.grocery.Repo

/**
 * Цветовая тема приложения. Кроме «Как в телефоне» (Material You, Android 12+) все темы
 * строятся из оттенка: тона по правилам Material 3 (T40 — основной цвет в светлой теме и т. д.).
 */
data class LookTheme(
    val id: String,
    val title: String,
    val hue: Float,          // оттенок основного цвета, 0..360
    val sat: Float,          // насыщенность основного цвета
    val tertiaryHue: Float,  // оттенок дополнительного цвета
    val neutralSat: Float = 0.08f,
)

val LOOK_THEMES = listOf(
    LookTheme("system", "Как в телефоне", 140f, 0.42f, 190f),
    LookTheme("garden", "Зелень", 140f, 0.42f, 190f),
    LookTheme("ocean", "Океан", 208f, 0.62f, 255f),
    LookTheme("lavender", "Лаванда", 265f, 0.45f, 320f),
    LookTheme("berry", "Ягоды", 338f, 0.55f, 20f),
    LookTheme("citrus", "Цитрус", 28f, 0.78f, 55f, neutralSat = 0.10f),
    LookTheme("graphite", "Графит", 220f, 0.10f, 220f, neutralSat = 0.04f),
)

val LOOK_MODES = listOf(
    "system" to "Как в системе",
    "light" to "Светлая",
    "dark" to "Тёмная",
    "black" to "Чёрная",
)

private fun tone(hue: Float, sat: Float, t: Int): Color =
    Color.hsl(hue, sat.coerceIn(0f, 1f), (t / 100f).coerceIn(0f, 1f))

/** Цветовая схема для темы и режима. black = тёмная с настоящим чёрным фоном (экономит батарею на AMOLED). */
fun buildScheme(t: LookTheme, dark: Boolean, black: Boolean): ColorScheme {
    fun p(x: Int) = tone(t.hue, t.sat, x)
    fun s(x: Int) = tone(t.hue, t.sat * 0.35f, x)
    fun ter(x: Int) = tone(t.tertiaryHue, t.sat * 0.6f, x)
    fun n(x: Int) = tone(t.hue, t.neutralSat, x)
    fun nv(x: Int) = tone(t.hue, t.neutralSat * 2f, x)
    return if (!dark) {
        lightColorScheme(
            primary = p(40), onPrimary = Color.White, primaryContainer = p(88), onPrimaryContainer = p(12),
            inversePrimary = p(80),
            secondary = s(40), onSecondary = Color.White, secondaryContainer = s(90), onSecondaryContainer = s(12),
            tertiary = ter(40), onTertiary = Color.White, tertiaryContainer = ter(90), onTertiaryContainer = ter(12),
            background = n(98), onBackground = n(10),
            surface = n(98), onSurface = n(10),
            surfaceVariant = nv(90), onSurfaceVariant = nv(30),
            surfaceTint = p(40),
            inverseSurface = n(20), inverseOnSurface = n(95),
            outline = nv(50), outlineVariant = nv(82),
            surfaceBright = n(98), surfaceDim = n(87),
            surfaceContainerLowest = Color.White, surfaceContainerLow = n(96), surfaceContainer = n(94),
            surfaceContainerHigh = n(92), surfaceContainerHighest = n(90),
        )
    } else {
        val bg = if (black) Color.Black else n(6)
        darkColorScheme(
            primary = p(80), onPrimary = p(20), primaryContainer = p(30), onPrimaryContainer = p(90),
            inversePrimary = p(40),
            secondary = s(80), onSecondary = s(20), secondaryContainer = s(30), onSecondaryContainer = s(90),
            tertiary = ter(80), onTertiary = ter(20), tertiaryContainer = ter(30), onTertiaryContainer = ter(90),
            background = bg, onBackground = n(90),
            surface = bg, onSurface = n(90),
            surfaceVariant = nv(30), onSurfaceVariant = nv(80),
            surfaceTint = p(80),
            inverseSurface = n(90), inverseOnSurface = n(20),
            outline = nv(60), outlineVariant = nv(30),
            surfaceBright = n(24), surfaceDim = bg,
            surfaceContainerLowest = if (black) Color.Black else n(4),
            surfaceContainerLow = if (black) n(7) else n(10),
            surfaceContainer = if (black) n(10) else n(12),
            surfaceContainerHigh = if (black) n(14) else n(17),
            surfaceContainerHighest = if (black) n(18) else n(22),
        )
    }
}

/** Material You из обоев, а для «Чёрной» — тот же цвет на чёрном фоне. */
private fun systemScheme(ctx: android.content.Context, dark: Boolean, black: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < 31) return null
    if (!dark) return dynamicLightColorScheme(ctx)
    val d = dynamicDarkColorScheme(ctx)
    return if (!black) d else d.copy(
        background = Color.Black, surface = Color.Black, surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0E0E10), surfaceContainer = Color(0xFF141416),
        surfaceContainerHigh = Color(0xFF1B1B1E), surfaceContainerHighest = Color(0xFF232326),
    )
}

@Composable
fun schemeFor(themeId: String, mode: String): ColorScheme {
    val ctx = LocalContext.current
    val sysDark = isSystemInDarkTheme()
    val dark = when (mode) { "light" -> false; "dark", "black" -> true; else -> sysDark }
    val black = mode == "black"
    val t = LOOK_THEMES.firstOrNull { it.id == themeId } ?: LOOK_THEMES.first()
    return (if (t.id == "system") systemScheme(ctx, dark, black) else null) ?: buildScheme(t, dark, black)
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val themeId by Repo.appTheme.collectAsState()
    val mode by Repo.appMode.collectAsState()
    val scheme = schemeFor(themeId, mode)
    val view = LocalView.current
    val activity = view.context as? Activity
    // Цвет значков в строке состояния — под тему (только у главного экрана, окна над рабочим столом не трогаем).
    if (!view.isInEditMode && activity is MainActivity) {
        val light = scheme.background.luminance() > 0.5f
        SideEffect {
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
    }
    MaterialTheme(colorScheme = scheme, shapes = AppShapes, content = content)
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
