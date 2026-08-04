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


class Role(StrEnum):
    ADMIN = "Admin"
    MANAGER = "Manager"
    TECHNICIAN = "Technician"
    REQUESTER = "Requester"


class WorkOrderCreate(ApiModel):
    store_number: int = Field(default=1, ge=1, le=9999)
    title: str = Field(min_length=1, max_length=120)
    description: str = Field(min_length=1, max_length=2000)
    requested_by: str = Field(min_length=1, max_length=120)
    location: str = Field(min_length=1, max_length=160)
    priority: Priority = Priority.NORMAL
    assigned_to: str | None = Field(default=None, max_length=120)
    due_at: datetime | None = None


class WorkOrderUpdate(WorkOrderCreate):
    status: Status
    status_note: str | None = Field(default=None, max_length=1000)


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
    attachments: list["AttachmentRead"] = []


class LoginRequest(ApiModel):
    username: str = Field(min_length=1, max_length=80)
    password: str = Field(min_length=1, max_length=200)


class RegistrationRequest(ApiModel):
    username: str = Field(min_length=2, max_length=80, pattern=r"^[A-Za-z0-9_.-]+$")
    password: str = Field(min_length=8, max_length=200)
    display_name: str = Field(min_length=1, max_length=120)
    store_number: int = Field(ge=1, le=9999)
    email: str = Field(min_length=3, max_length=254)
    phone_number: str = Field(min_length=7, max_length=30)


class ProfileUpdate(ApiModel):
    display_name: str = Field(min_length=1, max_length=120)
    email: str | None = Field(default=None, max_length=254)


class UserRead(ApiModel):
    id: UUID
    username: str
    display_name: str
    email: str | None
    store_number: int = 1
    phone_number: str | None = None
    role: Role
    is_active: bool = True


class UserAdminUpdate(ApiModel):
    display_name: str = Field(min_length=1, max_length=120)
    store_number: int = Field(ge=1, le=99)
    role: Role
    email: str | None = Field(default=None, max_length=254)
    phone_number: str | None = Field(default=None, max_length=30)
    is_active: bool = True


class PasswordReset(ApiModel):
    new_password: str = Field(min_length=8, max_length=200)


class LoginResponse(ApiModel):
    token: str
    user: UserRead


class AttachmentRead(ApiModel):
    id: UUID
    original_name: str
    content_type: str
    size_bytes: int
    created_at: datetime
    url: str


class Health(ApiModel):
    status: str
    server_time: datetime
