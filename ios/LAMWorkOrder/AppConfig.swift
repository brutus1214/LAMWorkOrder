import Foundation

enum AppConfig {
    // Keep the trailing slash. For App Store/TestFlight release, replace this with HTTPS.
    static let apiBaseURL = URL(string: "http://50.190.210.154:5081/")!
}
