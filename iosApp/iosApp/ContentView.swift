import SwiftUI
import shared

/// The whole iOS UI for now: it asks the shared graph for a calendar screen and shows what came
/// back.
///
/// This is not product UI — M-2 replaces it. It exists so that launching the app exercises the
/// framework boundary end to end: resolving `CalendarScreenRepository` from the graph, building a
/// request in Kotlin, running it over the Darwin engine, and reading the `Result` back in Swift.
/// A screen that rendered a fixed string would prove only that the app did not crash.
struct ContentView: View {
    @State private var outcome: CalendarScreenFetchOutcome = .fetching

    var body: some View {
        VStack(spacing: 12.0) {
            Text("Calendar screen")
                .font(.headline)
            Text(outcome.summary)
                .font(.system(.footnote, design: .monospaced))
                .multilineTextAlignment(.center)
                .foregroundStyle(outcome.isFailure ? .red : .primary)
        }
        .padding(.all)
        .task {
            outcome = await CalendarScreenFetchOutcome.forCurrentMonth()
        }
    }
}

/// What the repository answered, reduced to the little this screen shows.
enum CalendarScreenFetchOutcome {
    case fetching
    case delivered(schemaVersion: String, theme: String, title: String)
    case failed(reason: String)

    var isFailure: Bool {
        if case .failed = self { return true }
        return false
    }

    var summary: String {
        switch self {
        case .fetching:
            return "Fetching…"
        case let .delivered(schemaVersion, theme, title):
            return "\(title)\nschema \(schemaVersion) · theme \(theme)"
        case let .failed(reason):
            // Expected until a backend is running — the fetch reaching a real failure still proves
            // the repository resolved and the request went out over the platform's HTTP stack.
            return "No screen: \(reason)"
        }
    }

    /// The device's locale as the BCP-47 tag the contract asks for.
    ///
    /// `Locale.current.identifier` is an ICU identifier, not a language tag: it separates with
    /// underscores and carries keyword extensions (`en_TH@calendar=gregorian`). Canonicalizing
    /// hyphenates it but keeps the keywords, so the suffix is dropped here. `identifier(.bcp47)`
    /// would say all of this in one call, but it needs iOS 16 and this app deploys to 15.
    private static var currentLanguageTag: String {
        let canonical = Locale.canonicalLanguageIdentifier(from: Locale.current.identifier)
        return String(canonical.prefix { $0 != "@" })
    }

    /// Fetches this month's screen through the graph and reduces the answer to a case above.
    static func forCurrentMonth() async -> CalendarScreenFetchOutcome {
        let repository = DependencyGraph.shared.calendarScreenRepository()
        let today = Calendar.current.dateComponents([.year, .month], from: Date())
        guard let year = today.year, let month = today.month else {
            return .failed(reason: "the current month is unreadable")
        }

        let request = CalendarScreenRequest(
            view: RequestedCalendarViewMonth.shared,
            start: CalendarDate(year: Int32(year), month: Int32(month), dayOfMonth: 1),
            end: CalendarDate(year: Int32(year), month: Int32(month), dayOfMonth: 28),
            timeZone: TimeZone.current.identifier,
            locale: Self.currentLanguageTag,
            knownTheme: nil)

        do {
            let result = try await repository.fetchCalendarScreen(request: request)
            if let success = result as? ResultSuccess<CalendarScreenResponse> {
                let response = success.data
                return .delivered(
                    schemaVersion: response.schemaVersion,
                    theme: response.theme.id,
                    title: response.screen.title)
            }
            if let failure = result as? ResultError {
                return .failed(reason: failure.exception.message ?? "\(failure.exception)")
            }
            return .failed(reason: "unrecognized result \(result)")
        } catch {
            // Only cancellation and non-Exception throwables reach here — everything a caller can
            // act on already arrived as `Result.Error` above.
            return .failed(reason: error.localizedDescription)
        }
    }
}
