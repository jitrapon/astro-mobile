import SwiftUI
import shared

/// The shell around every screen: a tab bar with one tab per destination the server delivered, and
/// the selected destination's screen above it — or, when no destination is there to draw, a
/// loading, failure or nothing-to-show placeholder with no tab bar at all.
///
/// Which destinations become tabs, and when the bar is shown, is decided once in the shared
/// `toAppShellState()` projection, so this view and the Android shell cannot disagree about it.
/// `calendar` is the one screen this app observes; a tab targeting it draws it, and every action
/// taken in the shell goes through `dispatch`.
struct AppShellView: View {
    let state: any AppShellState
    let calendar: CalendarUiState
    let dispatch: (Action) -> ActionEffect?

    /// The destination id the tab bar opens on. `nil`, or an id the state does not carry, opens on
    /// the first tab.
    var initialSelection: String?

    var body: some View {
        switch state {
        case let tabbed as AppShellStateTabs where !tabbed.tabs.isEmpty:
            TabbedShellView(
                tabs: tabbed.tabs, calendar: calendar, dispatch: dispatch,
                initialSelection: initialSelection)
        case is AppShellStateFailed:
            ShellMessageView(message: "Couldn't load your calendar.")
        case is AppShellStateNoDestinations:
            // Settled, unlike the loading case below: no progress, because nothing further is
            // coming to replace this.
            ShellMessageView(message: "Nothing to show here yet.")
        default:
            ShellMessageView(message: "Loading…", showsProgress: true)
        }
    }
}

/// The tab bar and each tab's navigation stack for `tabs`, which is never empty.
///
/// Selecting a tab dispatches its action, and the shell carries out the effect that comes back:
/// showing a screen selects the destination targeting it — the tapped one when it does, so two tabs
/// routing to one screen keep separate selection — opening a URL hands it to the system, and the
/// event effects open the not-yet-built event surface. A tab whose action does not navigate is never
/// selected: it acts, and the current screen stays in place.
///
/// Tabs are identified and selected by destination id, not by target screen id. When a refresh
/// delivers destinations that no longer include the selected one, the first navigating tab is
/// shown instead.
private struct TabbedShellView: View {
    let tabs: [AppShellTab]
    let calendar: CalendarUiState
    let dispatch: (Action) -> ActionEffect?

    /// The destination the user last selected. No initial value is declared: `@State` ignores an
    /// assignment in `init` over one, and an optional would carry an implicit `nil`.
    @State private var selectedDestinationId: String

    /// The message the not-yet-built event surface is showing, or `nil` when it is closed.
    @State private var eventSurfaceMessage: String?

    @Environment(\.openURL) private var openURL

    init(
        tabs: [AppShellTab], calendar: CalendarUiState,
        dispatch: @escaping (Action) -> ActionEffect?, initialSelection: String?
    ) {
        self.tabs = tabs
        self.calendar = calendar
        self.dispatch = dispatch
        self.selectedDestinationId =
            initialSelection ?? tabs.first { $0.targetScreenId != nil }?.destinationId
            ?? tabs[0].destinationId
    }

    var body: some View {
        TabView(selection: selection) {
            ForEach(tabs, id: \.destinationId) { tab in
                Tab(value: tab.destinationId) {
                    NavigationStack {
                        DestinationView(
                            tab: tab, calendar: calendar,
                            onAction: { act($0, source: tab) })
                    }
                } label: {
                    // The delivered label is the tab's whole name. The symbol is decoration, so
                    // VoiceOver never reads a symbol's own meaning — "favorite" for a star — as
                    // part of a destination it says nothing about.
                    Label {
                        Text(verbatim: tab.label)
                    } icon: {
                        Image(systemName: TabSymbol.name(for: tab.iconToken))
                            .accessibilityHidden(true)
                    }
                }
            }
        }
        .alert(
            "Not available yet", isPresented: eventSurfaceIsPresented,
            presenting: eventSurfaceMessage
        ) { _ in
            Button("OK") {}
        } message: { message in
            Text(verbatim: message)
        }
    }

    /// Reads the shown destination; a tap on a tab dispatches that tab's action instead of
    /// selecting it outright, so only an effect that shows a screen moves the selection.
    private var selection: Binding<String> {
        Binding(
            get: { resolvedDestinationId },
            set: { destinationId in
                guard let tab = tabs.first(where: { $0.destinationId == destinationId }) else {
                    return
                }
                act(tab.action, source: tab)
            }
        )
    }

