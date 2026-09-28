import SwiftUI
import shared

// Stand-ins for the real calendar views until the month grid and agenda list are built. Each reads
// only its own component's props — the subtitle lines already capped and the chip style already
// scoped by the shared projection — matching the Android renderers prop for prop.

/// The month body: its heading unless the title already names the month, a weekday row starting on the resolved week start, and its events in
/// the server's display order. Past `maxVisibleEvents` the rest collapse into one "more" affordance
/// presenting the hidden events — the contract attaches no action to an overflow, so it builds its
/// own from the ids it hides.
struct MonthBodyPlaceholderView: View {
    let month: MonthBodyUiState
    let onAction: (Action) -> Void

    /// How many events are listed before the rest collapse into "more"; the Android month
    /// placeholder uses the same.
    private static let maxVisibleEvents = 3

    var body: some View {
        let hidden = month.events.dropFirst(Self.maxVisibleEvents)
        VStack(alignment: .leading, spacing: 8) {
            if let headerLabel = month.headerLabel {
                Text(verbatim: headerLabel)
                    .font(.headline)
            }
            WeekdayRowView(weekStart: month.weekStart)
            ForEach(month.events.prefix(Self.maxVisibleEvents), id: \.eventId) { event in
                CalendarEventComponentView(event: event, onAction: onAction)
            }
            if !hidden.isEmpty {
                // A caption line is ~17 pt tall; the frame pads the tap target out to the 44 pt the
                // Human Interface Guidelines ask for without enlarging the text.
                Button {
                    onAction(PresentModalAction(eventIds: hidden.map(\.eventId)))
                } label: {
                    Text("+\(hidden.count) more")
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                }
                .font(.caption)
            }
        }
    }
}

/// The agenda body: each day's heading followed by that day's events, in delivered order.
struct AgendaBodyPlaceholderView: View {
    let agenda: AgendaBodyUiState
    let onAction: (Action) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(agenda.days, id: \.date) { day in
                Text(verbatim: day.headerLabel)
                    .font(.subheadline.weight(.semibold))
                ForEach(day.events, id: \.eventId) { event in
                    CalendarEventComponentView(event: event, onAction: onAction)
                }
            }
        }
    }
}

/// Seven short weekday names in the device's language, starting on `weekStart` — the order the
/// month grid's rows will take. Read from the Gregorian calendar, whose weekday symbols start on
/// Sunday, so the rotation below does not depend on the user's own first weekday. A calendar made
/// by identifier carries no locale and names weekdays in English, so it is given the device's.
struct WeekdayRowView: View {
    let weekStart: WeekStart

    var body: some View {
        let symbols = Self.localizedShortWeekdaySymbols()
        let first = weekStart == .monday ? 1 : 0
        let ordered = Array(symbols[first...] + symbols[..<first])
        HStack(spacing: 0) {
            ForEach(ordered, id: \.self) { symbol in
                Text(verbatim: symbol)
                    .font(.caption)
                    .frame(maxWidth: .infinity)
            }
        }
    }

    /// The Gregorian calendar's short weekday names, Sunday first, in the device's language.
    private static func localizedShortWeekdaySymbols() -> [String] {
        var gregorian = Foundation.Calendar(identifier: .gregorian)
        gregorian.locale = .current
        return gregorian.shortWeekdaySymbols
    }
}

/// A filled bar — the month and time-grid all-day components chip style reaches — drawn per the
/// resolved style: a pastel fill in the calendar's background colour, or a surface chip with an
/// accent edge.
struct FilledBarChipView: View {
    let event: EventChipUiState
    let onAction: (Action) -> Void

    var body: some View {
        if event.chipStyle == .pastel {
            PastelChipView(event: event, onAction: onAction)
        } else {
            AccentEdgeChipView(event: event, onAction: onAction)
        }
    }
}

/// A pastel fill in the calendar's background colour, its foreground colour on top.
struct PastelChipView: View {
    let event: EventChipUiState
    let onAction: (Action) -> Void

