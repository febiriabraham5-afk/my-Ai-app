package com.example.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

private const val TAG = "SoundFeedback"

object SoundFeedback {

    private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val chimeSamples: ShortArray by lazy { generateSendChimeSamples() }

    fun playSendChime(context: Context) {
        val appContext = context.applicationContext
        audioScope.launch {
            var track: AudioTrack? = null
            try {
                val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (audioManager != null && audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) {
                    return@launch
                }

                val sampleRate = 22050
                val samples = chimeSamples
                val bufferBytes = samples.size * 2

                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val format = AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                track = AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferBytes)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                if (track.state != AudioTrack.STATE_INITIALIZED) {
                    return@launch
                }

                track.write(samples, 0, samples.size)
                track.play()
                delay(190L)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to play send chime", e)
            } finally {
                runCatching {
                    track?.stop()
                    track?.release()
                }
            }
        }
    }

    private fun generateSendChimeSamples(): ShortArray {
        val sampleRate = 22050
        val totalMs = 150
        val totalSamples = (sampleRate * totalMs) / 1000
        val splitSample = (sampleRate * 62) / 1000
        val samples = ShortArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val sampleValue = if (i < splitSample) {
                val localT = i.toDouble() / sampleRate
                val attack = (i / 140.0).coerceAtMost(1.0)
                val decay = exp(-localT * 24.0)
                sin(2.0 * PI * 659.25 * t) * attack * decay
            } else {
                val localIdx = i - splitSample
                val localT = localIdx.toDouble() / sampleRate
                val attack = (localIdx / 140.0).coerceAtMost(1.0)
                val decay = exp(-localT * 22.0)
                (sin(2.0 * PI * 987.77 * t) + 0.25 * sin(2.0 * PI * 1975.53 * t)) * attack * decay
            }
            val scaled = (sampleValue * 0.32 * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            samples[i] = scaled.toShort()
        }
        return samples
    }
}
