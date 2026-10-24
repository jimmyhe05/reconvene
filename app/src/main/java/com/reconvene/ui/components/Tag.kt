package com.reconvene.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reconvene.ui.theme.ReconveneTheme

@Composable
fun Tag(text: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    val colors = ReconveneTheme.colors
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (accent) colors.primaryContainer else colors.surfaceVariant,
        contentColor = if (accent) colors.onPrimaryContainer else colors.ink,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
