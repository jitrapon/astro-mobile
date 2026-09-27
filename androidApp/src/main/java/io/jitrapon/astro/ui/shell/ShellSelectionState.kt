package io.jitrapon.astro.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.jitrapon.astro.presentation.action.ActionEffect
import io.jitrapon.astro.presentation.shell.AppShellTab

/**
 * What the shell is showing in answer to what the person did: the selected destination, and the
 * not-yet-built event surface when an effect asked for one — and how each effect changes them.
 *
 * Only a navigating tab can be selected. The selection is held by destination id, the tab's
 * identity, and survives recreation; when a refresh drops the selected destination, the first
 * navigating tab stands in.
 */
@Stable
internal class ShellSelectionState(private val selectedDestinationId: MutableState<String?>) {

    /** The event effect whose placeholder surface is showing, or `null` when none is. */
    var shownEventEffect: ActionEffect? by mutableStateOf(null)
        private set

    /** The tab to show among [navigatingTabs], or `null` when there is none to show. */
    fun selectedTab(navigatingTabs: List<AppShellTab>): AppShellTab? =
        navigatingTabs.firstOrNull { it.destinationId == selectedDestinationId.value }
            ?: navigatingTabs.firstOrNull()

    /** Shows [tab]'s screen. */
    fun select(tab: AppShellTab) {
        selectedDestinationId.value = tab.destinationId
    }

    /**
     * Carries out [effect], which acting from [source] produced — the tab tapped, or the one
     * showing the screen an action came from: showing a screen selects the destination that targets
     * it, opening a URL goes to [openExternalUrl], and the event effects show the placeholder
     * surface. A screen no navigating tab targets has nowhere to be shown, so that effect leaves
     * the selection where it is.
     */
    fun carryOut(
        effect: ActionEffect?,
        source: AppShellTab?,
        navigatingTabs: List<AppShellTab>,
        openExternalUrl: (String) -> Unit,
    ) {
        when (effect) {
            is ActionEffect.ShowScreen ->
                navigatingTabs
                    .destinationShowing(effect.screenId, preferring = source)
                    ?.let(::select)
            is ActionEffect.OpenExternalUrl -> openExternalUrl(effect.url)
            is ActionEffect.ShowEventDetail,
            is ActionEffect.ShowEvents -> shownEventEffect = effect
            null -> Unit
        }
    }

    /** Closes the event placeholder surface. */
    fun dismissEventSurface() {
        shownEventEffect = null
    }
}

/** A [ShellSelectionState] whose selection survives recreation. */
@Composable
internal fun rememberShellSelectionState(): ShellSelectionState {
    val selectedDestinationId = rememberSaveable { mutableStateOf<String?>(null) }
    return remember(selectedDestinationId) { ShellSelectionState(selectedDestinationId) }
}

/**
 * The destination to show for [screenId]: [preferring] when it targets that screen — so two tabs
 * routing to one screen keep their own selection — otherwise the first that does, or `null` when no
 * tab targets it.
 */
private fun List<AppShellTab>.destinationShowing(
    screenId: String,
    preferring: AppShellTab?,
): AppShellTab? =
    if (preferring?.targetScreenId == screenId) preferring
    else firstOrNull { it.targetScreenId == screenId }
