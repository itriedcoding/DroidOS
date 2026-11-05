package dev.droidos.shell.security

import java.util.concurrent.TimeUnit

/** Everything the scanner is allowed to know about a package. Pure data - unit-testable. */
data class PackageFacts(
    val packageName: String,
    val label: String,
    val installerPackage: String?,
    val isSystem: Boolean,
    val targetSdk: Int,
    val requestedPermissions: Set<String>,
    val hasAccessibilityService: Boolean,
    val isDeviceAdmin: Boolean,
    val isDebuggable: Boolean,
    val firstInstallTime: Long
) {
    /** Sideloaded = installed outside a recognised app store and not part of the firmware. */
    val isSideloaded: Boolean
        get() = !isSystem && installerPackage != null && installerPackage !in TRUSTED_INSTALLERS

    val dangerousPermissionCount: Int
        get() = requestedPermissions.count { it in DANGEROUS_PERMISSIONS }

    companion object {
        val TRUSTED_INSTALLERS = setOf(
            "com.android.vending",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.apps.restore",
            "com.samsung.android.galaxyapps"
        )

        val DANGEROUS_PERMISSIONS = setOf(
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.READ_SMS",
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.READ_PHONE_STATE",
            "android.permission.CALL_PHONE",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.BODY_SENSORS",
            "android.permission.GET_ACCOUNTS",
            "android.permission.PROCESS_OUTGOING_CALLS"
        )
    }
}

enum class Severity {
    SAFE, LOW, MEDIUM, HIGH;

    val label: String
        get() = when (this) {
            SAFE -> "Trusted"
            LOW -> "Low risk"
            MEDIUM -> "Worth a look"
            HIGH -> "High risk"
        }
}

data class Finding(
    val ruleId: String,
    val title: String,
    val detail: String,
    val severity: Severity,
    val points: Int
)

data class ScanResult(
    val packageName: String,
    val label: String,
    val score: Int,
    val findings: List<Finding>
) {
    val risk: Severity = ScanEngine.riskFor(score)
}

data class ScanReport(
    val results: List<ScanResult>,
    val scannedAt: Long,
    val durationMs: Long
) {
    val highRisk: Int get() = results.count { it.risk == Severity.HIGH }
    val mediumRisk: Int get() = results.count { it.risk == Severity.MEDIUM }
    val flagged: List<ScanResult> get() = results.filter { it.findings.isNotEmpty() }
}

/**
 * Heuristic on-device malware assessment. It does not pretend to be a signature-based
 * anti-virus: every finding is derived from facts the OS exposes (installer, requested
 * permissions, exported control surfaces) and each one explains itself in plain language.
 */
object ScanEngine {

    private const val FRESH_INSTALL_WINDOW_MS = 72L * 60L * 60L * 1000L

    private data class Rule(
        val id: String,
        val title: String,
        val severity: Severity,
        val points: Int,
        val triggers: (PackageFacts) -> Boolean,
        val detail: (PackageFacts) -> String
    )

