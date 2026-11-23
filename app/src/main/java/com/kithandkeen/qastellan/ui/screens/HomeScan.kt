package com.kithandkeen.qastellan.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kithandkeen.qastellan.data.AppState
import com.kithandkeen.qastellan.data.Resolution
import com.kithandkeen.qastellan.data.ScanState
import com.kithandkeen.qastellan.device.DeviceScanner
import com.kithandkeen.qastellan.device.SecurityAudit
import com.kithandkeen.qastellan.engine.Finding
import com.kithandkeen.qastellan.ui.BottomNav
import com.kithandkeen.qastellan.ui.Tab
import com.kithandkeen.qastellan.ui.components.ButtonKind
import com.kithandkeen.qastellan.ui.components.Chip
import com.kithandkeen.qastellan.ui.components.Divider
import com.kithandkeen.qastellan.ui.components.focusRing
import com.kithandkeen.qastellan.ui.components.LocalLayout
import com.kithandkeen.qastellan.ui.components.Glyph
import com.kithandkeen.qastellan.ui.components.Icon
import com.kithandkeen.qastellan.ui.components.Icons
import com.kithandkeen.qastellan.ui.components.Initials
import com.kithandkeen.qastellan.ui.components.MonoLabel
import com.kithandkeen.qastellan.ui.components.QButton
import com.kithandkeen.qastellan.ui.components.QCard
import com.kithandkeen.qastellan.ui.components.Screen
import com.kithandkeen.qastellan.ui.components.TopBar
import com.kithandkeen.qastellan.ui.friendlyTime
import com.kithandkeen.qastellan.ui.theme.LocalTokens
import com.kithandkeen.qastellan.ui.theme.Mono
import com.kithandkeen.qastellan.ui.theme.Outfit
import com.kithandkeen.qastellan.ui.theme.Type

// ===================================================================== Home

data class HomeModel(
    val threats: Int,
    val lastScan: Long,
    val definitionsVersion: String,
    val definitionsChecked: Long,
    val realtime: Boolean,
    val appUpdate: String?,
    val appUpdateUrl: String?,
    val fileAccess: Boolean = true,
    /** False on devices without a file picker (most TVs): the Scan file tile is hidden. */
    val canPickFiles: Boolean = true,
    /** Set while a full scan runs in the background: 0..1 and the item being checked. */
    val scanProgress: Float? = null,
    val scanCurrent: String? = null,
)

