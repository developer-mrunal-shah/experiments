package com.kidshield.tv.ui.parent.setupwizard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.kidshield.tv.domain.model.AgeProfile
import com.kidshield.tv.ui.parent.setupwizard.steps.JioHotstarSetupStep
import com.kidshield.tv.ui.parent.setupwizard.steps.NetflixSetupStep
import com.kidshield.tv.ui.parent.setupwizard.steps.YouTubeKidsSetupStep
import com.kidshield.tv.ui.theme.KidShieldBlue
import com.kidshield.tv.ui.theme.KidShieldGreen
import com.kidshield.tv.ui.theme.KidShieldPurple
import com.kidshield.tv.ui.theme.TvTextStyles

@Composable
fun SetupWizardScreen(
    viewModel: SetupWizardViewModel = hiltViewModel(),
    isFirstLaunch: Boolean = false,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentStep by remember { mutableIntStateOf(0) }
    val totalSteps = if (isFirstLaunch) 5 else 3 // First launch has Welcome + PIN + Age + Apps + Streaming tips

    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentStep > 0 || !isFirstLaunch) {
                    Surface(
                        onClick = {
                            if (currentStep > 0) currentStep-- else onBack()
                        },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                }
                Text(
                    if (isFirstLaunch) "KidShield Setup" else "Setup Wizard",
                    style = TvTextStyles.headlineLarge
                )
            }
            // Step indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(totalSteps) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentStep) 12.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    index < currentStep -> KidShieldGreen
                                    index == currentStep -> KidShieldBlue
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "${currentStep + 1} / $totalSteps",
                    style = TvTextStyles.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Step content
        if (isFirstLaunch) {
            when (currentStep) {
                0 -> WelcomeStep(onNext = { currentStep++ })
                1 -> PinSetupStep(
                    viewModel = viewModel,
                    uiState = uiState,
                    onNext = { currentStep++ }
                )
                2 -> AgeProfileStep(
                    selectedProfile = uiState.selectedAgeProfile,
                    onProfileSelected = { viewModel.setAgeProfile(it) },
                    onNext = { currentStep++ }
                )
                3 -> AppSelectionStep(
                    apps = uiState.apps,
                    onToggleApp = { pkg, allowed -> viewModel.toggleApp(pkg, allowed) },
                    onNext = { currentStep++ }
                )
                4 -> StreamingTipsStep(
                    onComplete = {
                        viewModel.completeSetup()
                        onComplete()
                    }
                )
            }
        } else {
            // Non-first-launch: just the streaming setup tips
            when (currentStep) {
                0 -> NetflixSetupStep(onComplete = { currentStep++ })
                1 -> JioHotstarSetupStep(onComplete = { currentStep++ })
                2 -> YouTubeKidsSetupStep(onComplete = onBack)
            }
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Shield,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = KidShieldBlue
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Welcome to KidShield",
            style = TvTextStyles.displayLarge,
            color = KidShieldBlue
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Let's set up a safe TV environment for your kids.",
            style = TvTextStyles.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "This will take about 2 minutes.",
            style = TvTextStyles.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Surface(
            onClick = onNext,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(16.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = KidShieldBlue,
                focusedContainerColor = KidShieldBlue
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Get Started", style = TvTextStyles.headlineMedium, color = Color.White)
                Spacer(modifier = Modifier.width(12.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun PinSetupStep(
    viewModel: SetupWizardViewModel,
    uiState: SetupWizardViewModel.UiState,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = KidShieldBlue
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = when {
                uiState.pinConfirmStep -> "Confirm your PIN"
                else -> "Create a Parent PIN"
            },
            style = TvTextStyles.headlineMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "This PIN protects the parent dashboard",
            style = TvTextStyles.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        // PIN dots
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(6) { index ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                uiState.pinError != null && index < uiState.enteredPin.length ->
                                    MaterialTheme.colorScheme.error
                                index < uiState.enteredPin.length ->
                                    MaterialTheme.colorScheme.primary
                                else ->
                                    MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.pinError != null) {
            Text(
                text = uiState.pinError,
                style = TvTextStyles.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (uiState.pinSetupComplete) {
            Text(
                "PIN created successfully!",
                style = TvTextStyles.bodyLarge,
                color = KidShieldGreen
            )
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                onClick = onNext,
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = KidShieldGreen,
                    focusedContainerColor = KidShieldGreen
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Continue", style = TvTextStyles.labelLarge, color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White)
                }
            }
        } else {
            com.kidshield.tv.ui.common.PinKeypad(
                onDigit = { viewModel.onPinDigit(it) },
                onBackspace = { viewModel.onPinBackspace() },
                onConfirm = { viewModel.onPinConfirm() }
            )
        }
    }
}

@Composable
private fun AgeProfileStep(
    selectedProfile: AgeProfile,
    onProfileSelected: (AgeProfile) -> Unit,
    onNext: () -> Unit
) {
    val profiles = listOf(
        Triple(AgeProfile.TODDLER, "Toddler (2-5)", "Most restrictive. Only kids-specific apps like YouTube Kids."),
        Triple(AgeProfile.CHILD, "Child (6-12)", "Moderate. Allows filtered content on Netflix, YouTube Kids, Disney+."),
        Triple(AgeProfile.TEEN, "Teen (13-17)", "Less restrictive. Allows most streaming apps with parental ratings.")
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Icon(
            Icons.Default.ChildCare,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = KidShieldPurple
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("Select Age Profile", style = TvTextStyles.headlineMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "This determines which apps and content are visible",
            style = TvTextStyles.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(profiles) { (profile, title, description) ->
                val isSelected = selectedProfile == profile
                Surface(
                    onClick = { onProfileSelected(profile) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSelected)
                            KidShieldPurple.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = KidShieldPurple.copy(alpha = 0.15f)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, style = TvTextStyles.titleLarge)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                description,
                                style = TvTextStyles.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = KidShieldPurple,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    onClick = onNext,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = KidShieldGreen,
                        focusedContainerColor = KidShieldGreen
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Continue", style = TvTextStyles.labelLarge, color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppSelectionStep(
    apps: List<SetupWizardViewModel.AppItem>,
    onToggleApp: (String, Boolean) -> Unit,
    onNext: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Choose Allowed Apps", style = TvTextStyles.headlineMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Select which streaming apps your kids can access",
            style = TvTextStyles.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(apps) { app ->
                Surface(
                    onClick = { onToggleApp(app.packageName, !app.isAllowed) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(app.displayName, style = TvTextStyles.titleLarge)
                        Surface(
                            onClick = { onToggleApp(app.packageName, !app.isAllowed) },
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (app.isAllowed)
                                    KidShieldGreen.copy(alpha = 0.2f)
                                else
                                    Color(0xFFE57373).copy(alpha = 0.2f)
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
                        ) {
                            Text(
                                text = if (app.isAllowed) "Allowed" else "Blocked",
                                style = TvTextStyles.labelLarge,
                                color = if (app.isAllowed) KidShieldGreen else Color(0xFFE57373),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    onClick = onNext,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = KidShieldGreen,
                        focusedContainerColor = KidShieldGreen
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Continue", style = TvTextStyles.labelLarge, color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingTipsStep(onComplete: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Set Up Streaming App Controls", style = TvTextStyles.headlineMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "For best protection, also enable parental controls within each streaming app. " +
                        "Here are quick tips for the most common apps:",
                style = TvTextStyles.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            StreamingTipCard(
                title = "Netflix",
                color = Color(0xFFE50914),
                tips = listOf(
                    "Create a Kids profile with age-appropriate ratings",
                    "Enable Profile Lock on all adult profiles"
                )
            )
        }

        item {
            StreamingTipCard(
                title = "YouTube Kids",
                color = Color(0xFFFF0000),
                tips = listOf(
                    "Select your child's age range (Preschool/Younger/Older)",
                    "Turn off Search for younger kids",
                    "Set a passcode for parent settings"
                )
            )
        }

        item {
            StreamingTipCard(
                title = "JioHotstar",
                color = KidShieldBlue,
                tips = listOf(
                    "Enable Parental Lock with a 4-digit PIN",
                    "Set content rating level and enable Kids Mode"
                )
            )
        }

        item {
            StreamingTipCard(
                title = "Disney+",
                color = Color(0xFF0063E5),
                tips = listOf(
                    "Create a Kids profile with content ratings",
                    "Set profile PIN on adult profiles"
                )
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            Surface(
                onClick = onComplete,
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(16.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = KidShieldGreen,
                    focusedContainerColor = KidShieldGreen
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Finish Setup", style = TvTextStyles.headlineMedium, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun StreamingTipCard(
    title: String,
    color: Color,
    tips: List<String>
) {
    Surface(
        onClick = {},
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = TvTextStyles.titleLarge, color = color)
            Spacer(modifier = Modifier.height(8.dp))
            tips.forEach { tip ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("•  ", style = TvTextStyles.bodyLarge, color = color)
                    Text(tip, style = TvTextStyles.bodyLarge)
                }
            }
        }
    }
}
