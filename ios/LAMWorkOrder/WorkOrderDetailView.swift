import SwiftUI
import UIKit

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
    @State private var sharePayload: SharePayload?

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

                Button {
                    sharePayload = SharePayload(text: shareText(for: order))
                } label: {
                    Label("Share", systemImage: "square.and.arrow.up")
                        .frame(maxWidth: .infinity)
                }
            }
        }
        .navigationTitle(order.workOrderNumber)
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $pickerMode) { mode in
            MediaPicker(mode: mode) { media in
                selectedMedia.append(media)
            }
        }
        .sheet(item: $sharePayload) { payload in
            ActivityView(activityItems: [payload.text])
        }
        .onAppear {
            Task {
                if app.assignees.isEmpty {
                    await app.loadAssignees()
                }
            }
        }
    }

    private func shareText(for order: WorkOrder) -> String {
        [
            "Work Order \(order.workOrderNumber)",
            "Created: \(formatWorkOrderDate(order.createdAt))",
            "Store: \(storeNumber)",
            "Location: \(location)",
            "Status: \((WorkOrderStatus(rawValue: status) ?? order.statusValue).label)",
            "Priority: \(priority)",
            "Title: \(title)",
            "Description: \(description)",
            "Requested by: \(requestedBy)",
            assignedTo.trimmed.isEmpty ? nil : "Assigned to: \(assignedTo)",
            statusNote.trimmed.isEmpty ? nil : "Status note: \(statusNote)"
        ]
        .compactMap { $0 }
        .joined(separator: "\n")
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

struct SharePayload: Identifiable {
    let id = UUID()
    let text: String
}

struct ActivityView: UIViewControllerRepresentable {
    let activityItems: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {
    }
}
