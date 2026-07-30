# Version 1 Foundation

## Goal

Version 1 creates the baseline architecture for LAMWorkOrder across web, Windows, Android, and the shared backend. The focus is a working vertical slice around work-order intake and queue visibility.

## Approved Foundation

- Shared .NET models for work orders, priorities, statuses, validation, seed data, and route constants.
- ASP.NET Core API exposing the first work-order contract.
- Blazor web dashboard for desktop browser usage.
- .NET MAUI Blazor Hybrid shell for Windows and Android.
- A reusable typed API client shared by every front end.
- Local scripts and CI that validate the server/web foundation before mobile workloads are introduced.

## V1 Boundaries

In scope:

- Create a work order.
- List the active work-order queue.
- Filter by status and search by work-order text.
- Read work-order details.
- Update work-order status through the API.

Out of scope:

- Production persistence.
- Authentication and role-based authorization.
- Offline-first sync.
- Attachments and photos.
- Push notifications.
- Calendar, inventory, and external system integrations.

## Runtime Defaults

- API: `http://localhost:5080`
- Web: `http://localhost:5081`
- Android emulator API base URL: `http://10.0.2.2:5080`
- Windows API base URL: `http://localhost:5080`