    private var eventSurfaceIsPresented: Binding<Bool> {
        Binding(
            get: { eventSurfaceMessage != nil },
            set: { if !$0 { eventSurfaceMessage = nil } }
        )
    }

    private var resolvedDestinationId: String {
        let navigating = tabs.filter { $0.targetScreenId != nil }
        if navigating.contains(where: { $0.destinationId == selectedDestinationId }) {
            return selectedDestinationId
        }
        return navigating.first?.destinationId ?? tabs[0].destinationId
    }

    /// Dispatches `action`, taken from `source`, and carries out the effect that comes back.
    private func act(_ action: Action, source: AppShellTab) {
        switch dispatch(action) {
        case let show as ActionEffectShowScreen:
            let target =
                source.targetScreenId == show.screenId
                ? source : tabs.first { $0.targetScreenId == show.screenId }
            if let target {
                selectedDestinationId = target.destinationId
            }
        case let open as ActionEffectOpenExternalUrl:
            if let url = URL(string: open.url) {
                openURL(url)
            }
        case let detail as ActionEffectShowEventDetail:
            eventSurfaceMessage = "Event details aren't available yet (\(detail.eventId))."
        case let events as ActionEffectShowEvents:
            eventSurfaceMessage =
                "Event lists aren't available yet (\(events.eventIds.joined(separator: ", ")))."
        default:
            break
        }
    }
}

/// A tab's screen: the calendar screen when the tab targets it, otherwise a placeholder.
private struct DestinationView: View {
    let tab: AppShellTab
    let calendar: CalendarUiState
    let onAction: (Action) -> Void

    var body: some View {
        if let screenId = tab.targetScreenId, screenId == calendar.content?.screen.id {
            CalendarScreenView(
                title: calendar.title ?? "", viewSwitcher: calendar.viewSwitcher,
                component: calendar.body, isLoading: calendar.isLoading,
                hasFailed: calendar.failure != nil, onAction: onAction)
        } else {
            DestinationPlaceholderView(label: tab.label)
        }
    }
}

/// SF Symbols for the contract's semantic icon tokens.
enum TabSymbol {

    /// The symbol for `iconToken`. Tokens this app does not know — including ones the server adds
    /// later — fall back to a generic symbol rather than leaving the tab blank: a plain grid, which
    /// claims no meaning a destination might not have, where a star reads as "favorite".
    static func name(for iconToken: String?) -> String {
        switch iconToken {
        case "icon.calendar": "calendar"
        case "icon.wallet": "wallet.pass"
        default: "square.grid.2x2"
        }
    }
}

/// What stands in for a destination's screen until that screen is built.
private struct DestinationPlaceholderView: View {
    let label: String

    var body: some View {
        Text(verbatim: label)
            .font(.title2)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .navigationTitle(Text(verbatim: label))
    }
}

/// A full-screen message, with a spinner above it while something is still on its way.
private struct ShellMessageView: View {
    let message: LocalizedStringKey
    var showsProgress = false

    var body: some View {
        VStack(spacing: 16.0) {
            if showsProgress {
                ProgressView()
            }
            Text(message)
                .font(.title3)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Previews

/// Tabs shaped like the contract's example screen: two destinations, each routing to a screen.
private let previewTabs = [
    AppShellTab(
        destinationId: "calendar", label: "Calendar", iconToken: "icon.calendar",
        action: NavigateAction(screen: "calendar")),
    AppShellTab(
        destinationId: "expense", label: "Expense", iconToken: "icon.wallet",
        action: NavigateAction(screen: "expense")),
]

/// No calendar screen observed: the previews show the shell's own states, not a screen's chrome.
private let previewCalendar = CalendarUiState(content: nil, isLoading: false, failure: nil)

#Preview("Loading") {
    AppShellView(
        state: AppShellStateLoading.shared, calendar: previewCalendar, dispatch: { _ in nil })
}

#Preview("No destinations") {
    AppShellView(
        state: AppShellStateNoDestinations.shared, calendar: previewCalendar,
        dispatch: { _ in nil })
}

#Preview("Failure") {
    AppShellView(
        state: AppShellStateFailed(failure: KotlinException(message: "No backend reachable")),
        calendar: previewCalendar, dispatch: { _ in nil })
}

#Preview("Calendar selected") {
    AppShellView(
        state: AppShellStateTabs(tabs: previewTabs), calendar: previewCalendar,
        dispatch: { _ in nil }, initialSelection: "calendar")
}

#Preview("Expense selected") {
    AppShellView(
        state: AppShellStateTabs(tabs: previewTabs), calendar: previewCalendar,
        dispatch: { _ in nil }, initialSelection: "expense")
}
