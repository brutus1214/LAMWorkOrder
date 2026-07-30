namespace LAMWorkOrder.Client.WorkOrders;

public sealed record ClientResult<T>(
    bool IsSuccess,
    T? Value,
    string? Error)
{
    public static ClientResult<T> Success(T value)
    {
        return new ClientResult<T>(true, value, null);
    }

    public static ClientResult<T> Failure(string error)
    {
        return new ClientResult<T>(false, default, error);
    }
}
