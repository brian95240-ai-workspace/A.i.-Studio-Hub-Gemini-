package com.example.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

enum class DictationTarget {
    QUERY,
    PROJECT_FOLDER,
    FOLLOW_UP,
    SEARCH
}

data class DictationUiState(
    val activeTarget: DictationTarget? = null,
    val liveDraft: String = "",
    val micLevel: Float = 0f,
    val statusNote: String? = null
) {
    val isRecording: Boolean get() = activeTarget != null
}

class VortexSpeechController(
    private val appContext: Context,
    private val onState: (DictationUiState) -> Unit,
    private val onFinalTranscript: (DictationTarget, String) -> Unit,
    private val onFallbackIntentNeeded: (DictationTarget) -> Unit,
    private val onWarning: (String) -> Unit
) {
    private var engine: SpeechRecognizer? = null
    private var currentTarget: DictationTarget? = null
    private var latestPartial: String = ""

    fun isDirectRecognizerReady(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(appContext)

    fun buildSystemSpeechIntent(promptText: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, promptText)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

    fun startInline(target: DictationTarget) {
        stopInline(commitPartial = false)
        if (!isDirectRecognizerReady()) {
            onFallbackIntentNeeded(target)
            return
        }

        currentTarget = target
        latestPartial = ""
        onState(DictationUiState(activeTarget = target, liveDraft = "", micLevel = 0.12f))

        val sr = runCatching { SpeechRecognizer.createSpeechRecognizer(appContext) }.getOrNull()
        if (sr == null) {
            currentTarget = null
            onState(DictationUiState())
            onFallbackIntentNeeded(target)
            return
        }
        engine = sr
        sr.setRecognitionListener(createCallbacks(target))

        val captureIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        runCatching { sr.startListening(captureIntent) }.onFailure {
            teardownEngine()
            currentTarget = null
            onState(DictationUiState())
            onFallbackIntentNeeded(target)
        }
    }

    fun stopInline(commitPartial: Boolean) {
        val target = currentTarget
        val savedPartial = latestPartial.trim()
        currentTarget = null
        latestPartial = ""
        runCatching { engine?.stopListening() }
        teardownEngine()
        onState(DictationUiState())
        if (commitPartial && target != null && savedPartial.isNotEmpty()) {
            onFinalTranscript(target, savedPartial)
        }
    }

    fun release() {
        currentTarget = null
        latestPartial = ""
        teardownEngine()
    }

    private fun teardownEngine() {
        runCatching { engine?.cancel() }
        runCatching { engine?.destroy() }
        engine = null
    }

    private fun createCallbacks(target: DictationTarget) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            onState(DictationUiState(activeTarget = target, liveDraft = latestPartial, micLevel = 0.18f))
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            if (currentTarget != target) return
            val scaled = ((rmsdB + 2f) / 12f).coerceIn(0.08f, 1f)
            onState(DictationUiState(activeTarget = target, liveDraft = latestPartial, micLevel = scaled))
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            if (currentTarget == target) {
                onState(
                    DictationUiState(
                        activeTarget = target,
                        liveDraft = latestPartial,
                        micLevel = 0.1f,
                        statusNote = "Finalizing text..."
                    )
                )
            }
        }

        override fun onError(error: Int) {
            val rescued = latestPartial.trim()
            currentTarget = null
            latestPartial = ""
            teardownEngine()
            onState(DictationUiState())

            if (rescued.isNotEmpty() &&
                (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH)
            ) {
                onFinalTranscript(target, rescued)
                return
            }

            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                error == SpeechRecognizer.ERROR_CLIENT ||
                error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
            ) {
                onFallbackIntentNeeded(target)
                return
            }

            val notice = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch any speech — tap the mic to try again."
                SpeechRecognizer.ERROR_AUDIO -> "Audio capture failed — check device microphone."
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service requires a network connection or offline language pack."
                else -> "Voice dictation interrupted (code $error)."
            }
            onWarning(notice)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val chosen = matches?.firstOrNull()?.trim().orEmpty().ifEmpty { latestPartial.trim() }
            currentTarget = null
            latestPartial = ""
            teardownEngine()
            onState(DictationUiState())
            if (chosen.isNotEmpty()) {
                onFinalTranscript(target, chosen)
            } else {
                onWarning("No words were recognized — tap the mic to dictate again.")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (currentTarget != target) return
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (text.isNotEmpty()) {
                latestPartial = text
                onState(DictationUiState(activeTarget = target, liveDraft = text, micLevel = 0.55f))
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    companion object {
        fun mergeTranscriptIntoField(existing: String, spoken: String): String {
            val cleanSpoken = spoken.trim()
            if (cleanSpoken.isEmpty()) return existing
            val trimmedExisting = existing.trimEnd()
            return if (trimmedExisting.isEmpty()) {
                cleanSpoken
            } else {
                val joiner = if (trimmedExisting.endsWith("\n")) "" else " "
                "$trimmedExisting$joiner$cleanSpoken"
            }
        }

        fun formatSpokenFolderSlug(spoken: String): String =
            spoken.lowercase(Locale.US)
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
    }
}
