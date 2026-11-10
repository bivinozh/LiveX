package com.example.livex.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.livex.player.ads.AdState

private val AdYellow = Color(0xFFFFD54F)

/** Yellow "AD 1/2 · 8 s" badge over the video while player.isPlayingAd; nothing during content. */
@Composable
fun AdBadge(ad: AdState, modifier: Modifier = Modifier) {
    val text = ad.badge ?: return
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        modifier = modifier
            .background(AdYellow, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
