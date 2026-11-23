package com.kithandkeen.qastellan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kithandkeen.qastellan.data.HistoryEvent
import com.kithandkeen.qastellan.data.QuarantinedItem
import com.kithandkeen.qastellan.device.AppReport
import com.kithandkeen.qastellan.engine.Finding
import com.kithandkeen.qastellan.ui.components.BigTitle
import com.kithandkeen.qastellan.ui.BottomNav
import com.kithandkeen.qastellan.ui.Tab
import com.kithandkeen.qastellan.ui.clock
import com.kithandkeen.qastellan.ui.components.ButtonKind
import com.kithandkeen.qastellan.ui.components.Chip
import com.kithandkeen.qastellan.ui.components.ChipRow
import com.kithandkeen.qastellan.ui.components.Divider
import com.kithandkeen.qastellan.ui.components.focusRing
import com.kithandkeen.qastellan.ui.components.Glyph
import com.kithandkeen.qastellan.ui.components.Icon
import com.kithandkeen.qastellan.ui.components.Icons
import com.kithandkeen.qastellan.ui.components.Initials
import com.kithandkeen.qastellan.ui.components.MonoLabel
import com.kithandkeen.qastellan.ui.components.QButton
import com.kithandkeen.qastellan.ui.components.QCard
import com.kithandkeen.qastellan.ui.components.Screen
import com.kithandkeen.qastellan.ui.components.SectionLabel
import com.kithandkeen.qastellan.ui.components.StatusMark
import com.kithandkeen.qastellan.ui.components.TopBar
import com.kithandkeen.qastellan.ui.dayLabel
import com.kithandkeen.qastellan.ui.theme.LocalTokens
import com.kithandkeen.qastellan.ui.theme.Mono
import com.kithandkeen.qastellan.ui.theme.Type

// ===================================================================== Quarantine

@Composable
fun QuarantineScreen(items: List<QuarantinedItem>, onRestore: (QuarantinedItem) -> Unit, onDelete: (QuarantinedItem) -> Unit, onBack: () -> Unit) {
    val t = LocalTokens.current
    Screen {
        TopBar("Quarantine", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Quarantined files are locked away in Qastellan's private storage, where nothing can open them. Restore anything you trust, or delete it for good.", style = Type.body, color = t.muted)
            if (items.isEmpty()) {
                QCard(radius = 28.dp, padding = 36.dp) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Glyph(80.dp, t.accent, t.onAccent, 7f)
                        Text("Quarantine is empty", style = Type.heading.copy(fontSize = 24.sp), color = t.text)
                        Text("Nothing is locked away right now.", style = Type.body, color = t.muted)
                    }
                }
            }
            items.forEach { item ->
                QCard(padding = 16.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(t.ghost), contentAlignment = Alignment.Center) { Icon(Icons.lock, t.warn, 20.dp) }
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(item.name, style = Type.bodyStrong.copy(fontSize = 16.sp), color = t.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                MonoLabel("${item.detection} · ${dayLabel(item.time)} ${clock(item.time)}")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QButton("Restore", { onRestore(item) }, Modifier.weight(1f), kind = ButtonKind.SECONDARY, height = 44.dp)
                            QButton("Delete", { onDelete(item) }, Modifier.weight(1f), kind = ButtonKind.DANGER, height = 44.dp)
                        }
                    }
                }
            }
        }
    }
}

// ===================================================================== History

