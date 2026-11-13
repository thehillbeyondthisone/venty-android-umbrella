package io.github.orth4x0r.venty.ui.subventy

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * A short, quiet, synthesised dial-up handshake: dial tone, a few DTMF digits, an answer tone and a
 * burst of scrambled carrier. Generated on the fly (no recorded audio), played once per genuine
 * connect, and only when the user has switched the modem speaker on.
 */
object ModemSound {
    private const val RATE = 22050

    suspend fun play() = withContext(Dispatchers.Default) {
        val pcm = synth()
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
        }.getOrNull() ?: return@withContext
        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            kotlinx.coroutines.delay(pcm.size * 1000L / RATE + 100)
        } finally { runCatching { track.stop() }; track.release() }
    }

    private fun synth(): ShortArray {
        val out = ArrayList<Short>(RATE * 3)
        fun tone(ms: Int, vararg hz: Double, gain: Double = .18) {
            val n = RATE * ms / 1000
            for (i in 0 until n) {
                val t = i.toDouble() / RATE
                val env = minOf(1.0, i / 200.0, (n - i) / 200.0)
                val v = hz.sumOf { sin(2 * PI * it * t) } / hz.size * gain * env
                out.add((v * Short.MAX_VALUE).toInt().toShort())
            }
        }
        fun silence(ms: Int) = repeat(RATE * ms / 1000) { out.add(0) }
        val dtmf = mapOf('5' to (770.0 to 1336.0), '1' to (697.0 to 1209.0),
            '7' to (852.0 to 1209.0), '0' to (941.0 to 1336.0), '9' to (852.0 to 1477.0))
        tone(450, 350.0, 440.0, gain = .12)
        silence(120)
        "51709".forEach { d -> dtmf[d]?.let { (a, b) -> tone(70, a, b) }; silence(55) }
        silence(300)
        tone(520, 2100.0, gain = .10)                       // answer tone
        silence(60)
        tone(220, 1300.0, 2100.0, gain = .08)               // probing
        val rnd = Random(56)
        repeat(26) { tone(28, 600.0 + rnd.nextInt(2400), 1200.0 + rnd.nextInt(1600), gain = .07) } // training noise
        return out.toShortArray()
    }
}
