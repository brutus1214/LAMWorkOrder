import SwiftUI

struct WorkOrderHomeView: View {
    @EnvironmentObject private var app: AppState

    @State private var search = ""
    @State private var filter: WorkOrderFilter = .new
    @State private var showingCreate = false
    @State private var showingProfile = false
    @State private var showingUsers = false

    private var visibleOrders: [WorkOrder] {
        visibleWorkOrders(app.orders, filter: filter, currentUser: app.user)
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                header
                filterBar
                orderList
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Work orders")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        showingCreate = true
                    } label: {
                        Label("New Work Order", systemImage: "plus")
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        if app.canManageUsers {
                            Button {
                                showingUsers = true
                            } label: {
                                Label("Manage Users", systemImage: "person.2")
                            }
                        }
                        Button {
                            showingProfile = true
                        } label: {
                            Label("Profile", systemImage: "person.crop.circle")
                        }
                        Button(role: .destructive) {
                            app.logout()
                        } label: {
                            Label("Logout", systemImage: "rectangle.portrait.and.arrow.right")
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                }
            }
            .refreshable {
                await app.refresh(search: search)
                await app.loadAssignees()
            }
            .sheet(isPresented: $showingCreate) {
                NewWorkOrderView()
            }
            .sheet(isPresented: $showingProfile) {
                ProfileView()
            }
            .sheet(isPresented: $showingUsers) {
                ManageUsersView()
            }
            .task {
                if app.orders.isEmpty {
                    await app.refresh()
                }
                if app.assignees.isEmpty {
                    await app.loadAssignees()
                }
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let user = app.user {
                VStack(alignment: .leading, spacing: 2) {
                    Text(user.displayName)
                        .font(.headline)
                    Text("\(user.role) - \(user.storeLabel)")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }

            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(.secondary)
                TextField("Search number, title, location", text: $search)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .onSubmit {
                        Task { await app.refresh(search: search) }
                    }
                Button {
                    Task {
                        await app.refresh(search: search)
                        await app.loadAssignees()
                    }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .buttonStyle(.borderless)
            }
            .padding(12)
            .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 8))
        }
        .padding([.horizontal, .top], 16)
        .padding(.bottom, 10)
    }

    private var filterBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(WorkOrderFilter.allCases) { item in
                    Button {
                        filter = item
                    } label: {
                        Text(item.rawValue)
                            .font(.subheadline.weight(.medium))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .foregroundStyle(filter == item ? Color.white : Color.primary)
                            .background(filter == item ? Color.accentColor : Color(.secondarySystemGroupedBackground), in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 10)
        }
    }

    private var orderList: some View {
        Group {
            if visibleOrders.isEmpty {
                ContentUnavailableView("No work orders", systemImage: "tray", description: Text("Pull down to refresh."))
            } else {
                List(visibleOrders) { order in
                    NavigationLink {
                        WorkOrderDetailView(order: order)
                    } label: {
                        WorkOrderRow(order: order)
                    }
                }
                .listStyle(.plain)
            }
        }
    }
}

struct WorkOrderRow: View {
    let order: WorkOrder

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(order.workOrderNumber)
                    .font(.headline)
                Spacer()
                StatusBadge(status: order.statusValue)
            }

            Text(order.title)
                .font(.body.weight(.medium))
                .lineLimit(2)

            HStack {
                Label(order.location, systemImage: "mappin.and.ellipse")
                Spacer()
                Text(order.priority)
                    .fontWeight(order.priority == Priority.emergency.rawValue ? .bold : .regular)
            }
            .font(.caption)
            .foregroundStyle(.secondary)

            HStack {
                Text("Store \(order.storeNumber)")
                Text(formatWorkOrderDate(order.createdAt))
                if let assigned = order.assignedTo, !assigned.isEmpty {
                    Text(assigned)
                        .lineLimit(1)
                }
            }
            .font(.caption2)
            .foregroundStyle(.secondary)
        }
        .padding(.vertical, 6)
    }
}

struct StatusBadge: View {
    let status: WorkOrderStatus

    var body: some View {
        Text(status.label)
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .foregroundStyle(color)
            .background(color.opacity(0.12), in: Capsule())
    }

    private var color: Color {
        switch status {
        case .new:
            return .blue
        case .scheduled, .inProgress:
            return .orange
        case .blocked:
            return .red
        case .completed:
            return .green
        case .cancelled:
            return .secondary
        }
    }
}
