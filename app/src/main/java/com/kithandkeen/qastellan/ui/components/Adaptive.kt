package com.kithandkeen.qastellan.ui.components

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kithandkeen.qastellan.ui.theme.LocalTokens

/**
 * How the screen is laid out.
 * - wide: landscape phones, tablets and TVs (600 dp or wider): side navigation, two columns.
 * - tv: Android TV / Google TV: remote control, safe margins, no touch-only features.
 */
data class Layout(val wide: Boolean = false, val tv: Boolean = false)

val LocalLayout = staticCompositionLocalOf { Layout() }

fun isTv(context: Context): Boolean =
    context.getSystemService(UiModeManager::class.java)?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature("android.software.leanback")

/**
 * Shows which control a remote or keyboard has selected: a ring and a slight
 * enlargement. Touch users never see it (taps don't move focus in Compose).
 * Put it right before .clickable / .toggleable in the modifier chain.
 */
fun Modifier.focusRing(shape: Shape, grow: Boolean = true): Modifier = composed {
    val t = LocalTokens.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused && grow) 1.04f else 1f, label = "focus")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .onFocusChanged { focused = it.hasFocus }
        .then(if (focused) Modifier.border(3.dp, t.text, shape) else Modifier)
}
