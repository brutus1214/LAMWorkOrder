import Foundation

enum Priority: String, CaseIterable, Identifiable, Codable {
    case low = "Low"
    case normal = "Normal"
    case high = "High"
    case emergency = "Emergency"

    var id: String { rawValue }
}

enum WorkOrderStatus: String, CaseIterable, Identifiable, Codable {
    case new = "New"
    case scheduled = "Scheduled"
    case inProgress = "InProgress"
    case blocked = "Blocked"
    case completed = "Completed"
    case cancelled = "Cancelled"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .inProgress:
            return "In Progress"
        default:
            return rawValue
        }
    }
}

enum Role: String, CaseIterable, Identifiable, Codable {
    case requester = "Requester"
    case employee = "Employee"
    case security = "Security"
    case technician = "Technician"
    case manager = "Manager"
    case admin = "Admin"

    var id: String { rawValue }
}

struct Attachment: Codable, Identifiable, Hashable {
    let id: String
    let originalName: String
    let contentType: String
    let sizeBytes: Int64
    let createdAt: String
    let url: String
}

struct User: Codable, Identifiable, Hashable {
    let id: String
    let username: String
    var displayName: String
    var email: String?
    var storeNumber: Int
    var phoneNumber: String?
    var role: String
    var isActive: Bool

    var storeLabel: String {
        storeNumber == 99 ? "All Stores" : "LA Mart \(storeNumber)"
    }

    var assigneeLabel: String {
        "\(displayName) - \(storeLabel)"
    }

    var canManageUsers: Bool {
        role == Role.admin.rawValue || role == Role.manager.rawValue
    }
}

struct WorkOrder: Codable, Identifiable, Hashable {
    let id: String
    let workOrderNumber: String
    let createdById: String?
    var storeNumber: Int
    var title: String
    var description: String
    var requestedBy: String
    var location: String
    var priority: String
    var status: String
    var assignedTo: String?
    var dueAt: String?
    let createdAt: String
    let updatedAt: String
    var statusNote: String?
    var attachments: [Attachment]

    var statusValue: WorkOrderStatus {
        WorkOrderStatus(rawValue: status) ?? .new
    }

    var priorityValue: Priority {
        Priority(rawValue: priority) ?? .normal
    }
}

struct CreateWorkOrder: Codable {
    var storeNumber: Int
    var title: String
    var description: String
    var requestedBy: String
    var location: String
    var priority: String
    var assignedTo: String?
    var dueAt: String?
}

struct UpdateWorkOrder: Codable {
    var storeNumber: Int
    var title: String
    var description: String
    var requestedBy: String
    var location: String
    var priority: String
    var assignedTo: String?
    var dueAt: String?
    var status: String
    var statusNote: String?
}

struct StatusUpdate: Codable {
    var status: String
    var note: String?
}

struct LoginRequest: Codable {
    var username: String
    var password: String
}

struct RegistrationRequest: Codable {
    var username: String
    var password: String
    var displayName: String
    var storeNumber: Int
    var email: String
    var phoneNumber: String
}

struct LoginResponse: Codable {
    var token: String
    var user: User
}

struct ProfileUpdate: Codable {
    var displayName: String
    var email: String?
}

struct UserAdminUpdate: Codable {
    var displayName: String
    var storeNumber: Int
    var role: String
    var email: String?
    var phoneNumber: String?
    var isActive: Bool
}

struct PasswordReset: Codable {
    var newPassword: String
}

struct SelectedMedia: Identifiable {
    let id = UUID()
    var filename: String
    var mimeType: String
    var data: Data
}

extension String {
    var trimmed: String {
        trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var nilIfBlank: String? {
        let value = trimmed
        return value.isEmpty ? nil : value
    }
}
