import Foundation
import SwiftUI

@MainActor
final class AppState: ObservableObject {
    @Published var user: User?
    @Published var orders: [WorkOrder] = []
    @Published var users: [User] = []
    @Published var assignees: [User] = []
    @Published var loading = false
    @Published var errorMessage: String?

    private let api: APIClient
    private var token: String?

    init(api: APIClient = APIClient()) {
        self.api = api
        token = KeychainStore.readToken()
        if token != nil {
            Task { await restoreSession() }
        }
    }

    var canManageUsers: Bool {
        user?.canManageUsers == true
    }

    func restoreSession() async {
        guard let token = token else { return }
        loading = true
        defer { loading = false }
        do {
            user = try await api.profile(token: token)
            orders = try await api.listWorkOrders(token: token)
            assignees = try await api.assignees(token: token)
            errorMessage = nil
        } catch {
            self.token = nil
            self.user = nil
            KeychainStore.deleteToken()
            errorMessage = userMessage(from: error)
        }
    }

    func login(username: String, password: String) async {
        loading = true
        defer { loading = false }
        do {
            let response = try await api.login(username: username.trimmingCharacters(in: .whitespacesAndNewlines), password: password)
            token = response.token
            KeychainStore.saveToken(response.token)
            user = response.user
            orders = try await api.listWorkOrders(token: response.token)
            assignees = try await api.assignees(token: response.token)
            errorMessage = nil
        } catch {
            errorMessage = userMessage(from: error)
        }
    }

    func register(_ request: RegistrationRequest) async {
        loading = true
        defer { loading = false }
        do {
            let response = try await api.register(request)
            token = response.token
            KeychainStore.saveToken(response.token)
            user = response.user
            orders = try await api.listWorkOrders(token: response.token)
            assignees = try await api.assignees(token: response.token)
            errorMessage = nil
        } catch {
            errorMessage = userMessage(from: error)
        }
    }

    func logout() {
        token = nil
        user = nil
        orders = []
        users = []
        assignees = []
        errorMessage = nil
        KeychainStore.deleteToken()
    }

    func refresh(search: String? = nil) async {
        guard let token = token else { return }
        loading = true
        defer { loading = false }
        do {
            orders = try await api.listWorkOrders(token: token, search: search)
            errorMessage = nil
        } catch {
            errorMessage = userMessage(from: error)
        }
    }

    func loadAssignees() async {
        guard let token = token else { return }
        do {
            assignees = try await api.assignees(token: token)
        } catch {
            errorMessage = "Unable to load assignees. Restart the backend, then refresh."
        }
    }

    func loadUsers() async {
        guard let token = token else { return }
        loading = true
        defer { loading = false }
        do {
            users = try await api.users(token: token)
            errorMessage = nil
        } catch {
            errorMessage = userMessage(from: error)
        }
    }

    func createWorkOrder(_ request: CreateWorkOrder, media: [SelectedMedia]) async -> Bool {
        guard let token = token else { return false }
        loading = true
        defer { loading = false }
        do {
            let created = try await api.createWorkOrder(token: token, request: request)
            if !media.isEmpty {
                _ = try await api.uploadAttachments(token: token, workOrderID: created.id, media: media)
            }
            orders = try await api.listWorkOrders(token: token)
            errorMessage = nil
            return true
        } catch {
            errorMessage = userMessage(from: error)
            return false
        }
    }

    func updateWorkOrder(id: String, request: UpdateWorkOrder) async -> Bool {
        guard let token = token else { return false }
        loading = true
        defer { loading = false }
        do {
            _ = try await api.updateWorkOrder(token: token, id: id, request: request)
            orders = try await api.listWorkOrders(token: token)
            errorMessage = nil
            return true
        } catch {
            errorMessage = userMessage(from: error)
            return false
        }
    }

    func updateStatus(id: String, status: String, note: String?) async -> Bool {
        guard let token = token else { return false }
        loading = true
        defer { loading = false }
        do {
            _ = try await api.updateStatus(token: token, id: id, status: status, note: note)
            orders = try await api.listWorkOrders(token: token)
            errorMessage = nil
            return true
        } catch {
            errorMessage = userMessage(from: error)
            return false
        }
    }

    func uploadAttachments(workOrderID: String, media: [SelectedMedia]) async -> Bool {
        guard let token = token else { return false }
        loading = true
        defer { loading = false }
        do {
            _ = try await api.uploadAttachments(token: token, workOrderID: workOrderID, media: media)
            orders = try await api.listWorkOrders(token: token)
            errorMessage = nil
            return true
        } catch {
            errorMessage = userMessage(from: error)
            return false
        }
    }

    func attachmentData(id: String) async throws -> Data {
        guard let token = token else {
            throw APIError.server("Sign in again.")
        }
        return try await api.attachmentContent(token: token, id: id)
    }

    func updateProfile(displayName: String, email: String?) async -> Bool {
        guard let token = token else { return false }
        loading = true
        defer { loading = false }
        do {
            user = try await api.updateProfile(
                token: token,
                ProfileUpdate(displayName: displayName, email: email?.nilIfBlank)
            )
            errorMessage = nil
            return true
        } catch {
            errorMessage = userMessage(from: error)
            return false
        }
    }

    func updateUser(id: String, request: UserAdminUpdate) async -> Bool {
        guard let token = token else { return false }
        loading = true
        defer { loading = false }
        do {
            _ = try await api.updateUser(token: token, id: id, request: request)
            users = try await api.users(token: token)
            errorMessage = nil
            return true
        } catch {
            errorMessage = userMessage(from: error)
            return false
        }
    }

    func resetPassword(userID: String, password: String) async -> Bool {
        guard let token = token else { return false }
        loading = true
        defer { loading = false }
        do {
            try await api.resetPassword(token: token, id: userID, password: password)
            errorMessage = nil
            return true
        } catch {
            errorMessage = userMessage(from: error)
            return false
        }
    }

    private func userMessage(from error: Error) -> String {
        if let localized = error as? LocalizedError, let description = localized.errorDescription {
            return description
        }
        return error.localizedDescription
    }
}
