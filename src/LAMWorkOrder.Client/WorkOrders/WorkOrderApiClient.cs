using System.Net.Http.Json;
using LAMWorkOrder.Shared.WorkOrders;

namespace LAMWorkOrder.Client.WorkOrders;

public sealed class WorkOrderApiClient
{
    private readonly HttpClient _httpClient;

    public WorkOrderApiClient(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    public async Task<IReadOnlyList<WorkOrderSummary>> ListAsync(
        WorkOrderStatus? status = null,
        WorkOrderPriority? priority = null,
        string? search = null,
        CancellationToken cancellationToken = default)
    {
        var path = BuildListPath(status, priority, search);
        var workOrders = await _httpClient.GetFromJsonAsync<List<WorkOrderSummary>>(path, cancellationToken);
        return workOrders ?? Array.Empty<WorkOrderSummary>();
    }

    public Task<WorkOrderDetail?> GetAsync(Guid id, CancellationToken cancellationToken = default)
    {
        return _httpClient.GetFromJsonAsync<WorkOrderDetail>(WorkOrderRoutes.WorkOrder(id), cancellationToken);
    }

    public async Task<ClientResult<WorkOrderDetail>> CreateAsync(
        CreateWorkOrderRequest request,
        CancellationToken cancellationToken = default)
    {
        using var response = await _httpClient.PostAsJsonAsync(WorkOrderRoutes.WorkOrders, request, cancellationToken);
        return await ReadResultAsync(response, cancellationToken);
    }

    public async Task<ClientResult<WorkOrderDetail>> UpdateStatusAsync(
        Guid id,
        UpdateWorkOrderStatusRequest request,
        CancellationToken cancellationToken = default)
    {
        using var response = await _httpClient.PatchAsJsonAsync(WorkOrderRoutes.Status(id), request, cancellationToken);
        return await ReadResultAsync(response, cancellationToken);
    }

    private static string BuildListPath(WorkOrderStatus? status, WorkOrderPriority? priority, string? search)
    {
        var query = new List<string>();

        if (status is not null)
        {
            query.Add($"status={Uri.EscapeDataString(status.Value.ToString())}");
        }

        if (priority is not null)
        {
            query.Add($"priority={Uri.EscapeDataString(priority.Value.ToString())}");
        }

        if (!string.IsNullOrWhiteSpace(search))
        {
            query.Add($"search={Uri.EscapeDataString(search)}");
        }

        return query.Count == 0
            ? WorkOrderRoutes.WorkOrders
            : $"{WorkOrderRoutes.WorkOrders}?{string.Join("&", query)}";
    }

    private static async Task<ClientResult<WorkOrderDetail>> ReadResultAsync(
        HttpResponseMessage response,
        CancellationToken cancellationToken)
    {
        if (!response.IsSuccessStatusCode)
        {
            var error = await response.Content.ReadAsStringAsync(cancellationToken);
            return ClientResult<WorkOrderDetail>.Failure(
                string.IsNullOrWhiteSpace(error) ? response.ReasonPhrase ?? "Request failed." : error);
        }

        var workOrder = await response.Content.ReadFromJsonAsync<WorkOrderDetail>(cancellationToken);
        return workOrder is null
            ? ClientResult<WorkOrderDetail>.Failure("The API returned an empty response.")
            : ClientResult<WorkOrderDetail>.Success(workOrder);
    }
}
