# Compose + navigation keep their default ProGuard behaviour.
-dontwarn org.jetbrains.annotations.**

# Keep the notification listener and VPN service entry points addressable by the
# system, plus the reflective pieces of Compose runtime.
-keep class dev.droidos.shell.shell.notifications.DroidNotificationListener { *; }
-keep class dev.droidos.shell.firewall.DroidFirewallService { *; }
-keep class dev.droidos.shell.core.DroidOsApp { *; }

# Room-free SQLite access uses plain framework classes; nothing to keep.

# Coroutines
-dontwarn kotlinx.coroutines.**
