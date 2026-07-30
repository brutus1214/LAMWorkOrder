namespace LAMWorkOrder.Shared.WorkOrders;

public enum WorkOrderStatus
{
    New = 0,
    Scheduled = 1,
    InProgress = 2,
    Blocked = 3,
    Completed = 4,
    Cancelled = 5
}
