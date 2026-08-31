from datetime import UTC, datetime
from pathlib import Path
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, File, HTTPException, Query, UploadFile
from fastapi.responses import FileResponse
from sqlalchemy import delete, or_, select
from sqlalchemy.orm import Session

from .auth import current_user, hash_password, issue_token, require_roles, verify_password
from .database import get_session
from .models import Attachment, SessionToken, User
from .repository import WorkOrderRepository
from .schemas import (
    AttachmentRead,
    Health,
    LoginRequest,
    LoginResponse,
    PasswordReset,
    Priority,
    ProfileUpdate,
    RegistrationRequest,
    Status,
    StatusUpdate,
    UserAdminUpdate,
    UserRead,
    WorkOrderCreate,
    WorkOrderRead,
    WorkOrderUpdate,
)

router = APIRouter()
UPLOADS = Path("uploads")
MAX_FILE_SIZE = 50 * 1024 * 1024
ALLOWED_TYPES = {
    "image/jpeg",
    "image/png",
    "image/webp",
    "video/mp4",
    "video/quicktime",
    "video/webm",
}
ASSIGNABLE_ROLES = ("Employee", "Manager", "Security", "Technician")
SPECIAL_ASSIGNEE_USERNAMES = ("jc",)
EMPLOYEE_SCOPED_ROLES = {"Employee", "Security"}


def repository(session: Session = Depends(get_session)) -> WorkOrderRepository:
    return WorkOrderRepository(session)


def find_order(work_order_id: UUID, repo: WorkOrderRepository):
    item = repo.get(str(work_order_id))
    if not item:
        raise HTTPException(status_code=404, detail="Work order not found")
    return item


def require_work_order_store(user: User, item, action: str = "modify") -> None:
    if user.role == "Admin":
        return
    if user.store_number != item.store_number:
        raise HTTPException(
            status_code=403,
            detail=f"Only an Administrator can {action} work orders for another store",
        )


def normalized_identity(value: str | None) -> str:
    return " ".join((value or "").strip().casefold().split())


def without_store_label(value: str) -> str:
    value = normalized_identity(value)
    marker = " - la mart "
    if marker in value:
        name, store = value.rsplit(marker, 1)
        if store.isdigit():
            return name
    return value.removesuffix(" - all stores")


def user_assignee_labels(user: User) -> set[str]:
    store_label = "All Stores" if user.store_number == 99 else f"LA Mart {user.store_number}"
    return {
        normalized_identity(user.display_name),
        normalized_identity(user.username),
        normalized_identity(f"{user.display_name} - {store_label}"),
    }


def work_order_assigned_to_user(user: User, item) -> bool:
    assigned_to = normalized_identity(item.assigned_to)
    if not assigned_to:
        return False
    return assigned_to in user_assignee_labels(user) or without_store_label(assigned_to) == normalized_identity(
        user.display_name
    )


def work_order_created_by_user(user: User, item) -> bool:
    if item.created_by_id == user.id:
        return True
    return item.created_by_id is None and normalized_identity(item.requested_by) == normalized_identity(
        user.display_name
    )


def can_update_work_order(user: User, item) -> bool:
    if user.role == "Admin":
        return True
    if user.role == "Manager":
        return user.store_number == item.store_number
    if user.role in EMPLOYEE_SCOPED_ROLES:
        return work_order_created_by_user(user, item) or work_order_assigned_to_user(user, item)
    if user.role == "Technician":
        return work_order_assigned_to_user(user, item)
    return False


def require_work_order_update(user: User, item, action: str = "update") -> None:
    if can_update_work_order(user, item):
        return
    if user.role == "Manager":
        detail = f"Managers can only {action} work orders for their store"
    elif user.role in EMPLOYEE_SCOPED_ROLES:
        detail = f"Employees and Security can only {action} work orders they created or are assigned to"
    elif user.role == "Technician":
        detail = f"Technicians can only {action} work orders assigned to them"
    else:
        detail = f"You do not have permission to {action} this work order"
    raise HTTPException(status_code=403, detail=detail)


def require_work_order_attachment(user: User, item) -> None:
    if can_update_work_order(user, item) or work_order_created_by_user(user, item):
        return
    require_work_order_update(user, item, "add attachments to")


@router.get("/health", response_model=Health, tags=["system"])
def health() -> Health:
    return Health(status="ok", server_time=datetime.now(UTC))


