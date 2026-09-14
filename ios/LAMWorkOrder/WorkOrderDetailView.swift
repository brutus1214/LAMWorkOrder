import SwiftUI
import UIKit

private enum WorkOrderSendDestination {
    case share
    case text(String)
    case email([String])
}

struct WorkOrderDetailView: View {
    @EnvironmentObject private var app: AppState

    private let initialOrder: WorkOrder
    @State private var storeNumber: Int
    @State private var title: String
    @State private var description: String
    @State private var requestedBy: String
    @State private var location: String
    @State private var priority: String
    @State private var assignedTo: String
    @State private var status: String
    @State private var statusNote: String
    @State private var selectedMedia: [SelectedMedia] = []
    @State private var pickerMode: MediaPickerMode?
    @State private var vendorEmail = ""
    @State private var preparingSend = false
    @State private var sendPayload: WorkOrderSendPayload?

    init(order: WorkOrder) {
        initialOrder = order
        _storeNumber = State(initialValue: order.storeNumber)
        _title = State(initialValue: order.title)
        _description = State(initialValue: order.description)
        _requestedBy = State(initialValue: order.requestedBy)
        _location = State(initialValue: order.location)
        _priority = State(initialValue: order.priority)
        _assignedTo = State(initialValue: order.assignedTo ?? "")
        _status = State(initialValue: order.status)
        _statusNote = State(initialValue: order.statusNote ?? "")
    }

    private var order: WorkOrder {
        app.orders.first { $0.id == initialOrder.id } ?? initialOrder
    }

    private var contacts: [User] {
        var seenIDs: Set<String> = []
        return ([app.user].compactMap { $0 } + app.users + app.assignees).filter { user in
            seenIDs.insert(user.id).inserted
        }
    }

    private var requesterContact: User? {
        findWorkOrderContact(in: contacts, name: requestedBy)
    }

    private var assigneeContact: User? {
        findWorkOrderContact(in: contacts, name: assignedTo)
    }

    private var validVendorEmail: Bool {
        vendorEmail.trimmed.isEmpty || isValidEmail(vendorEmail)
    }

