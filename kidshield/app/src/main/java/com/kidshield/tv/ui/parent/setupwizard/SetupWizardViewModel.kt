package com.kidshield.tv.ui.parent.setupwizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidshield.tv.data.local.preferences.PinManager
import com.kidshield.tv.data.repository.AppRepository
import com.kidshield.tv.data.repository.SettingsRepository
import com.kidshield.tv.domain.model.AgeProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SetupWizardViewModel @Inject constructor(
    private val pinManager: PinManager,
    private val settingsRepository: SettingsRepository,
    private val appRepository: AppRepository
) : ViewModel() {

    data class AppItem(
        val packageName: String,
        val displayName: String,
        val isAllowed: Boolean
    )

    data class UiState(
        val enteredPin: String = "",
        val firstPin: String = "",
        val pinConfirmStep: Boolean = false,
        val pinError: String? = null,
        val pinSetupComplete: Boolean = false,
        val selectedAgeProfile: AgeProfile = AgeProfile.CHILD,
        val apps: List<AppItem> = emptyList()
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            val allApps = appRepository.getAllApps().first()
            _uiState.update { state ->
                state.copy(
                    apps = allApps.map { app ->
                        AppItem(
                            packageName = app.packageName,
                            displayName = app.displayName,
                            isAllowed = app.isAllowed
                        )
                    }
                )
            }
        }
    }

    // PIN setup methods
    fun onPinDigit(digit: Int) {
        _uiState.update {
            if (it.enteredPin.length < 6)
                it.copy(enteredPin = it.enteredPin + digit, pinError = null)
            else it
        }
    }

    fun onPinBackspace() {
        _uiState.update {
            it.copy(enteredPin = it.enteredPin.dropLast(1), pinError = null)
        }
    }

    fun onPinConfirm() {
        val state = _uiState.value
        if (state.enteredPin.length < 4) {
            _uiState.update { it.copy(pinError = "PIN must be at least 4 digits") }
            return
        }

        if (!state.pinConfirmStep) {
            // First entry: save and ask for confirmation
            _uiState.update {
                it.copy(
                    firstPin = state.enteredPin,
                    enteredPin = "",
                    pinConfirmStep = true,
                    pinError = null
                )
            }
        } else {
            // Confirm step
            if (state.enteredPin == state.firstPin) {
                pinManager.setPin(state.enteredPin)
                _uiState.update {
                    it.copy(pinSetupComplete = true, pinError = null)
                }
            } else {
                _uiState.update {
                    it.copy(
                        enteredPin = "",
                        pinConfirmStep = false,
                        firstPin = "",
                        pinError = "PINs don't match. Try again."
                    )
                }
            }
        }
    }

    // Age profile
    fun setAgeProfile(profile: AgeProfile) {
        _uiState.update { it.copy(selectedAgeProfile = profile) }
        viewModelScope.launch {
            settingsRepository.setAgeProfile(profile)
        }
    }

    // App toggling
    fun toggleApp(packageName: String, allowed: Boolean) {
        viewModelScope.launch {
            appRepository.setAppAllowed(packageName, allowed)
            _uiState.update { state ->
                state.copy(
                    apps = state.apps.map {
                        if (it.packageName == packageName) it.copy(isAllowed = allowed) else it
                    }
                )
            }
        }
    }

    // Mark setup complete
    fun completeSetup() {
        viewModelScope.launch {
            settingsRepository.setFirstLaunchComplete()
        }
    }
}
