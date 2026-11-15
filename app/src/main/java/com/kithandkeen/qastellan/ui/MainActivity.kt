package com.kithandkeen.qastellan.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import com.kithandkeen.qastellan.ui.theme.Appearance
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kithandkeen.qastellan.Qastellan
import com.kithandkeen.qastellan.data.AppState
import com.kithandkeen.qastellan.data.ScanState
import com.kithandkeen.qastellan.device.AppReport
import com.kithandkeen.qastellan.device.BatteryAccess
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.kithandkeen.qastellan.device.Notifications
import com.kithandkeen.qastellan.device.SecurityAudit
import com.kithandkeen.qastellan.device.StorageAccess
import com.kithandkeen.qastellan.ui.screens.AboutScreen
import com.kithandkeen.qastellan.ui.screens.AlertScreen
import com.kithandkeen.qastellan.ui.screens.AppDetailScreen
import com.kithandkeen.qastellan.ui.screens.AppsScreen
import com.kithandkeen.qastellan.ui.screens.AuditScreen
import com.kithandkeen.qastellan.ui.screens.HistoryScreen
import com.kithandkeen.qastellan.ui.screens.HomeModel
import com.kithandkeen.qastellan.ui.screens.HomeScreen
import com.kithandkeen.qastellan.ui.screens.OnboardingScreen
import com.kithandkeen.qastellan.ui.screens.OnboardingStep
import com.kithandkeen.qastellan.ui.screens.QuarantineScreen
import com.kithandkeen.qastellan.ui.screens.ResultsScreen
import com.kithandkeen.qastellan.ui.screens.ScanningScreen
import com.kithandkeen.qastellan.ui.screens.SettingsScreen
import com.kithandkeen.qastellan.ui.theme.QastellanTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handle(intent)
        val state = Qastellan.from(this).state
        setContent {
            val appearance by state.appearance.collectAsStateWithLifecycle()
            val dark = when (appearance) {
                Appearance.DARK -> true
                Appearance.LIGHT -> false
                Appearance.SYSTEM -> isSystemInDarkTheme()
            }
            // Status and navigation bar icons follow Qastellan's own light/dark setting.
            LaunchedEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(bars, bars)
            }
            QastellanTheme(appearance) { App(state, pendingRoute) { pendingRoute = null } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(Notifications.EXTRA_ALERT_PACKAGE)?.let { pendingRoute = Routes.alert(it); return }
        if (intent.getBooleanExtra(Notifications.EXTRA_SHOW_RESULTS, false)) pendingRoute = Routes.RESULTS
    }
}

