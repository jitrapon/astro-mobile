package io.jitrapon.astro.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import io.jitrapon.astro.ui.main.theme.AstroTheme
import io.jitrapon.astro.ui.shell.AppShellRoute
import io.jitrapon.astro.ui.shell.AppShellViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opt in explicitly rather than relying on the platform enforcing it above API 35, so the
        // shell lays out against the same insets on every supported release. The shell's bars and
        // content consume them; see AppShell.
        enableEdgeToEdge()
        setContent {
            AstroTheme {
                val shellViewModel: AppShellViewModel =
                    viewModel(factory = AppShellViewModel.Factory)
                AppShellRoute(shellState = shellViewModel.shellState)
            }
        }
    }
}
