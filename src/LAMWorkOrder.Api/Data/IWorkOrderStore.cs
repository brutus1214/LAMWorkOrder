using LAMWorkOrder.Shared.WorkOrders;

namespace LAMWorkOrder.Api.Data;

public interface IWorkOrderStore
{
    IReadOnlyList<WorkOrder> List(
        WorkOrderStatus? status = null,
        WorkOrderPriority? priority = null,
        string? search = null);

    WorkOrder? Get(Guid id);

    WorkOrder Create(CreateWorkOrderRequest request);

    WorkOrder? UpdateStatus(Guid id, UpdateWorkOrderStatusRequest request);
}
