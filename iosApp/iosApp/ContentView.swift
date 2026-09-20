import SwiftUI
import shared

/// The app's root: it watches the current month's calendar screen through the shared graph and
/// draws the app shell that screen's destinations describe.
///
/// Each delivered `CalendarUiState` goes through the shared `toAppShellState()` projection, so which
/// destinations become tabs — and whether a tab bar shows at all — is decided in Kotlin exactly as
/// it is for the Android shell. Until the first state arrives the shell is loading.
struct ContentView: View {
    @State private var shellState: any AppShellState = AppShellStateLoading.shared

    var body: some View {
        AppShellView(state: shellState)
            .task {
                // Ending the view's task finishes the stream, which cancels the subscription. A
                // cancelled subscription delivers nothing further, so the last shell stays standing
                // rather than being overwritten with a failure the user never caused.
                for await delivered in CalendarScreenObservation.statesForCurrentMonth() {
                    shellState = delivered.toAppShellState()
                }
            }
    }
}

/// The current month's calendar screen as a stream of the states it passes through.
enum CalendarScreenObservation {

    /// The device's locale as the BCP-47 tag the contract asks for.
    ///
    /// `Locale.current.identifier` is an ICU identifier, not a language tag: it separates with
    /// underscores and carries keyword extensions (`en_TH@calendar=gregorian`). Canonicalizing
    /// hyphenates it but keeps the keywords, so the suffix is dropped here. `identifier(.bcp47)` is
    /// not a substitute: it re-encodes those keywords as a `-u-` extension rather than dropping
    /// them.
    private static var currentLanguageTag: String {
        let canonical = Locale.canonicalLanguageIdentifier(from: Locale.current.identifier)
        return String(canonical.prefix { $0 != "@" })
    }

    /// The device's time zone read through the proleptic Gregorian calendar, whatever calendar the
    /// user has chosen to see dates in.
    ///
    /// `Calendar.current` follows that choice, and a request window is not a display: the contract
    /// declares `start` and `end` as ISO-8601 dates, which are Gregorian by definition. A device set
    /// to the Buddhist calendar reports the year 2026 as 2569, so asking `Calendar.current` for the
    /// current month would send a window five centuries away — a request the backend answers
    /// perfectly well, with an empty calendar, giving nothing to trace the fault back from.
    ///
    /// The time zone stays the device's: which month it currently is genuinely depends on where the
    /// user is. Only the era and year numbering are pinned. The user's own calendar preference
    /// still reaches the server, through `locale`, where it belongs — it governs how the server
    /// formats the labels it sends back, not which dates the client asks for.
    ///
    /// Qualified as `Foundation.Calendar` because the shared framework exports a `Calendar` of its
    /// own — the contract's calendar-source model — and an unqualified name here resolves to
    /// neither.
    private static var gregorianDeviceCalendar: Foundation.Calendar {
        var calendar = Foundation.Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone.current
        return calendar
    }

    /// Subscribes to this month's screen through the graph and yields every state it passes
    /// through, for as long as the stream is iterated.
    ///
    /// The Kotlin subscription is a callback plus a handle, because a `Flow` does not cross the
    /// framework boundary. The stream owns that handle: whichever way iteration ends — the view's
    /// task cancelled, or the loop abandoned — its termination cancels the subscription, so no
    /// Kotlin coroutine outlives the screen that asked for it.
    static func statesForCurrentMonth() -> AsyncStream<CalendarUiState> {
        AsyncStream { continuation in
            guard let request = currentMonthRequest() else {
                continuation.finish()
                return
            }
            let subscription = DependencyGraph.shared.calendarScreenObserver().observe(
                request: request
            ) { state in
                continuation.yield(state)
            }
            continuation.onTermination = { _ in
                subscription.cancel()
            }
        }
    }

    /// The request for the current month, or `nil` when the device's calendar cannot say which
    /// month that is.
    private static func currentMonthRequest() -> CalendarScreenRequest? {
        let now = Date()
        let calendar = Self.gregorianDeviceCalendar
        let today = calendar.dateComponents([.year, .month], from: now)
        // The window has to end on the month's own last day: a fixed 28 would ask for a short month
        // in every month that isn't February, and the days it left out would be missing from a
        // screen that promises the current month.
        guard let year = today.year, let month = today.month,
            let lastDayOfMonth = calendar.range(of: .day, in: .month, for: now)?.count
        else {
            return nil
        }

        return CalendarScreenRequest(
            view: RequestedCalendarViewMonth.shared,
            start: CalendarDate(year: Int32(year), month: Int32(month), dayOfMonth: 1),
            end: CalendarDate(
                year: Int32(year), month: Int32(month), dayOfMonth: Int32(lastDayOfMonth)),
            timeZone: TimeZone.current.identifier,
            locale: Self.currentLanguageTag,
            knownTheme: nil)
    }
}
