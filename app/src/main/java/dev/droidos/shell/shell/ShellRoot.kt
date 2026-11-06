package dev.droidos.shell.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.droidos.shell.apps.calculator.CalculatorScreen
import dev.droidos.shell.apps.cipher.CipherScreen
import dev.droidos.shell.apps.cleaner.CleanerScreen
import dev.droidos.shell.apps.clock.ClockScreen
import dev.droidos.shell.apps.compass.CompassScreen
import dev.droidos.shell.apps.convert.ConvertScreen
import dev.droidos.shell.apps.digest.DigestScreen
import dev.droidos.shell.apps.feeds.FeedsScreen
import dev.droidos.shell.apps.files.FilesScreen
import dev.droidos.shell.apps.flashcards.FlashcardsScreen
import dev.droidos.shell.apps.gallery.GalleryScreen
import dev.droidos.shell.apps.generator.GeneratorScreen
import dev.droidos.shell.apps.ledger.LedgerScreen
import dev.droidos.shell.apps.level.LevelScreen
import dev.droidos.shell.apps.metronome.MetronomeScreen
import dev.droidos.shell.apps.habits.HabitsScreen
import dev.droidos.shell.apps.music.MusicScreen
import dev.droidos.shell.apps.music.spotify.SpotifyScreen
import dev.droidos.shell.apps.netcheck.NetcheckScreen
import dev.droidos.shell.apps.notes.NotesScreen
import dev.droidos.shell.apps.power.PowerScreen
import dev.droidos.shell.apps.privacy.PrivacyScreen
import dev.droidos.shell.apps.relay.RelayScreen
import dev.droidos.shell.apps.recorder.RecorderScreen
import dev.droidos.shell.apps.scanner.ScannerScreen
import dev.droidos.shell.apps.sensors.SensorsScreen
import dev.droidos.shell.apps.speedtest.SpeedTestScreen
import dev.droidos.shell.apps.terminal.TerminalScreen
import dev.droidos.shell.apps.texttools.TextToolsScreen
import dev.droidos.shell.apps.timelab.TimeLabScreen
import dev.droidos.shell.apps.torch.TorchScreen
import dev.droidos.shell.apps.vault.VaultScreen
import dev.droidos.shell.apps.weather.WeatherScreen
import dev.droidos.shell.firewall.FirewallScreen
import dev.droidos.shell.settings.AboutScreen
import dev.droidos.shell.settings.AccessibilityScreen
import dev.droidos.shell.settings.AppearanceScreen
import dev.droidos.shell.settings.AppsScreen
import dev.droidos.shell.settings.BatteryScreen
import dev.droidos.shell.settings.DateTimeSettingsScreen
import dev.droidos.shell.settings.DiagnosticsScreen
import dev.droidos.shell.settings.DisplaySettingsScreen
import dev.droidos.shell.settings.LocationSettingsScreen
import dev.droidos.shell.settings.NetworkScreen
import dev.droidos.shell.settings.PermissionsScreen
import dev.droidos.shell.settings.SecurityScreen
import dev.droidos.shell.settings.SettingsScreen
import dev.droidos.shell.settings.SoundSettingsScreen
import dev.droidos.shell.settings.StorageScreen
import dev.droidos.shell.settings.UpdatesScreen
import dev.droidos.shell.shell.drawer.AppDrawer
import dev.droidos.shell.shell.drawer.RecentsScreen
import dev.droidos.shell.shell.home.HomeScreen
import dev.droidos.shell.shell.search.SearchScreen
import dev.droidos.shell.shell.shade.Shade
import dev.droidos.shell.ui.components.MessageBanner
import dev.droidos.shell.ui.theme.DroidWallpaper
import kotlinx.coroutines.delay

/**
 * Root of the whole shell: lock screen, destination dispatch, notification shade overlay
 * and transient messages. Everything is one composition so state never desynchronises
 * between the launcher, the shade and the running app.
 */