@Composable
fun HomeScreen(
    model: HomeModel,
    onScan: () -> Unit,
    onReview: () -> Unit,
    onPickApp: () -> Unit,
    onPickFile: () -> Unit,
    onQuarantine: () -> Unit,
    onAudit: () -> Unit,
    onSettings: () -> Unit,
    onTab: (Tab) -> Unit,
    onAllowFiles: () -> Unit = {},
) {
    val t = LocalTokens.current
    val context = LocalContext.current
    val action = model.threats > 0
    val noDefinitions = model.definitionsVersion == "none"
    val scanning = model.scanProgress != null
    val pct = ((model.scanProgress ?: 0f) * 100).toInt().coerceIn(0, 99)
    val neverScanned = !action && model.lastScan == 0L
    val color = when { scanning -> t.ok; action -> t.warn; neverScanned -> t.accent; else -> t.ok }
    val layout = LocalLayout.current
    val scanFocus = remember { FocusRequester() }
    // On a TV the remote starts on Scan now.
    LaunchedEffect(layout.tv) { if (layout.tv) runCatching { scanFocus.requestFocus() } }
    val banners: @Composable () -> Unit = {
            if (model.appUpdate != null) {
                QCard(padding = 14.dp, background = t.okSoft, borderColor = t.ok) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Qastellan ${model.appUpdate} is available", Modifier.weight(1f), style = Type.bodyStrong, color = t.text)
                        QButton("Get it", {
                            model.appUpdateUrl?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                        }, height = 40.dp)
                    }
                }
            }
            if (!model.fileAccess) {
                QCard(padding = 14.dp, background = t.warnSoft, borderColor = t.warn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Downloads aren't being checked", style = Type.bodyStrong, color = t.text)
                            Text("Allow all files access so Qastellan can scan downloads and remove infected files.", style = Type.small, color = t.muted)
                        }
                        QButton("Allow", onAllowFiles, Modifier.padding(start = 10.dp), kind = ButtonKind.DANGER, height = 40.dp)
                    }
                }
            }
    }
    val statusCard: @Composable () -> Unit = {
            QCard(radius = 28.dp, padding = 20.dp) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val headline = when { scanning -> "Scanning… $pct%"; action -> "Action needed"; neverScanned -> "Not scanned yet"; else -> "You're protected" }
                    StatusRing(color, 148.dp, headline, model.scanProgress)
                    Text(
                        headline,
                        style = Type.title.copy(fontWeight = FontWeight.Light), // the status line stays light; titles are medium
                        color = color, textAlign = TextAlign.Center,
                    )
                    Text(
                        when {
                            scanning -> model.scanCurrent ?: "Preparing…"
                            action -> "${model.threats} ${if (model.threats == 1) "threat is" else "threats are"} waiting for your review"
                            model.lastScan == 0L -> "Run your first scan to check every app and file"
                            else -> "No threats found in your last scan"
                        },
                        style = Type.body, color = t.muted, textAlign = TextAlign.Center,
                        // While scanning, names change many times a second: keep them to one line
                        // so the card doesn't grow and shrink.
                        maxLines = if (scanning) 1 else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (action && !scanning) QButton("Review threats", onReview, kind = ButtonKind.DANGER, height = 44.dp)
                    MonoLabel(
                        if (noDefinitions) "DEFINITIONS NOT DOWNLOADED YET" else "DEFS ${model.definitionsVersion} · ${if (model.realtime) "REAL-TIME ON" else "REAL-TIME OFF"}",
                    )
                }
            }
    }
    val scanButton: @Composable () -> Unit = {
            QButton(
                if (scanning) "Scanning… $pct%" else "Scan now", onScan, Modifier.fillMaxWidth().focusRequester(scanFocus), height = 58.dp,
                leading = { Icon(if (scanning) Icons.sync else Icons.search, t.onAccent, 20.dp, strokeWidth = 2.2f) },
            )
    }
    val infoCard: @Composable () -> Unit = {
            QCard(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    InfoRow("Last scan", friendlyTime(model.lastScan), null)
                    Divider()
                    InfoRow(
                        "Virus definitions",
                        if (noDefinitions) "Not downloaded" else if (model.definitionsChecked > 0) "Up to date" else model.definitionsVersion,
                        if (noDefinitions) t.warn else t.ok,
                    )
                    Divider()
                    InfoRow("Real-time protection", if (model.realtime) "On" else "Off", if (model.realtime) t.ok else t.warn)
                }
            }
    }
    val tiles: @Composable () -> Unit = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Scan app", Icons.phone, onPickApp, Modifier.weight(1f))
                if (model.canPickFiles) Tile("Scan file", Icons.file, onPickFile, Modifier.weight(1f))
                Tile("Quarantine", Icons.lock, onQuarantine, Modifier.weight(1f))
                Tile("Audit", Icons.audit, onAudit, Modifier.weight(1f))
            }
    }

    Screen(bottomBar = { BottomNav(Tab.HOME, onTab) }) {
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            // Wide screens already show the logo in the side navigation.
            if (!layout.wide) Glyph(30.dp, t.accent, t.onAccent, 10f)
            Text("Qastellan", Modifier.padding(start = if (layout.wide) 0.dp else 8.dp).weight(1f), style = Type.heading, color = t.text)
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).focusRing(RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClick = onSettings)
                    .semantics { contentDescription = "Protection settings" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.settings, t.text, 22.dp) }
        }
        if (layout.wide) {
            // Landscape, tablet, TV: status and Scan on the left, details and tools on the right.
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                banners()
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        statusCard()
                        scanButton()
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        infoCard()
                        tiles()
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        } else {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                banners()
                statusCard()
                scanButton()
                infoCard()
                tiles()
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color?) {
    val t = LocalTokens.current
    Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = Type.small.copy(fontSize = 14.sp), color = t.muted)
        Text(value, style = Type.bodyStrong.copy(fontSize = 14.sp), color = valueColor ?: t.text)
    }
}

@Composable
private fun Tile(label: String, icon: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .height(76.dp)
            .clip(shape)
            .background(t.solid)
            .border(1.5.dp, t.accent, shape)
            .focusRing(shape)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, t.ok, 22.dp)
        Text(label, Modifier.padding(top = 6.dp), style = Type.label, color = t.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The big status badge: the logo inside dashed and solid rings. */
@Composable
fun StatusRing(color: androidx.compose.ui.graphics.Color, size: Dp, description: String, progress: Float? = null) {
    val t = LocalTokens.current
    Box(Modifier.size(size).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val r = this.size.width / 2
            drawCircle(color.copy(alpha = 0.7f), radius = r - 1.dp.toPx(), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx()))))
            drawCircle(t.border, radius = r * 0.82f, style = Stroke(1.dp.toPx()))
            drawArc(color, -90f, if (progress != null) 360f * progress else 120f, false, topLeft = Offset(r * 0.18f, r * 0.18f), size = androidx.compose.ui.geometry.Size(r * 1.64f, r * 1.64f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(when (color) { t.warn -> t.warnSoft; t.ok -> t.okSoft; else -> t.ghost }, radius = r * 0.68f)
        }
        Glyph(size * 0.49f, color, if (t.dark) t.onAccent else androidx.compose.ui.graphics.Color.White, 7f)
    }
}

