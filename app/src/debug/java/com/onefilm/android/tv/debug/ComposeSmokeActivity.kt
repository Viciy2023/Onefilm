package com.onefilm.android.tv.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Debug-only Compose spike screen. */
class ComposeSmokeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SmokeScreen() }
    }
}

@Composable
private fun SmokeScreen() {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF07090C))
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "OneFilm",
                color = Color(0xFFE8B45A),
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Compose smoke OK",
                color = Color(0xFFF7F8FA),
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
