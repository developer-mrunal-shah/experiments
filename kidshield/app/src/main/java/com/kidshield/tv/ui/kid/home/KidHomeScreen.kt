package com.kidshield.tv.ui.kid.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.kidshield.tv.ui.kid.home.components.AppCard
import com.kidshield.tv.ui.theme.KidShieldBlue
import com.kidshield.tv.ui.theme.KidShieldPurple
import com.kidshield.tv.ui.theme.TvTextStyles

@Composable
fun KidHomeScreen(
    viewModel: KidHomeViewModel = hiltViewModel(),
    onParentAccess: () -> Unit,
    onTimesUp: (String, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Handle times up navigation
    LaunchedEffect(uiState.timesUpApp) {
        uiState.timesUpApp?.let { (pkg, name) ->
            onTimesUp(pkg, name)
            viewModel.clearTimesUp()
        }
    }

    // Subtle gradient background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        KidShieldBlue.copy(alpha = 0.08f),
                        KidShieldPurple.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    center = Offset(200f, 100f),
                    radius = 1200f
                )
            )
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Shield icon with glow
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    KidShieldBlue.copy(alpha = 0.15f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = KidShieldBlue
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = uiState.greeting,
                                style = TvTextStyles.displayLarge,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "What do you want to watch?",
                                style = TvTextStyles.titleLarge,
                                color = Color(0xFFB0B0B0)
                            )
                        }
                    }

                    // Parent access button (subtle)
                    Surface(
                        onClick = onParentAccess,
                        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        modifier = Modifier.size(48.dp),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.05f),
                            focusedContainerColor = Color.White.copy(alpha = 0.15f)
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Parent Settings",
                                tint = Color(0xFF888888)
                            )
                        }
                    }
                }
            }

            // Error message
            if (uiState.launchError != null) {
                item {
                    Surface(
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                        ),
                        onClick = { viewModel.clearError() },
                        shape = ClickableSurfaceDefaults.shape(
                            shape = RoundedCornerShape(12.dp)
                        )
                    ) {
                        Text(
                            text = uiState.launchError!!,
                            style = TvTextStyles.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // Category rows
            uiState.categories.forEach { (category, apps) ->
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Category accent bar
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(24.dp)
                                .background(
                                    KidShieldBlue,
                                    RoundedCornerShape(2.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = category,
                            style = TvTextStyles.headlineMedium,
                            color = Color.White
                        )
                    }
                }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(start = 8.dp, end = 48.dp)
                    ) {
                        items(apps, key = { it.packageName }) { app ->
                            AppCard(
                                app = app,
                                onClick = { viewModel.launchApp(app.packageName) }
                            )
                        }
                    }
                }
            }

            // Empty state
            if (uiState.categories.isEmpty() && !uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = KidShieldBlue.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No apps available yet",
                                style = TvTextStyles.headlineMedium,
                                color = Color(0xFFB0B0B0)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Ask a parent to set up your apps!",
                                style = TvTextStyles.bodyLarge,
                                color = Color(0xFF888888)
                            )
                        }
                    }
                }
            }

            // Footer
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Have fun! Remember to take breaks.",
                        style = TvTextStyles.bodyLarge,
                        color = Color(0xFF666666)
                    )
                }
            }
        }
    }
}
