package com.aidflow.pro.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aidflow.pro.AidFlowApp
import com.aidflow.pro.ai.Gemma4Manager
import com.aidflow.pro.ai.ModelDownloader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Drives the first-launch flow: download model (if needed) → initialize Gemma 4 → ready.
 *
 * Surfaces a single [SetupState] consumed by [ModelSetupScreen] so the UI is a thin
 * declarative function over state and never has to coordinate the two underlying
 * subsystems by itself.
 */
class ModelSetupViewModel(private val app: AidFlowApp) : ViewModel() {

    sealed interface SetupState {
        data object NotStarted : SetupState
        data class Downloading(val fraction: Float, val bytes: Long, val total: Long) : SetupState
        data object Verifying : SetupState
        data object LoadingEngine : SetupState
        data object Ready : SetupState
        data class Error(val message: String) : SetupState
    }

    private val _allowMetered = MutableStateFlow(false)
    val allowMetered = _allowMetered.asStateFlow()

    val state: Flow<SetupState> =
        combine(app.modelDownloader.state, app.gemma.state) { d, e -> toState(d, e) }

    fun setAllowMetered(allow: Boolean) { _allowMetered.value = allow }

    /** True when the model file is already on disk from a previous run. */
    fun isModelOnDisk(): Boolean = app.modelDownloader.isReady()

    fun start() {
        viewModelScope.launch {
            if (!app.modelDownloader.isReady()) {
                app.modelDownloader.download(requireUnmetered = !_allowMetered.value)
            }
            if (app.modelDownloader.isReady() && app.gemma.state.value != Gemma4Manager.State.Ready) {
                app.gemma.initialize(app.modelDownloader.modelFile)
            }
        }
    }

    fun retry() {
        viewModelScope.launch {
            if (!app.modelDownloader.isReady()) {
                app.modelDownloader.download(requireUnmetered = !_allowMetered.value)
            } else {
                app.gemma.initialize(app.modelDownloader.modelFile)
            }
        }
    }

    private fun toState(
        d: ModelDownloader.State,
        e: Gemma4Manager.State,
    ): SetupState = when {
        d is ModelDownloader.State.Error ->
            SetupState.Error(d.message)
        e == Gemma4Manager.State.Error ->
            SetupState.Error(app.gemma.lastError()?.message ?: "Engine failed to load")
        d is ModelDownloader.State.Downloading ->
            SetupState.Downloading(d.fraction, d.bytes, d.total)
        d is ModelDownloader.State.Verifying ->
            SetupState.Verifying
        d is ModelDownloader.State.Ready && e == Gemma4Manager.State.Idle ->
            SetupState.LoadingEngine
        d is ModelDownloader.State.Ready && e == Gemma4Manager.State.Loading ->
            SetupState.LoadingEngine
        d is ModelDownloader.State.Ready && e == Gemma4Manager.State.Ready ->
            SetupState.Ready
        else -> SetupState.NotStarted
    }
}
