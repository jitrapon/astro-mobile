package io.jitrapon.astro.ui.main

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import io.jitrapon.astro.R
import io.jitrapon.astro.ui.main.theme.AstroTheme
import io.jitrapon.astro.ui.shell.AppShellInteractions
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
                val interactions =
                    remember(shellViewModel) {
                        AppShellInteractions(
                            dispatch = shellViewModel::dispatch,
                            openExternalUrl = ::openExternalUrl,
                        )
                    }
                AppShellRoute(
                    shellState = shellViewModel.shellState,
                    calendarState = shellViewModel.calendarState,
                    interactions = interactions,
                )
            }
        }
    }

    /**
     * Hands [url] to whichever app handles it. A URL no installed app can open says so briefly
     * rather than crashing — the server may deliver a scheme this device has no handler for.
     */
    private fun openExternalUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.app_shell_no_app_for_url, Toast.LENGTH_SHORT).show()
        }
    }
}
