package com.ezhil.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.ezhil.app.data.local.SecurePrefs
import com.ezhil.app.ui.navigation.AppNavGraph
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
                AppNavGraph(
                    navController = navController,
                    securePrefs   = securePrefs
                )
            }
        }
    }
}
