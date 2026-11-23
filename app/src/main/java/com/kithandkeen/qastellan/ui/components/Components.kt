package com.kithandkeen.qastellan.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kithandkeen.qastellan.ui.theme.LocalTokens
import com.kithandkeen.qastellan.ui.theme.Outfit
import com.kithandkeen.qastellan.ui.theme.Type
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ------------------------------------------------------------------ background

/** The isometric cube pattern, strongest at the bottom and fading out towards the top. */
@Composable
fun CubeBackground(modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Canvas(
        modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.15f to Color.Transparent,
                        0.65f to Color.Black.copy(alpha = 0.75f),
                        1f to Color.Black,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        val r = 18.dp.toPx()
        val w = sqrt(3f) * r
        val path = Path()
        var row = 0
        while (row * 1.5f * r < size.height * 0.95f) {
            val cy = size.height + r - row * 1.5f * r
            var cx = -w + if (row % 2 == 1) w / 2 else 0f
            while (cx < size.width + w) {
                for (k in 0..6) {
                    val a = Math.toRadians((60.0 * k - 90)).toFloat()
                    val x = cx + r * cos(a)
                    val y = cy + r * sin(a)
                    if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                for (deg in intArrayOf(90, 210, 330)) {
                    val b = Math.toRadians(deg.toDouble()).toFloat()
                    path.moveTo(cx, cy)
                    path.lineTo(cx + r * cos(b), cy + r * sin(b))
                }
                cx += w
            }
            row++
        }
        drawPath(path, t.pattern.copy(alpha = t.patternAlpha), style = Stroke(width = 1.dp.toPx()))
    }
}

/** A full screen: background colour, cube pattern, content and an optional bottom bar. */
@Composable
fun Screen(
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalTokens.current
    val layout = LocalLayout.current
    Box(Modifier.fillMaxSize().background(t.bg)) {
        CubeBackground()
        if (layout.wide) {
            // Landscape, tablet and TV: navigation on the left, content centred at a readable width.
            // TVs crop their edges, so keep everything inside the safe area.
            val edge = if (layout.tv) Modifier.padding(horizontal = 48.dp, vertical = 27.dp) else Modifier.systemBarsPadding()
            Row(Modifier.fillMaxSize().then(edge)) {
                if (bottomBar != null) bottomBar()
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 960.dp).fillMaxSize(), content = content)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Column(Modifier.weight(1f).fillMaxWidth(), content = content)
                if (bottomBar != null) bottomBar() else Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

// ------------------------------------------------------------------ logo

/** The Qastellan mark: a shield with a Q drawn as a magnifying glass. */
@Composable
fun Glyph(size: Dp, fill: Color, stroke: Color, ringWidth: Float = 8f, modifier: Modifier = Modifier, description: String? = null) {
    Canvas(
        modifier
            .size(size)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        val s = this.size.width / 120f
        val shield = Path().apply {
            moveTo(60 * s, 8 * s)
            lineTo(100 * s, 21 * s)
            lineTo(100 * s, 55 * s)
            cubicTo(100 * s, 81 * s, 83 * s, 99 * s, 60 * s, 112 * s)
            cubicTo(37 * s, 99 * s, 20 * s, 81 * s, 20 * s, 55 * s)
            lineTo(20 * s, 21 * s)
            close()
        }
        drawPath(shield, fill)
        drawCircle(stroke, radius = 18 * s, center = Offset(60 * s, 57 * s), style = Stroke(ringWidth * s))
        drawLine(stroke, Offset(71.6f * s, 70.8f * s), Offset(78.6f * s, 79.2f * s), strokeWidth = (ringWidth + 1) * s, cap = StrokeCap.Round)
    }
}

// ------------------------------------------------------------------ building blocks

@Composable
fun QCard(
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    padding: Dp = 18.dp,
    background: Color? = null,
    borderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background ?: t.surface)
            .border(1.dp, borderColor ?: t.border, shape)
            .padding(padding),
        content = content,
    )
}

@Composable
fun MonoLabel(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    Text(text.uppercase(), modifier, style = Type.mono, color = color ?: LocalTokens.current.muted)
}

@Composable
fun SectionLabel(text: String) {
    MonoLabel(text, Modifier.padding(top = 6.dp, start = 2.dp))
}

enum class ButtonKind { PRIMARY, DANGER, SECONDARY, TEXT }

@Composable
fun QButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    height: Dp = 54.dp,
    enabled: Boolean = true,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(height / 2)
    val (bg, fg, border) = when (kind) {
        ButtonKind.PRIMARY -> Triple(t.accent, t.onAccent, null)
        ButtonKind.DANGER -> Triple(t.warn, t.onWarn, null)
        ButtonKind.SECONDARY -> Triple(t.surface, t.text, t.border)
        ButtonKind.TEXT -> Triple(Color.Transparent, t.accent, null)
    }
    Row(
        modifier
            .height(height)
            .clip(shape)
            .background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .focusRing(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke(this)
        Text(text, color = fg.copy(alpha = if (enabled) 1f else 0.5f), style = Type.bodyStrong.copy(fontSize = 16.sp), textAlign = TextAlign.Center)
    }
}

@Composable
fun Toggle(checked: Boolean, label: String, hint: String, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Row(
        modifier
            .fillMaxWidth()
            .focusRing(RoundedCornerShape(12.dp), grow = false)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.bodyStrong, color = t.text)
            Text(hint, style = Type.small, color = t.muted)
        }
        Box(
            Modifier
                .width(46.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (checked) t.accent else t.track)
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(Color.White))
        }
    }
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalTokens.current.border))
}

/** Back arrow plus title, used by every inner screen. */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)?) {
    val t = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).focusRing(CircleShape).clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack)
                    .semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.back, t.text, 24.dp) }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(title, style = Type.heading, color = t.text, modifier = Modifier.semantics { heading() })
    }
}

@Composable
fun BigTitle(text: String) {
    Text(
        text,
        style = Type.title,
        color = LocalTokens.current.text,
        modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 6.dp).semantics { heading() },
    )
}

/** Small round status mark: teal tick for passed, pink "!" for needs attention. */
@Composable
fun StatusMark(ok: Boolean, size: Dp = 22.dp) {
    val t = LocalTokens.current
    Icon(if (ok) Icons.checkCircle else Icons.alertCircle, if (ok) t.ok else t.warn, size, if (ok) "Passed" else "Needs attention")
}

@Composable
fun Initials(text: String, size: Dp = 44.dp) {
    val t = LocalTokens.current
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(t.ghost),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.take(2).uppercase(), style = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 16.sp), color = t.text)
    }
}

@Composable
fun Chip(text: String, color: Color, background: Color) {
    Text(
        text,
        style = Type.label,
        color = color,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(background).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun ChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val t = LocalTokens.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Text(
                label,
                style = Type.label.copy(fontSize = 13.sp),
                color = if (on) t.onAccent else t.text,
                modifier = Modifier
                    .clip(RoundedCornerShape(17.dp))
                    .background(if (on) t.accent else t.surface)
                    .border(1.dp, if (on) t.accent else t.border, RoundedCornerShape(17.dp))
                    .focusRing(RoundedCornerShape(17.dp))
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .padding(horizontal = 13.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun BoxScope.CenterText(text: String) {
    Text(text, Modifier.align(Alignment.Center), style = Type.body, color = LocalTokens.current.muted, textAlign = TextAlign.Center)
}