@Composable
fun HistoryScreen(events: List<HistoryEvent>, onTab: (Tab) -> Unit) {
    val t = LocalTokens.current
    var filter by remember { mutableIntStateOf(0) }
    val shown = events.filter { e ->
        when (filter) {
            1 -> e.isThreat
            2 -> !e.isThreat
            else -> true
        }
    }
    val groups = shown.groupBy { dayLabel(it.time) }
    Screen(bottomBar = { BottomNav(Tab.HISTORY, onTab) }) {
        BigTitle("History")
        Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { ChipRow(listOf("All", "Threats", "Scans & updates"), filter) { filter = it } }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (shown.isEmpty()) item { com.kithandkeen.qastellan.ui.Placeholder(if (events.isEmpty()) "Nothing here yet. Run a scan to start." else "Nothing matches this filter.") }
            groups.forEach { (day, list) ->
                item(key = "h-$day") { SectionLabel(day) }
                item(key = "g-$day") {
                    QCard(padding = 0.dp) {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            list.forEachIndexed { i, e ->
                                HistoryRow(e)
                                if (i < list.size - 1) Divider()
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun HistoryRow(e: HistoryEvent) {
    val t = LocalTokens.current
    val (icon, color, soft) = when (e.kind) {
        HistoryEvent.Kind.SCAN_THREATS, HistoryEvent.Kind.THREAT -> Triple(Icons.alert, t.warn, t.warnSoft)
        HistoryEvent.Kind.DEFINITIONS -> Triple(Icons.sync, t.accent, t.ghost)
        HistoryEvent.Kind.QUARANTINE -> Triple(Icons.lock, t.accent, t.ghost)
        HistoryEvent.Kind.NEW_APP -> Triple(Icons.phone, t.ok, t.okSoft)
        HistoryEvent.Kind.ACTION -> Triple(Icons.checkCircle, t.accent, t.ghost)
        HistoryEvent.Kind.SCAN_CLEAN -> Triple(Icons.checkCircle, t.ok, t.okSoft)
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(soft), contentAlignment = Alignment.Center) { Icon(icon, color, 18.dp, strokeWidth = 2.2f) }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(e.title, style = Type.bodyStrong, color = t.text)
            if (e.detail.isNotBlank()) Text(e.detail, style = Type.small, color = t.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(clock(e.time), style = Type.small.copy(fontFamily = Mono, fontSize = 12.sp), color = t.muted)
    }
}

// ===================================================================== Apps

@Composable
fun AppsScreen(apps: List<AppReport>?, onOpen: (String) -> Unit, onTab: (Tab) -> Unit) {
    val t = LocalTokens.current
    var filter by remember { mutableIntStateOf(0) }
    val shown = apps?.filter { a ->
        when (filter) {
            1 -> a.trackers.isNotEmpty()
            2 -> a.sensitiveAccess.size > 1
            3 -> a.sideloaded
            else -> true
        }
    }
    Screen(bottomBar = { BottomNav(Tab.APPS, onTab) }) {
        BigTitle("Apps")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("What each app can access, who it reports to, and where it came from.", style = Type.body, color = t.muted)
            Row(Modifier.horizontalScrollCompat()) { ChipRow(listOf("All", "Has trackers", "Sensitive access", "Sideloaded"), filter) { filter = it } }
        }
        if (shown == null) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = t.accent)
                Text("Checking your apps…", Modifier.padding(top = 14.dp), style = Type.body, color = t.muted)
            }
        } else {
            LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp).padding(top = 12.dp)) {
                if (shown.isEmpty()) item { com.kithandkeen.qastellan.ui.Placeholder("No apps match this filter.") }
                items(shown, key = { it.facts.packageName }) { a ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).focusRing(RoundedCornerShape(14.dp), grow = false).clickable(role = Role.Button) { onOpen(a.facts.packageName) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Initials(a.facts.label, 40.dp)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(a.facts.label, style = Type.bodyStrong, color = t.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                (if (a.trackers.isEmpty()) "No known trackers" else "${a.trackers.size} ${if (a.trackers.size == 1) "tracker" else "trackers"}") +
                                    if (a.sensitiveAccess.isNotEmpty()) " · " + a.sensitiveAccess.joinToString() else "",
                                style = Type.small, color = t.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        when {
                            a.risk.level == com.kithandkeen.qastellan.engine.RiskReport.Level.HIGH -> Chip("Risky", t.warn, t.warnSoft)
                            a.sideloaded -> Chip("Sideloaded", t.warn, t.warnSoft)
                            a.newAccess.isNotEmpty() -> Chip("New access", t.warn, t.warnSoft)
                            a.trackers.size >= 3 -> Chip("Trackers", t.muted, t.ghost)
                        }
                        Icon(Icons.chevron, t.muted, 16.dp, modifier = Modifier.padding(start = 6.dp))
                    }
                    Divider()
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier = this.then(Modifier.horizontalScroll(rememberScrollState()))

// ===================================================================== App details

@Composable
fun AppDetailScreen(report: AppReport?, onAcknowledge: () -> Unit, onPermissions: () -> Unit, onUninstall: () -> Unit, onBack: () -> Unit) {
    val t = LocalTokens.current
    Screen {
        TopBar("App details", onBack)
        if (report == null) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = t.accent)
            }
            return@Screen
        }
        val f = report.facts
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Initials(f.label, 56.dp)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(f.label, style = Type.title.copy(fontSize = 24.sp), color = t.text)
                    MonoLabel("${f.packageName}${if (report.versionName.isNotBlank()) " · v" + report.versionName else ""}")
                }
            }
            SectionLabel("Integrity")
            QCard(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    CheckRow(!report.sideloaded, report.installerLabel)
                    Divider()
                    CheckRow(!f.hasAccessibilityService && !f.isDeviceAdmin,
                        when {
                            f.isDeviceAdmin && f.hasAccessibilityService -> "Has device admin and accessibility access"
                            f.isDeviceAdmin -> "Has device admin rights"
                            f.hasAccessibilityService -> "Can read and control the screen (accessibility)"
                            else -> "No admin or accessibility access"
                        })
                    Divider()
                    CheckRow(f.hasLauncherIcon, if (f.hasLauncherIcon) "Visible in the app drawer" else "Hidden: no icon in the app drawer")
                }
            }
            SectionLabel("Privacy")
            if (report.newAccess.isNotEmpty()) {
                QCard(padding = 14.dp, background = t.warnSoft, borderColor = t.warn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.alertCircle, t.warn, 20.dp)
                        Text("Gained access to ${report.newAccess.joinToString()} since Qastellan last checked.", Modifier.padding(start = 10.dp).weight(1f), style = Type.small, color = t.text)
                        Text("Got it", Modifier.focusRing(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onAcknowledge).padding(8.dp), style = Type.label, color = t.accent)
                    }
                }
            }
            QCard(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    if (report.sensitiveAccess.isEmpty()) {
                        Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) { Text("No sensitive access granted", style = Type.body, color = t.text) }
                    }
                    report.sensitiveAccess.forEachIndexed { i, p ->
                        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(p, Modifier.weight(1f), style = Type.body, color = t.text)
                            Text(if (p in report.newAccess) "New" else "Allowed", style = Type.small, color = if (p in report.newAccess) t.warn else t.muted)
                        }
                        if (i < report.sensitiveAccess.size - 1) Divider()
                    }
                }
            }
            QCard(padding = 16.dp) {
                Text(
                    if (report.trackers.isEmpty()) "No known trackers found" else "${report.trackers.size} known ${if (report.trackers.size == 1) "tracker" else "trackers"} inside",
                    style = Type.bodyStrong, color = t.text,
                )
                report.trackers.forEach { tr ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(tr.name, Modifier.weight(1f), style = Type.body, color = t.text)
                        Text(tr.category, style = Type.small, color = t.muted)
                    }
                }
                Text("Tracker libraries are detected from Exodus Privacy's public list.", Modifier.padding(top = 10.dp), style = Type.small, color = t.muted)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QButton("Permissions", onPermissions, Modifier.weight(1f), kind = ButtonKind.SECONDARY, height = 50.dp)
                QButton("Uninstall", onUninstall, Modifier.weight(1f), height = 50.dp)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CheckRow(ok: Boolean, text: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusMark(ok)
        Text(text, Modifier.padding(start = 12.dp), style = Type.body.copy(fontSize = 14.sp), color = LocalTokens.current.text)
    }
}

