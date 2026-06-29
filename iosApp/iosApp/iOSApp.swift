import SwiftUI
import shared

@main
struct IOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView(
                viewModel: .init(
                    loginRepository: LoginRepository(dataSource: LoginDataSource()),
                    loginValidator: LoginDataValidator()))
        }
    }
}
