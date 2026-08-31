import SwiftUI

struct ProfileView: View {
    @EnvironmentObject private var app: AppState
    @Environment(\.dismiss) private var dismiss

    @State private var displayName = ""
    @State private var email = ""

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Profile")) {
                    TextField("Display name", text: $displayName)
                    TextField("Email", text: $email)
                        .keyboardType(.emailAddress)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)

                    if let user = app.user {
                        HStack {
                            Text("Username")
                            Spacer()
                            Text(user.username)
                                .foregroundColor(.secondary)
                        }
                        HStack {
                            Text("Role")
                            Spacer()
                            Text(user.role)
                                .foregroundColor(.secondary)
                        }
                        HStack {
                            Text("Store")
                            Spacer()
                            Text(user.storeLabel)
                                .foregroundColor(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("Profile")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Save") {
                        Task {
                            if await app.updateProfile(displayName: displayName.trimmed, email: email) {
                                dismiss()
                            }
                        }
                    }
                    .disabled(displayName.trimmed.isEmpty)
                }
            }
            .onAppear {
                displayName = app.user?.displayName ?? ""
                email = app.user?.email ?? ""
            }
        }
        .navigationViewStyle(StackNavigationViewStyle())
    }
}
