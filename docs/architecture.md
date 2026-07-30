# Architecture

LAMWorkOrder starts as an API-first .NET solution.

## Layers

- `LAMWorkOrder.Shared` owns the durable cross-platform contract: DTOs, enums, validation, and route constants.
- `LAMWorkOrder.Client` wraps HTTP calls so Web, Windows, and Android use the same backend access code.
- `LAMWorkOrder.Api` owns endpoint routing and storage implementation.
- `LAMWorkOrder.Web` owns the browser dashboard.
- `LAMWorkOrder.App` owns the native Windows/Android shell through MAUI Blazor Hybrid.

## Dependency Direction

```text
Web/App -> Client -> Shared
Api -----> Shared
```

The API does not depend on UI projects. UI projects do not depend on API internals.

## Persistence Path

The V1 API uses an in-memory store so the front ends can be built against a stable contract immediately. The next persistence step should replace `InMemoryWorkOrderStore` with a database-backed implementation behind the same `IWorkOrderStore` interface.
