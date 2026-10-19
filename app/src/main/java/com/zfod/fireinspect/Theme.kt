package com.zfod.fireinspect

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection

/** Colours of the app itself (the report has its own palettes in [ReportTheme]). */
class AppColors(
    val bg: Color,
    val surface: Color,
    val card: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val dark: Boolean,
)

val ColOk = Color(0xFF22C55E)
val ColBad = Color(0xFFEF4444)
val ColNa = Color(0xFF94A3B8)
val ColPending = Color(0xFFA855F7)
val ColRate = Color(0xFF06B6D4)

fun parseHex(s: String): Color {
    val h = s.removePrefix("#")
    return Color(android.graphics.Color.parseColor("#$h"))
}

fun shiftColor(c: Char): Color = parseHex(SHIFT_COLORS[c] ?: "#3B82F6")

/** mode: 0 = dark, 1 = white, 2 = eye care. */
fun appColors(mode: Int, accentIndex: Int): AppColors {
    val accent = Color(APP_ACCENTS[accentIndex.coerceIn(0, APP_ACCENTS.size - 1)])
    return when (mode) {
        1 -> AppColors(
            bg = Color(0xFFF1F4F9), surface = Color(0xFFFFFFFF), card = Color(0xFFFFFFFF), line = Color(0xFFD3DBE7),
            text = Color(0xFF0F172A), muted = Color(0xFF5B6677), accent = accent, onAccent = Color.White, dark = false,
        )
        2 -> AppColors(
            bg = Color(0xFFF2E8CF), surface = Color(0xFFF7EFD9), card = Color(0xFFFAF3E0), line = Color(0xFFD8C9A3),
            text = Color(0xFF3A2F1B), muted = Color(0xFF7A6A48), accent = accent, onAccent = Color.White, dark = false,
        )
        else -> AppColors(
            bg = Color(0xFF0B1220), surface = Color(0xFF101A2E), card = Color(0xFF14213A), line = Color(0xFF24365A),
            text = Color(0xFFE8EEFC), muted = Color(0xFF8FA3C7), accent = accent, onAccent = Color.White, dark = true,
        )
    }
}

val LocalColors = staticCompositionLocalOf { appColors(0, 0) }
val LocalLang = staticCompositionLocalOf { Lang.EN }

/** Translates [key] with the current app language. */
@Composable
fun t(key: String): String = Tr.get(LocalLang.current, key)

@Composable
fun tf(key: String, vararg a: Any): String = Tr.f(LocalLang.current, key, *a)

@Composable
fun AppTheme(state: AppState, content: @Composable () -> Unit) {
    val c = appColors(state.appMode, state.accent)
    val scheme = if (c.dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, background = c.bg, onBackground = c.text,
            surface = c.surface, onSurface = c.text, surfaceVariant = c.card, onSurfaceVariant = c.muted,
            outline = c.line, outlineVariant = c.line,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, background = c.bg, onBackground = c.text,
            surface = c.surface, onSurface = c.text, surfaceVariant = c.card, onSurfaceVariant = c.muted,
            outline = c.line, outlineVariant = c.line,
        )
    }
    val dir = if (state.lang == Lang.AR) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalColors provides c, LocalLang provides state.lang, LocalLayoutDirection provides dir) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
