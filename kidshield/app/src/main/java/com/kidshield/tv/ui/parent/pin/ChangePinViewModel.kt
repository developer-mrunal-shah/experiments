package com.kidshield.tv.ui.parent.pin

import androidx.lifecycle.ViewModel
import com.kidshield.tv.data.local.preferences.PinManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class ChangePinStep {
    VERIFY_OLD, ENTER_NEW, CONFIRM_NEW
}

@HiltViewModel
class ChangePinViewModel @Inject constructor(
    private val pinManager: PinManager
) : ViewModel() {

    data class UiState(
        val enteredPin: String = "",
        val step: ChangePinStep = ChangePinStep.VERIFY_OLD,
        val newPin: String = "",
        val error: String? = null,
        val success: String? = null,
        val isComplete: Boolean = false,
        val attemptsRemaining: Int = 5
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun onDigitEntered(digit: Int) {
        _uiState.update {
            if (it.enteredPin.length < 6)
                it.copy(enteredPin = it.enteredPin + digit, error = null, success = null)
            else it
        }
    }

    fun onBackspace() {
        _uiState.update {
            it.copy(enteredPin = it.enteredPin.dropLast(1), error = null, success = null)
        }
    }

    fun onConfirm() {
        val state = _uiState.value
        if (state.enteredPin.length < 4) {
            _uiState.update { it.copy(error = "PIN must be at least 4 digits") }
            return
        }

        when (state.step) {
            ChangePinStep.VERIFY_OLD -> verifyOldPin(state)
            ChangePinStep.ENTER_NEW -> enterNewPin(state)
            ChangePinStep.CONFIRM_NEW -> confirmNewPin(state)
        }
    }

    private fun verifyOldPin(state: UiState) {
        if (state.attemptsRemaining <= 0) {
            _uiState.update { it.copy(error = "Too many attempts. Try again later.") }
            return
        }

        if (pinManager.verifyPin(state.enteredPin)) {
            _uiState.update {
                it.copy(
                    enteredPin = "",
                    step = ChangePinStep.ENTER_NEW,
                    error = null
                )
            }
        } else {
            val remaining = state.attemptsRemaining - 1
            _uiState.update {
                it.copy(
                    enteredPin = "",
                    attemptsRemaining = remaining,
                    error = if (remaining > 0) "Wrong PIN. $remaining attempts left."
                    else "Too many attempts. Try again later."
                )
            }
        }
    }

    private fun enterNewPin(state: UiState) {
        _uiState.update {
            it.copy(
                newPin = state.enteredPin,
                enteredPin = "",
                step = ChangePinStep.CONFIRM_NEW,
                error = null
            )
        }
    }

    private fun confirmNewPin(state: UiState) {
        if (state.enteredPin == state.newPin) {
            pinManager.setPin(state.enteredPin)
            _uiState.update {
                it.copy(
                    success = "PIN changed successfully!",
                    isComplete = true
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    enteredPin = "",
                    step = ChangePinStep.ENTER_NEW,
                    newPin = "",
                    error = "PINs don't match. Try again."
                )
            }
        }
    }
}
