# LAMWorkOrder

Version 1 establishes a cross-platform work-order foundation with shared contracts, a backend API, a web dashboard, and a Windows/Android client shell.

## Projects

| Project | Purpose |
| --- | --- |
| `src/LAMWorkOrder.Shared` | Work-order domain models, DTOs, routes, validation, and seed data. |
| `src/LAMWorkOrder.Client` | Reusable typed API client for all front ends. |
| `src/LAMWorkOrder.Api` | ASP.NET Core minimal API with an in-memory work-order store. |
| `src/LAMWorkOrder.Web` | Blazor web dashboard for queue review and work-order intake. |
| `src/LAMWorkOrder.App` | .NET MAUI Blazor Hybrid shell targeting Windows and Android. |

## Prerequisites

- .NET 8 SDK
- For Windows and Android client builds: .NET MAUI workload

```powershell
dotnet workload install maui
```

## Run Locally

Start the API:

```powershell
dotnet run --project src/LAMWorkOrder.Api --urls http://localhost:5080
```

Start the web client in another terminal:

```powershell
dotnet run --project src/LAMWorkOrder.Web --urls http://localhost:5081
```

Then open `http://localhost:5081`.

For MAUI, run one target at a time:

```powershell
dotnet build src/LAMWorkOrder.App -f net8.0-windows10.0.19041.0
dotnet build src/LAMWorkOrder.App -f net8.0-android
```

## Version 1 Scope

- Shared work-order records, statuses, priorities, routes, and validation.
- Backend endpoints for list, detail, create, and status update.
- Web UI for operational queue review and intake.
- Windows/Android shell using the same API client and shared contracts.
- CI for the server-side and web projects.

Persistence, authentication, authorization, offline sync, push notifications, and production deployment wiring are intentionally deferred.
