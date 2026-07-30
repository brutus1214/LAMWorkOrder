using LAMWorkOrder.App.Services;
using LAMWorkOrder.Client.WorkOrders;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Maui.Hosting;

namespace LAMWorkOrder.App;

public static class MauiProgram
{
    public static MauiApp CreateMauiApp()
    {
        var builder = MauiApp.CreateBuilder();

        builder.UseMauiApp<App>();

        builder.Services.AddMauiBlazorWebView();
        builder.Services.AddSingleton(new HttpClient
        {
            BaseAddress = ClientApiSettings.DefaultApiBaseAddress
        });
        builder.Services.AddSingleton<WorkOrderApiClient>();

        return builder.Build();
    }
}
