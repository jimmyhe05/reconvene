package com.reconvene.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.reconvene.R
import com.reconvene.ui.theme.ReconveneTheme

enum class BadgeKind { LIVE, OPEN, FULL }

@Composable
fun StatusBadge(kind: BadgeKind, text: String, modifier: Modifier = Modifier) {
    val colors = ReconveneTheme.colors
    val containerColor = when (kind) {
        BadgeKind.LIVE -> colors.live
        BadgeKind.OPEN -> Color.Transparent
        BadgeKind.FULL -> colors.full
    }
    val contentColor = when (kind) {
        BadgeKind.LIVE -> colors.onLive
        BadgeKind.OPEN -> colors.ink
        BadgeKind.FULL -> colors.onFull
    }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        border = if (kind == BadgeKind.OPEN) BorderStroke(1.dp, colors.outlineStrong) else null,
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (kind == BadgeKind.LIVE) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(contentColor, CircleShape)
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_users),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}