@router.post("/api/auth/login", response_model=LoginResponse, tags=["authentication"])
def login(request: LoginRequest, session: Session = Depends(get_session)):
    login_name = request.username.strip().lower()
    user = session.scalar(
        select(User).where((User.username == login_name) | (User.email == login_name))
    )
    if not user or not user.is_active or not verify_password(request.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Invalid username or password")
    return LoginResponse(token=issue_token(session, user), user=user)


@router.post("/api/auth/register", response_model=LoginResponse, status_code=201, tags=["authentication"])
def register(request: RegistrationRequest, session: Session = Depends(get_session)):
    username = request.username.strip().lower()
    email = request.email.strip().lower()
    if session.scalar(select(User.id).where(User.username == username)):
        raise HTTPException(status_code=409, detail="Username is already in use")
    if session.scalar(select(User.id).where(User.email == email)):
        raise HTTPException(status_code=409, detail="Email is already in use")
    is_jc_administrator = username == "jc"
    user = User(
        username=username,
        password_hash=hash_password(request.password),
        display_name=request.display_name.strip(),
        store_number=99 if is_jc_administrator else request.store_number,
        email=email,
        phone_number=request.phone_number.strip(),
        role="Admin" if is_jc_administrator else "Requester",
    )
    session.add(user)
    session.commit()
    session.refresh(user)
    return LoginResponse(token=issue_token(session, user), user=user)


@router.post("/api/auth/logout", status_code=204, tags=["authentication"])
def logout(user: User = Depends(current_user), session: Session = Depends(get_session)):
    session.execute(delete(SessionToken).where(SessionToken.user_id == user.id))
    session.commit()


@router.get("/api/profile", response_model=UserRead, tags=["profile"])
def profile(user: User = Depends(current_user)):
    return user


@router.patch("/api/profile", response_model=UserRead, tags=["profile"])
def update_profile(
    request: ProfileUpdate,
    user: User = Depends(current_user),
    session: Session = Depends(get_session),
):
    user.display_name = request.display_name.strip()
    user.email = request.email.strip() if request.email else None
    session.commit()
    return user


def _managed_user(user_id: UUID, actor: User, session: Session) -> User:
    target = session.get(User, str(user_id))
    if not target:
        raise HTTPException(status_code=404, detail="User not found")
    if actor.role == "Manager" and (actor.store_number == 99 or target.store_number != actor.store_number):
        raise HTTPException(status_code=403, detail="Managers can only manage users in their store")
    return target


@router.get("/api/users", response_model=list[UserRead], tags=["user management"])
def list_users(
    actor: User = Depends(require_roles("Admin", "Manager")),
    session: Session = Depends(get_session),
):
    statement = select(User).order_by(User.display_name)
    if actor.role == "Manager":
        statement = statement.where(User.store_number == actor.store_number)
    return list(session.scalars(statement))


@router.get("/api/technicians", response_model=list[UserRead], tags=["work orders"])
def list_technicians(
    _: User = Depends(current_user),
    session: Session = Depends(get_session),
):
    statement = (
        select(User)
        .where(User.role == "Technician", User.is_active == 1)
        .order_by(User.display_name)
    )
    return list(session.scalars(statement))


@router.get("/api/assignees", response_model=list[UserRead], tags=["work orders"])
def list_assignees(
    _: User = Depends(current_user),
    session: Session = Depends(get_session),
):
    statement = (
        select(User)
        .where(
            User.is_active == 1,
            or_(
                User.role.in_(ASSIGNABLE_ROLES),
                User.username.in_(SPECIAL_ASSIGNEE_USERNAMES),
            ),
        )
        .order_by(User.role, User.store_number, User.display_name)
    )
    return list(session.scalars(statement))


@router.get(
    "/api/work-order-notification-recipients",
    response_model=list[UserRead],
    tags=["work orders"],
)
def list_work_order_notification_recipients(
    store_number: int = Query(ge=1, le=9999, alias="storeNumber"),
    _: User = Depends(current_user),
    session: Session = Depends(get_session),
):
    statement = (
        select(User)
        .where(
            User.is_active == 1,
            (User.username == "jc")
            | ((User.role == "Manager") & (User.store_number == store_number)),
        )
        .order_by(User.display_name)
    )
    return list(session.scalars(statement))


@router.patch("/api/users/{user_id}", response_model=UserRead, tags=["user management"])
def update_user(
    user_id: UUID,
    request: UserAdminUpdate,
    actor: User = Depends(require_roles("Admin", "Manager")),
    session: Session = Depends(get_session),
):
    target = _managed_user(user_id, actor, session)
    if actor.role == "Manager" and (request.role in {"Admin", "Manager"} or request.store_number == 99):
        raise HTTPException(status_code=403, detail="Only an Administrator can assign this role or All Stores")
    if target.id == actor.id and (not request.is_active or request.role != actor.role):
        raise HTTPException(status_code=400, detail="You cannot deactivate or demote your own account")
    target.display_name = request.display_name.strip()
    target.store_number = request.store_number
    target.role = request.role.value
    target.email = request.email.strip().lower() if request.email else None
    target.phone_number = request.phone_number.strip() if request.phone_number else None
    target.is_active = request.is_active
    session.commit()
    session.refresh(target)
    return target


@router.post("/api/users/{user_id}/reset-password", status_code=204, tags=["user management"])
def reset_user_password(
    user_id: UUID,
    request: PasswordReset,
    actor: User = Depends(require_roles("Admin", "Manager")),
    session: Session = Depends(get_session),
):
    target = _managed_user(user_id, actor, session)
    target.password_hash = hash_password(request.new_password)
    session.execute(delete(SessionToken).where(SessionToken.user_id == target.id))
    session.commit()


@router.get("/api/work-orders", response_model=list[WorkOrderRead], tags=["work orders"])
def list_work_orders(
    work_order_status: Status | None = Query(default=None, alias="status"),
    priority: Priority | None = None,
    search: str | None = None,
    repo: WorkOrderRepository = Depends(repository),
    _: User = Depends(current_user),
):
    return repo.list(work_order_status, priority, search)


@router.get("/api/work-orders/{work_order_id}", response_model=WorkOrderRead, tags=["work orders"])
def get_work_order(
    work_order_id: UUID,
    repo: WorkOrderRepository = Depends(repository),
    _: User = Depends(current_user),
):
    return find_order(work_order_id, repo)


@router.delete("/api/work-orders/{work_order_id}", status_code=204, tags=["work orders"])
def delete_work_order(
    work_order_id: UUID,
    repo: WorkOrderRepository = Depends(repository),
    user: User = Depends(current_user),
):
    if user.username != "jc":
        raise HTTPException(status_code=403, detail="Only jc can delete work orders")
    item = find_order(work_order_id, repo)
    attachment_paths = [UPLOADS / attachment.stored_name for attachment in item.attachments]
    repo.session.delete(item)
    repo.session.commit()
    for path in attachment_paths:
        path.unlink(missing_ok=True)


@router.post(
    "/api/work-orders", response_model=WorkOrderRead, status_code=201, tags=["work orders"]
)
def create_work_order(
    request: WorkOrderCreate,
    repo: WorkOrderRepository = Depends(repository),
    user: User = Depends(current_user),
):
    if user.role != "Admin" and request.store_number != user.store_number:
        raise HTTPException(
            status_code=403,
            detail="Only an Administrator can create work orders for another store",
        )
    request = request.model_copy(update={"requested_by": user.display_name})
    return repo.create(request, user.id)


@router.put("/api/work-orders/{work_order_id}", response_model=WorkOrderRead, tags=["work orders"])
def update_work_order(
    work_order_id: UUID,
    request: WorkOrderUpdate,
    repo: WorkOrderRepository = Depends(repository),
    user: User = Depends(current_user),
):
    item = find_order(work_order_id, repo)
    require_work_order_update(user, item)
    return repo.update(item, request)


@router.patch(
    "/api/work-orders/{work_order_id}/status", response_model=WorkOrderRead, tags=["work orders"]
)
def update_status(
    work_order_id: UUID,
    request: StatusUpdate,
    repo: WorkOrderRepository = Depends(repository),
    user: User = Depends(current_user),
):
    item = find_order(work_order_id, repo)
    require_work_order_update(user, item)
    return repo.update_status(item, request)


@router.post(
    "/api/work-orders/{work_order_id}/attachments",
    response_model=list[AttachmentRead],
    status_code=201,
    tags=["attachments"],
)
async def upload_attachments(
    work_order_id: UUID,
    files: list[UploadFile] = File(...),
    repo: WorkOrderRepository = Depends(repository),
    user: User = Depends(current_user),
):
    item = find_order(work_order_id, repo)
    require_work_order_attachment(user, item)
    if not files:
        raise HTTPException(status_code=422, detail="At least one file is required")
    UPLOADS.mkdir(parents=True, exist_ok=True)
    created = []
    for upload in files:
        content_type = (upload.content_type or "").lower()
        if content_type not in ALLOWED_TYPES:
            raise HTTPException(status_code=415, detail=f"Unsupported media type: {content_type}")
        data = await upload.read(MAX_FILE_SIZE + 1)
        if len(data) > MAX_FILE_SIZE:
            raise HTTPException(status_code=413, detail="Each attachment must be 50 MB or smaller")
        suffix = Path(upload.filename or "attachment").suffix.lower()
        stored_name = f"{uuid4().hex}{suffix}"
        (UPLOADS / stored_name).write_bytes(data)
        attachment = Attachment(
            work_order_id=item.id,
            original_name=Path(upload.filename or "attachment").name,
            stored_name=stored_name,
            content_type=content_type,
            size_bytes=len(data),
            uploaded_by_id=user.id,
        )
        repo.session.add(attachment)
        created.append(attachment)
    repo.session.commit()
    return created


@router.get("/api/attachments/{attachment_id}/content", tags=["attachments"])
def attachment_content(
    attachment_id: UUID, session: Session = Depends(get_session), _: User = Depends(current_user)
):
    attachment = session.get(Attachment, str(attachment_id))
    if not attachment or not (UPLOADS / attachment.stored_name).is_file():
        raise HTTPException(status_code=404, detail="Attachment not found")
    return FileResponse(
        UPLOADS / attachment.stored_name,
        media_type=attachment.content_type,
        filename=attachment.original_name,
    )


@router.delete("/api/attachments/{attachment_id}", status_code=204, tags=["attachments"])
def delete_attachment(
    attachment_id: UUID,
    session: Session = Depends(get_session),
    user: User = Depends(require_roles("Admin", "Manager")),
):
    attachment = session.get(Attachment, str(attachment_id))
    if not attachment:
        raise HTTPException(status_code=404, detail="Attachment not found")
    require_work_order_store(user, attachment.work_order)
    path = UPLOADS / attachment.stored_name
    session.delete(attachment)
    session.commit()
    path.unlink(missing_ok=True)
