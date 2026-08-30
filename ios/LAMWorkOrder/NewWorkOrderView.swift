import PhotosUI
import SwiftUI
import UniformTypeIdentifiers
import UIKit

struct NewWorkOrderView: View {
    @EnvironmentObject private var app: AppState
    @Environment(\.dismiss) private var dismiss

    @State private var storeNumber = 1
    @State private var title = ""
    @State private var description = ""
    @State private var location = ""
    @State private var priority = Priority.normal.rawValue
    @State private var assignedTo = ""
    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var selectedMedia: [SelectedMedia] = []
    @State private var showingCamera = false

    private var user: User? {
        app.user
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Work") {
                    if user?.role == Role.admin.rawValue {
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
                    TextField("Requested by", text: .constant(user?.displayName ?? ""))
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

                Section("Photos and Videos") {
                    if UIImagePickerController.isSourceTypeAvailable(.camera) {
                        Button {
                            showingCamera = true
                        } label: {
                            Label("Take Photo", systemImage: "camera")
                        }
                    }

                    PhotosPicker(
                        selection: $pickerItems,
                        maxSelectionCount: 8,
                        matching: .any(of: [.images, .videos])
                    ) {
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
                            .buttonStyle(.borderless)
                        }
                    }
                }
            }
            .navigationTitle("New Work Order")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Create") {
                        Task {
                            guard let user else { return }
                            let created = await app.createWorkOrder(
                                CreateWorkOrder(
                                    storeNumber: storeNumber,
                                    title: title.trimmed,
                                    description: description.trimmed,
                                    requestedBy: user.displayName,
                                    location: location.trimmed,
                                    priority: priority,
                                    assignedTo: assignedTo.trimmed.nilIfBlank,
                                    dueAt: nil
                                ),
                                media: selectedMedia
                            )
                            if created {
                                dismiss()
                            }
                        }
                    }
                    .disabled(!canCreate)
                }
            }
            .sheet(isPresented: $showingCamera) {
                CameraPhotoPicker { media in
                    selectedMedia.append(media)
                }
            }
            .task {
                if let user, storeNumber == 1 {
                    storeNumber = user.role == Role.admin.rawValue ? 1 : user.storeNumber
                }
                if app.assignees.isEmpty {
                    await app.loadAssignees()
                }
            }
            .onChange(of: pickerItems) { _, newItems in
                Task { selectedMedia.append(contentsOf: await loadMedia(from: newItems)) }
            }
        }
    }

    private var canCreate: Bool {
        title.trimmed.isEmpty == false
            && description.trimmed.isEmpty == false
            && location.trimmed.isEmpty == false
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
