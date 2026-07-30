using LAMWorkOrder.Shared.WorkOrders;

namespace LAMWorkOrder.Api.Data;

public sealed class InMemoryWorkOrderStore : IWorkOrderStore
{
    private readonly object _gate = new();
    private readonly TimeProvider _timeProvider;
    private readonly List<WorkOrder> _workOrders;
    private int _nextSequence = 1003;

    public InMemoryWorkOrderStore(TimeProvider timeProvider)
    {
        _timeProvider = timeProvider;
        _workOrders = WorkOrderSeedData.Create(_timeProvider.GetUtcNow()).ToList();
    }

    public IReadOnlyList<WorkOrder> List(
        WorkOrderStatus? status = null,
        WorkOrderPriority? priority = null,
        string? search = null)
    {
        lock (_gate)
        {
            IEnumerable<WorkOrder> query = _workOrders;

            if (status is not null)
            {
                query = query.Where(workOrder => workOrder.Status == status.Value);
            }

            if (priority is not null)
            {
                query = query.Where(workOrder => workOrder.Priority == priority.Value);
            }

            if (!string.IsNullOrWhiteSpace(search))
            {
                query = query.Where(workOrder => MatchesSearch(workOrder, search));
            }

            return query
                .OrderBy(workOrder => workOrder.Status is WorkOrderStatus.Completed or WorkOrderStatus.Cancelled)
                .ThenByDescending(workOrder => workOrder.Priority)
                .ThenBy(workOrder => workOrder.DueAt ?? DateTimeOffset.MaxValue)
                .ThenByDescending(workOrder => workOrder.UpdatedAt)
                .ToList();
        }
    }

    public WorkOrder? Get(Guid id)
    {
        lock (_gate)
        {
            return _workOrders.FirstOrDefault(workOrder => workOrder.Id == id);
        }
    }

    public WorkOrder Create(CreateWorkOrderRequest request)
    {
        var now = _timeProvider.GetUtcNow();

        lock (_gate)
        {
            _nextSequence++;

            var workOrder = new WorkOrder(
                Guid.NewGuid(),
                $"WO-{now:yyyy}-{_nextSequence:0000}",
                request.Title.Trim(),
                request.Description.Trim(),
                request.RequestedBy.Trim(),
                request.Location.Trim(),
                request.Priority,
                WorkOrderStatus.New,
                now,
                now,
                request.DueAt,
                string.IsNullOrWhiteSpace(request.AssignedTo) ? null : request.AssignedTo.Trim());

            _workOrders.Add(workOrder);
            return workOrder;
        }
    }

    public WorkOrder? UpdateStatus(Guid id, UpdateWorkOrderStatusRequest request)
    {
        if (!Enum.IsDefined(request.Status))
        {
            return null;
        }

        lock (_gate)
        {
            var index = _workOrders.FindIndex(workOrder => workOrder.Id == id);
            if (index < 0)
            {
                return null;
            }

            var existing = _workOrders[index];
            var updated = existing with
            {
                Status = request.Status,
                UpdatedAt = _timeProvider.GetUtcNow()
            };

            _workOrders[index] = updated;
            return updated;
        }
    }

    private static bool MatchesSearch(WorkOrder workOrder, string search)
    {
        return Contains(workOrder.WorkOrderNumber, search)
            || Contains(workOrder.Title, search)
            || Contains(workOrder.Description, search)
            || Contains(workOrder.RequestedBy, search)
            || Contains(workOrder.Location, search)
            || Contains(workOrder.AssignedTo, search);
    }

    private static bool Contains(string? value, string search)
    {
        return value?.Contains(search, StringComparison.OrdinalIgnoreCase) == true;
    }
}
