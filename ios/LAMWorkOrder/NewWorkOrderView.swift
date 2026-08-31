import SwiftUI
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
    @State private var selectedMedia: [SelectedMedia] = []
    @State private var pickerMode: MediaPickerMode?

    private var user: User? {
        app.user
    }

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Work")) {
                    if user?.role == Role.admin.rawValue {
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

                Section(header: Text("Photos and Videos")) {
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
                }
            }
            .navigationTitle("New Work Order")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Create") {
                        Task {
                            guard let user = user else { return }
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
        }
        .navigationViewStyle(StackNavigationViewStyle())
        .sheet(item: $pickerMode) { mode in
            MediaPicker(mode: mode) { media in
                selectedMedia.append(media)
            }
        }
        .onAppear {
            if let currentUser = user, storeNumber == 1 {
                storeNumber = currentUser.role == Role.admin.rawValue ? 1 : currentUser.storeNumber
            }
            Task {
                if app.assignees.isEmpty {
                    await app.loadAssignees()
                }
            }
        }
    }

    private var canCreate: Bool {
        title.trimmed.isEmpty == false
            && description.trimmed.isEmpty == false
            && location.trimmed.isEmpty == false
    }
}
