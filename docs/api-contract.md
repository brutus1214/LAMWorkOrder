# API Contract

The Version 1 API is intentionally small and centered on work-order queue operations.

## Health

`GET /health`

Returns service status and current server time.

## Work Orders

`GET /api/work-orders`

Optional query parameters:

- `status`: `New`, `Scheduled`, `InProgress`, `Blocked`, `Completed`, or `Cancelled`
- `priority`: `Low`, `Normal`, `High`, or `Emergency`
- `search`: title, location, requester, assignee, or work-order number text

`GET /api/work-orders/{id}`

Returns one work order by identifier.

`POST /api/work-orders`

Creates a work order.

```json
{
  "title": "Replace line filter",
  "description": "Filter housing is leaking near bay 4.",
  "requestedBy": "A. Rivera",
  "location": "Line 2 / Bay 4",
  "priority": "High",
  "assignedTo": "Maintenance",
  "dueAt": "2026-08-03T18:00:00Z"
}
```

`PATCH /api/work-orders/{id}/status`

Updates the workflow status.

```json
{
  "status": "InProgress",
  "note": "Technician dispatched."
}
```
