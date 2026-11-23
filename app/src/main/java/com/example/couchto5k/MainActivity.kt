package com.example.couchto5k

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.couchto5k.domain.model.ThemeMode
import com.example.couchto5k.domain.repository.SettingsRepository
import com.example.couchto5k.ui.navigation.AppNavGraph
import com.example.couchto5k.ui.theme.SafeStride5KTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsRepository.themeMode.collectAsState(initial = ThemeMode.LIGHT)
            SafeStride5KTheme(themeMode = themeMode) {
                AppNavGraph()
            }
        }
    }
}
