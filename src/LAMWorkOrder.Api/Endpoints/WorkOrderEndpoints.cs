using LAMWorkOrder.Api.Data;
using LAMWorkOrder.Shared.WorkOrders;

namespace LAMWorkOrder.Api.Endpoints;

public static class WorkOrderEndpoints
{
    public static IEndpointRouteBuilder MapWorkOrderEndpoints(this IEndpointRouteBuilder routes)
    {
        var group = routes.MapGroup(WorkOrderRoutes.WorkOrders).WithTags("Work Orders");

        group.MapGet("", (
            string? status,
            string? priority,
            string? search,
            IWorkOrderStore store) =>
        {
            if (!TryParse(status, out WorkOrderStatus? parsedStatus, out var statusError))
            {
                return Results.BadRequest(new { Error = statusError });
            }

            if (!TryParse(priority, out WorkOrderPriority? parsedPriority, out var priorityError))
            {
                return Results.BadRequest(new { Error = priorityError });
            }

            var workOrders = store
                .List(parsedStatus, parsedPriority, search)
                .Select(workOrder => workOrder.ToSummary());

            return Results.Ok(workOrders);
        });

        group.MapGet("{id:guid}", (Guid id, IWorkOrderStore store) =>
        {
            var workOrder = store.Get(id);
            return workOrder is null
                ? Results.NotFound()
                : Results.Ok(workOrder.ToDetail());
        });

        group.MapPost("", (CreateWorkOrderRequest request, IWorkOrderStore store) =>
        {
            var issues = WorkOrderValidation.Validate(request);
            if (issues.Count > 0)
            {
                return Results.ValidationProblem(ToValidationDictionary(issues));
            }

            var workOrder = store.Create(request).ToDetail();
            return Results.Created(WorkOrderRoutes.WorkOrder(workOrder.Id), workOrder);
        });

        group.MapPatch("{id:guid}/status", (
            Guid id,
            UpdateWorkOrderStatusRequest request,
            IWorkOrderStore store) =>
        {
            if (!Enum.IsDefined(request.Status))
            {
                return Results.ValidationProblem(new Dictionary<string, string[]>
                {
                    [nameof(request.Status)] = new[] { "Status is not valid." }
                });
            }

            var workOrder = store.UpdateStatus(id, request);
            return workOrder is null
                ? Results.NotFound()
                : Results.Ok(workOrder.ToDetail());
        });

        return routes;
    }

    private static bool TryParse<TEnum>(string? value, out TEnum? parsed, out string? error)
        where TEnum : struct, Enum
    {
        parsed = null;
        error = null;

        if (string.IsNullOrWhiteSpace(value))
        {
            return true;
        }

        if (Enum.TryParse<TEnum>(value, ignoreCase: true, out var result) && Enum.IsDefined(result))
        {
            parsed = result;
            return true;
        }

        error = $"{typeof(TEnum).Name} value '{value}' is not valid.";
        return false;
    }

    private static Dictionary<string, string[]> ToValidationDictionary(IEnumerable<ValidationIssue> issues)
    {
        return issues
            .GroupBy(issue => issue.Field)
            .ToDictionary(
                group => group.Key,
                group => group.Select(issue => issue.Message).ToArray());
    }
}
