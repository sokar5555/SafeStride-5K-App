package com.example.couchto5k

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.couchto5k.ui.navigation.AppNavGraph
import com.example.couchto5k.ui.theme.SafeStride5KTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SafeStride5KTheme {
                AppNavGraph()
            }
        }
    }
}
