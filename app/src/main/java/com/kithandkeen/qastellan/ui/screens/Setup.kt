package com.kithandkeen.qastellan.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kithandkeen.qastellan.BuildConfig
import com.kithandkeen.qastellan.data.AppState
import com.kithandkeen.qastellan.device.AuditCheck
import com.kithandkeen.qastellan.ui.BottomNav
import com.kithandkeen.qastellan.ui.Tab
import com.kithandkeen.qastellan.ui.components.BigTitle
import com.kithandkeen.qastellan.ui.components.ButtonKind
import com.kithandkeen.qastellan.ui.components.Divider
import com.kithandkeen.qastellan.ui.components.focusRing
import com.kithandkeen.qastellan.ui.components.Glyph
import com.kithandkeen.qastellan.ui.components.Icon
import com.kithandkeen.qastellan.ui.components.Icons
import com.kithandkeen.qastellan.ui.components.MonoLabel
import com.kithandkeen.qastellan.ui.components.QButton
import com.kithandkeen.qastellan.ui.components.QCard
import com.kithandkeen.qastellan.ui.components.Screen
import com.kithandkeen.qastellan.ui.components.SectionLabel
import com.kithandkeen.qastellan.ui.components.StatusMark
import com.kithandkeen.qastellan.ui.components.Toggle
import com.kithandkeen.qastellan.ui.components.TopBar
import com.kithandkeen.qastellan.ui.friendlyTime
import com.kithandkeen.qastellan.ui.theme.Appearance
import com.kithandkeen.qastellan.ui.theme.LocalTokens
import com.kithandkeen.qastellan.ui.theme.Type

// ===================================================================== Protection settings

@Composable
fun SettingsScreen(
    settings: AppState.Settings,
    definitions: AppState.DefinitionsInfo,
    appearance: Appearance,
    onRealtime: (Boolean) -> Unit,
    onScanNewApps: (Boolean) -> Unit,
    onAutoUpdate: (Boolean) -> Unit,
    onWifiOnly: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit,
    onAppearance: (Appearance) -> Unit,
    onAbout: () -> Unit,
    onTab: (Tab) -> Unit,
    batteryRestricted: Boolean = false,
    onFixBattery: () -> Unit = {},
) {
    val t = LocalTokens.current
    Screen(bottomBar = { BottomNav(Tab.PROTECTION, onTab) }) {
        BigTitle("Protection")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (batteryRestricted && settings.realtime) {
                QCard(padding = 14.dp, background = t.warnSoft, borderColor = t.warn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Your phone may stop real-time protection", style = Type.bodyStrong, color = t.text)
                            Text("Battery saving can close Qastellan in the background.", style = Type.small, color = t.muted)
                        }
                        QButton("Fix", onFixBattery, Modifier.padding(start = 10.dp), kind = ButtonKind.DANGER, height = 40.dp)
                    }
                }
            }
            QCard(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    Toggle(settings.realtime, "Real-time protection", "Check downloads as they arrive", onRealtime)
                    Divider()
                    Toggle(settings.scanNewApps, "Scan new apps", "Check every app on install", onScanNewApps)
                    Divider()
                    Toggle(settings.autoUpdate, "Auto-update definitions", "Daily threat list updates", onAutoUpdate)
                    Divider()
                    Toggle(settings.wifiOnly, "Update on Wi-Fi only", "Save mobile data", onWifiOnly)
                }
            }
            QCard(padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Updates", style = Type.bodyStrong, color = t.text)
                        MonoLabel(if (definitions.checking) "DEFINITIONS · CHECKING…" else "DEFINITIONS ${definitions.version} · ${definitions.signatures} SIGNATURES")
                        MonoLabel(
                            if (settings.appUpdateVersion != null) "APP v${BuildConfig.VERSION_NAME} · v${settings.appUpdateVersion} AVAILABLE"
                            else "APP v${BuildConfig.VERSION_NAME} · UP TO DATE",
                            color = if (settings.appUpdateVersion != null) t.warn else null,
                        )
                        Text("Checked every time Qastellan starts · last ${friendlyTime(definitions.lastCheck)}", style = Type.small, color = t.muted)
                    }
                    QButton(if (definitions.checking) "Checking…" else "Check now", onCheckUpdates, kind = ButtonKind.SECONDARY, height = 40.dp, enabled = !definitions.checking)
                }
            }
            QCard(padding = 16.dp) {
                Text("Appearance", style = Type.bodyStrong, color = t.text)
                Row(
                    Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(t.ghost).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Appearance.entries.forEach { a ->
                        val on = a == appearance
                        Box(
                            Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(if (on) t.surface else androidx.compose.ui.graphics.Color.Transparent)
                                .focusRing(RoundedCornerShape(10.dp)).clickable(role = Role.RadioButton) { onAppearance(a) }.semantics { selected = on },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(a.name.lowercase().replaceFirstChar { it.uppercase() }, style = Type.label.copy(fontSize = 13.sp), color = if (on) t.text else t.muted)
                        }
                    }
                }
            }
            QCard(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    MonoLabel("Advanced", Modifier.padding(top = 12.dp))
                    LinkRow("Signature sources and licences", onAbout)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    val t = LocalTokens.current
    Row(Modifier.fillMaxWidth().height(52.dp).focusRing(RoundedCornerShape(10.dp), grow = false).clickable(role = Role.Button, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = Type.body, color = t.text)
        Icon(Icons.chevron, t.muted, 18.dp)
    }
}

