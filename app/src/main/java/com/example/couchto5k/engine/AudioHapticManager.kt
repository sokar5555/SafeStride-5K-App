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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

interface AudioHapticManager {
    val isTtsReady: StateFlow<Boolean>

    fun playTransitionSound()
    fun vibrateTransition()
    fun speak(message: String)
    fun release()
}

@Singleton
class DefaultAudioHapticManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AudioHapticManager, TextToSpeech.OnInitListener {

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

    private var audioFocusRequest: AudioFocusRequest? = null

    init {
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
                // Explicitly set high-quality speech audio attributes
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
                                abandonAudioFocus()
                            }
                            @Suppress("OVERRIDE_DEPRECATION")
                            override fun onError(utteranceId: String?) {
                                abandonAudioFocus()
                            }
                        },
                    )

                    _isTtsReady.value = true

                    // Warm up native AudioTrack pipeline with silent utterance to eliminate cold-start lag & static
                    tts?.speak(" ", TextToSpeech.QUEUE_FLUSH, null, "warmup_utterance")

                    // Play queued speech message if speak() was called before TTS finished initializing
                    pendingSpeechMessage?.let { msg ->
                        speak(msg)
                        pendingSpeechMessage = null
                    }
                }
            } catch (_: Exception) {
                // Ignore locale setup error
            }
        }
    }

    private fun requestAudioFocus() {
        try {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(false)
                .build()
            audioFocusRequest = focusRequest
            audioManager.requestAudioFocus(focusRequest)
        } catch (_: Exception) {}
    }

    private fun abandonAudioFocus() {
        try {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } catch (_: Exception) {}
    }

    override fun playTransitionSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (_: Exception) {}
    }

    override fun vibrateTransition() {
        try {
            val timings = longArrayOf(0, 300, 150, 300)
            val amplitudes = intArrayOf(0, 200, 0, 200)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } catch (_: Exception) {}
    }

    override fun speak(message: String) {
        try {
            if (_isTtsReady.value) {
                pendingSpeechMessage = null
                requestAudioFocus()
                tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "WorkoutEngineTTS")
            } else {
                // Queue message if TTS is still initializing on cold startup
                pendingSpeechMessage = message
            }
        } catch (_: Exception) {
            // Ignore speech errors
        }
    }

    override fun release() {
        abandonAudioFocus()
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
