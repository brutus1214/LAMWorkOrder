using LAMWorkOrder.Client.WorkOrders;
using LAMWorkOrder.Web;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddRazorComponents()
    .AddInteractiveServerComponents();

builder.Services.AddHttpClient<WorkOrderApiClient>((services, httpClient) =>
{
    var configuration = services.GetRequiredService<IConfiguration>();
    var apiBaseUrl = configuration["ApiBaseUrl"] ?? "http://localhost:5080";
    httpClient.BaseAddress = new Uri(EnsureTrailingSlash(apiBaseUrl));
});

var app = builder.Build();

app.UseStaticFiles();
app.UseAntiforgery();

app.MapRazorComponents<App>()
    .AddInteractiveServerRenderMode();

app.Run();

static string EnsureTrailingSlash(string value)
{
    return value.EndsWith("/", StringComparison.Ordinal) ? value : $"{value}/";
}
