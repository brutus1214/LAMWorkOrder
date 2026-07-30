namespace LAMWorkOrder.App.Services;

public static class ClientApiSettings
{
    public static Uri DefaultApiBaseAddress
    {
        get
        {
#if ANDROID
            return new Uri("http://10.0.2.2:5080/");
#else
            return new Uri("http://localhost:5080/");
#endif
        }
    }
}
