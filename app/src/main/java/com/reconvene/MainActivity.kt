package com.reconvene

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.reconvene.ui.ReconveneApp
import com.reconvene.ui.theme.ReconveneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReconveneTheme {
                ReconveneApp()
            }
        }
    }
}
