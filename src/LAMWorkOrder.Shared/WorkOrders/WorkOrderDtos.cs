namespace LAMWorkOrder.Shared.WorkOrders;

public sealed record WorkOrderSummary(
    Guid Id,
    string WorkOrderNumber,
    string Title,
    string RequestedBy,
    string Location,
    WorkOrderPriority Priority,
    WorkOrderStatus Status,
    DateTimeOffset CreatedAt,
    DateTimeOffset UpdatedAt,
    DateTimeOffset? DueAt,
    string? AssignedTo);

public sealed record WorkOrderDetail(
    Guid Id,
    string WorkOrderNumber,
    string Title,
    string Description,
    string RequestedBy,
    string Location,
    WorkOrderPriority Priority,
    WorkOrderStatus Status,
    DateTimeOffset CreatedAt,
    DateTimeOffset UpdatedAt,
    DateTimeOffset? DueAt,
    string? AssignedTo);

public sealed record CreateWorkOrderRequest(
    string Title,
    string Description,
    string RequestedBy,
    string Location,
    WorkOrderPriority Priority,
    string? AssignedTo,
    DateTimeOffset? DueAt);

public sealed record UpdateWorkOrderStatusRequest(
    WorkOrderStatus Status,
    string? Note);

public sealed record ValidationIssue(
    string Field,
    string Message);
