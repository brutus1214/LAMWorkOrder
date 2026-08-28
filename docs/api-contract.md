# API Contract

Interactive OpenAPI is at `/docs`.

- `GET /health`
- `GET /api/work-orders?status=&priority=&search=`
- `GET /api/work-orders/{id}`
- `GET /api/technicians`
- `GET /api/assignees` (active Employees, Security, Managers, and Technicians)
- `GET /api/work-order-notification-recipients?storeNumber=`
- `POST /api/work-orders`
- `PATCH /api/work-orders/{id}/status`
- `PUT /api/work-orders/{id}` (Admin all stores; Manager same store; Employee/Security when creator or assignee; Technician when assignee)
- `DELETE /api/work-orders/{id}` (`jc` only)
- `POST /api/work-orders/{id}/attachments` (same role scope as work-order updates; requesters can attach to their own submissions)
- `GET /api/attachments/{id}/content`
- `DELETE /api/attachments/{id}` (Admin or Manager)

Authentication uses `Authorization: Bearer <token>` for all `/api` resources except login:

- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/profile`
- `PATCH /api/profile`

Roles are `Requester`, `Employee`, `Security`, `Technician`, `Manager`, and `Admin`. Fresh installations seed one account per role with the temporary password `ChangeMe123!`; deployments should change these credentials before use. Existing SQLite work-order records are retained by additive startup migrations.

Statuses: `New`, `Scheduled`, `InProgress`, `Blocked`, `Completed`, `Cancelled`.
Priorities: `Low`, `Normal`, `High`, `Emergency`. JSON uses camelCase.
