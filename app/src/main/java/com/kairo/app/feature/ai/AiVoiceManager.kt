package com.kairo.app.feature.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
 * 1. Speech-to-Text (STT) for continuous voice input with partial transcription streaming.
 * 2. Text-to-Speech (TTS) for instant spoken responses with zero lag.
 * 3. Automatic turn-taking voice loop that never disconnects mid-conversation.
 * 4. "Hey Buddy" foreground wake-phrase listener.
 */
object AiVoiceManager : TextToSpeech.OnInitListener {

    private const val TAG = "AiVoiceManager"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _partialSpeech = MutableStateFlow("")
    val partialSpeech: StateFlow<String> = _partialSpeech.asStateFlow()

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
                tts?.setSpeechRate(1.05f) // crisp conversational cadence
                tts?.setPitch(1.0f)
            }
        }
    }

    /**
     * Speaks text aloud with zero perceptible delay.
     * When speaking ends, automatically triggers [onDone] on the Main Thread after a brief 250ms echo-gap.
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        val cleanText = text.replace(Regex("[*#_`~]"), "").trim()
        if (cleanText.isBlank() || tts == null || !isTtsInitialized) {
            mainHandler.post { onDone?.invoke() }
            return
        }

        stopListening()
        val utteranceId = "kairo_utterance_${System.currentTimeMillis()}"

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                // Echo protection gap: wait 250ms so speaker audio does not bleed into the mic
                mainHandler.postDelayed({
                    if (isVoiceModeActive) {
                        onDone?.invoke()
                    }
                }, 250L)
            }

            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                mainHandler.postDelayed({
                    if (isVoiceModeActive) {
                        onDone?.invoke()
                    }
                }, 250L)
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
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { startListening(context, onResult) }
            return
        }

        stopSpeaking()
        this.onSpeechResultListener = onResult
        _partialSpeech.value = ""

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
                        _partialSpeech.value = ""
                        Log.d(TAG, "SpeechRecognizer error code: $error")
                        // Automatically re-arm listener so recurring voice mode never dies
                        if (isVoiceModeActive && !_isSpeaking.value) {
                            mainHandler.postDelayed({
                                if (isVoiceModeActive && !_isSpeaking.value) {
                                    startListening(context, onResult)
                                }
                            }, 400L)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        _partialSpeech.value = ""
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val heard = matches?.firstOrNull()?.trim()
                        if (!heard.isNullOrBlank()) {
                            onSpeechResultListener?.invoke(heard)
                        } else if (isVoiceModeActive && !_isSpeaking.value) {
                            // If empty, re-listen seamlessly
                            mainHandler.postDelayed({
                                if (isVoiceModeActive && !_isSpeaking.value) {
                                    startListening(context, onResult)
                                }
                            }, 300L)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull()?.trim()
                        if (!partial.isNullOrBlank()) {
                            _partialSpeech.value = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val deviceLocaleTag = Locale.getDefault().toLanguageTag().ifBlank { "en-US" }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, deviceLocaleTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, deviceLocaleTag)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1200L)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
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
        _partialSpeech.value = ""
    }

    fun setVoiceMode(active: Boolean) {
        this.isVoiceModeActive = active
        if (active) {
            stopWakeWordDetection()
        } else {
            stopListening()
            stopSpeaking()
        }
    }

    // ==========================================
    // "HEY BUDDY" WAKE PHRASE MONITOR
    // ==========================================

    private var wakeWordRecognizer: SpeechRecognizer? = null
    private var isWakeWordActive = false
    private var wakeWordContext: Context? = null

    fun setWakeWordListener(listener: (() -> Unit)?) {
        this.onWakeWordListener = listener
    }

    /**
     * Starts continuous low-overhead foreground wake-word detector.
     * When "Hey Buddy" is heard, triggers [onWakeWordListener] and stops itself.
     */
    fun startWakeWordDetection(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { startWakeWordDetection(context) }
            return
        }
        if (isVoiceModeActive || _isListening.value || _isSpeaking.value) {
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "SpeechRecognizer not available for wake word.")
            return
        }

        wakeWordContext = context.applicationContext
        isWakeWordActive = true
        initiateWakeWordRecognizer()
    }

    private fun initiateWakeWordRecognizer() {
        val ctx = wakeWordContext ?: return
        if (!isWakeWordActive || isVoiceModeActive || _isSpeaking.value) return

        try {
            wakeWordRecognizer?.destroy()
            wakeWordRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        Log.d(TAG, "Wake word recognizer error: $error")
                        if (isWakeWordActive && !isVoiceModeActive && !_isSpeaking.value) {
                            mainHandler.postDelayed({
                                initiateWakeWordRecognizer()
                            }, 500L)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
                        val detected = matches.any { checkWakeWord(it) }
                        if (detected) {
                            stopWakeWordDetection()
                            mainHandler.post { onWakeWordListener?.invoke() }
                        } else if (isWakeWordActive && !isVoiceModeActive && !_isSpeaking.value) {
                            mainHandler.postDelayed({
                                initiateWakeWordRecognizer()
                            }, 300L)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
                        val detected = matches.any { checkWakeWord(it) }
                        if (detected) {
                            stopWakeWordDetection()
                            mainHandler.post { onWakeWordListener?.invoke() }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val deviceLocaleTag = Locale.getDefault().toLanguageTag().ifBlank { "en-US" }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, deviceLocaleTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, deviceLocaleTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            }

            wakeWordRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating wake word recognizer: ${e.message}")
        }
    }

    fun stopWakeWordDetection() {
        isWakeWordActive = false
        try {
            wakeWordRecognizer?.stopListening()
            wakeWordRecognizer?.destroy()
            wakeWordRecognizer = null
        } catch (_: Exception) {}
    }

    /**
     * Checks heard speech for "hey buddy" wake phrase and triggers navigation.
     */
    fun checkWakeWord(text: String): Boolean {
        val lower = text.trim().lowercase()
        return lower.contains("hey buddy") ||
                lower.contains("hi buddy") ||
                lower.contains("ok buddy") ||
                lower.contains("hello buddy") ||
                lower.contains("hey body") ||
                lower.contains("hi body") ||
                lower.contains("hey kairo") ||
                lower.contains("hi kairo") ||
                lower.contains("kairo")
    }
}
