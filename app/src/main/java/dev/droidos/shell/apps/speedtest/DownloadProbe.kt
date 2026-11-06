package dev.droidos.shell.apps.speedtest

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

sealed interface ProbeResult {
    data class Measured(
        val bytes: Long,
        val millis: Long,
        val status: Int,
        val complete: Boolean
    ) : ProbeResult

    data class Failed(val reason: String) : ProbeResult
}

/**
 * Downloads a URL for real and counts the bytes as they arrive. It stops at [limitBytes] or
 * when the payload ends, whichever comes first, so a huge file cannot run the phone out of
 * memory or data. No rate is invented: a failed connection is a failure, not a number.
 */
class DownloadProbe {

    private val cancelled = AtomicBoolean(false)

    fun cancel() {
        cancelled.set(true)
    }

    fun run(
        url: String,
        limitBytes: Long = 25L * 1024L * 1024L,
        onProgress: (bytes: Long, elapsedMillis: Long) -> Unit = { _, _ -> }
    ): ProbeResult {
        cancelled.set(false)
        val connection = try {
            (URL(url.trim()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "DroidOS-SpeedTest")
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Cache-Control", "no-store")
            }
        } catch (e: Exception) {
            return ProbeResult.Failed("Not a usable URL: ${e.message ?: e::class.java.simpleName}")
        }

        val started = System.nanoTime()
        var total = 0L
        var lastReport = 0L
        var complete = false
        return try {
            connection.connect()
            val status = connection.responseCode
            if (status !in 200..299) {
                return ProbeResult.Failed("Server answered HTTP $status")
            }
            val buffer = ByteArray(64 * 1024)
            connection.inputStream.use { stream ->
                while (!cancelled.get() && total < limitBytes) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    total += read
                    val elapsed = (System.nanoTime() - started) / 1_000_000L
                    if (elapsed - lastReport >= 250L) {
                        lastReport = elapsed
                        onProgress(total, elapsed)
                    }
                }
            }
            complete = total >= limitBytes || connection.contentLengthLong in 0..total
            val elapsed = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(1L)
            onProgress(total, elapsed)
            if (total == 0L) {
                ProbeResult.Failed("The server sent no data")
            } else {
                ProbeResult.Measured(total, elapsed, status, complete && !cancelled.get())
            }
        } catch (e: IOException) {
            if (total > 0L) {
                val elapsed = ((System.nanoTime() - started) / 1_000_000L).coerceAtLeast(1L)
                ProbeResult.Measured(total, elapsed, connection.responseCode.coerceAtLeast(0), false)
            } else {
                ProbeResult.Failed(e.message ?: "Connection failed")
            }
        } catch (e: SecurityException) {
            ProbeResult.Failed("Blocked by the DroidOS firewall: ${e.message}")
        } finally {
            runCatching { connection.disconnect() }
        }
    }
}