    var body: some View {
        let colors = ChipColors(event.calendarColor)
        EventChipButton(event: event, onAction: onAction) {
            ChipTextView(event: event, color: colors.foreground)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(colors.background, in: .rect(cornerRadius: 4))
        }
    }
}

/// A surface-coloured chip marked by an edge in the calendar's accent colour.
struct AccentEdgeChipView: View {
    let event: EventChipUiState
    let onAction: (Action) -> Void

    var body: some View {
        let colors = ChipColors(event.calendarColor)
        EventChipButton(event: event, onAction: onAction) {
            HStack(spacing: 0) {
                colors.accent.frame(width: 4)
                ChipTextView(event: event, color: .primary)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                Spacer(minLength: 0)
            }
            .fixedSize(horizontal: false, vertical: true)
            .background(.background.secondary, in: .rect(cornerRadius: 4))
        }
    }
}

/// A month timed marker: a dot in the calendar's accent before its one server-composed line.
struct TimedMarkerChipView: View {
    let event: EventChipUiState
    let onAction: (Action) -> Void

    var body: some View {
        let colors = ChipColors(event.calendarColor)
        EventChipButton(event: event, onAction: onAction) {
            HStack(spacing: 6) {
                Circle().fill(colors.accent).frame(width: 8, height: 8)
                ChipTextView(event: event, color: .primary)
            }
        }
    }
}

/// Makes a chip tappable to open its event's detail, and speaks the event's accessibility label,
/// when it has one, in place of the chip's drawn text. Without one, VoiceOver reads what the chip
/// draws — its title and every subtitle line — as SwiftUI derives it, rather than the title alone.
/// The contract attaches no action to an event, so the tap builds its own from the event's id.
struct EventChipButton<Label: View>: View {
    let event: EventChipUiState
    let onAction: (Action) -> Void
    @ViewBuilder let label: Label

    var body: some View {
        Button {
            onAction(OpenEventDetailAction(eventId: event.eventId))
        } label: {
            label
        }
        .buttonStyle(.plain)
        .accessibilityLabel(ifDelivered: event.accessibilityLabel)
    }
}

/// The chip's title and every subtitle line it carries. The lines arrive already capped at the
/// resolved chip density, so this draws them all and derives no cap of its own.
struct ChipTextView: View {
    let event: EventChipUiState
    let color: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(verbatim: event.title)
                .font(.subheadline)
                .lineLimit(1)
            // Lines carry no identity — two may share their text — hold no state, and never
            // reorder within one delivered chip, so their position is a sound id here.
            ForEach(Array(event.subtitleLines.enumerated()), id: \.offset) { _, line in
                Text(verbatim: line.text)
                    .font(.caption)
                    .lineLimit(1)
            }
        }
        .foregroundStyle(color)
    }
}

/// The three colours a chip draws with, from the event's calendar. An event whose calendar the body
/// does not carry, or a colour that does not parse, takes neutral system colours instead — one bad
/// reference must not fail the whole body.
struct ChipColors {
    let accent: Color
    let background: Color
    let foreground: Color

    init(_ calendarColor: CalendarColor?) {
        accent = calendarColor.flatMap { Color(hex: $0.accentColor) } ?? .accentColor
        background =
            calendarColor.flatMap { Color(hex: $0.backgroundColor) } ?? Color(.secondarySystemFill)
        foreground = calendarColor.flatMap { Color(hex: $0.foregroundColor) } ?? .primary
    }
}

extension Color {
    /// A `#rrggbb` string as an opaque colour, or `nil` when it is not one.
    init?(hex: String) {
        guard hex.count == 7, hex.first == "#", let value = UInt32(hex.dropFirst(), radix: 16)
        else {
            return nil
        }
        self.init(
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255)
    }
}

extension View {
    /// Replaces this view's accessibility label with `label` when the server delivered one, and
    /// otherwise leaves the label SwiftUI derives from the view's own content.
    @ViewBuilder
    fileprivate func accessibilityLabel(ifDelivered label: String?) -> some View {
        if let label {
            accessibilityLabel(Text(verbatim: label))
        } else {
            self
        }
    }
}
