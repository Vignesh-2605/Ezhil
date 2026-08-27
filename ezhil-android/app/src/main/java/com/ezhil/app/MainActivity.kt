package com.ezhil.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.ezhil.app.data.local.SecurePrefs
import com.ezhil.app.ui.navigation.AppNavGraph
import com.ezhil.app.ui.theme.BgDark
import com.ezhil.app.ui.theme.EzhilTheme
import com.ezhil.app.ui.viewmodel.AppLanguageViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var securePrefs: SecurePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            val langVm: AppLanguageViewModel = hiltViewModel()

            EzhilTheme {
                // enableEdgeToEdge() above lets the app draw behind the status
                // and gesture bars, but nothing in the app consumed the insets,
                // so every screen drew underneath them — the teacher login
                // title rendered on top of the clock and battery icons, and the
                // bottom navigation sat under the gesture bar.
                //
                // Padding once at the root fixes every screen rather than
                // thirty. The background is painted outside the padding so the
                // inset strips stay the app's dark ground instead of showing
                // white behind a translucent system bar.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BgDark)
                        .systemBarsPadding()
                ) {
                    AppNavGraph(
                        navController = navController,
                        securePrefs   = securePrefs
                    )
                }
            }
        }
    }
}