    private val rules: List<Rule> = listOf(
        Rule(
            id = "installer.untrusted",
            title = "Installed outside an app store",
            severity = Severity.LOW,
            points = 10,
            triggers = { it.isSideloaded },
            detail = { "Installer package: ${it.installerPackage}" }
        ),
        Rule(
            id = "installer.untrusted.control",
            title = "Sideloaded app with a control surface",
            severity = Severity.HIGH,
            points = 35,
            triggers = {
                it.isSideloaded && (
                    it.hasAccessibilityService ||
                        it.isDeviceAdmin ||
                        "android.permission.REQUEST_INSTALL_PACKAGES" in it.requestedPermissions
                    )
            },
            detail = { "A sideloaded app that can observe the screen, administer the device or install software is a common malware pattern." }
        ),
        Rule(
            id = "surface.accessibility",
            title = "Accessibility service access",
            severity = Severity.MEDIUM,
            points = 20,
            triggers = { it.hasAccessibilityService },
            detail = { "Can read screen content and act on the user's behalf." }
        ),
        Rule(
            id = "surface.device_admin",
            title = "Device administrator",
            severity = Severity.MEDIUM,
            points = 15,
            triggers = { it.isDeviceAdmin },
            detail = { "Can enforce policies such as lock and wipe." }
        ),
        Rule(
            id = "build.debuggable",
            title = "Debuggable build",
            severity = Severity.HIGH,
            points = 30,
            triggers = { it.isDebuggable },
            detail = { "Debuggable apps allow code attachment and data extraction; production software is never shipped this way." }
        ),
        Rule(
            id = "build.outdated_sdk",
            title = "Built against an old Android release",
            severity = Severity.LOW,
            points = 8,
            triggers = { !it.isSystem && it.targetSdk < 30 },
            detail = { "Targets API ${it.targetSdk}; modern OS protections are only enforced for API 30 and above." }
        ),
        Rule(
            id = "perm.install_packages",
            title = "Can install other applications",
            severity = Severity.MEDIUM,
            points = 12,
            triggers = { "android.permission.REQUEST_INSTALL_PACKAGES" in it.requestedPermissions },
            detail = { "Holding this permission is how droppers install additional payloads." }
        ),
        Rule(
            id = "perm.overlay",
            title = "Can draw over other apps",
            severity = Severity.MEDIUM,
            points = 10,
            triggers = { "android.permission.SYSTEM_ALERT_WINDOW" in it.requestedPermissions },
            detail = { "Overlays are used for click-jacking and credential theft." }
        ),
        Rule(
            id = "perm.surveillance_combo",
            title = "Camera + microphone + location together",
            severity = Severity.MEDIUM,
            points = 12,
            triggers = {
                "android.permission.CAMERA" in it.requestedPermissions &&
                    "android.permission.RECORD_AUDIO" in it.requestedPermissions &&
                    "android.permission.ACCESS_FINE_LOCATION" in it.requestedPermissions
            },
            detail = { "The full sensor set is only needed by a small number of app categories." }
        ),
        Rule(
            id = "perm.messaging",
            title = "Reads or sends messages",
            severity = Severity.MEDIUM,
            points = 15,
            triggers = {
                !it.isSystem && (
                    "android.permission.READ_SMS" in it.requestedPermissions ||
                        "android.permission.SEND_SMS" in it.requestedPermissions
                    )
            },
            detail = { "SMS access is required for one-time-code interception." }
        ),
        Rule(
            id = "perm.breadth",
            title = "Requests an unusually broad permission set",
            severity = Severity.LOW,
            points = 8,
            triggers = { it.dangerousPermissionCount >= 10 },
            detail = { "Holds ${it.dangerousPermissionCount} dangerous permissions." }
        ),
        Rule(
            id = "install.fresh_sideload",
            title = "Recently sideloaded",
            severity = Severity.LOW,
            points = 5,
            triggers = { it.isSideloaded && it.firstInstallTime > 0L },
            detail = { "Installed outside a store; verify you trust the source." }
        )
    )

    fun riskFor(score: Int): Severity = when {
        score >= 50 -> Severity.HIGH
        score >= 20 -> Severity.MEDIUM
        score >= 1 -> Severity.LOW
        else -> Severity.SAFE
    }

    fun evaluate(facts: PackageFacts, nowMs: Long = System.currentTimeMillis()): ScanResult {
        val findings = rules.filter { it.triggers(facts) }.map { rule ->
            Finding(
                ruleId = rule.id,
                title = rule.title,
                detail = rule.detail(facts),
                severity = rule.severity,
                points = rule.points
            )
        }.filterNot { finding ->
            // Drop the generic "recently sideloaded" note once it is already explained.
            finding.ruleId == "install.fresh_sideload" &&
                nowMs - facts.firstInstallTime > FRESH_INSTALL_WINDOW_MS
        }

        val score = findings.sumOf { it.points }.coerceIn(0, 100)
        return ScanResult(
            packageName = facts.packageName,
            label = facts.label,
            score = score,
            findings = findings.sortedByDescending { it.points }
        )
    }

    /** Scans a batch and orders the riskiest first. Used by the app-level scan screen. */
    fun scanAll(factsList: List<PackageFacts>, nowMs: Long = System.currentTimeMillis()): List<ScanResult> =
        factsList.map { evaluate(it, nowMs) }
            .filter { it.findings.isNotEmpty() }
            .sortedWith(compareByDescending<ScanResult> { it.score }.thenBy { it.label })

    fun unused(): Long = TimeUnit.SECONDS.toMillis(0)
}
