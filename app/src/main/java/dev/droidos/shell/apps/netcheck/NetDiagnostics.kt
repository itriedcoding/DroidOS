package dev.droidos.shell.apps.netcheck

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

/**
 * Network probes against a host the user names. Every result is measured here: DNS through
 * `InetAddress`, a real TCP handshake through `Socket`, and a real HTTPS request. Failures are
 * returned with the platform's own message rather than a friendly substitute.
 */
class NetDiagnostics {

    sealed interface Probe {
        data class Ok(val detail: String, val millis: Long) : Probe
        data class Failed(val reason: String, val millis: Long) : Probe
    }

    data class Report(val host: String, val dns: Probe, val tcp: Probe, val https: Probe)

    suspend fun check(rawHost: String): Report = withContext(Dispatchers.IO) {
        val host = normalize(rawHost)
        Report(host = host, dns = dns(host), tcp = tcp(host), https = https(host))
    }

    private fun dns(host: String): Probe {
        val started = System.nanoTime()
        return try {
            val addresses = InetAddress.getAllByName(host)
            Probe.Ok(
                addresses.joinToString(", ") { it.hostAddress ?: it.hostName },
                elapsed(started)
            )
        } catch (e: Exception) {
            Probe.Failed(e.message ?: e::class.java.simpleName, elapsed(started))
        }
    }

    private fun tcp(host: String): Probe {
        val started = System.nanoTime()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, PORT), TIMEOUT_MILLIS)
                Probe.Ok("connected to $host:$PORT", elapsed(started))
            }
        } catch (e: Exception) {
            Probe.Failed(e.message ?: e::class.java.simpleName, elapsed(started))
        }
    }

    private fun https(host: String): Probe {
        val started = System.nanoTime()
        return try {
            val connection = (URL("https://$host/").openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "DroidOS-Netcheck/1.0")
            }
            try {
                val code = connection.responseCode
                val message = connection.responseMessage.orEmpty()
                Probe.Ok("HTTP $code $message".trim(), elapsed(started))
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Probe.Failed(e.message ?: e::class.java.simpleName, elapsed(started))
        }
    }

    private fun elapsed(startedNanos: Long): Long = (System.nanoTime() - startedNanos) / 1_000_000

    companion object {
        private const val PORT = 443
        private const val TIMEOUT_MILLIS = 8_000

        /** Accepts "example.com", "https://example.com/path" and "example.com:443". */
        fun normalize(raw: String): String {
            val trimmed = raw.trim()
            val withoutScheme = trimmed.substringAfter("://", trimmed)
            return withoutScheme.substringBefore('/').substringBefore(':').trim()
        }
    }
}
