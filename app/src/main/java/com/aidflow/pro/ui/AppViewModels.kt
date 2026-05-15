package com.aidflow.pro.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aidflow.pro.AidFlowApp
import com.aidflow.pro.export.Exporter

/**
 * Cross-screen factory that gives every ViewModel access to the app-wide singletons
 * (Gemma manager, OCR, translation repo, exporter). Keeping a single factory means
 * we don't end up with one bespoke factory per ViewModel.
 */
object AppViewModelFactory {

    fun build(app: AidFlowApp): ViewModelProvider.Factory = viewModelFactory {
        initializer { ModelSetupViewModel(app) }
        initializer { ScanViewModel(app, Exporter(app)) }
        initializer { TranslateViewModel(app) }
    }
}

@androidx.compose.runtime.Composable
inline fun <reified VM : ViewModel> appViewModel(): VM {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext as AidFlowApp
    return viewModel(factory = AppViewModelFactory.build(context))
}
