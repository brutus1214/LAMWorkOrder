import Foundation
import MessageUI
import SwiftUI
import UIKit
import UniformTypeIdentifiers

struct WorkOrderPreparedAttachment: Identifiable {
    let id: String
    let data: Data
    let filename: String
    let mimeType: String
    let typeIdentifier: String
    let fileURL: URL
}

struct WorkOrderAttachmentPreparation {
    let attachments: [WorkOrderPreparedAttachment]
    let failedCount: Int
}

enum WorkOrderSendDestination {
    case share
    case text(String)
    case email([String])
}

struct WorkOrderSendPayload: Identifiable {
    let id = UUID()
    let kind: WorkOrderSendKind
}

enum WorkOrderSendKind {
    case share(subject: String, message: String, attachments: [WorkOrderPreparedAttachment])
    case text(recipient: String, message: String, attachments: [WorkOrderPreparedAttachment])
    case email(recipients: [String], subject: String, message: String, attachments: [WorkOrderPreparedAttachment])
}

struct AssignmentSendRequest: Identifiable {
    let id = UUID()
    let order: WorkOrder
    let title: String
    let description: String
    let requestedBy: String
    let location: String
    let priority: String
    let assignedTo: String
    let status: String
    let statusNote: String?
}

func workOrderShareSubject(for order: WorkOrder) -> String {
    "Work Order \(order.workOrderNumber)"
}

func workOrderShareText(
    for order: WorkOrder,
    storeNumber: Int? = nil,
    title: String,
    description: String,
    requestedBy: String,
    location: String,
    priority: String,
    assignedTo: String?,
    status: String,
    statusNote: String?
) -> String {
    var lines = [
        "Work Order \(order.workOrderNumber)",
        "Created: \(formatWorkOrderDate(order.createdAt))",
        "Store: \(storeNumber ?? order.storeNumber)",
        "Location: \(location)",
        "Status: \((WorkOrderStatus(rawValue: status) ?? order.statusValue).label)",
        "Priority: \(priority)",
        "Title: \(title)",
        "Description: \(description)",
        "Requested by: \(requestedBy)"
    ]

    if let assignedTo = assignedTo?.trimmed, assignedTo.isEmpty == false {
        lines.append("Assigned to: \(assignedTo)")
    }
    if let statusNote = statusNote?.trimmed, statusNote.isEmpty == false {
        lines.append("Status note: \(statusNote)")
    }

    return lines.joined(separator: "\n")
}

func workOrderAssignmentSubject(for request: AssignmentSendRequest) -> String {
    "Assigned: \(request.order.workOrderNumber)"
}

func workOrderAssignmentMessage(for request: AssignmentSendRequest) -> String {
    "Assigned to: \(request.assignedTo)\n\n" + workOrderShareText(
        for: request.order,
        storeNumber: request.order.storeNumber,
        title: request.title,
        description: request.description,
        requestedBy: request.requestedBy,
        location: request.location,
        priority: request.priority,
        assignedTo: request.assignedTo,
        status: request.status,
        statusNote: request.statusNote?.trimmed.nilIfBlank
    )
}

func workOrderAssignmentPromptMessage(
    for request: AssignmentSendRequest,
    contacts: [User]
) -> String {
    let assignee = findWorkOrderContact(in: contacts, name: request.assignedTo)
    var lines = [
        "Work order saved. Send the details to \(request.assignedTo)?",
        workOrderAttachmentSummary(count: request.order.attachments.count)
    ]

    if emailAddress(for: assignee) == nil && textNumber(for: assignee) == nil {
        lines.append("No email or phone number is saved for this assignee.")
    }

    return lines.joined(separator: "\n")
}

func workOrderAttachmentSummary(count: Int) -> String {
    switch count {
    case 0:
        return "No pictures or videos are attached."
    case 1:
        return "1 picture or video is attached."
    default:
        return "\(count) pictures or videos are attached."
    }
}