// ===================================================================== About

@Composable
fun AboutScreen(definitions: AppState.DefinitionsInfo, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val t = LocalTokens.current
    Screen {
        TopBar("About Qastellan", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Glyph(72.dp, t.accent, t.onAccent, 7f)
                Text("Qastellan ${BuildConfig.VERSION_NAME}", style = Type.heading, color = t.text)
                Text("Free and open source · GPL-3.0 · Made in Singapore", style = Type.small, color = t.muted)
            }
            SectionLabel("Threat data")
            QCard(padding = 16.dp) {
                listOf(
                    "ClamAV" to "Android signatures · GPL-2.0",
                    "MalwareBazaar by abuse.ch" to "Android samples · CC0",
                    "MVT indicators" to "Spyware indicators · CC BY / CC BY-SA",
                    "Exodus Privacy" to "Tracker library list",
                ).forEachIndexed { i, (name, desc) ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    Text(name, style = Type.bodyStrong, color = t.text)
                    Text(desc, style = Type.small, color = t.muted)
                }
                Text("Current definitions: ${definitions.version} · ${definitions.signatures} signatures", Modifier.padding(top = 12.dp), style = Type.small, color = t.muted)
            }
            SectionLabel("Links")
            QCard(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    LinkRow("Website") { onOpen("https://aadvark.github.io/Qastellan/") }
                    Divider()
                    LinkRow("Source code") { onOpen("https://github.com/${BuildConfig.GITHUB_REPO}") }
                    Divider()
                    LinkRow("Report a problem or a wrong detection") { onOpen("https://github.com/${BuildConfig.GITHUB_REPO}/issues") }
                    Divider()
                    LinkRow("Licence (GPL-3.0)") { onOpen("https://github.com/${BuildConfig.GITHUB_REPO}/blob/main/LICENSE") }
                }
            }
            Text("Qastellan is not affiliated with or endorsed by the projects above. Fonts: Outfit, Inter and JetBrains Mono (SIL Open Font License).", style = Type.small, color = t.muted)
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ===================================================================== Security audit

@Composable
fun AuditScreen(checks: List<AuditCheck>, onFix: (AuditCheck) -> Unit, onBack: () -> Unit) {
    val t = LocalTokens.current
    val problems = checks.count { it.state == AuditCheck.State.PROBLEM }
    val passed = checks.count { it.state == AuditCheck.State.OK }
    val scored = checks.count { it.state != AuditCheck.State.UNKNOWN }.coerceAtLeast(1)
    Screen {
        TopBar("Security audit", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            QCard(radius = 22.dp, padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val color = if (problems > 0) t.warn else t.ok
                    Box(Modifier.size(76.dp).semantics { contentDescription = "$passed of $scored checks passed" }, contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(76.dp)) {
                            val inset = 6.dp.toPx()
                            val s = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                            drawArc(t.border, 0f, 360f, false, Offset(inset, inset), s, style = Stroke(7.dp.toPx()))
                            drawArc(color, -90f, 360f * passed / scored, false, Offset(inset, inset), s, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
                        }
                        Text(if (checks.isEmpty()) "…" else "$passed/$scored", style = Type.heading, color = t.text)
                    }
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(
                            when { checks.isEmpty() -> "Checking…"; problems == 0 -> "All checks passed"; problems == 1 -> "1 setting to fix"; else -> "$problems settings to fix" },
                            style = Type.heading.copy(fontSize = 20.sp), color = t.text,
                        )
                        Text("Settings that make malware easier to install or harder to remove.", style = Type.small, color = t.muted)
                    }
                }
            }
            QCard(padding = 0.dp) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    checks.forEachIndexed { i, c ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (c.state == AuditCheck.State.UNKNOWN) Icon(Icons.waiting, t.muted, 22.dp, "Unknown") else StatusMark(c.state == AuditCheck.State.OK)
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(c.label, style = Type.bodyStrong.copy(fontSize = 14.sp), color = t.text)
                                Text(c.detail, style = Type.small.copy(fontSize = 12.sp), color = t.muted)
                            }
                            if (c.state == AuditCheck.State.PROBLEM && c.fix != null) {
                                Text(
                                    c.fixLabel ?: "Fix",
                                    Modifier.clip(RoundedCornerShape(17.dp)).border(1.dp, t.accent, RoundedCornerShape(17.dp))
                                        .focusRing(RoundedCornerShape(17.dp)).clickable(role = Role.Button) { onFix(c) }.padding(horizontal = 14.dp, vertical = 8.dp),
                                    style = Type.label.copy(fontSize = 13.sp), color = t.accent,
                                )
                            }
                        }
                        if (i < checks.size - 1) Divider()
                    }
                }
            }
        }
    }
}

