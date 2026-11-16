package com.kithandkeen.qastellan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kithandkeen.qastellan.R

/** Colours from the Qastellan mockups: indigo for actions, teal for safe, hot pink for threats. */
@Immutable
data class Tokens(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val solid: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val ok: Color,
    val okSoft: Color,
    val warn: Color,
    val onWarn: Color,
    val warnSoft: Color,
    val pattern: Color,
    val patternAlpha: Float,
    val nav: Color,
    val track: Color,
    val ghost: Color,
)

val LightTokens = Tokens(
    dark = false,
    bg = Color(0xFFF4F6FA),
    surface = Color(0xD9FFFFFF), // 85% opaque
    solid = Color(0xD9FFFFFF),
    border = Color(0xFFE2E8F0),
    text = Color(0xFF0F172A),
    muted = Color(0xFF475569),
    accent = Color(0xFF4F46E5),
    onAccent = Color.White,
    ok = Color(0xFF0F766E),
    okSoft = Color(0xFFDDF3F0),
    warn = Color(0xFFC8175D),
    onWarn = Color.White,
    warnSoft = Color(0xFFFCE7F3),
    pattern = Color(0xFF4F46E5),
    patternAlpha = 0.22f,
    nav = Color(0xD9FFFFFF),
    track = Color(0xFF94A3B8),
    ghost = Color(0xFFEEF2F7),
)

val DarkTokens = Tokens(
    dark = true,
    bg = Color(0xFF0A1020),
    surface = Color(0xD9121B2E),
    solid = Color(0xD9121B2E),
    border = Color(0x2E94A3B8),
    text = Color(0xFFE6EDF7),
    muted = Color(0xFFA9B4C4),
    accent = Color(0xFF818CF8),
    onAccent = Color(0xFF0A0F24),
    ok = Color(0xFF2DD4BF),
    okSoft = Color(0x1F2DD4BF),
    warn = Color(0xFFFF5FA2),
    onWarn = Color(0xFF1A0510),
    warnSoft = Color(0x24FF5FA2),
    pattern = Color(0xFF818CF8),
    patternAlpha = 0.34f,
    nav = Color(0xD90A1020),
    track = Color(0xFF3B4A63),
    ghost = Color(0x1A94A3B8),
)

val LocalTokens = staticCompositionLocalOf { LightTokens }

// Static weights rather than one variable font: some phones cache the first
// weight of a variable font and draw every weight with it.
val Outfit = FontFamily(
    Font(R.font.outfit_light, FontWeight.Light),
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
)
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)
val Mono = FontFamily(
    Font(R.font.mono_regular, FontWeight.Normal),
    Font(R.font.mono_medium, FontWeight.Medium),
)

object Type {
    val title = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 34.sp)
    val heading = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp)
    val body = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 22.sp)
    val bodyStrong = body.copy(fontWeight = FontWeight.SemiBold)
    val small = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp)
    val label = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val mono = TextStyle(fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.sp)
}

enum class Appearance { SYSTEM, LIGHT, DARK }

@Composable
fun QastellanTheme(appearance: Appearance = Appearance.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (appearance) {
        Appearance.SYSTEM -> isSystemInDarkTheme()
        Appearance.LIGHT -> false
        Appearance.DARK -> true
    }
    val t = if (dark) DarkTokens else LightTokens
    val scheme = if (dark) {
        darkColorScheme(primary = t.accent, onPrimary = t.onAccent, background = t.bg, surface = t.bg, onSurface = t.text, error = t.warn)
    } else {
        lightColorScheme(primary = t.accent, onPrimary = t.onAccent, background = t.bg, surface = t.bg, onSurface = t.text, error = t.warn)
    }
    CompositionLocalProvider(LocalTokens provides t) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
