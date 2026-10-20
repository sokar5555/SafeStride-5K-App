package com.example.couchto5k.engine

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
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

    init {
        try {
            // Stream notification at 75% volume for clear, crisp audibility
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75)
        } catch (_: Exception) {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 75)
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
                val result = tts?.setLanguage(Locale.getDefault())
                if ((result != TextToSpeech.LANG_MISSING_DATA) && (result != TextToSpeech.LANG_NOT_SUPPORTED)) {
                    tts?.setSpeechRate(1.05f)
                    tts?.setPitch(1.0f)
                    _isTtsReady.value = true

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

    override fun playTransitionSound() {
        try {
            // TONE_PROP_BEEP is a single beep (unlike TONE_PROP_BEEP2 which is a double beep)
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (_: Exception) {
            // Ignore sound feedback errors
        }
    }

    override fun vibrateTransition() {
        try {
            val timings = longArrayOf(0, 300, 150, 300)
            val amplitudes = intArrayOf(0, 200, 0, 200)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } catch (_: Exception) {
            // Ignore vibration permission or hardware errors
        }
    }

    override fun speak(message: String) {
        try {
            if (_isTtsReady.value) {
                pendingSpeechMessage = null
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