// ===================================================================== Onboarding

enum class OnboardingStep { WELCOME, APPS, FILES, NOTIFICATIONS, BATTERY, DONE }

@Composable
fun OnboardingScreen(step: OnboardingStep, onNext: () -> Unit, onAllow: () -> Unit, onFirstScan: () -> Unit) {
    val t = LocalTokens.current
    Screen {
        Column(Modifier.weight(1f).padding(horizontal = 24.dp, vertical = 24.dp)) {
            when (step) {
                OnboardingStep.WELCOME -> {
                    Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                        Glyph(112.dp, t.accent, t.onAccent, 7f, description = "Qastellan logo")
                        Text("Qastellan", style = Type.title.copy(fontSize = 34.sp, fontWeight = FontWeight.Normal), color = t.text)
                        Text("Guards your phone and the apps on it.", style = Type.body.copy(fontSize = 16.sp), color = t.muted, textAlign = TextAlign.Center)
                        QCard(padding = 0.dp, modifier = Modifier.padding(top = 12.dp)) {
                            Column(Modifier.padding(horizontal = 18.dp)) {
                                listOf("Checks every app and file for malware", "Nothing you have ever leaves your phone", "Free and open source, no ads").forEachIndexed { i, s ->
                                    Row(Modifier.height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(8.dp).clip(CircleShape).background(t.accent))
                                        Text(s, Modifier.padding(start = 12.dp), style = Type.body, color = t.text)
                                    }
                                    if (i < 2) Divider()
                                }
                            }
                        }
                    }
                    QButton("Get started", onNext, Modifier.fillMaxWidth(), height = 56.dp)
                }
                OnboardingStep.DONE -> {
                    Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                        StatusRing(t.ok, 150.dp, "Set up")
                        Text("You're set up", style = Type.title, color = t.text)
                        Text("Run your first scan now. It checks every app and file on your phone.", style = Type.body, color = t.muted, textAlign = TextAlign.Center)
                    }
                    QButton("Run first scan", onFirstScan, Modifier.fillMaxWidth(), height = 56.dp)
                }
                else -> {
                    val n = step.ordinal
                    val (icon, title, why, note, allow) = when (step) {
                        OnboardingStep.APPS -> Step(Icons.apps, "See your apps", "Qastellan looks at the list of installed apps to check each one for malware and hidden trackers.", "App names and files are checked on your phone. Nothing is uploaded.", "Continue")
                        OnboardingStep.FILES -> Step(Icons.file, "Scan your files", "Infected files usually arrive as downloads. All files access lets Qastellan check them the moment they land.", "Without it, only installed apps are scanned.", "Allow file access")
                        OnboardingStep.BATTERY -> Step(Icons.shield, "Stay on guard", "Some phones stop apps in the background to save battery. Letting Qastellan run keeps real-time protection on after you close it.", "It checks new apps and downloads only when they arrive, so battery use stays low.", "Allow background use")
                        else -> Step(Icons.bell, "Get warnings", "If a risky app is installed, Qastellan warns you straight away so you can remove it before it does harm.", "Qastellan never sends ads or promotions.", "Allow notifications")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                        (1..4).forEach { i -> Box(Modifier.height(6.dp).width(if (i == n) 22.dp else 6.dp).clip(RoundedCornerShape(3.dp)).background(if (i <= n) t.accent else t.border)) }
                    }
                    Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                        Box(Modifier.size(112.dp).clip(CircleShape).background(t.ghost).border(1.dp, t.border, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, t.accent, 48.dp, strokeWidth = 1.6f) }
                        MonoLabel("Step $n of 4")
                        Text(title, style = Type.title, color = t.text)
                        Text(why, style = Type.body, color = t.muted, textAlign = TextAlign.Center)
                        Text(note, style = Type.small, color = t.muted, textAlign = TextAlign.Center)
                    }
                    QButton(allow, onAllow, Modifier.fillMaxWidth(), height = 56.dp)
                    if (step != OnboardingStep.APPS) QButton("Not now", onNext, Modifier.fillMaxWidth(), kind = ButtonKind.TEXT, height = 48.dp)
                }
            }
        }
    }
}

private data class Step(val icon: String, val title: String, val why: String, val note: String, val allow: String)
