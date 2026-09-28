import SwiftUI
import shared

/// Draws a delivered calendar body with the view registered to its component id — the iOS side of
/// the server-driven component registry, keyed on the same `CalendarComponentIds` constants the
/// shared decoder uses, so it cannot key on a spelling the decoder never produces.
///
/// An id with no view, and a body whose type does not match its id, both draw
/// `UnregisteredComponentView` rather than nothing. Neither is reachable from a delivered response
/// today: the decoder has no default for an unknown `component`, so an unmodelled body fails the
/// whole response, and every modelled id is handled here. What does reach the fallback is a
/// component the shared module models before this registry learns it, or a hand-built state.
struct CalendarBodyComponentView: View {
    let component: any CalendarBodyUiState
    let onAction: (Action) -> Void

    var body: some View {
        switch component.componentId {
        case CalendarComponentIds.shared.MONTH_BODY:
            if let month = component as? MonthBodyUiState {
                MonthBodyPlaceholderView(month: month, onAction: onAction)
            } else {
                UnregisteredComponentView(componentId: component.componentId)
            }
        case CalendarComponentIds.shared.AGENDA_BODY:
            if let agenda = component as? AgendaBodyUiState {
                AgendaBodyPlaceholderView(agenda: agenda, onAction: onAction)
            } else {
                UnregisteredComponentView(componentId: component.componentId)
            }
        default:
            UnregisteredComponentView(componentId: component.componentId)
        }
    }
}

/// Draws one event with the view registered to its presentation component id, falling back to
/// `UnregisteredComponentView` for an id with none — for the same reasons as
/// `CalendarBodyComponentView`.
struct CalendarEventComponentView: View {
    let event: EventChipUiState
    let onAction: (Action) -> Void

    var body: some View {
        switch event.componentId {
        case CalendarComponentIds.shared.MONTH_ALL_DAY_BAR,
            CalendarComponentIds.shared.TIME_GRID_ALL_DAY_BAR:
            FilledBarChipView(event: event, onAction: onAction)
        case CalendarComponentIds.shared.MONTH_TIMED_MARKER:
            TimedMarkerChipView(event: event, onAction: onAction)
        case CalendarComponentIds.shared.EVENT_BLOCK,
            CalendarComponentIds.shared.EVENT_CARD:
            // Chip style does not reach a time-grid block or an agenda card, so each keeps one look
            // under every style.
            AccentEdgeChipView(event: event, onAction: onAction)
        default:
            UnregisteredComponentView(componentId: event.componentId)
        }
    }
}

/// What the registry draws for a component id it holds no view for: the id itself, so the gap is
/// visible and nameable rather than a silent hole in the screen.
struct UnregisteredComponentView: View {
    let componentId: String

    var body: some View {
        Text("Can't show \(componentId) yet.")
            .font(.caption)
            .foregroundStyle(.secondary)
    }
}

// MARK: - Previews

#if DEBUG

    /// One preview per registry case and one for the fallback, so each can be rendered and checked on
    /// its own — the registry has no Swift test target, and no delivered response reaches the fallback.
    #Preview("Month body") {
        CalendarBodyComponentView(component: PreviewCalendarStates.month, onAction: { _ in })
            .padding()
    }

    #Preview("Agenda body") {
        CalendarBodyComponentView(component: PreviewCalendarStates.agenda, onAction: { _ in })
            .padding()
    }

    #Preview("Event presentations") {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(PreviewCalendarStates.everyPresentation, id: \.eventId) { event in
                CalendarEventComponentView(event: event, onAction: { _ in })
            }
        }
        .padding()
    }

    #Preview("Unregistered fallback") {
        VStack(alignment: .leading, spacing: 8) {
            CalendarBodyComponentView(
                component: PreviewCalendarStates.month(componentId: "calendar.unmodelled.v1"),
                onAction: { _ in })
            CalendarEventComponentView(
                event: PreviewCalendarStates.event(
                    id: "unmodelled", componentId: "calendar.event.unmodelled.v1"),
                onAction: { _ in })
        }
        .padding()
    }

    /// Hand-built calendar states for the previews above, shaped like the contract's example month.
    enum PreviewCalendarStates {
        private static let workCalendar = CalendarColor(
            accentToken: "calendar.work.accent", accentColor: "#b81311",
            backgroundToken: "calendar.work.background", backgroundColor: "#f9dcda",
            foregroundToken: "calendar.work.foreground", foregroundColor: "#5c0a09")

        static let month = month(componentId: CalendarComponentIds.shared.MONTH_BODY)

        static let agenda = AgendaBodyUiState(
            componentId: CalendarComponentIds.shared.AGENDA_BODY,
            days: [
                AgendaDayUiState(
                    date: "2026-04-16", headerLabel: "พฤ. 16 เม.ย.",
                    events: [event(id: "c1", componentId: CalendarComponentIds.shared.EVENT_CARD)]),
                AgendaDayUiState(date: "2026-04-17", headerLabel: "ศ. 17 เม.ย.", events: []),
            ])

        /// One event in each presentation component, the filled bars in both chip styles.
        static let everyPresentation = [
            event(id: "bar-accent", componentId: CalendarComponentIds.shared.MONTH_ALL_DAY_BAR),
            event(
                id: "bar-pastel", componentId: CalendarComponentIds.shared.MONTH_ALL_DAY_BAR,
                chipStyle: .pastel),
            event(id: "marker", componentId: CalendarComponentIds.shared.MONTH_TIMED_MARKER),
            event(
                id: "grid-bar", componentId: CalendarComponentIds.shared.TIME_GRID_ALL_DAY_BAR,
                chipStyle: .pastel),
            event(id: "block", componentId: CalendarComponentIds.shared.EVENT_BLOCK),
            event(id: "card", componentId: CalendarComponentIds.shared.EVENT_CARD),
        ]

        static func month(componentId: String) -> MonthBodyUiState {
            MonthBodyUiState(
                componentId: componentId, headerLabel: "เมษายน 2569", monthAnchor: "2026-04-01",
                weekStart: .monday,
                events: ["s1", "s2", "s3", "s4"].map {
                    event(
                        id: $0, componentId: CalendarComponentIds.shared.MONTH_ALL_DAY_BAR,
                        chipStyle: .pastel)
                })
        }

        static func event(
            id: String, componentId: String, chipStyle: ChipStyle? = .accentEdge
        ) -> EventChipUiState {
            EventChipUiState(
                eventId: id, componentId: componentId, title: "สงกรานต์ (\(id))",
                leadingIconToken: nil,
                subtitleLines: [PresentationLine(text: "Office", iconToken: nil)],
                chipStyle: chipStyle, calendarColor: workCalendar, accessibilityLabel: nil)
        }
    }
#endif
