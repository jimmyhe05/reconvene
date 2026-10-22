package com.reconvene

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.reconvene.ui.theme.ReconveneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReconveneTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    WelcomePlaceholder(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

// Temporary first screen that shows the theme in use. The navigation step replaces it.
@Composable
fun WelcomePlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        // Eyebrow label: uppercase with wide letter spacing, as in the prototype
        Text(
            text = stringResource(R.string.placeholder_eyebrow).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = ReconveneTheme.colors.inkSubtle,
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            text = stringResource(R.string.slogan),
            style = MaterialTheme.typography.bodyLarge,
            color = ReconveneTheme.colors.inkMuted,
        )
        LiveBadge()
        // Material's Button reads primary from the color scheme; no colors passed in.
        Button(onClick = { /* Does nothing until the pod screens exist */ }) {
            Text(stringResource(R.string.placeholder_action))
        }
    }
}

// Status badges pair a marker with a word, never color alone.
@Composable
private fun LiveBadge() {
    Surface(
        color = ReconveneTheme.colors.live,
        contentColor = ReconveneTheme.colors.onLive,
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(ReconveneTheme.colors.onLive, CircleShape)
            )
            Text(stringResource(R.string.placeholder_live), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WelcomePlaceholderPreview() {
    ReconveneTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            WelcomePlaceholder()
        }
    }
}
