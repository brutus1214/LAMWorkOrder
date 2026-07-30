namespace LAMWorkOrder.Shared.WorkOrders;

public sealed record WorkOrder(
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
    string? AssignedTo)
{
    public WorkOrderSummary ToSummary()
    {
        return new WorkOrderSummary(
            Id,
            WorkOrderNumber,
            Title,
            RequestedBy,
            Location,
            Priority,
            Status,
            CreatedAt,
            UpdatedAt,
            DueAt,
            AssignedTo);
    }

    public WorkOrderDetail ToDetail()
    {
        return new WorkOrderDetail(
            Id,
            WorkOrderNumber,
            Title,
            Description,
            RequestedBy,
            Location,
            Priority,
            Status,
            CreatedAt,
            UpdatedAt,
            DueAt,
            AssignedTo);
    }
}