func findWorkOrderContact(in users: [User], name: String?) -> User? {
    let normalizedName = name.normalizedContactName
    guard normalizedName.isEmpty == false else { return nil }

    return users.first { user in
        let normalizedDisplayName = user.displayName.normalizedContactName
        let normalizedUsername = user.username.normalizedContactName
        return normalizedDisplayName == normalizedName
            || normalizedUsername == normalizedName
            || user.assigneeLabel.normalizedContactName == normalizedName
            || normalizedDisplayName == normalizedName.withoutStoreAssignment
    }
}

func textNumber(for user: User?) -> String? {
    guard let phoneNumber = user?.phoneNumber?.trimmed else { return nil }
    return phoneNumber.filter(\.isNumber).count >= 7 ? phoneNumber : nil
}

func emailAddress(for user: User?) -> String? {
    guard let email = user?.email?.trimmed else { return nil }
    return isValidEmail(email) ? email : nil
}

func isValidEmail(_ value: String) -> Bool {
    let pattern = #"^[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$"#
    return value.trimmed.range(of: pattern, options: .regularExpression) != nil
}

func canTextWorkOrder() -> Bool {
    MFMessageComposeViewController.canSendText()
}

func canEmailWorkOrder() -> Bool {
    MFMailComposeViewController.canSendMail()
}

@MainActor
func prepareWorkOrderSendPayload(
    app: AppState,
    destination: WorkOrderSendDestination,
    subject: String,
    message: String,
    attachments: [Attachment]
) async -> WorkOrderSendPayload? {
    switch destination {
    case .text:
        guard canTextWorkOrder() else {
            app.errorMessage = "Text messages are not available on this device."
            return nil
        }
    case .email:
        guard canEmailWorkOrder() else {
            app.errorMessage = "Email is not available on this device."
            return nil
        }
    case .share:
        break
    }

    let preparation = await prepareWorkOrderAttachments(app: app, attachments: attachments)
    if attachments.isEmpty == false && preparation.attachments.isEmpty {
        app.errorMessage = "Unable to attach pictures or videos."
        return nil
    }
    if preparation.failedCount > 0 {
        app.errorMessage = "Some attachments could not be added."
    }

    switch destination {
    case .share:
        return WorkOrderSendPayload(
            kind: .share(subject: subject, message: message, attachments: preparation.attachments)
        )
    case .text(let phone):
        return WorkOrderSendPayload(
            kind: .text(recipient: phone, message: message, attachments: preparation.attachments)
        )
    case .email(let recipients):
        return WorkOrderSendPayload(
            kind: .email(
                recipients: recipients,
                subject: subject,
                message: message,
                attachments: preparation.attachments
            )
        )
    }
}

@MainActor
func prepareWorkOrderAttachments(
    app: AppState,
    attachments: [Attachment]
) async -> WorkOrderAttachmentPreparation {
    guard attachments.isEmpty == false else {
        return WorkOrderAttachmentPreparation(attachments: [], failedCount: 0)
    }

    let fileManager = FileManager.default
    let rootDirectory = fileManager.temporaryDirectory
        .appendingPathComponent("LAMWorkOrderSend", isDirectory: true)
    let directory = rootDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)

    do {
        try? fileManager.removeItem(at: rootDirectory)
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
    } catch {
        return WorkOrderAttachmentPreparation(attachments: [], failedCount: attachments.count)
    }

    var prepared: [WorkOrderPreparedAttachment] = []
    var failedCount = 0

    for attachment in attachments {
        do {
            let data = try await app.attachmentData(id: attachment.id)
            let filename = safeWorkOrderAttachmentFilename("\(attachment.id)_\(attachment.originalName)")
            let fileURL = directory.appendingPathComponent(filename)
            try data.write(to: fileURL, options: .atomic)
            prepared.append(
                WorkOrderPreparedAttachment(
                    id: attachment.id,
                    data: data,
                    filename: filename,
                    mimeType: attachment.contentType,
                    typeIdentifier: UTType(mimeType: attachment.contentType)?.identifier ?? UTType.data.identifier,
                    fileURL: fileURL
                )
            )
        } catch {
            failedCount += 1
        }
    }

    return WorkOrderAttachmentPreparation(attachments: prepared, failedCount: failedCount)
}

