import Foundation

enum WorkOrderFilter: String, CaseIterable, Identifiable {
    case me = "Me"
    case all = "All"
    case new = "New"
    case open = "Open/In Progress"
    case completed = "Completed"
    case closed = "Closed/Cancelled"

    var id: String { rawValue }
}

func visibleWorkOrders(_ orders: [WorkOrder], filter: WorkOrderFilter, currentUser: User?) -> [WorkOrder] {
    let cutoff = Calendar.current.date(byAdding: .day, value: -30, to: Date()) ?? Date()
    return orders
        .filter { order in
            switch filter {
            case .me:
                return order.hasUserName(currentUser)
            case .all:
                return order.createdDate.map { $0 > cutoff } ?? true
            case .new:
                return order.status == WorkOrderStatus.new.rawValue
            case .open:
                return ["Scheduled", "InProgress", "Blocked"].contains(order.status)
            case .completed:
                return order.status == WorkOrderStatus.completed.rawValue
            case .closed:
                return order.status == WorkOrderStatus.cancelled.rawValue
            }
        }
        .sorted {
            if statusRank($0.status) == statusRank($1.status) {
                return $0.workOrderNumber < $1.workOrderNumber
            }
            return statusRank($0.status) < statusRank($1.status)
        }
}

func formatWorkOrderDate(_ value: String) -> String {
    guard let date = parseDate(value) else {
        return value.components(separatedBy: "T").first ?? value
    }
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.dateStyle = .short
    formatter.timeStyle = .none
    return formatter.string(from: date)
}

private func statusRank(_ status: String) -> Int {
    switch status {
    case "New":
        return 0
    case "Scheduled":
        return 1
    case "InProgress":
        return 2
    case "Blocked":
        return 3
    case "Completed":
        return 4
    case "Cancelled":
        return 5
    default:
        return 6
    }
}

private extension WorkOrder {
    var createdDate: Date? {
        parseDate(createdAt)
    }

    func hasUserName(_ user: User?) -> Bool {
        guard let user = user else { return false }
        let names = Set([
            user.displayName.normalizedIdentity,
            user.username.normalizedIdentity,
            user.assigneeLabel.normalizedIdentity
        ])
        return [requestedBy, assignedTo ?? ""]
            .map(\.normalizedIdentity)
            .contains { field in
                names.contains(field) || field.withoutStoreLabel == user.displayName.normalizedIdentity
            }
    }
}

private func parseDate(_ value: String) -> Date? {
    let withFractional = ISO8601DateFormatter()
    withFractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    if let date = withFractional.date(from: value) {
        return date
    }
    return ISO8601DateFormatter().date(from: value)
}

private extension String {
    var normalizedIdentity: String {
        trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .components(separatedBy: .whitespacesAndNewlines)
            .filter { !$0.isEmpty }
            .joined(separator: " ")
    }

    var withoutStoreLabel: String {
        replacingOccurrences(
            of: "\\s+-\\s+(la mart \\d+|all stores)$",
            with: "",
            options: [.regularExpression]
        )
    }
}