// ===================================================================== Risky app alert

@Composable
fun AlertScreen(report: AppReport?, finding: Finding?, onUninstall: () -> Unit, onAppInfo: () -> Unit, onTrust: () -> Unit, onBack: () -> Unit) {
    val t = LocalTokens.current
    val known = finding as? Finding.KnownThreat
    val reasons = when {
        known != null -> listOf("Matches known malware: ${known.detection}")
        finding is Finding.RiskyApp -> finding.report.reasons
        report != null -> report.risk.reasons
        else -> emptyList()
    }
    Screen {
        TopBar("Risky app installed", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            QCard(radius = 24.dp, padding = 20.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Initials(report?.facts?.label ?: finding?.title ?: "?", 52.dp)
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(report?.facts?.label ?: finding?.title ?: "Unknown app", style = Type.heading, color = t.text)
                            Text(report?.installerLabel ?: "", style = Type.small, color = t.muted)
                        }
                    }
                    Chip(if (known != null) "Known malware" else "High risk · not in any malware list yet", t.warn, t.warnSoft)
                    reasons.forEach { r ->
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.alertCircle, t.warn, 20.dp, modifier = Modifier.padding(top = 1.dp))
                            Text(r, Modifier.padding(start = 10.dp), style = Type.body, color = t.text)
                        }
                    }
                    QButton("Uninstall now", onUninstall, Modifier.fillMaxWidth(), kind = ButtonKind.DANGER, height = 52.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QButton("App info", onAppInfo, Modifier.weight(1f), kind = ButtonKind.SECONDARY, height = 46.dp)
                        QButton("I trust it", onTrust, Modifier.weight(1f), kind = ButtonKind.SECONDARY, height = 46.dp)
                    }
                    Text("App info lets you force-stop or disable the app if you'd rather not uninstall it yet.", style = Type.small, color = t.muted, textAlign = TextAlign.Start)
                }
            }
        }
    }
}
