import PhotosUI
import SwiftUI
import UniformTypeIdentifiers
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
    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var selectedMedia: [SelectedMedia] = []

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
                        .foregroundStyle(.secondary)
                }
            }

            Section("Work") {
                if app.user?.role == Role.admin.rawValue {
                    Picker("Store", selection: $storeNumber) {
                        Text("All Stores").tag(99)
                        ForEach(1...9, id: \.self) { store in
                            Text("LA Mart \(store)").tag(store)
                        }
                    }
                } else {
                    LabeledContent("Store", value: "\(storeNumber)")
                }

                TextField("Title", text: $title)
                TextField("Description", text: $description, axis: .vertical)
                    .lineLimit(4...8)
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

            Section("Status") {
                Picker("Status", selection: $status) {
                    ForEach(WorkOrderStatus.allCases) { item in
                        Text(item.label).tag(item.rawValue)
                    }
                }
                TextField("Status note", text: $statusNote, axis: .vertical)
                    .lineLimit(2...5)
            }

            Section("Attachments") {
                if order.attachments.isEmpty {
                    Text("No attachments")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(order.attachments) { attachment in
                        AttachmentPreviewRow(attachment: attachment)
                    }
                }

                PhotosPicker(
                    selection: $pickerItems,
                    maxSelectionCount: 8,
                    matching: .any(of: [.images, .videos])
                ) {
                    Label("Choose Photos or Videos", systemImage: "photo.on.rectangle")
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
                        .buttonStyle(.borderless)
                    }
                }

                Button {
                    Task {
                        if await app.uploadAttachments(workOrderID: order.id, media: selectedMedia) {
                            selectedMedia = []
                            pickerItems = []
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

                ShareLink(
                    item: shareText(for: order),
                    subject: Text("Work Order \(order.workOrderNumber)")
                ) {
                    Label("Share", systemImage: "square.and.arrow.up")
                        .frame(maxWidth: .infinity)
                }
            }
        }
        .navigationTitle(order.workOrderNumber)
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if app.assignees.isEmpty {
                await app.loadAssignees()
            }
        }
        .onChange(of: pickerItems) { _, newItems in
            Task { selectedMedia.append(contentsOf: await loadMedia(from: newItems)) }
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

    private func loadMedia(from items: [PhotosPickerItem]) async -> [SelectedMedia] {
        var media: [SelectedMedia] = []
        for item in items {
            guard let data = try? await item.loadTransferable(type: Data.self) else {
                continue
            }
            let type = item.supportedContentTypes.first
            let ext = type?.preferredFilenameExtension ?? "bin"
            let mimeType = type?.preferredMIMEType ?? "application/octet-stream"
            let prefix = mimeType.hasPrefix("video/") ? "video" : "photo"
            media.append(
                SelectedMedia(
                    filename: "\(prefix)_\(Int(Date().timeIntervalSince1970)).\(ext)",
                    mimeType: mimeType,
                    data: data
                )
            )
        }
        return media
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
                    .foregroundStyle(.secondary)
            }

            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .frame(maxHeight: 220)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            } else if attachment.contentType.hasPrefix("image/") && !loadFailed {
                ProgressView()
                    .task(id: attachment.id) {
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
