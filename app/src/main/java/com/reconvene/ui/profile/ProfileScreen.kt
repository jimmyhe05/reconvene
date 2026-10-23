package com.reconvene.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.reconvene.R

@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.nav_profile),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier
                .padding(vertical = 16.dp)
                .semantics { heading() },
        )
    }
}
