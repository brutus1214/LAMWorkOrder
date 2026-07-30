namespace LAMWorkOrder.Shared.WorkOrders;

public static class WorkOrderRoutes
{
    public const string WorkOrders = "/api/work-orders";

    public static string WorkOrder(Guid id)
    {
        return $"{WorkOrders}/{id:D}";
    }

    public static string Status(Guid id)
    {
        return $"{WorkOrder(id)}/status";
    }
}
