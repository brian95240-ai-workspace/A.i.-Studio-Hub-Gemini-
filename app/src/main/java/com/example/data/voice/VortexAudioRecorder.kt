package com.example.data.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

data class RecordedAudioClip(
    val bytes: ByteArray,
    val mimeType: String,
    val durationSec: Int
)

/**
 * Real Android MediaRecorder wrapper that captures microphone audio into an app-private
 * temporary MPEG-4/AAC (.m4a) file and returns raw bytes ready for Firebase AI Logic
 * multimodal transcription (`audio/mp4`), immediately deleting the temporary file.
 */
class VortexAudioRecorder(private val appContext: Context) {

    companion object {
        private const val TAG = "VortexAudioRecorder"
        const val MIME_AUDIO_MP4 = "audio/mp4"
        const val MAX_RECORDING_SECONDS = 300 // 5 minutes (~2.4 MB at 64kbps, well under 20MB limit)
        const val MAX_AUDIO_BYTES = 18 * 1024 * 1024 // 18 MB safety ceiling under 20 MB inline limit
    }

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMillis: Long = 0L

    @Volatile
    var isRecording: Boolean = false
        private set

    fun startRecording(): Result<Unit> {
        cancelAndDiscard()
        val tempFile = File(appContext.cacheDir, "vortex_voice_note_${System.currentTimeMillis()}.m4a")
        return try {
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(appContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(24_000)
                setAudioEncodingBitRate(64_000)
                setMaxDuration(MAX_RECORDING_SECONDS * 1000)
                setMaxFileSize(MAX_AUDIO_BYTES.toLong())
                setOutputFile(tempFile.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            outputFile = tempFile
            startedAtMillis = System.currentTimeMillis()
            isRecording = true
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start microphone recording", e)
            cleanupRecorderAndFile(tempFile)
            Result.failure(
                IllegalStateException(
                    "Unable to access device microphone (${e.localizedMessage ?: "hardware unavailable"})."
                )
            )
        }
    }

    /**
     * Reads the current microphone peak amplitude normalized into [0.08f, 1.0f] for live waveform bars.
     */
    fun readNormalizedAmplitude(): Float {
        if (!isRecording) return 0f
        val raw = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        return (raw.toFloat() / 22_000f).coerceIn(0.08f, 1f)
    }

    fun elapsedSeconds(): Int {
        if (!isRecording || startedAtMillis <= 0L) return 0
        return ((System.currentTimeMillis() - startedAtMillis) / 1000L).toInt().coerceAtLeast(0)
    }

    fun stopAndCollect(): Result<RecordedAudioClip> {
        val mr = recorder
        val file = outputFile
        val elapsed = elapsedSeconds().coerceAtLeast(1)
        isRecording = false
        recorder = null
        outputFile = null
        startedAtMillis = 0L

        if (mr == null || file == null) {
            return Result.failure(IllegalStateException("No active voice recording to transcribe."))
        }

        return try {
            runCatching { mr.stop() }
                .onFailure { Log.w(TAG, "MediaRecorder stop warning (clip may be very short)", it) }
            runCatching { mr.reset() }
            runCatching { mr.release() }

            if (!file.exists() || file.length() < 512L) {
                Result.failure(
                    IllegalStateException("Recording was too short or empty — speak for at least 1 second and try again.")
                )
            } else if (file.length() > MAX_AUDIO_BYTES) {
                Result.failure(
                    IllegalStateException("Audio clip exceeds the 18 MB upload ceiling. Record a shorter verbal note.")
                )
            } else {
                val bytes = file.readBytes()
                Result.success(
                    RecordedAudioClip(
                        bytes = bytes,
                        mimeType = MIME_AUDIO_MP4,
                        durationSec = elapsed
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to finalize recorded audio", e)
            Result.failure(
                IllegalStateException("Failed to finalize audio recording: ${e.localizedMessage ?: "I/O error"}")
            )
        } finally {
            runCatching { file.delete() }
        }
    }

    fun cancelAndDiscard() {
        val file = outputFile
        isRecording = false
        startedAtMillis = 0L
        cleanupRecorderAndFile(file)
    }

    fun release() {
        cancelAndDiscard()
    }

    private fun cleanupRecorderAndFile(file: File?) {
        val mr = recorder
        recorder = null
        outputFile = null
        if (mr != null) {
            runCatching { mr.stop() }
            runCatching { mr.reset() }
            runCatching { mr.release() }
        }
        if (file != null) {
            runCatching { if (file.exists()) file.delete() }
        }
    }
}
