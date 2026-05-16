package com.aidflow.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aidflow.pro.ui.screens.FamilyIntakeScreen
import com.aidflow.pro.ui.screens.HomeScreen
import com.aidflow.pro.ui.screens.ItemsScreen
import com.aidflow.pro.ui.screens.ModelSetupScreen
import com.aidflow.pro.ui.screens.ScanScreen
import com.aidflow.pro.ui.screens.TranslateScreen
import com.aidflow.pro.ui.theme.AidFlowTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AidFlowTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AidFlowNavGraph(initialModelReady = (application as AidFlowApp).modelDownloader.isReady())
                }
            }
        }
    }
}

private object Routes {
    const val Setup = "setup"
    const val Home = "home"
    const val Scan = "scan"
    const val Translate = "translate"
    const val FamilyIntake = "intake/family"
    const val Items = "intake/items"
}

@Composable
private fun AidFlowNavGraph(initialModelReady: Boolean) {
    val nav = rememberNavController()
    val start = if (initialModelReady) Routes.Home else Routes.Setup

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.Setup) {
            ModelSetupScreen(onReady = {
                nav.navigate(Routes.Home) {
                    popUpTo(Routes.Setup) { inclusive = true }
                }
            })
        }
        composable(Routes.Home) {
            HomeScreen(
                onScan = { nav.navigate(Routes.Scan) },
                onTranslate = { nav.navigate(Routes.Translate) },
                onFamilyIntake = { nav.navigate(Routes.FamilyIntake) },
                onIdentifyItems = { nav.navigate(Routes.Items) },
            )
        }
        composable(Routes.Scan) { ScanScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.Translate) { TranslateScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.FamilyIntake) { FamilyIntakeScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.Items) { ItemsScreen(onBack = { nav.popBackStack() }) }
    }
}
