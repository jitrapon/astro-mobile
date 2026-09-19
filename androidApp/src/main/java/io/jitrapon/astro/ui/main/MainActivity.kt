package io.jitrapon.astro.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import io.jitrapon.astro.ui.main.theme.AstroTheme
import io.jitrapon.astro.ui.shell.AppShellRoute
import io.jitrapon.astro.ui.shell.AppShellViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AstroTheme {
                val shellViewModel: AppShellViewModel =
                    viewModel(factory = AppShellViewModel.Factory)
                AppShellRoute(shellState = shellViewModel.shellState)
            }
        }
    }
}
