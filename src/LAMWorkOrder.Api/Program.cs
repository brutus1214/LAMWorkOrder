using LAMWorkOrder.Api.Data;
using LAMWorkOrder.Api.Endpoints;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddProblemDetails();
builder.Services.AddSingleton(TimeProvider.System);
builder.Services.AddSingleton<IWorkOrderStore, InMemoryWorkOrderStore>();
builder.Services.AddCors(options =>
{
    options.AddPolicy("ClientApps", policy =>
    {
        policy
            .WithOrigins("http://localhost:5081", "https://localhost:7081")
            .AllowAnyHeader()
            .AllowAnyMethod();
    });
});

var app = builder.Build();

app.UseExceptionHandler();
app.UseCors("ClientApps");

app.MapGet("/", () => Results.Redirect("/health"));
app.MapGet("/health", () => Results.Ok(new
{
    Service = "LAMWorkOrder.Api",
    Status = "Healthy",
    CheckedAt = DateTimeOffset.UtcNow
}));

app.MapWorkOrderEndpoints();

app.Run();

public partial class Program;
