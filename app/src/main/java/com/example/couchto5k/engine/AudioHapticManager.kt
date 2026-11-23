package com.example.couchto5k.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.couchto5k.domain.model.DistanceUnit
import com.example.couchto5k.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

interface AudioHapticManager {
    val isTtsReady: StateFlow<Boolean>

    fun playTransitionSound()
    fun vibrateTransition()
    fun speak(message: String)
    fun announceSplit(splitNumber: Int, paceSecondsPerUnit: Double, unit: DistanceUnit) {
        playTransitionSound()
        vibrateTransition()
        val totalPaceSeconds = paceSecondsPerUnit.toInt().coerceAtLeast(0)
        val minutes = totalPaceSeconds / 60
        val seconds = totalPaceSeconds % 60
        val formattedPace = String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        val unitLabel = if (unit == DistanceUnit.MILES) "Mile" else "Kilometer"
        speak("$unitLabel $splitNumber complete, $formattedPace pace")
    }
    fun release()
}

@Singleton
class DefaultAudioHapticManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) : AudioHapticManager, TextToSpeech.OnInitListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var voiceCuesEnabled: Boolean = true

    @Volatile
    private var soundCuesEnabled: Boolean = true

    @Volatile
    private var vibrationEnabled: Boolean = true

    @Volatile
    private var audioDuckingEnabled: Boolean = true

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private var toneGenerator: ToneGenerator? = null
    private var tts: TextToSpeech? = null
    private val _isTtsReady = MutableStateFlow(value = false)
    override val isTtsReady: StateFlow<Boolean> = _isTtsReady.asStateFlow()

    @Volatile
    private var pendingSpeechMessage: String? = null

    private val focusLock = Any()
    private var audioFocusRequest: AudioFocusRequest? = null
    private var activeUtteranceId: String? = null
    private var toneFocusReleaseJob: Job? = null
    private val utteranceCounter = AtomicLong(0L)

    companion object {
        private const val WARMUP_UTTERANCE_ID = "warmup_utterance"
        private const val TONE_DUCK_DURATION_MS = 300L
    }

    init {
        scope.launch {
            settingsRepository.voiceCuesEnabled.collectLatest { voiceCuesEnabled = it }
        }
        scope.launch {
            settingsRepository.soundCuesEnabled.collectLatest { soundCuesEnabled = it }
        }
        scope.launch {
            settingsRepository.vibrationEnabled.collectLatest { vibrationEnabled = it }
        }
        scope.launch {
            settingsRepository.audioDuckingEnabled.collectLatest { enabled ->
                audioDuckingEnabled = enabled
                if (!enabled) {
                    synchronized(focusLock) {
                        toneFocusReleaseJob?.cancel()
                        toneFocusReleaseJob = null
                        abandonAudioFocusLocked()
                    }
                }
            }
        }

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        } catch (_: Exception) {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            } catch (_: Exception) {}
        }

        try {
            tts = TextToSpeech(context, this)
        } catch (_: Exception) {
            // Ignore TTS initialization error
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                tts?.setAudioAttributes(audioAttributes)

                val result = tts?.setLanguage(Locale.getDefault())
                if ((result != TextToSpeech.LANG_MISSING_DATA) && (result != TextToSpeech.LANG_NOT_SUPPORTED)) {
                    tts?.setSpeechRate(1.0f)
                    tts?.setPitch(1.0f)

                    tts?.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}

                            override fun onDone(utteranceId: String?) {
                                handleUtteranceFinished(utteranceId)
                            }

                            @Suppress("OVERRIDE_DEPRECATION")
                            override fun onError(utteranceId: String?) {
                                handleUtteranceFinished(utteranceId)
                            }

                            override fun onError(utteranceId: String?, errorCode: Int) {
                                handleUtteranceFinished(utteranceId)
                            }

                            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                                handleUtteranceFinished(utteranceId)
                            }
                        },
                    )

                    _isTtsReady.value = true

                    // Warm up native AudioTrack pipeline with silent utterance to eliminate cold-start lag & static
                    tts?.speak(" ", TextToSpeech.QUEUE_FLUSH, null, WARMUP_UTTERANCE_ID)

                    // Play queued speech message if speak() was called before TTS finished initializing
                    pendingSpeechMessage?.let { msg ->
                        pendingSpeechMessage = null
                        speak(msg)
                    }
                }
            } catch (_: Exception) {
                // Ignore locale setup error
            }
        }
    }

    private fun handleUtteranceFinished(utteranceId: String?) {
        if ((utteranceId == null) || (utteranceId == WARMUP_UTTERANCE_ID)) return
        synchronized(focusLock) {
            if (utteranceId == activeUtteranceId) {
                activeUtteranceId = null
                abandonAudioFocusLocked()
            }
        }
    }

    private fun requestAudioFocusLocked() {
        if (!audioDuckingEnabled) return
        try {
            if (audioFocusRequest == null) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    .setAcceptsDelayedFocusGain(false)
                    .build()
            }
            audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
        } catch (_: Exception) {}
    }

    private fun abandonAudioFocusLocked() {
        try {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } catch (_: Exception) {}
    }

    override fun playTransitionSound() {
        if (!soundCuesEnabled) return
        try {
            if (audioDuckingEnabled) {
                synchronized(focusLock) {
                    toneFocusReleaseJob?.cancel()
                    requestAudioFocusLocked()
                    toneFocusReleaseJob = scope.launch {
                        delay(TONE_DUCK_DURATION_MS.milliseconds)
                        synchronized(focusLock) {
                            if (activeUtteranceId == null) {
                                abandonAudioFocusLocked()
                            }
                        }
                    }
                }
            }
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (_: Exception) {}
    }

    override fun vibrateTransition() {
        if (!vibrationEnabled) return
        try {
            val timings = longArrayOf(0, 300, 150, 300)
            val amplitudes = intArrayOf(0, 200, 0, 200)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } catch (_: Exception) {}
    }

    override fun speak(message: String) {
        if (!voiceCuesEnabled) return
        try {
            if (_isTtsReady.value) {
                pendingSpeechMessage = null
                val utteranceId = "WorkoutEngineTTS_${utteranceCounter.incrementAndGet()}"
                synchronized(focusLock) {
                    toneFocusReleaseJob?.cancel()
                    toneFocusReleaseJob = null
                    activeUtteranceId = utteranceId
                    requestAudioFocusLocked()
                }
                val speakResult = tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                if (speakResult == TextToSpeech.ERROR) {
                    handleUtteranceFinished(utteranceId)
                }
            } else {
                // Queue message if TTS is still initializing on cold startup
                pendingSpeechMessage = message
            }
        } catch (_: Exception) {
            // Ignore speech errors
        }
    }

    override fun release() {
        scope.cancel()
        synchronized(focusLock) {
            toneFocusReleaseJob?.cancel()
            toneFocusReleaseJob = null
            activeUtteranceId = null
            abandonAudioFocusLocked()
            audioFocusRequest = null
        }
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        _isTtsReady.value = false
        pendingSpeechMessage = null

        try {
            toneGenerator?.release()
        } catch (_: Exception) {}
        toneGenerator = null
    }
}
