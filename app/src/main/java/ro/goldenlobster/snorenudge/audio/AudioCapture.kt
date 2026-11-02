package ro.goldenlobster.snorenudge.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat

/** Opens an AudioRecord following the source/sample-rate preference from CLAUDE.md section 4.1. */
class AudioCapture(private val context: Context) {

    class Opened(
        val record: AudioRecord,
        val sourceName: String,
        val sampleRate: Int,
        val unprocessedSupported: Boolean,
    ) {
        /** Samples per 100 ms chunk. */
        val chunkSamples: Int get() = sampleRate / 10
    }

    /** Returns null if permission is missing or no configuration could be initialised. */
    @SuppressLint("MissingPermission") // checked explicitly below
    fun open(): Opened? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO not granted")
            return null
        }

        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val unprocessed = am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"

        val sources = buildList {
            if (unprocessed) add(MediaRecorder.AudioSource.UNPROCESSED to "UNPROCESSED")
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION")
            add(MediaRecorder.AudioSource.MIC to "MIC")
        }
        val rates = listOf(16000, 48000)

        for (rate in rates) {
            val minBuf = AudioRecord.getMinBufferSize(
                rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuf <= 0) {
                Log.w(TAG, "Sample rate $rate not supported (minBuf=$minBuf)")
                continue
            }
            // At least 1 s of audio (2 bytes per sample), never below the driver minimum.
            val bufBytes = maxOf(minBuf * 2, rate * 2)
            for ((src, name) in sources) {
                val rec = try {
                    AudioRecord.Builder()
                        .setAudioSource(src)
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(rate)
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                .build(),
                        )
                        .setBufferSizeInBytes(bufBytes)
                        .build()
                } catch (e: Exception) {
                    Log.w(TAG, "AudioRecord build failed for $name @ $rate", e)
                    null
                }
                if (rec != null && rec.state == AudioRecord.STATE_INITIALIZED) {
                    return Opened(rec, name, rate, unprocessed)
                }
                rec?.release()
            }
        }
        return null
    }

    private companion object {
        const val TAG = "SnoreNudge"
    }
}
