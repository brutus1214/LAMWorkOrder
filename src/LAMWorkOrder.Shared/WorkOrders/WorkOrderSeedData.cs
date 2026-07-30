namespace LAMWorkOrder.Shared.WorkOrders;

public static class WorkOrderSeedData
{
    public static IReadOnlyList<WorkOrder> Create(DateTimeOffset now)
    {
        return new List<WorkOrder>
        {
            new(
                Guid.Parse("44d8288c-3b5c-4df8-a14d-d5b0ce672964"),
                $"WO-{now:yyyy}-1001",
                "Inspect packaging line conveyor",
                "Operators reported intermittent belt drift on the primary packaging conveyor.",
                "Jordan Lee",
                "Packaging Line 1",
                WorkOrderPriority.High,
                WorkOrderStatus.New,
                now.AddHours(-5),
                now.AddHours(-5),
                now.AddDays(1),
                "Maintenance"),
            new(
                Guid.Parse("ef170f5e-2efd-4c02-b226-acfe40ebf5ff"),
                $"WO-{now:yyyy}-1002",
                "Replace inspection station light",
                "The inspection bench light is flickering and reducing operator visibility.",
                "Maria Chen",
                "Quality Lab",
                WorkOrderPriority.Normal,
                WorkOrderStatus.Scheduled,
                now.AddDays(-1),
                now.AddHours(-3),
                now.AddDays(2),
                "Facilities"),
            new(
                Guid.Parse("d583448c-a291-4892-968e-dce851c6d27e"),
                $"WO-{now:yyyy}-1003",
                "Clear blocked drain near washdown bay",
                "Water is pooling after sanitation cycles and needs immediate attention.",
                "Andre Patel",
                "Washdown Bay",
                WorkOrderPriority.Emergency,
                WorkOrderStatus.InProgress,
                now.AddHours(-12),
                now.AddHours(-1),
                now.AddHours(4),
                "Facilities")
        };
    }
}
