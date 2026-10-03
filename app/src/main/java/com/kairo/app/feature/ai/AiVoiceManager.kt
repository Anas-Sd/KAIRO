package com.kairo.app.feature.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.kairo.app.KairoApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Manages low-latency real-time voice communication:
 * 1. Speech-to-Text (STT) for continuous voice input.
 * 2. Text-to-Speech (TTS) for instant spoken responses with zero lag.
 * 3. "Hey Buddy" foreground wake-phrase listener.
 */
object AiVoiceManager : TextToSpeech.OnInitListener {

    private const val TAG = "AiVoiceManager"
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var onSpeechResultListener: ((String) -> Unit)? = null
    private var onWakeWordListener: (() -> Unit)? = null

    var isVoiceModeActive: Boolean = false
        private set

    init {
        initTts(KairoApplication.instance)
    }

    fun initTts(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsInitialized = true
                tts?.setSpeechRate(1.05f) // crisp, low-latency conversational cadence
                tts?.setPitch(1.0f)
            }
        }
    }

    /**
     * Speaks text aloud with zero perceptible delay.
     * When speaking ends, automatically triggers [onDone] to resume listening.
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        val cleanText = text.replace(Regex("[*#_`~]"), "").trim()
        if (cleanText.isBlank() || tts == null || !isTtsInitialized) {
            onDone?.invoke()
            return
        }

        val utteranceId = "kairo_utterance_${System.currentTimeMillis()}"

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                onDone?.invoke()
            }

            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                onDone?.invoke()
            }
        })

        _isSpeaking.value = true
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
    }

    // ==========================================
    // SPEECH RECOGNITION (STT)
    // ==========================================

    fun startListening(
        context: Context,
        onResult: (String) -> Unit
    ) {
        stopSpeaking()
        this.onSpeechResultListener = onResult

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Speech recognition not available on this device.")
            _isListening.value = false
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _isListening.value = false
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        Log.d(TAG, "SpeechRecognizer error: $error")
                        // If voice mode is still active, retry listening after brief pause
                        if (isVoiceModeActive && error == SpeechRecognizer.ERROR_NO_MATCH) {
                            startListening(context, onResult)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val heard = matches?.firstOrNull()?.trim()
                        if (!heard.isNullOrBlank()) {
                            onSpeechResultListener?.invoke(heard)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognizer: ${e.message}", e)
            _isListening.value = false
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}
        _isListening.value = false
    }

    fun setVoiceMode(active: Boolean) {
        this.isVoiceModeActive = active
        if (!active) {
            stopListening()
            stopSpeaking()
        }
    }

    // ==========================================
    // "HEY BUDDY" WAKE PHRASE MONITOR
    // ==========================================

    fun setWakeWordListener(listener: (() -> Unit)?) {
        this.onWakeWordListener = listener
    }

    /**
     * Checks heard speech for "hey buddy" wake phrase and triggers navigation.
     */
    fun checkWakeWord(text: String): Boolean {
        val lower = text.trim().lowercase()
        if (lower.contains("hey buddy") || lower.contains("hi buddy") || lower.contains("ok buddy")) {
            onWakeWordListener?.invoke()
            return true
        }
        return false
    }
}