@Composable
fun ShellRoot(viewModel: ShellViewModel, initialScreen: Screen?) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val unlocked by viewModel.unlocked.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()

    val navigator = remember { ShellNavigator() }
    var shadeOpen by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialScreen) { initialScreen?.let { navigator.push(it) } }

    val locked = settings.lockEnabled && !unlocked

    BackHandler(enabled = !locked && (shadeOpen || navigator.canGoBack)) {
        if (shadeOpen) shadeOpen = false else navigator.pop()
    }

    LaunchedEffect(message) {
        if (message != null) {
            delay(2800)
            message = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DroidWallpaper)
    ) {
        when {
            !ready -> BootSplash()
            locked -> LockScreen(viewModel = viewModel)
            else -> {
                ShellContent(
                    navigator = navigator,
                    settings = settings,
                    viewModel = viewModel,
                    onOpenShade = { shadeOpen = true },
                    onMessage = { message = it }
                )

                AnimatedVisibility(
                    visible = shadeOpen,
                    enter = fadeIn(tween(180)) + slideInVertically(tween(280)) { -it / 3 },
                    exit = fadeOut(tween(160)) + slideOutVertically(tween(220)) { -it / 3 }
                ) {
                    Shade(
                        onClose = { shadeOpen = false },
                        onOpenScreen = { screen ->
                            shadeOpen = false
                            navigator.push(screen)
                        },
                        onLock = {
                            shadeOpen = false
                            navigator.home()
                            viewModel.lockNow()
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = message != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            message?.let { MessageBanner(it) }
        }
    }
}

@Composable
private fun ShellContent(
    navigator: ShellNavigator,
    settings: dev.droidos.shell.data.model.AppSettings,
    viewModel: ShellViewModel,
    onOpenShade: () -> Unit,
    onMessage: (String) -> Unit
) {
    val launch = rememberSecureLauncher(settings.lockedApps, onMessage)
    val back: () -> Unit = { if (!navigator.pop()) navigator.home() }
    val open: (Screen) -> Unit = { navigator.push(it) }

    when (navigator.current) {
        Screen.HOME -> HomeScreen(
            settings = settings,
            onOpenDrawer = { open(Screen.DRAWER) },
            onOpenShade = onOpenShade,
            onOpenScreen = open,
            onLaunchApp = launch,
            onOpenRecents = { open(Screen.RECENTS) },
            onLock = { viewModel.lockNow() }
        )

        Screen.DRAWER -> AppDrawer(
            onBack = back,
            onLaunchApp = launch,
            onOpenScreen = open
        )

        Screen.RECENTS -> RecentsScreen(
            onBack = back,
            onLaunchApp = launch
        )

        Screen.SEARCH -> SearchScreen(
            onBack = back,
            onOpenScreen = open,
            onLaunchApp = launch
        )

        Screen.SETTINGS -> SettingsScreen(onBack = back, onOpenScreen = open, viewModel = viewModel)
        Screen.APPEARANCE -> AppearanceScreen(onBack = back, viewModel = viewModel)
        Screen.DISPLAY -> DisplaySettingsScreen(onBack = back, viewModel = viewModel)
        Screen.SOUND -> SoundSettingsScreen(onBack = back)
        Screen.LOCATION -> LocationSettingsScreen(onBack = back)
        Screen.DATETIME -> DateTimeSettingsScreen(onBack = back)
        Screen.NETWORK -> NetworkScreen(onBack = back)
        Screen.BATTERY -> BatteryScreen(onBack = back)
        Screen.STORAGE -> StorageScreen(onBack = back)
        Screen.ACCESSIBILITY -> AccessibilityScreen(onBack = back)
        Screen.APPS -> AppsScreen(onBack = back, onLaunchApp = launch, onOpenScreen = open)
        Screen.PERMISSIONS -> PermissionsScreen(onBack = back)
        Screen.SECURITY -> SecurityScreen(onBack = back, onOpenScreen = open, viewModel = viewModel)
        Screen.UPDATES -> UpdatesScreen(onBack = back)
        Screen.DIAGNOSTICS -> DiagnosticsScreen(onBack = back)
        Screen.ABOUT -> AboutScreen(onBack = back)

        Screen.FILES -> FilesScreen(onBack = back)
        Screen.NOTES -> NotesScreen(onBack = back)
        Screen.VAULT -> VaultScreen(onBack = back)
        Screen.SCANNER -> ScannerScreen(onBack = back)
        Screen.CALCULATOR -> CalculatorScreen(onBack = back)
        Screen.CLOCK -> ClockScreen(onBack = back)
        Screen.FIREWALL -> FirewallScreen(onBack = back)
        Screen.PRIVACY -> PrivacyScreen(onBack = back)
        Screen.TERMINAL -> TerminalScreen(onBack = back)
        Screen.SENSORS -> SensorsScreen(onBack = back)
        Screen.CONVERT -> ConvertScreen(onBack = back)
        Screen.DIGEST -> DigestScreen(onBack = back)
        Screen.TEXT_TOOLS -> TextToolsScreen(onBack = back)
        Screen.COMPASS -> CompassScreen(onBack = back)
        Screen.NETCHECK -> NetcheckScreen(onBack = back)
        Screen.LEDGER -> LedgerScreen(onBack = back)
        Screen.CLEANER -> CleanerScreen(onBack = back)
        Screen.TORCH -> TorchScreen(onBack = back)
        Screen.LEVEL -> LevelScreen(onBack = back)
        Screen.TIMELAB -> TimeLabScreen(onBack = back)
        Screen.CIPHER -> CipherScreen(onBack = back)
        Screen.SPEEDTEST -> SpeedTestScreen(onBack = back)
        Screen.GENERATOR -> GeneratorScreen(onBack = back)
        Screen.MUSIC -> MusicScreen(onBack = back, onOpenSpotify = { open(Screen.SPOTIFY) })
        Screen.RECORDER -> RecorderScreen(onBack = back)
        Screen.METRONOME -> MetronomeScreen(onBack = back)
        Screen.SPOTIFY -> SpotifyScreen(onBack = back)
        Screen.RELAY -> RelayScreen(onBack = back)
        Screen.POWER -> PowerScreen(onBack = back)
        Screen.GALLERY -> GalleryScreen(onBack = back)
        Screen.WEATHER -> WeatherScreen(onBack = back)
        Screen.HABITS -> HabitsScreen(onBack = back)
        Screen.FLASHCARDS -> FlashcardsScreen(onBack = back)
        Screen.FEEDS -> FeedsScreen(onBack = back)
    }
}

@Composable
private fun BootSplash() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "DroidOS",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
    }
}
