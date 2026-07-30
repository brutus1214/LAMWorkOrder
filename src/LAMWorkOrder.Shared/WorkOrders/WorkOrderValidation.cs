namespace LAMWorkOrder.Shared.WorkOrders;

public static class WorkOrderValidation
{
    public static IReadOnlyList<ValidationIssue> Validate(CreateWorkOrderRequest request)
    {
        var issues = new List<ValidationIssue>();

        AddRequired(issues, nameof(request.Title), request.Title, 120);
        AddRequired(issues, nameof(request.Description), request.Description, 2_000);
        AddRequired(issues, nameof(request.RequestedBy), request.RequestedBy, 120);
        AddRequired(issues, nameof(request.Location), request.Location, 160);

        if (!Enum.IsDefined(request.Priority))
        {
            issues.Add(new ValidationIssue(nameof(request.Priority), "Priority is not valid."));
        }

        if (request.DueAt is not null && request.DueAt.Value < DateTimeOffset.UtcNow.AddMinutes(-1))
        {
            issues.Add(new ValidationIssue(nameof(request.DueAt), "Due date cannot be in the past."));
        }

        if (request.AssignedTo is { Length: > 120 })
        {
            issues.Add(new ValidationIssue(nameof(request.AssignedTo), "Assigned to must be 120 characters or less."));
        }

        return issues;
    }

    private static void AddRequired(
        ICollection<ValidationIssue> issues,
        string field,
        string value,
        int maxLength)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            issues.Add(new ValidationIssue(field, $"{field} is required."));
            return;
        }

        if (value.Length > maxLength)
        {
            issues.Add(new ValidationIssue(field, $"{field} must be {maxLength} characters or less."));
        }
    }
}