struct WorkOrderActivityView: UIViewControllerRepresentable {
    let subject: String
    let message: String
    let attachments: [WorkOrderPreparedAttachment]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        var items: [Any] = [WorkOrderActivityItemSource(subject: subject, message: message)]
        items.append(contentsOf: attachments.map(\.fileURL))
        return UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {
    }
}

struct WorkOrderMessageComposeView: UIViewControllerRepresentable {
    @Environment(\.dismiss) private var dismiss

    let recipient: String
    let message: String
    let attachments: [WorkOrderPreparedAttachment]

    func makeCoordinator() -> Coordinator {
        Coordinator(dismiss: dismiss)
    }

    func makeUIViewController(context: Context) -> MFMessageComposeViewController {
        let controller = MFMessageComposeViewController()
        controller.messageComposeDelegate = context.coordinator
        controller.recipients = [recipient]
        controller.body = message

        if MFMessageComposeViewController.canSendAttachments() {
            for attachment in attachments {
                _ = controller.addAttachmentData(
                    attachment.data,
                    typeIdentifier: attachment.typeIdentifier,
                    filename: attachment.filename
                )
            }
        }

        return controller
    }

    func updateUIViewController(_ uiViewController: MFMessageComposeViewController, context: Context) {
    }

    final class Coordinator: NSObject, MFMessageComposeViewControllerDelegate {
        private let dismiss: DismissAction

        init(dismiss: DismissAction) {
            self.dismiss = dismiss
        }

        func messageComposeViewController(
            _ controller: MFMessageComposeViewController,
            didFinishWith result: MessageComposeResult
        ) {
            dismiss()
        }
    }
}

struct WorkOrderMailComposeView: UIViewControllerRepresentable {
    @Environment(\.dismiss) private var dismiss

    let recipients: [String]
    let subject: String
    let message: String
    let attachments: [WorkOrderPreparedAttachment]

    func makeCoordinator() -> Coordinator {
        Coordinator(dismiss: dismiss)
    }

    func makeUIViewController(context: Context) -> MFMailComposeViewController {
        let controller = MFMailComposeViewController()
        controller.mailComposeDelegate = context.coordinator
        controller.setToRecipients(recipients)
        controller.setSubject(subject)
        controller.setMessageBody(message, isHTML: false)

        for attachment in attachments {
            controller.addAttachmentData(
                attachment.data,
                mimeType: attachment.mimeType,
                fileName: attachment.filename
            )
        }

        return controller
    }

    func updateUIViewController(_ uiViewController: MFMailComposeViewController, context: Context) {
    }

    final class Coordinator: NSObject, MFMailComposeViewControllerDelegate {
        private let dismiss: DismissAction

        init(dismiss: DismissAction) {
            self.dismiss = dismiss
        }

        func mailComposeController(
            _ controller: MFMailComposeViewController,
            didFinishWith result: MFMailComposeResult,
            error: Error?
        ) {
            dismiss()
        }
    }
}

private final class WorkOrderActivityItemSource: NSObject, UIActivityItemSource {
    private let subject: String
    private let message: String

    init(subject: String, message: String) {
        self.subject = subject
        self.message = message
    }

    func activityViewControllerPlaceholderItem(_ activityViewController: UIActivityViewController) -> Any {
        message
    }

    func activityViewController(
        _ activityViewController: UIActivityViewController,
        itemForActivityType activityType: UIActivity.ActivityType?
    ) -> Any? {
        message
    }

    func activityViewController(
        _ activityViewController: UIActivityViewController,
        subjectForActivityType activityType: UIActivity.ActivityType?
    ) -> String {
        subject
    }
}

private func safeWorkOrderAttachmentFilename(_ name: String) -> String {
    let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "._-"))
    let scalars = name.unicodeScalars.map { allowed.contains($0) ? Character($0) : "_" }
    let cleaned = String(scalars)
    return cleaned.isEmpty ? "attachment" : cleaned
}

private extension String? {
    var normalizedContactName: String {
        self?.normalizedContactName ?? ""
    }
}

private extension String {
    var normalizedContactName: String {
        trimmed
            .lowercased()
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
    }

    var withoutStoreAssignment: String {
        replacingOccurrences(
            of: #"\s+-\s+(la mart \d+|all stores)$"#,
            with: "",
            options: .regularExpression
        )
    }
}
