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
    @ApplicationContext private val context: Context
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
    private val _isTtsReady = MutableStateFlow(false)
    override val isTtsReady: StateFlow<Boolean> = _isTtsReady.asStateFlow()

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        } catch (e: Exception) {
            // Ignore if tone generator cannot be initialized
        }

        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                _isTtsReady.value = true
            }
        }
    }

    override fun playTransitionSound() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 500)
    }

    override fun vibrateTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 500, 200, 500)
            val amplitudes = intArrayOf(0, 255, 0, 255)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 500, 200, 500), -1)
        }
    }

    override fun speak(message: String) {
        if (_isTtsReady.value) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "WorkoutEngineTTS")
        }
    }

    override fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _isTtsReady.value = false

        toneGenerator?.release()
        toneGenerator = null
    }
}