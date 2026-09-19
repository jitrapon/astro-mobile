import SwiftUI
import shared

/// The shell around every screen: a tab bar with one tab per destination the server delivered, and
/// the selected destination's screen above it — or, before any destinations have arrived, a
/// loading or failure placeholder with no tab bar at all.
///
/// It draws a shell state and nothing more. Which destinations become tabs, and when the bar is
/// shown, is decided once in the shared `toAppShellState()` projection, so this view and the
/// Android shell cannot disagree about it.
struct AppShellView: View {
    let state: any AppShellState

    /// The destination id the tab bar opens on. `nil`, or an id the state does not carry, opens on
    /// the first tab.
    var initialSelection: String?

    var body: some View {
        switch state {
        case let tabbed as AppShellStateTabs where !tabbed.tabs.isEmpty:
            TabbedShellView(tabs: tabbed.tabs, initialSelection: initialSelection)
        case is AppShellStateFailed:
            ShellMessageView(message: "Couldn't load your calendar.")
        default:
            ShellMessageView(message: "Loading…", showsProgress: true)
        }
    }
}

/// The tab bar and each tab's navigation stack for `tabs`, which is never empty.
///
/// Tabs are identified and selected by destination id, not by target screen id: two destinations
/// may route to the same screen and must still be two tabs, each with its own navigation state.
/// When a refresh delivers destinations that no longer include the selected one, the first tab is
/// shown instead.
private struct TabbedShellView: View {
    let tabs: [AppShellTab]

    /// The destination the user last selected. No initial value is declared: `@State` ignores an
    /// assignment in `init` over one, and an optional would carry an implicit `nil`.
    @State private var selectedDestinationId: String

    init(tabs: [AppShellTab], initialSelection: String?) {
        self.tabs = tabs
        self.selectedDestinationId = initialSelection ?? tabs[0].destinationId
    }

    var body: some View {
        TabView(selection: selection) {
            ForEach(tabs, id: \.destinationId) { tab in
                Tab(
                    tab.label, systemImage: TabSymbol.name(for: tab.iconToken),
                    value: tab.destinationId
                ) {
                    NavigationStack {
                        DestinationPlaceholderView(label: tab.label)
                    }
                }
            }
        }
    }

    private var selection: Binding<String> {
        Binding(
            get: { resolvedDestinationId },
            set: { selectedDestinationId = $0 }
        )
    }

    private var resolvedDestinationId: String {
        tabs.contains(where: { $0.destinationId == selectedDestinationId })
            ? selectedDestinationId : tabs[0].destinationId
    }
}

/// SF Symbols for the contract's semantic icon tokens.
enum TabSymbol {

    /// The symbol for `iconToken`. Tokens this app does not know — including ones the server adds
    /// later — fall back to a generic symbol rather than leaving the tab blank.
    static func name(for iconToken: String?) -> String {
        switch iconToken {
        case "icon.calendar": "calendar"
        case "icon.wallet": "wallet.pass"
        default: "star"
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
        targetScreenId: "calendar"),
    AppShellTab(
        destinationId: "expense", label: "Expense", iconToken: "icon.wallet",
        targetScreenId: "expense"),
]

#Preview("Loading") {
    AppShellView(state: AppShellStateLoading.shared)
}

#Preview("Failure") {
    AppShellView(
        state: AppShellStateFailed(failure: KotlinException(message: "No backend reachable")))
}

#Preview("Calendar selected") {
    AppShellView(state: AppShellStateTabs(tabs: previewTabs), initialSelection: "calendar")
}

#Preview("Expense selected") {
    AppShellView(state: AppShellStateTabs(tabs: previewTabs), initialSelection: "expense")
}
