package com.kithandkeen.qastellan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kithandkeen.qastellan.ui.components.Divider
import com.kithandkeen.qastellan.ui.components.Icon
import com.kithandkeen.qastellan.ui.components.Icons
import com.kithandkeen.qastellan.ui.theme.LocalTokens
import com.kithandkeen.qastellan.ui.theme.Type
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SCANNING = "scanning"
    const val RESULTS = "results"
    const val QUARANTINE = "quarantine"
    const val HISTORY = "history"
    const val APPS = "apps"
    const val APP = "app/{pkg}"
    const val AUDIT = "audit"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val ALERT = "alert/{pkg}"

    fun app(pkg: String) = "app/$pkg"
    fun alert(pkg: String) = "alert/$pkg"
}

enum class Tab(val route: String, val label: String, val icon: String) {
    HOME(Routes.HOME, "Home", Icons.home),
    APPS(Routes.APPS, "Apps", Icons.apps),
    HISTORY(Routes.HISTORY, "History", Icons.history),
    PROTECTION(Routes.SETTINGS, "Protection", Icons.shield),
}

@Composable
fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    val t = LocalTokens.current
    Column(Modifier.fillMaxWidth().background(t.nav).navigationBarsPadding()) {
        Divider()
        Row(Modifier.fillMaxWidth().height(68.dp)) {
            Tab.entries.forEach { tab ->
                val on = tab == current
                val color = if (on) t.accent else t.muted
                Column(
                    Modifier
                        .weight(1f)
                        .height(68.dp)
                        .clickable(role = Role.Tab) { if (!on) onSelect(tab) }
                        .semantics { selected = on },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, color, 22.dp)
                    Text(tab.label, style = Type.label.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium), color = color)
                }
            }
        }
    }
}

/** "Today, 9:41 AM", "Yesterday, 6:02 PM" or a short date. */
fun friendlyTime(millis: Long): String {
    if (millis <= 0) return "Never"
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))
    return when (dayOffset(millis)) {
        0 -> "Today, $time"
        1 -> "Yesterday, $time"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
    }
}

fun dayLabel(millis: Long): String = when (dayOffset(millis)) {
    0 -> "Today"
    1 -> "Yesterday"
    else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
}

fun clock(millis: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))

private fun dayOffset(millis: Long): Int {
    fun startOfDay(ms: Long) = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return ((startOfDay(System.currentTimeMillis()) - startOfDay(millis)) / 86_400_000L).toInt()
}

@Composable
fun Placeholder(text: String) {
    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
        Text(text, style = Type.body, color = LocalTokens.current.muted)
    }
}
