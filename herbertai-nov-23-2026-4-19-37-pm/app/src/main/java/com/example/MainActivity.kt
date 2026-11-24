package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.configureAppCheck
import com.example.ui.LuminaryScreen
import com.example.ui.LuminaryViewModel
import com.example.ui.StudioViewModel
import com.example.ui.theme.LuminaryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before rendering UI or creating ViewModels.
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            val viewModel: LuminaryViewModel = viewModel()
            val studioViewModel: StudioViewModel = viewModel()
            val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()

            LuminaryTheme(themePreference = themePreference) {
                LuminaryScreen(
                    viewModel = viewModel,
                    studioViewModel = studioViewModel
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureAppCheck(this, intent)
    }
}
