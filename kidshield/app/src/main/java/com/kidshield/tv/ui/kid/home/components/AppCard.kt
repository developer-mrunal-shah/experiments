package com.kidshield.tv.ui.kid.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.kidshield.tv.domain.model.StreamingApp
import com.kidshield.tv.ui.theme.KidShieldBlue
import com.kidshield.tv.ui.theme.KidShieldGreen
import com.kidshield.tv.ui.theme.KidShieldOrange
import com.kidshield.tv.ui.theme.TvTextStyles

@Composable
fun AppCard(
    app: StreamingApp,
    onClick: () -> Unit
) {
    val timeColor = when {
        app.dailyMinutesRemaining == null -> Color.Transparent
        app.dailyMinutesRemaining > 30 -> KidShieldGreen
        app.dailyMinutesRemaining > 10 -> KidShieldOrange
        else -> Color.Red
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.size(width = 200.dp, height = 170.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(20.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(
                elevationColor = KidShieldBlue.copy(alpha = 0.4f),
                elevation = 20.dp
            )
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    Brush.linearGradient(
                        colors = listOf(KidShieldBlue, KidShieldBlue.copy(alpha = 0.4f))
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1A1A2E),
            focusedContainerColor = Color(0xFF222240)
        )
    ) {
        // Subtle gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            KidShieldBlue.copy(alpha = 0.03f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (app.iconDrawable != null) {
                    val bitmap = remember(app.iconDrawable) {
                        app.iconDrawable.toBitmap(128, 128).asImageBitmap()
                    }
                    Image(
                        bitmap = bitmap,
                        contentDescription = app.displayName,
                        modifier = Modifier.size(72.dp)
                    )
                } else {
                    // Styled placeholder
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                KidShieldBlue.copy(alpha = 0.15f),
                                RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = app.displayName.take(1),
                            style = TvTextStyles.headlineLarge,
                            color = KidShieldBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = app.displayName,
                    style = TvTextStyles.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )

                if (app.dailyMinutesRemaining != null) {
                    TimeRemainingBadge(
                        minutesRemaining = app.dailyMinutesRemaining,
                        color = timeColor
                    )
                }
            }
        }
    }
}
