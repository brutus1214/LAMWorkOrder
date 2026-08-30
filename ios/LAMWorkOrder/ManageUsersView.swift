import SwiftUI

struct ManageUsersView: View {
    @EnvironmentObject private var app: AppState
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List(app.users) { user in
                NavigationLink {
                    UserEditView(user: user)
                } label: {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(user.displayName)
                            .font(.headline)
                        Text("\(user.role) - \(user.storeLabel)")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        if user.isActive == false {
                            Text("Inactive")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.red)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
            .navigationTitle("Manage Users")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        Task { await app.loadUsers() }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                }
            }
            .task {
                await app.loadUsers()
            }
        }
    }
}

struct UserEditView: View {
    @EnvironmentObject private var app: AppState

    let user: User
    @State private var displayName: String
    @State private var storeNumber: Int
    @State private var role: String
    @State private var email: String
    @State private var phoneNumber: String
    @State private var isActive: Bool
    @State private var newPassword = ""
    @State private var passwordReset = false

    init(user: User) {
        self.user = user
        _displayName = State(initialValue: user.displayName)
        _storeNumber = State(initialValue: user.storeNumber)
        _role = State(initialValue: user.role)
        _email = State(initialValue: user.email ?? "")
        _phoneNumber = State(initialValue: user.phoneNumber ?? "")
        _isActive = State(initialValue: user.isActive)
    }

    var body: some View {
        Form {
            Section("Account") {
                TextField("Display name", text: $displayName)
                TextField("Email", text: $email)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                TextField("Phone number", text: $phoneNumber)
                    .keyboardType(.phonePad)

                Picker("Store", selection: $storeNumber) {
                    Text("All Stores").tag(99)
                    ForEach(1...9, id: \.self) { store in
                        Text("LA Mart \(store)").tag(store)
                    }
                }

                Picker("Role", selection: $role) {
                    ForEach(Role.allCases) { item in
                        Text(item.rawValue).tag(item.rawValue)
                    }
                }

                Toggle("Active", isOn: $isActive)
            }

            Section {
                Button {
                    Task {
                        _ = await app.updateUser(
                            id: user.id,
                            request: UserAdminUpdate(
                                displayName: displayName.trimmed,
                                storeNumber: storeNumber,
                                role: role,
                                email: email.trimmed.nilIfBlank,
                                phoneNumber: phoneNumber.trimmed.nilIfBlank,
                                isActive: isActive
                            )
                        )
                    }
                } label: {
                    Label("Save User", systemImage: "checkmark.circle")
                }
                .disabled(displayName.trimmed.isEmpty)
            }

            Section("Password") {
                SecureField("New password", text: $newPassword)
                Button {
                    Task {
                        if await app.resetPassword(userID: user.id, password: newPassword) {
                            newPassword = ""
                            passwordReset = true
                        }
                    }
                } label: {
                    Label("Reset Password", systemImage: "key")
                }
                .disabled(newPassword.count < 8)

                if passwordReset {
                    Text("Password reset.")
                        .foregroundStyle(.green)
                }
            }
        }
        .navigationTitle(user.displayName)
        .navigationBarTitleDisplayMode(.inline)
    }
}