@Composable
private fun App(state: AppState, pendingRoute: String?, onRouteHandled: () -> Unit) {
    val context = LocalContext.current
    val nav = rememberNavController()
    val settings by state.settings.collectAsStateWithLifecycle()
    val definitions by state.definitions.collectAsStateWithLifecycle()
    val appearance by state.appearance.collectAsStateWithLifecycle()
    val scan by state.scan.collectAsStateWithLifecycle()
    val threats by state.openThreats.collectAsStateWithLifecycle()
    val history by state.history.events.collectAsStateWithLifecycle()
    val quarantined by state.quarantine.items.collectAsStateWithLifecycle()
    val apps by state.apps.collectAsStateWithLifecycle()
    val audit by state.audit.collectAsStateWithLifecycle()

    // Uninstalling goes through the system dialog; refresh once it closes.
    val uninstaller = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        state.refreshAfterUninstall()
        state.loadApps(force = true)
    }
    val uninstall: (String) -> Unit = { pkg -> uninstaller.launch(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))) }

    // Home > File: copy the picked document into the cache, then scan it.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scanPicked(uri, context, state)
            nav.navigate(Routes.SCANNING) { launchSingleTop = true }
        }
    }

    fun tab(t: Tab) = nav.navigate(t.route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    fun startScan() {
        state.startScan()
        nav.navigate(Routes.SCANNING) { launchSingleTop = true }
    }

    LaunchedEffect(pendingRoute, settings.onboarded) {
        if (pendingRoute != null && settings.onboarded) {
            nav.navigate(pendingRoute) { launchSingleTop = true }
            onRouteHandled()
        }
    }

    NavHost(nav, startDestination = if (settings.onboarded) Routes.HOME else Routes.ONBOARDING) {
        composable(Routes.ONBOARDING) {
            var step by remember { mutableStateOf(OnboardingStep.WELCOME) }
            fun afterNotifications() = if (BatteryAccess.isExempt(context)) OnboardingStep.DONE else OnboardingStep.BATTERY
            val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { step = afterNotifications() }
            val battery = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { step = OnboardingStep.DONE }
            val files = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { step = OnboardingStep.NOTIFICATIONS }
            fun next() {
                step = when (step) {
                    OnboardingStep.WELCOME -> OnboardingStep.APPS
                    OnboardingStep.APPS -> if (StorageAccess.canReadAllFiles(context)) OnboardingStep.NOTIFICATIONS else OnboardingStep.FILES
                    OnboardingStep.FILES -> OnboardingStep.NOTIFICATIONS
                    OnboardingStep.NOTIFICATIONS -> afterNotifications()
                    else -> OnboardingStep.DONE
                }
                if (step == OnboardingStep.NOTIFICATIONS && Build.VERSION.SDK_INT < 33) step = afterNotifications()
            }
            OnboardingScreen(
                step,
                onNext = ::next,
                onAllow = {
                    when (step) {
                        OnboardingStep.FILES -> StorageAccess.requestIntent(context)?.let { files.launch(it) } ?: next()
                        OnboardingStep.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else next()
                        OnboardingStep.BATTERY -> runCatching { battery.launch(BatteryAccess.requestIntent(context)) }.onFailure { next() }
                        else -> next()
                    }
                },
                onFirstScan = {
                    state.finishOnboarding()
                    state.startScan()
                    nav.navigate(Routes.SCANNING) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                HomeModel(threats.size, settings.lastScan, definitions.version, definitions.lastCheck, settings.realtime, settings.appUpdateVersion, settings.appUpdateUrl),
                onScan = ::startScan,
                onReview = { nav.navigate(Routes.RESULTS) },
                onPickApp = { tab(Tab.APPS) },
                onPickFile = { picker.launch(arrayOf("*/*")) },
                onQuarantine = { nav.navigate(Routes.QUARANTINE) },
                onAudit = { nav.navigate(Routes.AUDIT) },
                onSettings = { tab(Tab.PROTECTION) },
                onTab = ::tab,
            )
        }
        composable(Routes.SCANNING) {
            ScanningScreen(
                scan,
                onCancel = {
                    if (scan is ScanState.Running) state.cancelScan()
                    nav.popBackStack()
                },
                onResults = { nav.navigate(Routes.RESULTS) { popUpTo(Routes.SCANNING) { inclusive = true } } },
            )
        }
        composable(Routes.RESULTS) {
            ResultsScreen(
                (scan as? ScanState.Done)?.summary,
                threats,
                onResolve = state::resolve,
                onUninstall = uninstall,
                onQuarantine = {},
                onDone = {
                    state.clearScanResult()
                    if (!nav.popBackStack(Routes.HOME, false)) nav.navigate(Routes.HOME)
                },
            )
        }
        composable(Routes.QUARANTINE) {
            QuarantineScreen(
                quarantined,
                onRestore = { state.quarantine.restore(it) },
                onDelete = { state.quarantine.delete(it) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.HISTORY) { HistoryScreen(history, ::tab) }
        composable(Routes.APPS) {
            LaunchedEffect(Unit) { state.loadApps() }
            AppsScreen(apps, onOpen = { nav.navigate(Routes.app(it)) }, onTab = ::tab)
        }
        composable(Routes.APP, arguments = listOf(navArgument("pkg") { type = NavType.StringType })) { entry ->
            val pkg = entry.arguments?.getString("pkg").orEmpty()
            val report = rememberReport(state, pkg, apps)
            AppDetailScreen(
                report.value,
                onAcknowledge = {
                    state.reports.acknowledge(pkg)
                    report.value = report.value?.copy(newAccess = emptyList())
                    state.loadApps(force = true)
                },
                onPermissions = { context.startActivity(SecurityAudit.appSettings(pkg)) },
                onUninstall = { uninstall(pkg) },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.AUDIT) {
            LaunchedEffect(Unit) { state.runAudit() }
            AuditScreen(audit, onFix = { c -> c.fix?.let { runCatching { context.startActivity(it) } } }, onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            // Re-checked whenever the screen comes back, e.g. after the user changes it in system settings.
            var batteryRestricted by remember { mutableStateOf(!BatteryAccess.isExempt(context)) }
            LifecycleResumeEffect(Unit) {
                batteryRestricted = !BatteryAccess.isExempt(context)
                onPauseOrDispose {}
            }
            SettingsScreen(
                settings, definitions, appearance,
                onRealtime = state::setRealtime,
                onScanNewApps = state::setScanNewApps,
                onAutoUpdate = state::setAutoUpdate,
                onWifiOnly = state::setWifiOnly,
                onCheckUpdates = state::checkForUpdates,
                onAppearance = state::setAppearance,
                onAbout = { nav.navigate(Routes.ABOUT) },
                onTab = ::tab,
                batteryRestricted = batteryRestricted,
                onFixBattery = {
                    runCatching { context.startActivity(BatteryAccess.requestIntent(context)) }
                        .onFailure { runCatching { context.startActivity(BatteryAccess.settingsIntent()) } }
                },
            )
        }
        composable(Routes.ABOUT) {
            AboutScreen(definitions, onOpen = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }, onBack = { nav.popBackStack() })
        }
        composable(Routes.ALERT, arguments = listOf(navArgument("pkg") { type = NavType.StringType })) { entry ->
            val pkg = entry.arguments?.getString("pkg").orEmpty()
            val report = rememberReport(state, pkg, null)
            val finding = threats.firstOrNull { AppState.packageOf(it) == pkg }
            AlertScreen(
                report.value, finding,
                onUninstall = { uninstall(pkg) },
                onAppInfo = { context.startActivity(SecurityAudit.appSettings(pkg)) },
                onTrust = {
                    finding?.let { state.resolve(it, com.kithandkeen.qastellan.data.Resolution.IGNORE) }
                    nav.popBackStack()
                },
                onBack = { if (!nav.popBackStack()) nav.navigate(Routes.HOME) },
            )
        }
    }
}

/** A single app's report: taken from the loaded list if present, otherwise built in the background. */
@Composable
private fun rememberReport(state: AppState, pkg: String, apps: List<AppReport>?): androidx.compose.runtime.MutableState<AppReport?> {
    val report = remember(pkg) { mutableStateOf(apps?.firstOrNull { it.facts.packageName == pkg }) }
    LaunchedEffect(pkg) {
        if (report.value == null) report.value = withContext(Dispatchers.Default) { state.reports.one(pkg) }
    }
    return report
}

private fun scanPicked(uri: Uri, context: android.content.Context, state: AppState) {
    state.markScanStarting()
    state.scope.launch(Dispatchers.IO) {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: "Picked file"
        val copy = File(context.cacheDir, "picked-${System.currentTimeMillis()}")
        val ok = runCatching { context.contentResolver.openInputStream(uri)?.use { input -> copy.outputStream().use { input.copyTo(it) } } != null }.getOrDefault(false)
        if (ok) state.scanPickedFile(copy, name)
    }
}