// ===================================================================== Scanning

@Composable
fun ScanningScreen(state: ScanState, onCancel: () -> Unit, onResults: () -> Unit, onBack: () -> Unit = onCancel) {
    val t = LocalTokens.current
    val running = state as? ScanState.Running
    val done = (state as? ScanState.Done)?.summary
    val progress = running?.progress
    val singleFile = running?.singleFile == true || done?.fileName != null
    val pct = when {
        done != null -> 100
        progress != null -> (progress.overall * 100).toInt().coerceIn(0, 99)
        else -> 0
    }
    Screen {
        TopBar(if (singleFile) "File check" else "Full scan", onBack)
        val ring: @Composable () -> Unit = {
            Box(Modifier.size(250.dp).semantics { contentDescription = "Scan progress $pct percent" }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(250.dp)) {
                    val r = size.width / 2
                    drawCircle(t.accent.copy(alpha = 0.6f), r - 2.dp.toPx(), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 7.dp.toPx()))))
                    val inset = r * 0.17f
                    val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                    drawArc(t.border, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(10.dp.toPx()))
                    drawArc(t.accent, -90f, 360f * pct / 100f, false, Offset(inset, inset), arcSize, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$pct%", style = Type.title.copy(fontSize = 56.sp, fontWeight = FontWeight.Normal, lineHeight = 60.sp), color = t.text)
                    MonoLabel(if (done != null) "Complete" else "Scanning")
                }
            }
            Text(
                when {
                    done != null -> "Scan complete"
                    progress != null -> "> ${progress.current}"
                    else -> "Preparing…"
                },
                style = Type.small.copy(fontFamily = Mono), color = t.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        val details: @Composable ColumnScope.() -> Unit = {
            QCard(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    if (singleFile) {
                        Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (done != null) Icons.checkCircle else Icons.waiting, if (done != null) t.ok else t.accent, 22.dp)
                            Text(done?.fileName ?: "Picked file", Modifier.padding(start = 12.dp).weight(1f), style = Type.body.copy(fontWeight = FontWeight.Medium), color = t.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (done != null) "1 file" else "—", style = Type.small.copy(fontFamily = Mono), color = t.muted)
                        }
                    } else {
                        DeviceScanner.Stage.entries.forEachIndexed { i, stage ->
                            val active = progress?.stage == stage
                            val finished = done != null || (progress != null && progress.stage.ordinal > stage.ordinal)
                            val count = done?.counts?.get(stage) ?: progress?.counts?.get(stage) ?: 0
                            Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (finished) Icons.checkCircle else Icons.waiting, when { finished -> t.ok; active -> t.accent; else -> t.muted }, 22.dp)
                                Text(stage.label, Modifier.padding(start = 12.dp).weight(1f), style = Type.body.copy(fontWeight = FontWeight.Medium), color = t.text)
                                Text(
                                    when {
                                        !active && !finished -> "—"
                                        stage == DeviceScanner.Stage.APPS -> "$count apps"
                                        else -> "$count files"
                                    },
                                    style = Type.small.copy(fontFamily = Mono), color = t.muted,
                                )
                            }
                            if (i < 2) Divider()
                        }
                    }
                }
            }
            if (!singleFile && done != null && (done.counts[DeviceScanner.Stage.DOWNLOADS] ?: 0) + (done.counts[DeviceScanner.Stage.STORAGE] ?: 0) == 0) {
                Text("Files were skipped: turn on all files access to scan downloads and storage.", style = Type.small, color = t.muted, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.weight(1f))
            if (done != null) {
                QButton("View results", onResults, Modifier.fillMaxWidth(), height = 56.dp)
            } else {
                QButton(if (singleFile) "Cancel" else "Cancel scan", onCancel, Modifier.fillMaxWidth(), kind = ButtonKind.SECONDARY, height = 56.dp)
            }
        }
        if (LocalLayout.current.wide) {
            // Landscape, tablet, TV: the ring beside the stage list.
            Row(Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)) { ring() }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) { details() }
            }
        } else {
        Column(Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                ring()
                details()
            }
        }
    }
}

// ===================================================================== Results

