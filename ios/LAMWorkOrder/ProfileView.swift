import SwiftUI

struct ProfileView: View {
    @EnvironmentObject private var app: AppState
    @Environment(\.dismiss) private var dismiss

    @State private var displayName = ""
    @State private var email = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("Profile") {
                    TextField("Display name", text: $displayName)
                    TextField("Email", text: $email)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()

                    if let user = app.user {
                        LabeledContent("Username", value: user.username)
                        LabeledContent("Role", value: user.role)
                        LabeledContent("Store", value: user.storeLabel)
                    }
                }
            }
            .navigationTitle("Profile")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
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
    }
}
