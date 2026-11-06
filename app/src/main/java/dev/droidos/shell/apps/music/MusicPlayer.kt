package dev.droidos.shell.apps.music

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.Equalizer

/** One equalizer band as the platform reports it: centred at [hz], levels in millibels. */
data class EqBand(val index: Short, val hz: Int, val level: Short, val min: Short, val max: Short)

/**
 * Thin, honest wrapper around MediaPlayer. Every call is guarded: a codec the device cannot decode
 * becomes an error message on screen instead of a crash, and the equalizer is optional because a
 * device may not expose the effect at all.
 */
class MusicPlayer(private val context: Context, private val library: MusicLibrary) {

    private var player: MediaPlayer? = null
    private var equalizer: Equalizer? = null

    var lastError: String? = null
        private set

    fun play(track: Track, onComplete: () -> Unit): Boolean {
        releaseCurrent()
        lastError = null
        val media = MediaPlayer()
        return runCatching {
            media.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            media.setDataSource(context, library.uriFor(track))
            media.setOnCompletionListener { onComplete() }
            media.prepare()
            media.start()
            player = media
            attachEqualizer(media.audioSessionId)
            true
        }.getOrElse { error ->
            lastError = error.message ?: "This track could not be played"
            runCatching { media.release() }
            false
        }
    }

    fun pause() {
        val media = player ?: return
        runCatching { if (media.isPlaying) media.pause() }
    }

    fun resume(): Boolean {
        val media = player ?: return false
        return runCatching {
            media.start()
            true
        }.getOrDefault(false)
    }

    fun seekTo(positionMs: Long) {
        val media = player ?: return
        runCatching { media.seekTo(positionMs.toInt()) }
    }

    fun isPlaying(): Boolean = runCatching { player?.isPlaying == true }.getOrDefault(false)

    fun positionMs(): Long = runCatching { player?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)

    fun durationMs(): Long = runCatching { player?.duration?.toLong() ?: 0L }.getOrDefault(0L)

    fun audioSession(): Int = runCatching { player?.audioSessionId ?: 0 }.getOrDefault(0)

    fun stop() {
        releaseCurrent()
    }

    private fun releaseCurrent() {
        runCatching { equalizer?.release() }
        equalizer = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }

    // ---------------------------------------------------------------- equalizer

    private fun attachEqualizer(sessionId: Int) {
        if (sessionId == 0) return
        equalizer = runCatching {
            Equalizer(0, sessionId).also { it.setEnabled(true) }
        }.getOrNull()
    }

    fun equalizerAttached(): Boolean = equalizer != null

    fun presetNames(): List<String> {
        val eq = equalizer ?: return emptyList()
        return runCatching {
            (0 until eq.getNumberOfPresets().toInt()).map { index ->
                eq.getPresetName(index.toShort()) ?: "Preset $index"
            }
        }.getOrDefault(emptyList())
    }

    fun currentPreset(): Int {
        val eq = equalizer ?: return -1
        return runCatching { eq.getCurrentPreset().toInt() }.getOrDefault(-1)
    }

    fun usePreset(index: Int): Boolean {
        val eq = equalizer ?: return false
        return runCatching {
            eq.usePreset(index.toShort())
            true
        }.getOrDefault(false)
    }

    fun bands(): List<EqBand> {
        val eq = equalizer ?: return emptyList()
        return runCatching {
            val range = eq.getBandLevelRange()
            (0 until eq.getNumberOfBands().toInt()).map { raw ->
                val band = raw.toShort()
                EqBand(
                    index = band,
                    hz = eq.getCenterFreq(band) / 1000,
                    level = eq.getBandLevel(band),
                    min = range[0],
                    max = range[1]
                )
            }
        }.getOrDefault(emptyList())
    }

    fun setBandLevel(index: Short, level: Short): Boolean {
        val eq = equalizer ?: return false
        return runCatching {
            eq.setBandLevel(index, level)
            true
        }.getOrDefault(false)
    }
}