@Composable
fun ResultsScreen(
    summary: DeviceScanner.Summary?,
    threats: List<Finding>,
    onResolve: (Finding, Resolution) -> Boolean,
    onUninstall: (String) -> Unit,
    onQuarantine: () -> Unit,
    onDone: () -> Unit,
    onAllowFiles: () -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    val t = LocalTokens.current
    Screen {
        TopBar("Scan results", onDone)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (threats.isEmpty()) {
                QCard(radius = 28.dp, padding = 32.dp) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusRing(t.ok, 120.dp, "No threats")
                        Text("No threats found", style = Type.title.copy(fontSize = 24.sp), color = t.ok)
                        if (summary != null) Text(summary.fileName?.let { "$it is clean" } ?: "${summary.apps} apps and ${summary.files} files checked", style = Type.body, color = t.muted, textAlign = TextAlign.Center)
                    }
                }
            } else {
                QCard(background = t.warnSoft, borderColor = t.warn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.shieldAlert, t.warn, 40.dp, strokeWidth = 1.8f)
                        Column(Modifier.padding(start = 14.dp)) {
                            Text("${threats.size} ${if (threats.size == 1) "threat" else "threats"} found", style = Type.heading, color = t.warn)
                            if (summary != null) MonoLabel(if (summary.fileName != null) "1 FILE CHECKED" else "${summary.apps} APPS · ${summary.files} FILES CHECKED")
                        }
                    }
                }
                threats.forEach { f -> key(AppState.keyOf(f)) { ThreatCard(f, onResolve, onUninstall, onQuarantine, onAllowFiles, onRefresh) } }
            }
            Spacer(Modifier.height(4.dp))
            QButton("Done", onDone, Modifier.fillMaxWidth(), height = 56.dp)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun ThreatCard(f: Finding, onResolve: (Finding, Resolution) -> Boolean, onUninstall: (String) -> Unit, onQuarantine: () -> Unit, onAllowFiles: () -> Unit = {}, onRefresh: () -> Unit = {}) {
    val t = LocalTokens.current
    var details by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var needsAccess by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val pkg = AppState.packageOf(f)
    val known = f as? Finding.KnownThreat
    QCard(padding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Initials(f.title, 50.dp)
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(f.title, style = Type.bodyStrong.copy(fontSize = 17.sp), color = t.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (pkg != null) "Installed app" else "File", style = Type.small, color = t.muted)
                }
                Chip(if (known != null) "Dangerous" else "High risk", t.warn, t.warnSoft)
            }
            Text(
                if (known != null) "Matches known malware. It can harm your phone or steal data, so it should be removed."
                else (f as Finding.RiskyApp).report.reasons.joinToString(". ", postfix = "."),
                style = Type.body, color = t.muted,
            )
            Text(
                if (details) "Hide technical details" else "Show technical details",
                Modifier.focusRing(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { details = !details }.padding(vertical = 6.dp),
                style = Type.bodyStrong.copy(fontSize = 14.sp), color = t.accent,
            )
            if (details) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(t.ghost).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val lines = buildList {
                        if (known != null) add("DETECTION  ${known.detection}")
                        if (f is Finding.RiskyApp) add("RISK SCORE ${f.report.score}/100")
                        pkg?.let { add("PACKAGE    $it") }
                        known?.path?.let { add("PATH       $it") }
                    }
                    lines.forEach { Text(it, style = Type.small.copy(fontFamily = Mono, fontSize = 12.sp), color = t.muted) }
                }
            }
            message?.let { Text(it, style = Type.small, color = t.warn) }
            if (needsAccess) QButton("Allow all files access", onAllowFiles, Modifier.fillMaxWidth(), kind = ButtonKind.SECONDARY, height = 44.dp)
            if (pkg != null) {
                QButton("Uninstall app", { onUninstall(pkg) }, Modifier.fillMaxWidth(), kind = ButtonKind.DANGER, height = 52.dp)
                QButton("I trust it", { onResolve(f, Resolution.IGNORE) }, Modifier.fillMaxWidth(), kind = ButtonKind.SECONDARY, height = 48.dp)
            } else if (known?.sourceUri != null) {
                // Picked with Scan file: Qastellan only has the file through the picker, so it can delete it but not move it.
                QButton("Delete file", {
                    if (!onResolve(f, Resolution.DELETE)) message = "Couldn't delete it from here. Open your Files app and delete ${f.title} there."
                }, Modifier.fillMaxWidth(), kind = ButtonKind.DANGER, height = 52.dp)
            } else {
                QButton("Move to quarantine", {
                    if (onResolve(f, Resolution.QUARANTINE)) {
                        onQuarantine()
                    } else if (!com.kithandkeen.qastellan.device.StorageAccess.canReadAllFiles(context)) {
                        message = "Qastellan needs all files access to move this file."
                        needsAccess = true
                    } else if (known?.path?.let { java.io.File(it).exists() } == false) {
                        onRefresh() // already deleted elsewhere: the threat is gone, so clear it
                    } else {
                        message = "Couldn't move the file. Try again, or delete it in your Files app."
                    }
                }, Modifier.fillMaxWidth(), kind = ButtonKind.DANGER, height = 52.dp)
                QButton("I trust it", { onResolve(f, Resolution.IGNORE) }, Modifier.fillMaxWidth(), kind = ButtonKind.SECONDARY, height = 48.dp)
            }
        }
    }
}
