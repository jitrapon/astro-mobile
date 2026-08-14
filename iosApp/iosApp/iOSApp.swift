import SwiftUI
import shared

@main
struct IOSApp: App {

    init() {
        // The shared graph has to be running before any view can resolve from it, and an App's
        // initializer is the earliest point SwiftUI offers.
        DependencyGraph.shared.start(baseUrl: Self.developmentBackendBaseURL)
    }

    var body: some Scene {
        WindowGroup {
            ContentView(
                viewModel: .init(
                    loginRepository: LoginRepository(dataSource: LoginDataSource()),
                    loginValidator: LoginDataValidator()))
        }
    }

    /// The backend as the simulator reaches it: the simulator shares the host's loopback, and the
    /// contract declares its operations under `/api`.
    ///
    /// A development placeholder — the backend is not deployed anywhere yet, so there is no
    /// environment to read a real origin from. Replace it with a configuration-driven value before
    /// a release build ships; this one resolves to nothing off a developer's machine.
    private static let developmentBackendBaseURL = "http://localhost:8080/api"
}
