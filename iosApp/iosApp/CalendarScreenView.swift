import SwiftUI
import shared

/// The calendar screen inside its tab: the server-formatted title as the navigation title, the view
/// switcher in the toolbar, and the delivered body drawn by the component registry. Everything the
/// person does on it — choosing a view, tapping an event, the month overflow — goes to `onAction`.
struct CalendarScreenView: View {
    let title: String
    let viewSwitcher: ViewSwitcherUiState?
    let component: (any CalendarBodyUiState)?
    let onAction: (Action) -> Void

    var body: some View {
        ScrollView {
            if let component {
                CalendarBodyComponentView(component: component, onAction: onAction)
                    .padding()
            }
        }
        .navigationTitle(Text(verbatim: title))
        .toolbar {
            if let viewSwitcher {
                ToolbarItem(placement: .topBarTrailing) {
                    ViewSwitcherMenu(switcher: viewSwitcher, onAction: onAction)
                }
            }
        }
    }
}

/// The views the screen offers, in contract order, with the active one checked. Choosing one
/// dispatches its switch; the menu keeps no selection of its own, so what it checks is always the
/// view the screen was last delivered in.
struct ViewSwitcherMenu: View {
    let switcher: ViewSwitcherUiState
    let onAction: (Action) -> Void

    var body: some View {
        Menu {
            ForEach(switcher.options, id: \.id) { option in
                Button {
                    onAction(option.action)
                } label: {
                    if option.isActive {
                        Label(option.label, systemImage: "checkmark")
                    } else {
                        Text(verbatim: option.label)
                    }
                }
            }
        } label: {
            Text(verbatim: switcher.options.first(where: \.isActive)?.label ?? "View")
        }
        .accessibilityIdentifier("calendar.viewSwitcher")
    }
}
