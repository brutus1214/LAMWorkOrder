from datetime import UTC, datetime, timedelta

ALL_WORK_ORDER_MAX_AGE = timedelta(days=30)

GROUPS = {
    "New": {"New"},
    "Open/In Progress": {"Scheduled", "InProgress", "Blocked"},
    "Completed": {"Completed"},
    "Closed/Cancelled": {"Cancelled"},
}


def filter_work_orders(
    orders: list[dict],
    selected_filter: str,
    now: datetime | None = None,
) -> list[dict]:
    now = now or datetime.now(UTC)
    selected_statuses = GROUPS.get(selected_filter)
    if selected_statuses is not None:
        return [order for order in orders if order["status"] in selected_statuses]
    return [order for order in orders if _is_recent_work_order(order, now)]


def _is_recent_work_order(order: dict, now: datetime) -> bool:
    created = _parse_created_at(order.get("createdAt", ""))
    if created is None:
        return True
    return created > now - ALL_WORK_ORDER_MAX_AGE


def format_created_date(value: str) -> str:
    created = _parse_created_at(value)
    if created is None:
        return value.split("T")[0]
    return f"{created.month}/{created.day}/{created:%y}"


def _parse_created_at(value: str) -> datetime | None:
    if not value:
        return None
    try:
        created = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None
    if created.tzinfo is None:
        return created.replace(tzinfo=UTC)
    return created.astimezone(UTC)
