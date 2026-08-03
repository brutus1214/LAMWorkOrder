from datetime import UTC, datetime
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

from .database import get_session
from .repository import WorkOrderRepository
from .schemas import Health, Priority, Status, StatusUpdate, WorkOrderCreate, WorkOrderRead

router = APIRouter()


def repository(session: Session = Depends(get_session)) -> WorkOrderRepository:
    return WorkOrderRepository(session)


@router.get("/health", response_model=Health, tags=["system"])
def health() -> Health:
    return Health(status="ok", server_time=datetime.now(UTC))


@router.get("/api/work-orders", response_model=list[WorkOrderRead], tags=["work orders"])
def list_work_orders(
    work_order_status: Status | None = Query(default=None, alias="status"),
    priority: Priority | None = None,
    search: str | None = None,
    repo: WorkOrderRepository = Depends(repository),
):
    return repo.list(work_order_status, priority, search)


@router.get("/api/work-orders/{work_order_id}", response_model=WorkOrderRead, tags=["work orders"])
def get_work_order(work_order_id: UUID, repo: WorkOrderRepository = Depends(repository)):
    item = repo.get(str(work_order_id))
    if not item:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Work order not found")
    return item


@router.post(
    "/api/work-orders",
    response_model=WorkOrderRead,
    status_code=status.HTTP_201_CREATED,
    tags=["work orders"],
)
def create_work_order(request: WorkOrderCreate, repo: WorkOrderRepository = Depends(repository)):
    return repo.create(request)


@router.patch(
    "/api/work-orders/{work_order_id}/status",
    response_model=WorkOrderRead,
    tags=["work orders"],
)
def update_status(
    work_order_id: UUID,
    request: StatusUpdate,
    repo: WorkOrderRepository = Depends(repository),
):
    item = repo.get(str(work_order_id))
    if not item:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Work order not found")
    return repo.update_status(item, request)
