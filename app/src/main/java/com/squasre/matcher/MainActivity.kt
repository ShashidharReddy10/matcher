package com.squasre.matcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.squasre.matcher.logic.AdManager
import com.squasre.matcher.logic.BillingManager
import com.squasre.matcher.logic.GameViewModel
import com.squasre.matcher.ui.GameScreen
import com.squasre.matcher.ui.theme.MatcherTheme
import com.squasre.matcher.data.GamePreferences
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val gamePrefs = GamePreferences(this)
        val adManager = AdManager(this)
        
        setContent {
            MatcherTheme {
                val viewModel: GameViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return GameViewModel(gamePrefs) as T
                        }
                    }
                )
                val scope = rememberCoroutineScope()
                val billingManager = remember {
                    BillingManager(this, scope) {
                        scope.launch { gamePrefs.setAdsRemoved(true) }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GameScreen(
                        viewModel = viewModel,
                        adManager = adManager,
                        billingManager = billingManager,
                        gamePrefs = gamePrefs,
                        activity = this
                    )
                }
            }
        }
    }
}
