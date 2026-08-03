from datetime import datetime
from enum import StrEnum
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


def to_camel(value: str) -> str:
    head, *tail = value.split("_")
    return head + "".join(part.title() for part in tail)


class ApiModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, from_attributes=True)


class Priority(StrEnum):
    LOW = "Low"
    NORMAL = "Normal"
    HIGH = "High"
    EMERGENCY = "Emergency"


class Status(StrEnum):
    NEW = "New"
    SCHEDULED = "Scheduled"
    IN_PROGRESS = "InProgress"
    BLOCKED = "Blocked"
    COMPLETED = "Completed"
    CANCELLED = "Cancelled"


class WorkOrderCreate(ApiModel):
    title: str = Field(min_length=1, max_length=120)
    description: str = Field(min_length=1, max_length=2000)
    requested_by: str = Field(min_length=1, max_length=120)
    location: str = Field(min_length=1, max_length=160)
    priority: Priority = Priority.NORMAL
    assigned_to: str | None = Field(default=None, max_length=120)
    due_at: datetime | None = None


class StatusUpdate(ApiModel):
    status: Status
    note: str | None = Field(default=None, max_length=1000)


class WorkOrderRead(WorkOrderCreate):
    id: UUID
    work_order_number: str
    status: Status
    created_at: datetime
    updated_at: datetime
    status_note: str | None = None


class Health(ApiModel):
    status: str
    server_time: datetime
