import Foundation

enum APIError: LocalizedError {
    case invalidURL
    case invalidResponse
    case server(String)

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "The server address is invalid."
        case .invalidResponse:
            return "The server returned an invalid response."
        case .server(let message):
            return message
        }
    }
}

final class APIClient {
    private let baseURL: URL
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    init(baseURL: URL = AppConfig.apiBaseURL) {
        self.baseURL = baseURL
    }

    func login(username: String, password: String) async throws -> LoginResponse {
        try await send("api/auth/login", method: "POST", body: LoginRequest(username: username, password: password))
    }

    func register(_ request: RegistrationRequest) async throws -> LoginResponse {
        try await send("api/auth/register", method: "POST", body: request)
    }

    func profile(token: String) async throws -> User {
        try await send("api/profile", token: token)
    }

    func updateProfile(token: String, _ request: ProfileUpdate) async throws -> User {
        try await send("api/profile", method: "PATCH", token: token, body: request)
    }

    func users(token: String) async throws -> [User] {
        try await send("api/users", token: token)
    }

    func assignees(token: String) async throws -> [User] {
        try await send("api/assignees", token: token)
    }

    func updateUser(token: String, id: String, request: UserAdminUpdate) async throws -> User {
        try await send("api/users/\(id)", method: "PATCH", token: token, body: request)
    }

    func resetPassword(token: String, id: String, password: String) async throws {
        try await sendNoContent(
            "api/users/\(id)/reset-password",
            method: "POST",
            token: token,
            body: PasswordReset(newPassword: password)
        )
    }

    func listWorkOrders(token: String, search: String? = nil) async throws -> [WorkOrder] {
        let query = search.flatMap { value -> [URLQueryItem]? in
            let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? nil : [URLQueryItem(name: "search", value: trimmed)]
        } ?? []
        return try await send("api/work-orders", token: token, query: query)
    }

    func createWorkOrder(token: String, request: CreateWorkOrder) async throws -> WorkOrder {
        try await send("api/work-orders", method: "POST", token: token, body: request)
    }

    func updateWorkOrder(token: String, id: String, request: UpdateWorkOrder) async throws -> WorkOrder {
        try await send("api/work-orders/\(id)", method: "PUT", token: token, body: request)
    }

    func updateStatus(token: String, id: String, status: String, note: String?) async throws -> WorkOrder {
        try await send(
            "api/work-orders/\(id)/status",
            method: "PATCH",
            token: token,
            body: StatusUpdate(status: status, note: note)
        )
    }

    func uploadAttachments(token: String, workOrderID: String, media: [SelectedMedia]) async throws -> [Attachment] {
        guard !media.isEmpty else { return [] }
        let boundary = "Boundary-\(UUID().uuidString)"
        var request = URLRequest(url: try makeURL(path: "api/work-orders/\(workOrderID)/attachments"))
        request.httpMethod = "POST"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

        var body = Data()
        for item in media {
            body.appendString("--\(boundary)\r\n")
            body.appendString("Content-Disposition: form-data; name=\"files\"; filename=\"\(item.filename.safeUploadFilename)\"\r\n")
            body.appendString("Content-Type: \(item.mimeType)\r\n\r\n")
            body.append(item.data)
            body.appendString("\r\n")
        }
        body.appendString("--\(boundary)--\r\n")
        request.httpBody = body

        return try decoder.decode([Attachment].self, from: try await data(for: request))
    }

    func attachmentContent(token: String, id: String) async throws -> Data {
        var request = URLRequest(url: try makeURL(path: "api/attachments/\(id)/content"))
        request.httpMethod = "GET"
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        return try await data(for: request)
    }

    private func send<T: Decodable>(
        _ path: String,
        method: String = "GET",
        token: String? = nil,
        query: [URLQueryItem] = []
    ) async throws -> T {
        var request = URLRequest(url: try makeURL(path: path, query: query))
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let token = token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        return try decoder.decode(T.self, from: try await data(for: request))
    }

    private func send<T: Decodable, Body: Encodable>(
        _ path: String,
        method: String,
        token: String? = nil,
        body: Body
    ) async throws -> T {
        var request = URLRequest(url: try makeURL(path: path))
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = try encoder.encode(body)
        return try decoder.decode(T.self, from: try await data(for: request))
    }

    private func sendNoContent<Body: Encodable>(
        _ path: String,
        method: String,
        token: String,
        body: Body
    ) async throws {
        var request = URLRequest(url: try makeURL(path: path))
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.httpBody = try encoder.encode(body)
        _ = try await data(for: request)
    }

    private func data(for request: URLRequest) async throws -> Data {
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }
        guard 200..<300 ~= httpResponse.statusCode else {
            throw APIError.server(serverMessage(from: data, statusCode: httpResponse.statusCode))
        }
        return data
    }

    private func makeURL(path: String, query: [URLQueryItem] = []) throws -> URL {
        var url = baseURL
        for component in path.split(separator: "/") {
            url.appendPathComponent(String(component))
        }
        guard var components = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
            throw APIError.invalidURL
        }
        components.queryItems = query.isEmpty ? nil : query
        guard let finalURL = components.url else {
            throw APIError.invalidURL
        }
        return finalURL
    }

    private func serverMessage(from data: Data, statusCode: Int) -> String {
        guard !data.isEmpty else { return "Server error \(statusCode)" }
        guard
            let object = try? JSONSerialization.jsonObject(with: data),
            let dictionary = object as? [String: Any],
            let detail = dictionary["detail"]
        else {
            return String(data: data, encoding: .utf8) ?? "Server error \(statusCode)"
        }

        if let message = detail as? String {
            return message
        }
        if
            let detailData = try? JSONSerialization.data(withJSONObject: detail),
            let message = String(data: detailData, encoding: .utf8)
        {
            return message
        }
        return "Server error \(statusCode)"
    }
}

private extension Data {
    mutating func appendString(_ value: String) {
        append(Data(value.utf8))
    }
}

private extension String {
    var safeUploadFilename: String {
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "._-"))
        let scalars = unicodeScalars.map { allowed.contains($0) ? Character($0) : "_" }
        let cleaned = String(scalars)
        return cleaned.isEmpty ? "attachment" : cleaned
    }
}