    var body: some View {
        Form {
            Section {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text(order.workOrderNumber)
                            .font(.title2.bold())
                        Spacer()
                        StatusBadge(status: WorkOrderStatus(rawValue: status) ?? order.statusValue)
                    }
                    Text("Created \(formatWorkOrderDate(order.createdAt))")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
            }

            Section(header: Text("Send work order")) {
                Menu {
                    Button {
                        startSend(.share)
                    } label: {
                        Label("Share work order", systemImage: "square.and.arrow.up")
                    }

                    Button {
                        if let phone = textNumber(for: requesterContact) {
                            startSend(.text(phone))
                        }
                    } label: {
                        Label("Text requester", systemImage: "message")
                    }
                    .disabled(textNumber(for: requesterContact) == nil || canTextWorkOrder() == false)

                    Button {
                        if let email = emailAddress(for: requesterContact) {
                            startSend(.email([email]))
                        }
                    } label: {
                        Label("Email requester", systemImage: "envelope")
                    }
                    .disabled(emailAddress(for: requesterContact) == nil || canEmailWorkOrder() == false)

                    Button {
                        if let phone = textNumber(for: assigneeContact) {
                            startSend(.text(phone))
                        }
                    } label: {
                        Label("Text assignee", systemImage: "message")
                    }
                    .disabled(textNumber(for: assigneeContact) == nil || canTextWorkOrder() == false)

                    Button {
                        if let email = emailAddress(for: assigneeContact) {
                            startSend(.email([email]))
                        }
                    } label: {
                        Label("Email assignee", systemImage: "envelope")
                    }
                    .disabled(emailAddress(for: assigneeContact) == nil || canEmailWorkOrder() == false)

                    Button {
                        startSend(.email([vendorEmail.trimmed]))
                    } label: {
                        Label("Email vendor", systemImage: "envelope")
                    }
                    .disabled(vendorEmail.trimmed.isEmpty || validVendorEmail == false || canEmailWorkOrder() == false)
                } label: {
                    Label(preparingSend ? "Preparing..." : "Send...", systemImage: preparingSend ? "hourglass" : "paperplane")
                        .frame(maxWidth: .infinity)
                }
                .disabled(preparingSend)

                TextField("Vendor email", text: $vendorEmail)
                    .keyboardType(.emailAddress)
                    .autocapitalization(.none)
                    .disableAutocorrection(true)

                if validVendorEmail == false {
                    Text("Enter a valid email address.")
                        .font(.caption)
                        .foregroundColor(.red)
                }
            }

            Section(header: Text("Work")) {
                if app.user?.role == Role.admin.rawValue {
                    Picker("Store", selection: $storeNumber) {
                        Text("All Stores").tag(99)
                        ForEach(1...9, id: \.self) { store in
                            Text("LA Mart \(store)").tag(store)
                        }
                    }
                } else {
                    HStack {
                        Text("Store")
                        Spacer()
                        Text("\(storeNumber)")
                            .foregroundColor(.secondary)
                    }
                }

                TextField("Title", text: $title)

                VStack(alignment: .leading, spacing: 8) {
                    Text("Description")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    TextEditor(text: $description)
                        .frame(minHeight: 110)
                }

                TextField("Requested by", text: $requestedBy)
                    .disabled(true)
                TextField("Location", text: $location)

                Picker("Priority", selection: $priority) {
                    ForEach(Priority.allCases) { item in
                        Text(item.rawValue).tag(item.rawValue)
                    }
                }

                Picker("Assigned to", selection: $assignedTo) {
                    Text("Unassigned").tag("")
                    ForEach(app.assignees) { user in
                        Text(user.assigneeLabel).tag(user.assigneeLabel)
                    }
                }
            }

            Section(header: Text("Status")) {
                Picker("Status", selection: $status) {
                    ForEach(WorkOrderStatus.allCases) { item in
                        Text(item.label).tag(item.rawValue)
                    }
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("Status note")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    TextEditor(text: $statusNote)
                        .frame(minHeight: 80)
                }
            }

            Section(header: Text("Attachments")) {
                if order.attachments.isEmpty {
                    Text("No attachments")
                        .foregroundColor(.secondary)
                } else {
                    ForEach(order.attachments) { attachment in
                        AttachmentPreviewRow(attachment: attachment)
                    }
                }

                if UIImagePickerController.isSourceTypeAvailable(.camera) {
                    Button {
                        pickerMode = .cameraPhoto
                    } label: {
                        Label("Take Photo", systemImage: "camera")
                    }

                    Button {
                        pickerMode = .cameraVideo
                    } label: {
                        Label("Record Video", systemImage: "video")
                    }
                }

                Button {
                    pickerMode = .library
                } label: {
                    Label("Choose From Device", systemImage: "photo.on.rectangle")
                }

                ForEach(selectedMedia) { item in
                    HStack {
                        Label(item.filename, systemImage: item.mimeType.hasPrefix("video/") ? "video" : "photo")
                        Spacer()
                        Button(role: .destructive) {
                            selectedMedia.removeAll { $0.id == item.id }
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                        }
                        .buttonStyle(BorderlessButtonStyle())
                    }
                }

                Button {
                    Task {
                        if await app.uploadAttachments(workOrderID: order.id, media: selectedMedia) {
                            selectedMedia = []
                        }
                    }
                } label: {
                    Label("Upload Selected", systemImage: "icloud.and.arrow.up")
                }
                .disabled(selectedMedia.isEmpty)
            }

            Section {
                Button {
                    Task {
                        _ = await app.updateWorkOrder(
                            id: order.id,
                            request: UpdateWorkOrder(
                                storeNumber: storeNumber,
                                title: title.trimmed,
                                description: description.trimmed,
                                requestedBy: requestedBy.trimmed,
                                location: location.trimmed,
                                priority: priority,
                                assignedTo: assignedTo.trimmed.nilIfBlank,
                                dueAt: order.dueAt,
                                status: status,
                                statusNote: statusNote.trimmed.nilIfBlank
                            )
                        )
                    }
                } label: {
                    Label("Save Changes", systemImage: "checkmark.circle")
                        .frame(maxWidth: .infinity)
                }
                .disabled(title.trimmed.isEmpty || description.trimmed.isEmpty || location.trimmed.isEmpty)
            }
        }
        .navigationTitle(order.workOrderNumber)
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $pickerMode) { mode in
            MediaPicker(mode: mode) { media in
                selectedMedia.append(media)
            }
        }
        .sheet(item: $sendPayload) { payload in
            sendSheet(for: payload)
        }
        .onAppear {
            Task {
                if app.assignees.isEmpty {
                    await app.loadAssignees()
                }
                if app.user?.role == Role.admin.rawValue || app.user?.role == Role.manager.rawValue {
                    if app.users.isEmpty {
                        await app.loadUsers()
                    }
                }
            }
        }
    }

    private var shareSubject: String {
        workOrderShareSubject(for: order)
    }

    private var shareMessage: String {
        workOrderShareText(
            for: order,
            storeNumber: storeNumber,
            title: title,
            description: description,
            requestedBy: requestedBy,
            location: location,
            priority: priority,
            assignedTo: assignedTo.trimmed.nilIfBlank,
            status: status,
            statusNote: statusNote.trimmed.nilIfBlank
        )
    }

    @ViewBuilder
    private func sendSheet(for payload: WorkOrderSendPayload) -> some View {
        switch payload.kind {
        case .share(let subject, let message, let attachments):
            WorkOrderActivityView(subject: subject, message: message, attachments: attachments)
        case .text(let recipient, let message, let attachments):
            WorkOrderMessageComposeView(recipient: recipient, message: message, attachments: attachments)
        case .email(let recipients, let subject, let message, let attachments):
            WorkOrderMailComposeView(
                recipients: recipients,
                subject: subject,
                message: message,
                attachments: attachments
            )
        }
    }

    private func startSend(_ destination: WorkOrderSendDestination) {
        guard preparingSend == false else { return }

        let subject = shareSubject
        let message = shareMessage
        let attachments = order.attachments

        preparingSend = true
        Task { @MainActor in
            defer { preparingSend = false }

            switch destination {
            case .text:
                guard canTextWorkOrder() else {
                    app.errorMessage = "Text messages are not available on this device."
                    return
                }
            case .email:
                guard canEmailWorkOrder() else {
                    app.errorMessage = "Email is not available on this device."
                    return
                }
            case .share:
                break
            }

            let preparation = await prepareWorkOrderAttachments(app: app, attachments: attachments)
            if attachments.isEmpty == false && preparation.attachments.isEmpty {
                app.errorMessage = "Unable to attach pictures or videos."
                return
            }
            if preparation.failedCount > 0 {
                app.errorMessage = "Some attachments could not be added."
            }

            switch destination {
            case .share:
                sendPayload = WorkOrderSendPayload(
                    kind: .share(subject: subject, message: message, attachments: preparation.attachments)
                )
            case .text(let phone):
                sendPayload = WorkOrderSendPayload(
                    kind: .text(recipient: phone, message: message, attachments: preparation.attachments)
                )
            case .email(let recipients):
                sendPayload = WorkOrderSendPayload(
                    kind: .email(
                        recipients: recipients,
                        subject: subject,
                        message: message,
                        attachments: preparation.attachments
                    )
                )
            }
        }
    }
}

struct AttachmentPreviewRow: View {
    @EnvironmentObject private var app: AppState

    let attachment: Attachment
    @State private var image: UIImage?
    @State private var loadFailed = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Label(attachment.originalName, systemImage: attachment.contentType.hasPrefix("video/") ? "video" : "photo")
                Spacer()
                Text(ByteCountFormatter.string(fromByteCount: attachment.sizeBytes, countStyle: .file))
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            if let image = image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .frame(maxHeight: 220)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            } else if attachment.contentType.hasPrefix("image/") && !loadFailed {
                ProgressView()
                    .onAppear {
                        Task {
                            do {
                                let data = try await app.attachmentData(id: attachment.id)
                                image = UIImage(data: data)
                                loadFailed = image == nil
                            } catch {
                                loadFailed = true
                            }
                        }
                    }
            }
        }
    }
}
