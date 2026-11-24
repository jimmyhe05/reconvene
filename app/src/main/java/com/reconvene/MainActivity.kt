package com.reconvene

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reconvene.data.reminders.ReminderNotifications
import com.reconvene.data.settings.Appearance
import com.reconvene.ui.ReconveneApp
import com.reconvene.ui.theme.ReconveneTheme

class MainActivity : ComponentActivity() {

    // The pod a tapped reminder asks to open, until the app has opened it.
    private var podToOpen by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only a fresh start: after rotation the pod is already open.
        if (savedInstanceState == null) podToOpen = intent.getStringExtra(ReminderNotifications.EXTRA_POD_ID)
        val settings = (application as ReconveneApplication).container.settingsRepository
        setContent {
            val appearance by settings.appearance.collectAsStateWithLifecycle(Appearance.SYSTEM)
            val darkTheme = when (appearance) {
                Appearance.SYSTEM -> isSystemInDarkTheme()
                Appearance.LIGHT -> false
                Appearance.DARK -> true
            }
            // The status and navigation bar icons follow the app's theme, not the phone's, so they
            // stay readable when the user picks Light or Dark.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                )
                onDispose {}
            }
            ReconveneTheme(darkTheme = darkTheme) {
                ReconveneApp(podToOpen = podToOpen, onPodOpened = { podToOpen = null })
            }
        }
    }

    // A reminder tapped while the app is already open.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(ReminderNotifications.EXTRA_POD_ID)?.let { podToOpen = it }
    }
}
