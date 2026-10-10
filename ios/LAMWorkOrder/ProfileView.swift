import SwiftUI

struct ProfileView: View {
    @EnvironmentObject private var app: AppState
    @Environment(\.dismiss) private var dismiss

    @State private var displayName = ""
    @State private var email = ""
    @State private var confirmingDelete = false

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

                Section(header: Text("Account")) {
                    Button(role: .destructive) {
                        confirmingDelete = true
                    } label: {
                        Text("Delete Account")
                    }
                    .disabled(app.loading)
                }

                if let error = app.errorMessage {
                    Section {
                        Text(error)
                            .foregroundColor(.red)
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
            .alert("Delete Account?", isPresented: $confirmingDelete) {
                Button("Cancel", role: .cancel) {}
                Button("Delete Account", role: .destructive) {
                    Task {
                        if await app.deleteAccount() {
                            dismiss()
                        }
                    }
                }
            } message: {
                Text("This removes your profile information and signs you out. Existing work orders stay in the operations history.")
            }
        }
        .navigationViewStyle(StackNavigationViewStyle())
    }
}
