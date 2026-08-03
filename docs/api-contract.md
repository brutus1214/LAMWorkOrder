# API Contract

Interactive OpenAPI is at `/docs`.

- `GET /health`
- `GET /api/work-orders?status=&priority=&search=`
- `GET /api/work-orders/{id}`
- `POST /api/work-orders`
- `PATCH /api/work-orders/{id}/status`

Statuses: `New`, `Scheduled`, `InProgress`, `Blocked`, `Completed`, `Cancelled`.
Priorities: `Low`, `Normal`, `High`, `Emergency`. JSON uses camelCase.
