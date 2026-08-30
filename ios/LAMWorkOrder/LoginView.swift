import SwiftUI

struct LoginView: View {
    @EnvironmentObject private var app: AppState

    @State private var createMode = false
    @State private var username = ""
    @State private var password = ""
    @State private var displayName = ""
    @State private var storeNumber = 1
    @State private var email = ""
    @State private var phoneNumber = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("LA MART OPERATIONS")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundStyle(Color.accentColor)
                        Text(createMode ? "Create new user" : "Sign in")
                            .font(.largeTitle.bold())
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    if createMode {
                        createAccountFields
                    }

                    signInFields

                    Button {
                        Task {
                            if createMode {
                                await app.register(
                                    RegistrationRequest(
                                        username: username.trimmed.lowercased(),
                                        password: password,
                                        displayName: displayName.trimmed,
                                        storeNumber: storeNumber,
                                        email: email.trimmed.lowercased(),
                                        phoneNumber: phoneNumber.trimmed
                                    )
                                )
                            } else {
                                await app.login(username: username, password: password)
                            }
                        }
                    } label: {
                        Text(createMode ? "Create user and sign in" : "Sign in")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(!canSubmit)

                    Button {
                        createMode.toggle()
                    } label: {
                        Text(createMode ? "Already have an account? Sign in" : "Create new user")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }
                .padding(24)
                .frame(maxWidth: 520)
                .frame(maxWidth: .infinity)
            }
            .background(Color(.systemGroupedBackground))
        }
    }

    private var createAccountFields: some View {
        Group {
            Text("New accounts start as Requester. A manager can change the role later.")
                .font(.subheadline)
                .foregroundStyle(.secondary)

            TextField("Full name", text: $displayName)
                .textContentType(.name)
                .textInputAutocapitalization(.words)
                .textFieldStyle(.roundedBorder)

            Picker("Store", selection: $storeNumber) {
                ForEach(1...9, id: \.self) { store in
                    Text("LA Mart \(store)").tag(store)
                }
            }
            .pickerStyle(.menu)

            TextField("Email", text: $email)
                .keyboardType(.emailAddress)
                .textContentType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .textFieldStyle(.roundedBorder)

            TextField("Phone number for texting", text: $phoneNumber)
                .keyboardType(.phonePad)
                .textContentType(.telephoneNumber)
                .textFieldStyle(.roundedBorder)
        }
    }

    private var signInFields: some View {
        Group {
            TextField("Username or email", text: $username)
                .textContentType(.username)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .textFieldStyle(.roundedBorder)

            SecureField("Password", text: $password)
                .textContentType(createMode ? .newPassword : .password)
                .textFieldStyle(.roundedBorder)
        }
    }

    private var canSubmit: Bool {
        if createMode {
            return displayName.trimmed.isEmpty == false
                && username.trimmed.count >= 2
                && password.count >= 8
                && email.contains("@")
                && email.contains(".")
                && phoneNumber.filter(\.isNumber).count >= 7
        }
        return username.trimmed.isEmpty == false && password.isEmpty == false
    }
}
