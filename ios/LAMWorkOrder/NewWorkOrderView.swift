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
    @State private var assignmentSendRequest: AssignmentSendRequest?
    @State private var sendPayload: WorkOrderSendPayload?
    @State private var preparingSend = false
    @State private var closeAfterSendSheet = false

    private var user: User? {
        app.user
    }

    private var contacts: [User] {
        var seenIDs: Set<String> = []
        return ([app.user].compactMap { $0 } + app.users + app.assignees).filter { user in
            seenIDs.insert(user.id).inserted
        }
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
                        createWorkOrder()
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
        .sheet(item: $sendPayload, onDismiss: handleSendSheetDismiss) { payload in
            sendSheet(for: payload)
        }
        .confirmationDialog(
            "Send work order?",
            isPresented: Binding(
                get: { assignmentSendRequest != nil },
                set: { isPresented in
                    if isPresented == false {
                        assignmentSendRequest = nil
                    }
                }
            ),
            titleVisibility: .visible
        ) {
            if let request = assignmentSendRequest {
                Button("Text") {
                    if let phone = assignmentPhone(for: request) {
                        startAssignmentSend(request, destination: .text(phone))
                    }
                }
                .disabled(assignmentPhone(for: request) == nil || canTextWorkOrder() == false)

                Button("Email") {
                    if let email = assignmentEmail(for: request) {
                        startAssignmentSend(request, destination: .email([email]))
                    }
                }
                .disabled(assignmentEmail(for: request) == nil || canEmailWorkOrder() == false)
            }

            Button("Skip", role: .cancel) {
                finishAssignmentSendPrompt()
            }
        } message: {
            if let request = assignmentSendRequest {
                Text(workOrderAssignmentPromptMessage(for: request, contacts: contacts))
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
                if app.user?.role == Role.admin.rawValue || app.user?.role == Role.manager.rawValue {
                    if app.users.isEmpty {
                        await app.loadUsers()
                    }
                }
            }
        }
    }

    private var canCreate: Bool {
        title.trimmed.isEmpty == false
            && description.trimmed.isEmpty == false
            && location.trimmed.isEmpty == false
    }

    private func createWorkOrder() {
        guard let user = user else { return }

        let selectedAssignee = assignedTo.trimmed
        let request = CreateWorkOrder(
            storeNumber: storeNumber,
            title: title.trimmed,
            description: description.trimmed,
            requestedBy: user.displayName,
            location: location.trimmed,
            priority: priority,
            assignedTo: selectedAssignee.nilIfBlank,
            dueAt: nil
        )

        Task {
            guard let created = await app.createWorkOrder(request, media: selectedMedia) else { return }
            if selectedAssignee.isEmpty {
                dismiss()
            } else {
                assignmentSendRequest = AssignmentSendRequest(
                    order: created,
                    title: request.title,
                    description: request.description,
                    requestedBy: request.requestedBy,
                    location: request.location,
                    priority: request.priority,
                    assignedTo: selectedAssignee,
                    status: created.status,
                    statusNote: created.statusNote
                )
            }
        }
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

    private func startAssignmentSend(
        _ request: AssignmentSendRequest,
        destination: WorkOrderSendDestination
    ) {
        assignmentSendRequest = nil
        startSend(
            destination,
            subject: workOrderAssignmentSubject(for: request),
            message: workOrderAssignmentMessage(for: request),
            attachments: request.order.attachments
        )
    }

    private func assignmentPhone(for request: AssignmentSendRequest) -> String? {
        textNumber(for: findWorkOrderContact(in: contacts, name: request.assignedTo))
    }

    private func assignmentEmail(for request: AssignmentSendRequest) -> String? {
        emailAddress(for: findWorkOrderContact(in: contacts, name: request.assignedTo))
    }

    private func startSend(
        _ destination: WorkOrderSendDestination,
        subject: String,
        message: String,
        attachments: [Attachment]
    ) {
        guard preparingSend == false else { return }

        preparingSend = true
        Task { @MainActor in
            defer { preparingSend = false }
            if let payload = await prepareWorkOrderSendPayload(
                app: app,
                destination: destination,
                subject: subject,
                message: message,
                attachments: attachments
            ) {
                closeAfterSendSheet = true
                sendPayload = payload
            }
        }
    }

    private func finishAssignmentSendPrompt() {
        assignmentSendRequest = nil
        dismiss()
    }

    private func handleSendSheetDismiss() {
        if closeAfterSendSheet {
            closeAfterSendSheet = false
            dismiss()
        }
    }
}
