import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var app: AppState

    var body: some View {
        Group {
            if app.user == nil {
                LoginView()
            } else {
                WorkOrderHomeView()
            }
        }
        .overlay {
            if app.loading {
                ZStack {
                    Color.black.opacity(0.12).ignoresSafeArea()
                    ProgressView()
                        .controlSize(.large)
                        .padding(24)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
                }
            }
        }
        .alert(
            "LAM Work Order",
            isPresented: Binding(
                get: { app.errorMessage != nil },
                set: { isPresented in
                    if !isPresented {
                        app.errorMessage = nil
                    }
                }
            )
        ) {
            Button("OK", role: .cancel) {
                app.errorMessage = nil
            }
        } message: {
            Text(app.errorMessage ?? "")
        }
    }
}
