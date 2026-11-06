package dev.droidos.shell.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Every destination the shell can render in-process. */
enum class Screen(val title: String) {
    HOME("DroidOS"),
    DRAWER("Apps"),
    RECENTS("Recents"),
    SEARCH("Search"),
    SETTINGS("Settings"),
    APPEARANCE("Appearance"),
    DISPLAY("Display"),
    SOUND("Sound & vibration"),
    LOCATION("Location"),
    DATETIME("Date & time"),
    NETWORK("Wi-Fi & Bluetooth"),
    BATTERY("Battery"),
    STORAGE("Storage"),
    ACCESSIBILITY("Accessibility"),
    APPS("Installed apps"),
    PERMISSIONS("Permission audit"),
    SECURITY("Security centre"),
    UPDATES("System update"),
    DIAGNOSTICS("Diagnostics"),
    ABOUT("About"),
    FILES("Files"),
    NOTES("Notes"),
    VAULT("Vault"),
    SCANNER("Security scan"),
    CALCULATOR("Calculator"),
    CLOCK("Clock"),
    FIREWALL("Firewall"),
    PRIVACY("Privacy"),
    TERMINAL("Terminal"),
    SENSORS("Sensors"),
    CONVERT("Convert"),
    DIGEST("Digest"),
    TEXT_TOOLS("Text tools"),
    COMPASS("Compass"),
    NETCHECK("Netcheck"),
    LEDGER("Ledger"),
    CLEANER("Cleaner"),
    TORCH("Torch"),
    LEVEL("Level"),
    TIMELAB("Time lab"),
    CIPHER("Cipher"),
    SPEEDTEST("Speed test"),
    GENERATOR("Generator")
}

/** Simple, predictable back stack. Home is always at the bottom. */
class ShellNavigator(initial: Screen = Screen.HOME) {
    var stack by mutableStateOf(listOf(initial))
        private set

    val current: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun push(screen: Screen) {
        if (stack.last() == screen) return
        stack = stack + screen
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack = stack.dropLast(1)
        return true
    }

    fun home() {
        stack = listOf(Screen.HOME)
    }
}
